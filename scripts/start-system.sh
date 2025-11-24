#!/bin/bash

###############################################################################
# Script de Inicialização do Sistema de Monitoramento Ambiental
#
# Fluxo:
#   1. Verifica se tmux está instalado
#   2. Verifica se sessão tmux já existe
#   3. Inicia serviços na ordem: Discovery → Datacenter → Edge → Sensores
#   4. Cria sessão tmux com layout 2x5 (8 painéis)
#   5. Anexa automaticamente à sessão
#   6. Cleanup ao sair (mata processos)
#
# Uso:
#   ./scripts/start-system.sh
###############################################################################

# Importar configurações
source "$(dirname "$0")/config.sh"

###############################################################################
# FASE 1: VERIFICAÇÕES
###############################################################################

# Verificar se tmux está instalado
if ! command -v tmux &> /dev/null; then
    echo -e "${RED}❌ ERRO: tmux não está instalado.${NC}"
    echo ""
    echo "Instale com:"
    echo "  Linux:  sudo apt install tmux"
    echo "  macOS:  brew install tmux"
    echo ""
    exit 1
fi

# Verificar se sessão tmux já existe
if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  AVISO: Sessão tmux '$TMUX_SESSION' já existe.${NC}"
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
echo "║     SISTEMA DE MONITORAMENTO AMBIENTAL - INICIALIZAÇÃO         ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

###############################################################################
# FASE 2: INICIAR SERVIÇOS
###############################################################################

echo -e "${GREEN}[1/4]${NC} Iniciando Discovery Service (UDP:4000)..."
mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$DISCOVERY_CLASS" \
    -Dexec.args="--daemon" \
    -Dexec.cleanupDaemonThreads=false \
    > "$LOG_DIR/discovery.log" 2>&1 &
DISCOVERY_PID=$!
echo "$DISCOVERY_PID" > "$PID_DIR/discovery.pid"
sleep $DISCOVERY_DELAY
echo -e "${GREEN}✓${NC} Discovery iniciado (PID: $DISCOVERY_PID)"
echo ""

echo -e "${GREEN}[2/4]${NC} Iniciando Datacenter (TCP:8080, HTTP:9090)..."
mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$DATACENTER_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > "$LOG_DIR/datacenter.log" 2>&1 &
DATACENTER_PID=$!
echo "$DATACENTER_PID" > "$PID_DIR/datacenter.pid"
sleep $DATACENTER_DELAY
echo -e "${GREEN}✓${NC} Datacenter iniciado (PID: $DATACENTER_PID)"
echo ""

echo -e "${GREEN}[3/4]${NC} Iniciando Edge Server (UDP:5000)..."
mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$EDGE_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > "$LOG_DIR/edge.log" 2>&1 &
EDGE_PID=$!
echo "$EDGE_PID" > "$PID_DIR/edge.pid"
sleep $EDGE_DELAY
echo -e "${GREEN}✓${NC} Edge Server iniciado (PID: $EDGE_PID)"
echo ""

echo -e "${GREEN}[4/4]${NC} Iniciando Sensores (5 processos)..."
for sensor_config in "${SENSORS[@]}"; do
    IFS='|' read -r sensor_id nome localizacao token <<< "$sensor_config"
    
    # Construir argumentos com aspas para campos com espaços
    sensor_args="$sensor_id \"$nome\" \"$localizacao\" $token $EDGE_HOST $EDGE_PORT"
    
    mvn -f "$POM_FILE" exec:java \
        -Dexec.mainClass="$SENSOR_CLASS" \
        -Dexec.args="$sensor_args" \
        -Dexec.cleanupDaemonThreads=false \
        > "$LOG_DIR/$sensor_id.log" 2>&1 &
    
    SENSOR_PID=$!
    echo "$SENSOR_PID" > "$PID_DIR/$sensor_id.pid"
    echo -e "${GREEN}  ✓${NC} $sensor_id iniciado (PID: $SENSOR_PID)"
    sleep $SENSOR_DELAY
done
echo ""

echo -e "${GREEN}✅ Todos os serviços iniciados com sucesso!${NC}"
echo ""
sleep 2

###############################################################################
# FASE 3: CRIAR SESSÃO TMUX COM LAYOUT
###############################################################################

echo -e "${GREEN}📊 Criando sessão tmux com 2 janelas...${NC}"
echo ""

###############################################################################
# JANELA 0: SERVIÇOS (Discovery, Datacenter, Edge)
###############################################################################

# Criar sessão com janela "Serviços"
tmux new-session -d -s "$TMUX_SESSION" -n "Serviços" \
    "tail -f $LOG_DIR/discovery.log"

# Adicionar Datacenter e Edge
tmux split-window -h -t "$TMUX_SESSION:Serviços" \
    "tail -f $LOG_DIR/datacenter.log"

tmux split-window -h -t "$TMUX_SESSION:Serviços.1" \
    "tail -f $LOG_DIR/edge.log"

# Layout tiled para distribuir igualmente os 3 painéis
tmux select-layout -t "$TMUX_SESSION:Serviços" tiled

# Títulos da janela Serviços
tmux select-pane -t "$TMUX_SESSION:Serviços.0" -T "🔍 Discovery (UDP:4000)"
tmux select-pane -t "$TMUX_SESSION:Serviços.1" -T "💾 Datacenter (TCP:8080)"
tmux select-pane -t "$TMUX_SESSION:Serviços.2" -T "🌐 Edge (UDP:5000)"

###############################################################################
# JANELA 1: SENSORES (Sensor 1, 2, 3, 4, 999)
###############################################################################

# Criar janela "Sensores"
tmux new-window -t "$TMUX_SESSION" -n "Sensores" \
    "tail -f $LOG_DIR/SENSOR_001.log"

# Adicionar sensores 2, 3, 4, 999
tmux split-window -h -t "$TMUX_SESSION:Sensores" \
    "tail -f $LOG_DIR/SENSOR_002.log"

tmux split-window -h -t "$TMUX_SESSION:Sensores.1" \
    "tail -f $LOG_DIR/SENSOR_003.log"

tmux split-window -v -t "$TMUX_SESSION:Sensores.0" \
    "tail -f $LOG_DIR/SENSOR_004.log"

tmux split-window -h -t "$TMUX_SESSION:Sensores.3" \
    "tail -f $LOG_DIR/SENSOR_999.log"

# Layout tiled para distribuir os 5 painéis
tmux select-layout -t "$TMUX_SESSION:Sensores" tiled

# Títulos da janela Sensores
tmux select-pane -t "$TMUX_SESSION:Sensores.0" -T "📡 Sensor 1 (Parque)"
tmux select-pane -t "$TMUX_SESSION:Sensores.1" -T "📡 Sensor 2 (Industrial)"
tmux select-pane -t "$TMUX_SESSION:Sensores.2" -T "📡 Sensor 3 (Comercial)"
tmux select-pane -t "$TMUX_SESSION:Sensores.3" -T "📡 Sensor 4 (Residencial)"
tmux select-pane -t "$TMUX_SESSION:Sensores.4" -T "⚠️ Sensor 999 (Malicioso)"

###############################################################################
# CONFIGURAÇÕES GLOBAIS
###############################################################################

# Habilitar títulos de painéis em todas as janelas
tmux set-option -t "$TMUX_SESSION" pane-border-status top
tmux set-option -t "$TMUX_SESSION" pane-border-format " #{pane_title} "

# Voltar para janela Serviços (painel Discovery)
tmux select-window -t "$TMUX_SESSION:Serviços"
tmux select-pane -t "$TMUX_SESSION:Serviços.0"

echo -e "${GREEN}✅ Sessão tmux criada com sucesso!${NC}"
echo -e "${GREEN}   Janela 0: Serviços (3 painéis)${NC}"
echo -e "${GREEN}   Janela 1: Sensores (5 painéis)${NC}"
echo ""

###############################################################################
# FASE 4: ANEXAR À SESSÃO TMUX
###############################################################################

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                 ENTRANDO NO MONITOR DO SISTEMA                 ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Layout: 2 janelas tmux com 8 painéis                          ║"
echo "║                                                                ║"
echo "║  JANELA 0 [Serviços]:  Discovery | Datacenter | Edge           ║"
echo "║  JANELA 1 [Sensores]:  Sensor 1-4 | Sensor 999 (Malicioso)     ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Navegação entre janelas:                                      ║"
echo "║    Ctrl+B 0          - Ir para janela Serviços                 ║"
echo "║    Ctrl+B 1          - Ir para janela Sensores                 ║"
echo "║    Ctrl+B n          - Próxima janela                          ║"
echo "║    Ctrl+B p          - Janela anterior                         ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Atalhos úteis:                                                ║"
echo "║    Ctrl+B Setas      - Navegar entre painéis                   ║"
echo "║    Ctrl+B [          - Modo scroll (Q para sair)               ║"
echo "║    Ctrl+B Z          - Zoom no painel atual                    ║"
echo "║    Ctrl+B D          - Desanexar (logs continuam)              ║"
echo "║    Ctrl+D            - Sair e parar sistema                    ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
sleep 3

# Anexar à sessão tmux
tmux attach-session -t "$TMUX_SESSION"

###############################################################################
# FASE 5: CLEANUP AO SAIR
###############################################################################

echo ""
echo -e "${YELLOW}🛑 Parando todos os serviços...${NC}"
echo ""

# Matar todos os processos salvos em PIDs
for pid_file in "$PID_DIR"/*.pid; do
    if [ -f "$pid_file" ]; then
        pid=$(cat "$pid_file")
        service_name=$(basename "$pid_file" .pid)
        
        if ps -p "$pid" > /dev/null 2>&1; then
            echo -e "${YELLOW}  • Parando $service_name (PID: $pid)...${NC}"
            kill -TERM "$pid" 2>/dev/null
            
            # Aguardar até 3 segundos para parada graceful
            for i in {1..3}; do
                if ! ps -p "$pid" > /dev/null 2>&1; then
                    break
                fi
                sleep 1
            done
            
            # Forçar se ainda estiver rodando
            if ps -p "$pid" > /dev/null 2>&1; then
                kill -9 "$pid" 2>/dev/null
            fi
        fi
        
        rm -f "$pid_file"
    fi
done

# Remover sessão tmux se ainda existir
tmux kill-session -t "$TMUX_SESSION" 2>/dev/null

echo ""
echo -e "${GREEN}✅ Sistema encerrado com sucesso.${NC}"
echo ""
