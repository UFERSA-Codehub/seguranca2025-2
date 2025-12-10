#!/bin/bash

###############################################################################
# Script de Inicialização do Sistema de Monitoramento Ambiental (DMZ)
#
# Arquitetura:
#   DMZ:      PacketFilter -> ReverseProxy -> Discovery + IDS
#   Interna:  AuthServer, Edge, Datacenter
#
# Layout:
#   Window 1 [DMZ]:         PacketFilter | ReverseProxy | IDS
#   Window 2 [Interna]:     Discovery | AuthServer | Edge | Datacenter
#   Window 3 [Sensores]:    Sensor 001-004 (2x2 grid)
#   Window 4 [Testes]:      MaliciousSensor (manual) | ClientApp (manual)
#
# Ordem de Inicializacao:
#   1. Discovery (UDP:4000)
#   2. IDS (TCP:3002)
#   3. AuthServer (TCP:4001)
#   4. Edge (TCP:5000, IDS:5001)
#   5. Datacenter (TCP:8080, HTTP:9090)
#   6. ReverseProxy (TCP:3001, 3011, 3021)
#   7. PacketFilter (TCP:3000, 3010, 3020)
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
echo "║               SISTEMA DE MONITORAMENTO AMBIENTAL               ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

###############################################################################
# FASE 2: CRIAR ESTRUTURA TMUX
###############################################################################

echo -e "${GREEN}Criando sessão tmux com 4 janelas...${NC}"
echo ""

# Criar sessão com janela "Interna" (índice base 1)
tmux new-session -d -s "$TMUX_SESSION" -n "Interna"

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
# INICIAR SERVIDORES (ordem critica para conexoes)
###############################################################################

echo -e "${GREEN}[1/7]${NC} Iniciando Discovery Service (UDP:4000)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/discovery.log" mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$DISCOVERY_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
DISCOVERY_PID=$!
echo "$DISCOVERY_PID" > "$PID_DIR/discovery.pid"
echo -e "${GREEN}  ✓${NC} Discovery iniciado (PID: $DISCOVERY_PID)"
sleep $DISCOVERY_DELAY

echo -e "${GREEN}[2/7]${NC} Iniciando IDS (TCP:3002)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/ids.log" mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$IDS_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
IDS_PID=$!
echo "$IDS_PID" > "$PID_DIR/ids.pid"
echo -e "${GREEN}  ✓${NC} IDS iniciado (PID: $IDS_PID)"
sleep $IDS_DELAY

echo -e "${GREEN}[3/7]${NC} Iniciando AuthServer (TCP:4001)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/auth.log" mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$AUTH_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
AUTH_PID=$!
echo "$AUTH_PID" > "$PID_DIR/auth.pid"
echo -e "${GREEN}  ✓${NC} AuthServer iniciado (PID: $AUTH_PID)"
sleep $AUTH_DELAY

echo -e "${GREEN}[4/7]${NC} Iniciando Edge Server (TCP:5000, IDS:5001)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/edge.log" mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$EDGE_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
EDGE_PID=$!
echo "$EDGE_PID" > "$PID_DIR/edge.pid"
echo -e "${GREEN}  ✓${NC} Edge Server iniciado (PID: $EDGE_PID)"
sleep $EDGE_DELAY

echo -e "${GREEN}[5/7]${NC} Iniciando Datacenter (TCP:8080, HTTP:9090)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/datacenter.log" mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$DATACENTER_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
DATACENTER_PID=$!
echo "$DATACENTER_PID" > "$PID_DIR/datacenter.pid"
echo -e "${GREEN}  ✓${NC} Datacenter iniciado (PID: $DATACENTER_PID)"
sleep $DATACENTER_DELAY

echo -e "${GREEN}[6/7]${NC} Iniciando ReverseProxy (TCP:3001, 3011, 3021)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/proxy.log" mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$PROXY_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
PROXY_PID=$!
echo "$PROXY_PID" > "$PID_DIR/proxy.pid"
echo -e "${GREEN}  ✓${NC} ReverseProxy iniciado (PID: $PROXY_PID)"
sleep $PROXY_DELAY

echo -e "${GREEN}[7/7]${NC} Iniciando PacketFilter (TCP:3000, 3010, 3020)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/pfilter.log" mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$PFILTER_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
PFILTER_PID=$!
echo "$PFILTER_PID" > "$PID_DIR/pfilter.pid"
echo -e "${GREEN}  ✓${NC} PacketFilter iniciado (PID: $PFILTER_PID)"
sleep $PFILTER_DELAY

###############################################################################
# JANELA 1: INTERNA (Discovery, AuthServer, Edge, Datacenter)
###############################################################################

# Criar painéis: split horizontal para 4 colunas
tmux split-window -h -t "$TMUX_SESSION:Interna"
tmux split-window -h -t "$TMUX_SESSION:Interna.0"
tmux split-window -h -t "$TMUX_SESSION:Interna.2"

# Aplicar layout horizontal uniforme
tmux select-layout -t "$TMUX_SESSION:Interna" even-horizontal

# Enviar comandos para cada painel
tmux send-keys -t "$TMUX_SESSION:Interna.0" "tail -F --retry '$LOG_DIR/discovery.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Interna.1" "tail -F --retry '$LOG_DIR/auth.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Interna.2" "tail -F --retry '$LOG_DIR/edge.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Interna.3" "tail -F --retry '$LOG_DIR/datacenter.log'" C-m

# Definir títulos APÓS enviar comandos
tmux select-pane -t "$TMUX_SESSION:Interna.0" -T "🔍 Discovery (UDP:4000)"
tmux select-pane -t "$TMUX_SESSION:Interna.1" -T "🔑 AuthServer (TCP:4001)"
tmux select-pane -t "$TMUX_SESSION:Interna.2" -T "🌐 Edge (TCP:5000)"
tmux select-pane -t "$TMUX_SESSION:Interna.3" -T "💾 Datacenter (TCP:8080)"

###############################################################################
# JANELA 2: DMZ (PacketFilter, ReverseProxy, IDS)
###############################################################################

tmux new-window -t "$TMUX_SESSION" -n "DMZ"

# Criar painéis: split horizontal para 3 colunas
tmux split-window -h -t "$TMUX_SESSION:DMZ"
tmux split-window -h -t "$TMUX_SESSION:DMZ.0"

# Aplicar layout horizontal uniforme
tmux select-layout -t "$TMUX_SESSION:DMZ" even-horizontal

# Enviar comandos para cada painel
tmux send-keys -t "$TMUX_SESSION:DMZ.0" "tail -F --retry '$LOG_DIR/pfilter.log'" C-m
tmux send-keys -t "$TMUX_SESSION:DMZ.1" "tail -F --retry '$LOG_DIR/proxy.log'" C-m
tmux send-keys -t "$TMUX_SESSION:DMZ.2" "tail -F --retry '$LOG_DIR/ids.log'" C-m

# Definir títulos APÓS enviar comandos
tmux select-pane -t "$TMUX_SESSION:DMZ.0" -T "🔥 PacketFilter (TCP:3000,3010,3020)"
tmux select-pane -t "$TMUX_SESSION:DMZ.1" -T "🛡️ ReverseProxy (TCP:3001,3011,3021)"
tmux select-pane -t "$TMUX_SESSION:DMZ.2" -T "🚨 IDS (TCP:3002)"

###############################################################################
# JANELA 3: SENSORES (Sensor 001-004)
###############################################################################

echo -e "${GREEN}[Sensores]${NC} Iniciando 4 sensores..."

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
    
    MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/$sensor_id.log" mvn -f "$POM_FILE" exec:java \
        -Dexec.mainClass="$SENSOR_CLASS" \
        -Dexec.args="$sensor_args" \
        -Dexec.cleanupDaemonThreads=false \
        > /dev/null 2>&1 &
    
    SENSOR_PID=$!
    echo "$SENSOR_PID" > "$PID_DIR/$sensor_id.pid"
    
    tmux send-keys -t "$TMUX_SESSION:Sensores.$painel_idx" "tail -F --retry '$LOG_DIR/$sensor_id.log'" C-m
    
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
# JANELA 4: TESTES (MaliciousSensor, ClientApp)
###############################################################################

tmux new-window -t "$TMUX_SESSION" -n "Testes"

# Criar painel adicional
tmux split-window -h -t "$TMUX_SESSION:Testes"

# Enviar comandos para cada painel (preparados, nao executam)
tmux send-keys -t "$TMUX_SESSION:Testes.0" "cd '$PROJECT_DIR' && mvn exec:java -Dexec.mainClass='$MALICIOUS_SENSOR_CLASS' -Dexec.args='--mode ANOMALY_DATA --password sensor123'"
tmux send-keys -t "$TMUX_SESSION:Testes.1" "cd '$PROJECT_DIR' && mvn exec:java -Dexec.mainClass='$CLIENT_APP_CLASS'"

# Definir títulos APÓS enviar comandos
tmux select-pane -t "$TMUX_SESSION:Testes.0" -T "⚠️ MaliciousSensor (ENTER para iniciar)"
tmux select-pane -t "$TMUX_SESSION:Testes.1" -T "👤 ClientApp (ENTER para iniciar)"

###############################################################################
# FASE 3: FINALIZAÇÃO
###############################################################################

# Voltar para janela Interna
tmux select-window -t "$TMUX_SESSION:Interna"
tmux select-pane -t "$TMUX_SESSION:Interna.0"

echo ""
echo -e "${GREEN}✅ Sistema DMZ iniciado com sucesso!${NC}"
echo ""
sleep 1

###############################################################################
# FASE 4: ANEXAR À SESSÃO TMUX
###############################################################################

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║            SISTEMA DE MONITORAMENTO AMBIENTAL (DMZ)            ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Arquitetura:                                                  ║"
echo "║    Sensor -> PacketFilter -> ReverseProxy -> Edge              ║"
echo "║                               ↓                                ║"
echo "║                              IDS                               ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Layout: 4 janelas tmux                                        ║"
echo "║                                                                ║"
echo "║  Ctrl+B 1  [Interna]:  Discovery | Auth | Edge | Datacenter    ║"
echo "║  Ctrl+B 2  [DMZ]:      PacketFilter | ReverseProxy | IDS       ║"
echo "║  Ctrl+B 3  [Sensores]: Sensor 001-004 (grid 2x2)               ║"
echo "║  Ctrl+B 4  [Testes]:   MaliciousSensor | ClientApp (manual)    ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Atalhos:                                                      ║"
echo "║    Ctrl+B <n>        - Ir para janela n                        ║"
echo "║    Ctrl+B Setas      - Navegar entre painéis                   ║"
echo "║    Ctrl+B [          - Modo scroll (Q para sair)               ║"
echo "║    Ctrl+B Z          - Zoom no painel atual                    ║"
echo "║    Ctrl+B D          - Desanexar (processos continuam)         ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Janela 4: Pressione ENTER para iniciar testes manualmente     ║"
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
