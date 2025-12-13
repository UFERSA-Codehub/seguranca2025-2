#!/bin/bash

###############################################################################
# WSL2 - Internal Zone: Auth, Edge, Datacenter
#
# Execute este script na segunda instancia WSL (Internal/Backend)
#
# Uso:
#   export WSL1_IP=<ip-do-wsl1-dmz>
#   export WSL2_IP=<ip-desta-maquina>
#   ./start-wsl2-internal.sh
###############################################################################

source "$(dirname "$0")/config-distributed.sh"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                   WSL2 - INTERNAL ZONE                         ║"
echo "║               Auth, Edge, Datacenter                           ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

validate_ips
show_config

# Verificar tmux
if ! command -v tmux &> /dev/null; then
    echo -e "${RED}❌ ERRO: tmux não está instalado.${NC}"
    exit 1
fi

TMUX_SESSION="internal-zone"

if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  Sessão '$TMUX_SESSION' já existe.${NC}"
    read -p "Anexar? (s/n): " resp
    [[ "$resp" =~ ^[Ss]$ ]] && tmux attach-session -t "$TMUX_SESSION" && exit 0
    exit 0
fi

###############################################################################
# Limpar portas em uso
###############################################################################

PORTS_TO_CHECK=(4001 5000 5001 8080 9090 9091)
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
# Verificar conectividade com DMZ
###############################################################################

echo -e "${YELLOW}Verificando conectividade com DMZ ($DMZ_HOST)...${NC}"

# Tentar conectar ao ReverseProxy UDP
if ! nc -zu "$DMZ_HOST" "$DISCOVERY_RP_PORT" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  ReverseProxy em $DMZ_HOST:$DISCOVERY_RP_PORT nao responde${NC}"
    echo "Certifique-se que WSL1 (DMZ) esta rodando primeiro."
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
# Iniciar Servidores Internos
###############################################################################

echo ""
echo -e "${GREEN}[1/3]${NC} Iniciando AuthServer (TCP:$AUTH_PORT)..."
# Auth conecta ao Discovery via ReverseProxy no DMZ
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/auth.log" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$AUTH_CLASS" \
    -Dexec.args="$AUTH_PORT $DMZ_HOST $DISCOVERY_RP_PORT" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
echo "$!" > "$PID_DIR/auth.pid"
echo -e "${GREEN}  ✓${NC} AuthServer iniciado (Discovery via $DMZ_HOST:$DISCOVERY_RP_PORT)"
sleep $AUTH_DELAY

echo -e "${GREEN}[2/3]${NC} Iniciando Edge Server (TCP:$EDGE_PORT, IDS:$EDGE_IDS_PORT)..."
# Edge conecta ao Discovery via ReverseProxy no DMZ
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/edge.log" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$EDGE_CLASS" \
    -Dexec.args="$EDGE_PORT $DMZ_HOST $DISCOVERY_RP_PORT" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
echo "$!" > "$PID_DIR/edge.pid"
echo -e "${GREEN}  ✓${NC} Edge Server iniciado (Discovery via $DMZ_HOST:$DISCOVERY_RP_PORT)"
sleep $EDGE_DELAY

echo -e "${GREEN}[3/3]${NC} Iniciando Datacenter (TCP:$DATACENTER_EDGE_PORT, CLI:$DATACENTER_CLIENT_PORT)..."
# Datacenter conecta ao Discovery via ReverseProxy no DMZ
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/datacenter.log" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$DATACENTER_CLASS" \
    -Dexec.args="$DATACENTER_EDGE_PORT $DATACENTER_CLIENT_PORT $DMZ_HOST $DISCOVERY_RP_PORT" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
echo "$!" > "$PID_DIR/datacenter.pid"
echo -e "${GREEN}  ✓${NC} Datacenter iniciado (Discovery via $DMZ_HOST:$DISCOVERY_RP_PORT)"
sleep $DATACENTER_DELAY

###############################################################################
# Criar sessao tmux para monitoramento
###############################################################################

tmux new-session -d -s "$TMUX_SESSION" -n "Internal"

tmux set-option -g pane-border-status top
tmux set-option -g pane-border-format " #{pane_title} "

# 3 paineis verticais
tmux split-window -h -t "$TMUX_SESSION:Internal"
tmux split-window -h -t "$TMUX_SESSION:Internal.0"
tmux select-layout -t "$TMUX_SESSION:Internal" even-horizontal

tmux send-keys -t "$TMUX_SESSION:Internal.0" "tail -F '$LOG_DIR/auth.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Internal.1" "tail -F '$LOG_DIR/edge.log'" C-m
tmux send-keys -t "$TMUX_SESSION:Internal.2" "tail -F '$LOG_DIR/datacenter.log'" C-m

tmux select-pane -t "$TMUX_SESSION:Internal.0" -T "🔑 Auth (TCP:$AUTH_PORT)"
tmux select-pane -t "$TMUX_SESSION:Internal.1" -T "🌐 Edge (TCP:$EDGE_PORT)"
tmux select-pane -t "$TMUX_SESSION:Internal.2" -T "💾 Datacenter (TCP:$DATACENTER_EDGE_PORT)"

echo ""
echo -e "${GREEN}✅ Internal Zone iniciada!${NC}"
echo ""
echo "Servicos registrados via ReverseProxy em $DMZ_HOST:$DISCOVERY_RP_PORT"
echo "WSL3 (External) pode agora iniciar sensores."
echo ""

tmux attach-session -t "$TMUX_SESSION"

###############################################################################
# Cleanup ao sair
###############################################################################

echo ""
echo -e "${YELLOW}🛑 Parando serviços Internal...${NC}"

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

echo -e "${GREEN}✅ Internal Zone encerrada.${NC}"
