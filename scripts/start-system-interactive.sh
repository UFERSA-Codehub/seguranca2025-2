#!/bin/bash

###############################################################################
# Script de Inicializacao do Sistema de Monitoramento Ambiental
#
# Uso:
#   ./system.sh           - Modo normal
#   ./system.sh -t        - Modo com tracing + dashboard
#
# Pressione ? dentro do tmux para ver ajuda (janelas, atalhos, etc)
###############################################################################

# Importar configuracoes
source "$(dirname "$0")/config.sh"

# Configurar flags de tracing
TRACE_OPTS=""
if [[ "$TRACE_ENABLED" == "true" ]]; then
    TRACE_OPTS="-Dtrace.enabled=true"
    echo -e "${GREEN}📊 Tracing habilitado${NC}"
fi

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

###############################################################################
# LIMPAR PORTAS EM USO (processos orfaos de execucoes anteriores)
###############################################################################

PORTS_TO_CHECK=(3000 3001 3002 3005 3010 3011 3020 3021 3030 3031 4000 4001 5000 5001 6000 6001 8080 9090)
hanging_pids=()

for port in "${PORTS_TO_CHECK[@]}"; do
    pid=$(lsof -t -i :"$port" 2>/dev/null)
    if [ -n "$pid" ]; then
        hanging_pids+=("$pid")
    fi
done

if [ ${#hanging_pids[@]} -gt 0 ]; then
    unique_pids=($(echo "${hanging_pids[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))
    echo -e "${YELLOW}⚠️  Encontrados ${#unique_pids[@]} processos orfaos nas portas do sistema${NC}"
    echo -e "${YELLOW}   Limpando processos...${NC}"
    kill -9 "${unique_pids[@]}" 2>/dev/null
    sleep 0.5
    echo -e "${GREEN}   ✓ Portas liberadas${NC}"
    echo ""
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

# Pre-compilar projetos para reduzir uso de CPU durante startup
echo -e "${GREEN}[Compilando]${NC} Pre-compilando projetos..."
mvn -f "$POM_FILE" compile -q
if [[ "$TRACE_ENABLED" == "true" ]]; then
    mvn -f "$API_POM_FILE" compile -q
fi
echo -e "${GREEN}  ✓${NC} Compilacao concluida"

# Limpar logs antigos
echo -e "${GREEN}[Limpando]${NC} Removendo logs antigos..."
rm -f "$LOG_DIR"/*.log 2>/dev/null
rm -f "$PROJECT_DIR/logs/system.log" 2>/dev/null
echo -e "${GREEN}  ✓${NC} Logs limpos"

# Se tracing habilitado, iniciar TraceCollector PRIMEIRO para capturar todos os eventos
if [[ "$TRACE_ENABLED" == "true" ]]; then
    echo -e "${GREEN}[0/7]${NC} Iniciando TraceCollector (UDP:6000, WS:6001)..."
    mvn -f "$API_POM_FILE" exec:java -o \
        -Dexec.mainClass="$TRACE_COLLECTOR_CLASS" \
        -Dexec.cleanupDaemonThreads=false \
        > "$LOG_DIR/trace-collector.log" 2>&1 &
    TRACE_PID=$!
    echo "$TRACE_PID" > "$PID_DIR/trace-collector.pid"
    echo -e "${GREEN}  ✓${NC} TraceCollector iniciado (PID: $TRACE_PID)"
    
    # Aguardar TraceCollector estar pronto (verificar se porta UDP está escutando)
    echo -e "${YELLOW}  ⏳${NC} Aguardando TraceCollector ficar pronto..."
    for i in {1..10}; do
        if nc -z -u localhost 6000 2>/dev/null || grep -q "Trace Collector running" "$LOG_DIR/trace-collector.log" 2>/dev/null; then
            echo -e "${GREEN}  ✓${NC} TraceCollector pronto"
            break
        fi
        sleep 0.5
    done
    sleep 1  # Delay extra para garantir estabilidade
fi

echo -e "${GREEN}[1/7]${NC} Iniciando Discovery Service (UDP:4000)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/discovery.log $TRACE_OPTS" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$DISCOVERY_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
DISCOVERY_PID=$!
echo "$DISCOVERY_PID" > "$PID_DIR/discovery.pid"
echo -e "${GREEN}  ✓${NC} Discovery iniciado (PID: $DISCOVERY_PID)"
sleep $DISCOVERY_DELAY

echo -e "${GREEN}[2/7]${NC} Iniciando IDS (TCP:3002)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/ids.log $TRACE_OPTS" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$IDS_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
IDS_PID=$!
echo "$IDS_PID" > "$PID_DIR/ids.pid"
echo -e "${GREEN}  ✓${NC} IDS iniciado (PID: $IDS_PID)"
sleep $IDS_DELAY

echo -e "${GREEN}[3/7]${NC} Iniciando AuthServer (TCP:4001)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/auth.log $TRACE_OPTS" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$AUTH_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
AUTH_PID=$!
echo "$AUTH_PID" > "$PID_DIR/auth.pid"
echo -e "${GREEN}  ✓${NC} AuthServer iniciado (PID: $AUTH_PID)"
sleep $AUTH_DELAY

echo -e "${GREEN}[4/7]${NC} Iniciando Edge Server (TCP:5000, IDS:5001)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/edge.log $TRACE_OPTS" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$EDGE_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
EDGE_PID=$!
echo "$EDGE_PID" > "$PID_DIR/edge.pid"
echo -e "${GREEN}  ✓${NC} Edge Server iniciado (PID: $EDGE_PID)"
sleep $EDGE_DELAY

echo -e "${GREEN}[5/7]${NC} Iniciando Datacenter (TCP:8080, HTTP:9090)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/datacenter.log $TRACE_OPTS" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$DATACENTER_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
DATACENTER_PID=$!
echo "$DATACENTER_PID" > "$PID_DIR/datacenter.pid"
echo -e "${GREEN}  ✓${NC} Datacenter iniciado (PID: $DATACENTER_PID)"
sleep $DATACENTER_DELAY

echo -e "${GREEN}[6/7]${NC} Iniciando ReverseProxy (TCP:3001, 3011, 3021)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/proxy.log $TRACE_OPTS" mvn -f "$POM_FILE" exec:java -o \
    -Dexec.mainClass="$PROXY_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > /dev/null 2>&1 &
PROXY_PID=$!
echo "$PROXY_PID" > "$PID_DIR/proxy.pid"
echo -e "${GREEN}  ✓${NC} ReverseProxy iniciado (PID: $PROXY_PID)"
sleep $PROXY_DELAY

echo -e "${GREEN}[7/7]${NC} Iniciando PacketFilter (TCP:3000, 3010, 3020)..."
MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/pfilter.log $TRACE_OPTS" mvn -f "$POM_FILE" exec:java -o \
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
# JANELA 3: SENSORES
###############################################################################

echo -e "${GREEN}[Sensores]${NC} Iniciando ${#SENSORS[@]} sensores..."

# Criar janela "Sensores"
tmux new-window -t "$TMUX_SESSION" -n "Sensores"

# Grid 2x2 para 4 sensores
tmux split-window -h -t "$TMUX_SESSION:Sensores"
tmux split-window -v -t "$TMUX_SESSION:Sensores.0"
tmux split-window -v -t "$TMUX_SESSION:Sensores.1"
tmux select-layout -t "$TMUX_SESSION:Sensores" tiled

# Iniciar sensores em background e enviar comandos para paineis
painel_idx=0
for sensor_config in "${SENSORS[@]}"; do
    IFS='|' read -r sensor_id password <<< "$sensor_config"
    
    sensor_args="$sensor_id $password $DISCOVERY_HOST $DISCOVERY_PORT"
    
    MAVEN_OPTS="-DLOG_FILE=$LOG_DIR/$sensor_id.log $TRACE_OPTS" mvn -f "$POM_FILE" exec:java -o \
        -Dexec.mainClass="$SENSOR_CLASS" \
        -Dexec.args="$sensor_args" \
        -Dexec.cleanupDaemonThreads=false \
        > /dev/null 2>&1 &
    
    SENSOR_PID=$!
    echo "$SENSOR_PID" > "$PID_DIR/$sensor_id.pid"
    
    tmux send-keys -t "$TMUX_SESSION:Sensores.$painel_idx" "tail -F --retry '$LOG_DIR/$sensor_id.log'" C-m
    tmux select-pane -t "$TMUX_SESSION:Sensores.$painel_idx" -T "📡 $sensor_id"
    
    echo -e "${GREEN}  ✓${NC} $sensor_id iniciado (PID: $SENSOR_PID)"
    sleep $SENSOR_DELAY
    
    ((painel_idx++))
done

###############################################################################
# JANELA 4: TESTES (MaliciousSensor, ClientApp)
###############################################################################

tmux new-window -t "$TMUX_SESSION" -n "Testes"

# Criar painel adicional
tmux split-window -h -t "$TMUX_SESSION:Testes"

# Enviar comandos para cada painel (preparados, nao executam)
tmux send-keys -t "$TMUX_SESSION:Testes.0" "MAVEN_OPTS='$TRACE_OPTS' mvn -f '$POM_FILE' exec:java -Dexec.mainClass='$MALICIOUS_SENSOR_CLASS' -Dexec.args='--mode ANOMALY_DATA --password sensor123'"
tmux send-keys -t "$TMUX_SESSION:Testes.1" "MAVEN_OPTS='$TRACE_OPTS' mvn -f '$POM_FILE' exec:java -Dexec.mainClass='$CLIENT_APP_CLASS'"

# Definir títulos APÓS enviar comandos
tmux select-pane -t "$TMUX_SESSION:Testes.0" -T "⚠️ MaliciousSensor (ENTER para iniciar)"
tmux select-pane -t "$TMUX_SESSION:Testes.1" -T "👤 ClientApp (ENTER para iniciar)"

###############################################################################
# JANELA 5: TRACE (TraceCollector + Dashboard) - apenas se TRACE_ENABLED
###############################################################################

if [[ "$TRACE_ENABLED" == "true" ]]; then
    echo -e "${GREEN}[Trace]${NC} Configurando janela de Trace e Dashboard..."

    # TraceCollector já foi iniciado no início do script
    # Apenas criar janela tmux para visualização

    # Criar janela "Trace"
    tmux new-window -t "$TMUX_SESSION" -n "Trace"

    # Split em 2 painéis
    tmux split-window -h -t "$TMUX_SESSION:Trace"

    # Painel 0: Log do TraceCollector
    tmux send-keys -t "$TMUX_SESSION:Trace.0" "tail -F --retry '$LOG_DIR/trace-collector.log'" C-m

    # Painel 1: Dashboard (npm run dev)
    tmux send-keys -t "$TMUX_SESSION:Trace.1" "cd '$DASHBOARD_DIR'; npm run dev" C-m

    # Definir títulos
    tmux select-pane -t "$TMUX_SESSION:Trace.0" -T "📊 TraceCollector (UDP:6000, WS:6001)"
    tmux select-pane -t "$TMUX_SESSION:Trace.1" -T "🖥️ Dashboard (http://localhost:3333)"

    echo -e "${GREEN}  ✓${NC} Dashboard iniciado em http://localhost:3333"
fi

###############################################################################
# FASE 3: FINALIZACAO
###############################################################################

# Voltar para janela Interna
tmux select-window -t "$TMUX_SESSION:Interna"
tmux select-pane -t "$TMUX_SESSION:Interna.0"

# Configurar keybind para ajuda (Ctrl+B ?)
tmux bind-key -T prefix 'H' run-shell "TRACE_ENABLED=$TRACE_ENABLED '$SCRIPT_DIR/help.sh'; read -n 1"

echo ""
echo -e "${GREEN}Sistema iniciado. Pressione Ctrl+B ? para ajuda.${NC}"
echo ""

tmux attach-session -t "$TMUX_SESSION"

###############################################################################
# FASE 4: CLEANUP AO SAIR
###############################################################################

# Desabilitar mensagens de job control (evita "Killed" messages)
set +m

echo ""
echo -e "${YELLOW}🛑 Parando serviços...${NC}"

# Coletar todos os PIDs ativos
pids_to_kill=()
for pid_file in "$PID_DIR"/*.pid; do
    if [ -f "$pid_file" ]; then
        pid=$(cat "$pid_file")
        if ps -p "$pid" > /dev/null 2>&1; then
            pids_to_kill+=("$pid")
        fi
        rm -f "$pid_file"
    fi
done

# Enviar SIGTERM para todos em paralelo
if [ ${#pids_to_kill[@]} -gt 0 ]; then
    echo -e "${YELLOW}  Enviando SIGTERM para ${#pids_to_kill[@]} processos...${NC}"
    kill -TERM "${pids_to_kill[@]}" 2>/dev/null
    
    # Aguardar brevemente
    sleep 0.5
    
    # Forcar kill nos que ainda estao rodando
    for pid in "${pids_to_kill[@]}"; do
        if ps -p "$pid" > /dev/null 2>&1; then
            kill -9 "$pid" 2>/dev/null
        fi
    done
fi

# Aguardar processos filhos terminarem (suprime mensagens)
wait 2>/dev/null

# Double check: verificar se algum processo ainda esta usando as portas
PORTS_TO_CHECK=(3000 3001 3002 3005 3010 3011 3020 3021 3030 3031 4000 4001 5000 5001 6000 6001 8080 9090)
hanging_pids=()

for port in "${PORTS_TO_CHECK[@]}"; do
    pid=$(lsof -t -i :"$port" 2>/dev/null)
    if [ -n "$pid" ]; then
        hanging_pids+=("$pid")
    fi
done

# Remover duplicatas e matar processos pendentes
if [ ${#hanging_pids[@]} -gt 0 ]; then
    unique_pids=($(echo "${hanging_pids[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))
    echo -e "${YELLOW}  Limpando ${#unique_pids[@]} processos pendentes nas portas...${NC}"
    kill -9 "${unique_pids[@]}" 2>/dev/null
    sleep 0.2
fi

tmux kill-session -t "$TMUX_SESSION" 2>/dev/null

echo -e "${GREEN}✅ Sistema encerrado.${NC}"
echo ""
