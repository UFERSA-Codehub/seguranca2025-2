#!/bin/bash

###############################################################################
# WSL3 - External Zone: Sensors, Clients
#
# Execute este script na terceira instancia WSL (External/Clients)
#
# Uso:
#   export WSL1_IP=<ip-do-wsl1-dmz>
#   ./start-wsl3-external.sh
###############################################################################

source "$(dirname "$0")/config-distributed.sh"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                   WSL3 - EXTERNAL ZONE                         ║"
echo "║                   Sensors, Clients                             ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

validate_ips
show_config

# Verificar tmux
if ! command -v tmux &> /dev/null; then
    echo -e "${RED}❌ ERRO: tmux não está instalado.${NC}"
    exit 1
fi

TMUX_SESSION="external-zone"

if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  Sessão '$TMUX_SESSION' já existe.${NC}"
    read -p "Anexar? (s/n): " resp
    [[ "$resp" =~ ^[Ss]$ ]] && tmux attach-session -t "$TMUX_SESSION" && exit 0
    exit 0
fi

###############################################################################
# Verificar conectividade com DMZ
###############################################################################

echo -e "${YELLOW}Verificando conectividade com DMZ ($DMZ_HOST)...${NC}"

# Tentar conectar ao PacketFilter UDP
if ! nc -zu "$DMZ_HOST" "$DISCOVERY_PF_PORT" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  PacketFilter em $DMZ_HOST:$DISCOVERY_PF_PORT nao responde${NC}"
    echo "Certifique-se que WSL1 (DMZ) e WSL2 (Internal) estao rodando."
    read -p "Continuar mesmo assim? (s/n): " resp
    [[ "$resp" =~ ^[Ss]$ ]] || exit 1
else
    echo -e "${GREEN}  ✓${NC} Conectividade OK"
fi

###############################################################################
# Compilar
###############################################################################

echo -e "${GREEN}[Compilando]${NC} Preparando projeto..."
mvn -f "$POM_FILE" compile -q
echo -e "${GREEN}  ✓${NC} Compilacao concluida"

rm -f "$LOG_DIR"/*.log 2>/dev/null

###############################################################################
# Criar sessao tmux para sensores e testes
###############################################################################

tmux new-session -d -s "$TMUX_SESSION" -n "Sensores"

tmux set-option -g pane-border-status top
tmux set-option -g pane-border-format " #{pane_title} "

###############################################################################
# Iniciar Sensores
###############################################################################

echo ""
echo -e "${GREEN}[Sensores]${NC} Iniciando ${#SENSORS[@]} sensores..."

# Grid 2x2 para 4 sensores
tmux split-window -h -t "$TMUX_SESSION:Sensores"
tmux split-window -v -t "$TMUX_SESSION:Sensores.0"
tmux split-window -v -t "$TMUX_SESSION:Sensores.1"
tmux select-layout -t "$TMUX_SESSION:Sensores" tiled

painel_idx=0
for sensor_config in "${SENSORS[@]}"; do
    IFS='|' read -r sensor_id password <<< "$sensor_config"
    
    # Sensores conectam ao Discovery via PacketFilter no DMZ
    sensor_args="$sensor_id $password $DMZ_HOST $DISCOVERY_PF_PORT"
    
    MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/$sensor_id.log" mvn -f "$POM_FILE" exec:java -o \
        -Dexec.mainClass="$SENSOR_CLASS" \
        -Dexec.args="$sensor_args" \
        -Dexec.cleanupDaemonThreads=false \
        > /dev/null 2>&1 &
    
    echo "$!" > "$PID_DIR/$sensor_id.pid"
    
    tmux send-keys -t "$TMUX_SESSION:Sensores.$painel_idx" "tail -F '$LOG_DIR/$sensor_id.log'" C-m
    tmux select-pane -t "$TMUX_SESSION:Sensores.$painel_idx" -T "📡 $sensor_id"
    
    echo -e "${GREEN}  ✓${NC} $sensor_id iniciado (Discovery via $DMZ_HOST:$DISCOVERY_PF_PORT)"
    sleep $SENSOR_DELAY
    
    ((painel_idx++))
done

###############################################################################
# Janela de Testes (MaliciousSensor, ClientApp)
###############################################################################

tmux new-window -t "$TMUX_SESSION" -n "Testes"

tmux split-window -h -t "$TMUX_SESSION:Testes"

# Comandos preparados (usuario pressiona ENTER para executar)
tmux send-keys -t "$TMUX_SESSION:Testes.0" "mvn -f '$POM_FILE' exec:java -Dexec.mainClass='$MALICIOUS_SENSOR_CLASS' -Dexec.args='--mode ANOMALY_DATA --password sensor123 --host $DMZ_HOST --port $DISCOVERY_PF_PORT'"
tmux send-keys -t "$TMUX_SESSION:Testes.1" "mvn -f '$POM_FILE' exec:java -Dexec.mainClass='$CLIENT_APP_CLASS' -Dexec.args='CLI_CLIENT $DMZ_HOST $DISCOVERY_PF_PORT'"

tmux select-pane -t "$TMUX_SESSION:Testes.0" -T "⚠️ MaliciousSensor (ENTER para iniciar)"
tmux select-pane -t "$TMUX_SESSION:Testes.1" -T "👤 ClientApp (ENTER para iniciar)"

# Voltar para janela de sensores
tmux select-window -t "$TMUX_SESSION:Sensores"

echo ""
echo -e "${GREEN}✅ External Zone iniciada!${NC}"
echo ""
echo "Sensores conectando via PacketFilter em $DMZ_HOST:$DISCOVERY_PF_PORT"
echo ""
echo "Janelas disponiveis:"
echo "  1. Sensores - 4 sensores ativos"
echo "  2. Testes   - MaliciousSensor e ClientApp (pressione ENTER para iniciar)"
echo ""

tmux attach-session -t "$TMUX_SESSION"

###############################################################################
# Cleanup ao sair
###############################################################################

echo ""
echo -e "${YELLOW}🛑 Parando sensores...${NC}"

for pid_file in "$PID_DIR"/*.pid; do
    [ -f "$pid_file" ] || continue
    pid=$(cat "$pid_file")
    kill -TERM "$pid" 2>/dev/null
    rm -f "$pid_file"
done

tmux kill-session -t "$TMUX_SESSION" 2>/dev/null

echo -e "${GREEN}✅ External Zone encerrada.${NC}"
