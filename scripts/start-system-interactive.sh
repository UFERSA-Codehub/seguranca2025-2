#!/bin/bash

###############################################################################
# Script de Inicialização do Sistema de Monitoramento Ambiental
#
# Layout:
#   Window 1 [Serviços]:   Discovery | Datacenter | Edge
#   Window 2 [Sensores]:   Sensor 001-004 (2x2 grid)
#   Window 3 [Edge]:       Edge log | MaliciousSensor (manual)
#   Window 4 [Datacenter]: Datacenter log | ClientApp (manual)
#
# Uso:
#   ./scripts/start-system-interactive.sh
###############################################################################

# Importar configurações
source "$(dirname "$0")/config.sh"

###############################################################################
# FASE 1: VERIFICAÇÕES
###############################################################################

if ! command -v tmux &> /dev/null; then
    echo -e "${RED}❌ ERRO: tmux não está instalado.${NC}"
    echo ""
    echo "Instale com:"
    echo "  Linux:  sudo apt install tmux"
    echo "  macOS:  brew install tmux"
    echo ""
    exit 1
fi

if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  Sessão tmux '$TMUX_SESSION' já existe.${NC}"
    echo ""
    read -p "Deseja anexar à sessão existente? (s/n): " resposta
    if [[ "$resposta" =~ ^[Ss]$ ]]; then
        tmux attach-session -t "$TMUX_SESSION"
        exit 0
    else
        echo "Saindo..."
        exit 0
    fi
fi

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║       SISTEMA DE MONITORAMENTO AMBIENTAL                       ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

###############################################################################
# FASE 2: CRIAR ESTRUTURA TMUX
###############################################################################

echo -e "${GREEN}📊 Criando sessão tmux com 4 janelas...${NC}"
echo ""

# Criar sessão com janela "Serviços" (índice base 1)
tmux new-session -d -s "$TMUX_SESSION" -n "Serviços"

# Configurar índice base 1 para janelas
tmux set-option -t "$TMUX_SESSION" base-index 1
tmux set-option -t "$TMUX_SESSION" pane-base-index 0

# Mover a primeira janela para índice 1 (foi criada como 0)
tmux move-window -s "$TMUX_SESSION:0" -t "$TMUX_SESSION:1"

# Habilitar títulos de painéis (global para todas as janelas)
tmux set-option -g pane-border-status top
tmux set-option -g pane-border-format " #{pane_title} "

# Desabilitar renomeação automática (preservar títulos customizados)
tmux set-option -t "$TMUX_SESSION" allow-rename off
tmux set-option -g allow-set-title off
tmux set-window-option -g automatic-rename off

###############################################################################
# JANELA 1: SERVIÇOS (Discovery, Datacenter, Edge)
###############################################################################

echo -e "${GREEN}[1/4]${NC} Iniciando Discovery Service (UDP:4000)..."

# Iniciar Discovery em background
mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$DISCOVERY_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > "$LOG_DIR/discovery.log" 2>&1 &
DISCOVERY_PID=$!
echo "$DISCOVERY_PID" > "$PID_DIR/discovery.pid"

echo -e "${GREEN}✓${NC} Discovery iniciado (PID: $DISCOVERY_PID)"
sleep $DISCOVERY_DELAY

echo -e "${GREEN}[2/4]${NC} Iniciando Datacenter (TCP:8080, HTTP:9090)..."

# Iniciar Datacenter em background
mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$DATACENTER_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > "$LOG_DIR/datacenter.log" 2>&1 &
DATACENTER_PID=$!
echo "$DATACENTER_PID" > "$PID_DIR/datacenter.pid"

echo -e "${GREEN}✓${NC} Datacenter iniciado (PID: $DATACENTER_PID)"
sleep $DATACENTER_DELAY

echo -e "${GREEN}[3/4]${NC} Iniciando Edge Server (UDP:5000)..."

# Iniciar Edge em background
mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$EDGE_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > "$LOG_DIR/edge.log" 2>&1 &
EDGE_PID=$!
echo "$EDGE_PID" > "$PID_DIR/edge.pid"

echo -e "${GREEN}✓${NC} Edge Server iniciado (PID: $EDGE_PID)"
sleep $EDGE_DELAY

# Criar painéis: split horizontal para 3 colunas
tmux split-window -h -t "$TMUX_SESSION:Serviços"
tmux split-window -h -t "$TMUX_SESSION:Serviços.0"

# Aplicar layout horizontal uniforme
tmux select-layout -t "$TMUX_SESSION:Serviços" even-horizontal

# Enviar comandos para cada painel
tmux send-keys -t "$TMUX_SESSION:Serviços.0" "tail -f '$LOG_DIR/discovery.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Serviços.1" "tail -f '$LOG_DIR/datacenter.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Serviços.2" "tail -f '$LOG_DIR/edge.log'" C-m

# Definir títulos APÓS enviar comandos
tmux select-pane -t "$TMUX_SESSION:Serviços.0" -T "🔍 Discovery (UDP:4000)"
tmux select-pane -t "$TMUX_SESSION:Serviços.1" -T "💾 Datacenter (TCP:8080)"
tmux select-pane -t "$TMUX_SESSION:Serviços.2" -T "🌐 Edge (UDP:5000)"

###############################################################################
# JANELA 2: SENSORES (Sensor 001-004)
###############################################################################

echo -e "${GREEN}[4/4]${NC} Iniciando Sensores (4 processos)..."

# Criar janela "Sensores"
tmux new-window -t "$TMUX_SESSION" -n "Sensores"

# Criar layout 2x2: primeiro split horizontal, depois vertical em cada lado
tmux split-window -h -t "$TMUX_SESSION:Sensores"
tmux split-window -v -t "$TMUX_SESSION:Sensores.0"
tmux split-window -v -t "$TMUX_SESSION:Sensores.1"

# Aplicar layout tiled para grid 2x2 perfeito
tmux select-layout -t "$TMUX_SESSION:Sensores" tiled

# Iniciar sensores em background e enviar comandos para painéis
painel_idx=0
for sensor_config in "${SENSORS[@]}"; do
    IFS='|' read -r sensor_id password <<< "$sensor_config"
    
    sensor_args="$sensor_id $password $DISCOVERY_HOST $DISCOVERY_PORT"
    
    mvn -f "$POM_FILE" exec:java \
        -Dexec.mainClass="$SENSOR_CLASS" \
        -Dexec.args="$sensor_args" \
        -Dexec.cleanupDaemonThreads=false \
        > "$LOG_DIR/$sensor_id.log" 2>&1 &
    
    SENSOR_PID=$!
    echo "$SENSOR_PID" > "$PID_DIR/$sensor_id.pid"
    
    tmux send-keys -t "$TMUX_SESSION:Sensores.$painel_idx" "tail -f '$LOG_DIR/$sensor_id.log'" C-m
    
    echo -e "${GREEN}  ✓${NC} $sensor_id iniciado (PID: $SENSOR_PID)"
    sleep $SENSOR_DELAY
    
    ((painel_idx++))
done

# Definir títulos APÓS enviar comandos
tmux select-pane -t "$TMUX_SESSION:Sensores.0" -T "📡 SENSOR_001"
tmux select-pane -t "$TMUX_SESSION:Sensores.1" -T "📡 SENSOR_002"
tmux select-pane -t "$TMUX_SESSION:Sensores.2" -T "📡 SENSOR_003"
tmux select-pane -t "$TMUX_SESSION:Sensores.3" -T "📡 SENSOR_004"

###############################################################################
# JANELA 3: EDGE + MALICIOUS SENSOR (manual)
###############################################################################

tmux new-window -t "$TMUX_SESSION" -n "Edge"

# Criar painel adicional
tmux split-window -h -t "$TMUX_SESSION:Edge"

# Enviar comandos para cada painel
tmux send-keys -t "$TMUX_SESSION:Edge.0" "tail -f '$LOG_DIR/edge.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Edge.1" "cd '$PROJECT_DIR' && ./scripts/stop-sensors.sh && mvn exec:java -Dexec.mainClass='$MALICIOUS_SENSOR_CLASS'"

# Definir títulos APÓS enviar comandos
tmux select-pane -t "$TMUX_SESSION:Edge.0" -T "🌐 Edge Server (log)"
tmux select-pane -t "$TMUX_SESSION:Edge.1" -T "⚠️ MaliciousSensor (manual)"

###############################################################################
# JANELA 4: DATACENTER + CLIENT APP (manual)
###############################################################################

tmux new-window -t "$TMUX_SESSION" -n "Datacenter"

# Criar painel adicional
tmux split-window -h -t "$TMUX_SESSION:Datacenter"

# Enviar comandos para cada painel
tmux send-keys -t "$TMUX_SESSION:Datacenter.0" "tail -f '$LOG_DIR/datacenter.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Datacenter.1" "cd '$PROJECT_DIR' && mvn exec:java -Dexec.mainClass='$CLIENT_APP_CLASS'"

# Definir títulos APÓS enviar comandos
tmux select-pane -t "$TMUX_SESSION:Datacenter.0" -T "💾 Datacenter (log)"
tmux select-pane -t "$TMUX_SESSION:Datacenter.1" -T "👤 ClientApp (manual)"

###############################################################################
# FASE 3: FINALIZAÇÃO
###############################################################################

# Voltar para janela Serviços
tmux select-window -t "$TMUX_SESSION:Serviços"
tmux select-pane -t "$TMUX_SESSION:Serviços.0"

echo ""
echo -e "${GREEN}✅ Sistema iniciado com sucesso!${NC}"
echo ""
sleep 1

###############################################################################
# FASE 4: ANEXAR À SESSÃO TMUX
###############################################################################

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    MONITOR DO SISTEMA                          ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Layout: 4 janelas tmux                                        ║"
echo "║                                                                ║"
echo "║  Ctrl+B 1  [Serviços]:   Discovery | Datacenter | Edge         ║"
echo "║  Ctrl+B 2  [Sensores]:   Sensor 001-004 (grid 2x2)             ║"
echo "║  Ctrl+B 3  [Edge]:       Edge log | MaliciousSensor (manual)   ║"
echo "║  Ctrl+B 4  [Datacenter]: Datacenter log | ClientApp (manual)   ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Atalhos:                                                      ║"
echo "║    Ctrl+B <n>        - Ir para janela n                        ║"
echo "║    Ctrl+B Setas      - Navegar entre painéis                   ║"
echo "║    Ctrl+B [          - Modo scroll (Q para sair)               ║"
echo "║    Ctrl+B Z          - Zoom no painel atual                    ║"
echo "║    Ctrl+B D          - Desanexar (processos continuam)         ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Janelas 3 e 4: Pressione ENTER para iniciar manualmente       ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
sleep 2

tmux attach-session -t "$TMUX_SESSION"

###############################################################################
# FASE 5: CLEANUP AO SAIR
###############################################################################

echo ""
echo -e "${YELLOW}🛑 Parando serviços...${NC}"
echo ""

for pid_file in "$PID_DIR"/*.pid; do
    if [ -f "$pid_file" ]; then
        pid=$(cat "$pid_file")
        service_name=$(basename "$pid_file" .pid)
        
        if ps -p "$pid" > /dev/null 2>&1; then
            echo -e "${YELLOW}  • Parando $service_name (PID: $pid)...${NC}"
            kill -TERM "$pid" 2>/dev/null
            
            for i in {1..3}; do
                if ! ps -p "$pid" > /dev/null 2>&1; then
                    break
                fi
                sleep 1
            done
            
            if ps -p "$pid" > /dev/null 2>&1; then
                kill -9 "$pid" 2>/dev/null
            fi
        fi
        
        rm -f "$pid_file"
    fi
done

tmux kill-session -t "$TMUX_SESSION" 2>/dev/null

echo ""
echo -e "${GREEN}✅ Sistema encerrado.${NC}"
echo ""
