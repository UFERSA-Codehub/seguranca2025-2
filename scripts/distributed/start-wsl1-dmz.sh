#!/bin/bash

###############################################################################
# WSL1 - DMZ Zone: PacketFilter, ReverseProxy, Discovery, IDS
#
# Execute este script na primeira instancia WSL (DMZ/Gateway)
#
# Uso:
#   export WSL1_IP=<ip-desta-maquina>
#   export WSL2_IP=<ip-do-wsl2>
#   ./start-wsl1-dmz.sh
###############################################################################

source "$(dirname "$0")/config-distributed.sh"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    WSL1 - DMZ ZONE                             ║"
echo "║         PacketFilter, ReverseProxy, Discovery, IDS            ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

validate_ips
show_config

# Verificar tmux
if ! command -v tmux &> /dev/null; then
    echo -e "${RED}❌ ERRO: tmux não está instalado.${NC}"
    exit 1
fi

TMUX_SESSION="dmz-zone"

if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  Sessão '$TMUX_SESSION' já existe.${NC}"
    read -p "Anexar? (s/n): " resp
    [[ "$resp" =~ ^[Ss]$ ]] && tmux attach-session -t "$TMUX_SESSION" && exit 0
    exit 0
fi

###############################################################################
# Limpar portas em uso
###############################################################################

PORTS_TO_CHECK=(3000 3001 3002 3010 3011 3020 3021 3030 3031 3040 3041 4000)
hanging_pids=()

for port in "${PORTS_TO_CHECK[@]}"; do
    pid=$(lsof -t -i :"$port" 2>/dev/null)
    [ -n "$pid" ] && hanging_pids+=("$pid")
done

if [ ${#hanging_pids[@]} -gt 0 ]; then
    unique_pids=($(echo "${hanging_pids[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))
    echo -e "${YELLOW}Limpando ${#unique_pids[@]} processos orfaos...${NC}"
    kill -9 "${unique_pids[@]}" 2>/dev/null
    sleep 0.5
fi

###############################################################################
# Compilar
###############################################################################

echo -e "${GREEN}[Compilando]${NC} Preparando projeto..."
mvn -f "$POM_FILE" compile -q
echo -e "${GREEN}  ✓${NC} Compilacao concluida"

rm -f "$LOG_DIR"/*.log 2>/dev/null

###############################################################################
# Iniciar Servidores DMZ
###############################################################################

echo ""
echo -e "${GREEN}[1/4]${NC} Iniciando Discovery Service (UDP:$DISCOVERY_PORT)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/discovery.log" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$DISCOVERY_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
echo "$!" > "$PID_DIR/discovery.pid"
echo -e "${GREEN}  ✓${NC} Discovery iniciado"
sleep $DISCOVERY_DELAY

echo -e "${GREEN}[2/4]${NC} Iniciando IDS (TCP:$IDS_PORT)..."
# IDS precisa saber onde Edge esta (WSL2)
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/ids.log" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$IDS_CLASS" \
    -Dexec.args="$IDS_PORT $INTERNAL_HOST $EDGE_IDS_PORT" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
echo "$!" > "$PID_DIR/ids.pid"
echo -e "${GREEN}  ✓${NC} IDS iniciado"
sleep $IDS_DELAY

echo -e "${GREEN}[3/4]${NC} Iniciando ReverseProxy (TCP + UDP:$DISCOVERY_RP_PORT)..."
# ReverseProxy precisa saber onde os servicos internos estao (WSL2)
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/proxy.log" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$PROXY_CLASS" \
    -Dexec.args="$INTERNAL_HOST" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
echo "$!" > "$PID_DIR/proxy.pid"
echo -e "${GREEN}  ✓${NC} ReverseProxy iniciado"
sleep $PROXY_DELAY

echo -e "${GREEN}[4/4]${NC} Iniciando PacketFilter (TCP + UDP:$DISCOVERY_PF_PORT)..."
# PacketFilter conecta ao ReverseProxy local e IDS local
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/pfilter.log" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$PFILTER_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
echo "$!" > "$PID_DIR/pfilter.pid"
echo -e "${GREEN}  ✓${NC} PacketFilter iniciado"
sleep $PFILTER_DELAY

###############################################################################
# Criar sessao tmux para monitoramento
###############################################################################

tmux new-session -d -s "$TMUX_SESSION" -n "DMZ"

tmux set-option -g pane-border-status top
tmux set-option -g pane-border-format " #{pane_title} "

# Grid 2x2 para 4 servicos
tmux split-window -h -t "$TMUX_SESSION:DMZ"
tmux split-window -v -t "$TMUX_SESSION:DMZ.0"
tmux split-window -v -t "$TMUX_SESSION:DMZ.1"
tmux select-layout -t "$TMUX_SESSION:DMZ" tiled

tmux send-keys -t "$TMUX_SESSION:DMZ.0" "tail -F '$LOG_DIR/discovery.log'" C-m
tmux send-keys -t "$TMUX_SESSION:DMZ.1" "tail -F '$LOG_DIR/pfilter.log'" C-m
tmux send-keys -t "$TMUX_SESSION:DMZ.2" "tail -F '$LOG_DIR/proxy.log'" C-m
tmux send-keys -t "$TMUX_SESSION:DMZ.3" "tail -F '$LOG_DIR/ids.log'" C-m

tmux select-pane -t "$TMUX_SESSION:DMZ.0" -T "🔍 Discovery (UDP:$DISCOVERY_PORT)"
tmux select-pane -t "$TMUX_SESSION:DMZ.1" -T "🔥 PacketFilter (UDP:$DISCOVERY_PF_PORT)"
tmux select-pane -t "$TMUX_SESSION:DMZ.2" -T "🛡️ ReverseProxy (UDP:$DISCOVERY_RP_PORT)"
tmux select-pane -t "$TMUX_SESSION:DMZ.3" -T "🚨 IDS (TCP:$IDS_PORT)"

echo ""
echo -e "${GREEN}✅ DMZ Zone iniciada!${NC}"
echo ""
echo "Aguardando WSL2 (Internal) iniciar Auth, Edge, Datacenter..."
echo "Depois WSL3 (External) pode iniciar sensores."
echo ""

tmux attach-session -t "$TMUX_SESSION"

###############################################################################
# Cleanup ao sair
###############################################################################

echo ""
echo -e "${YELLOW}🛑 Parando serviços DMZ...${NC}"

for pid_file in "$PID_DIR"/*.pid; do
    [ -f "$pid_file" ] || continue
    pid=$(cat "$pid_file")
    kill -TERM "$pid" 2>/dev/null
    rm -f "$pid_file"
done

sleep 0.5

for port in "${PORTS_TO_CHECK[@]}"; do
    pid=$(lsof -t -i :"$port" 2>/dev/null)
    [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null
done

tmux kill-session -t "$TMUX_SESSION" 2>/dev/null

echo -e "${GREEN}✅ DMZ Zone encerrada.${NC}"
