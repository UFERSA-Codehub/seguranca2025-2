#!/bin/bash

###############################################################################
# Script de Inicialização do Sistema de Monitoramento Ambiental (MODO INTERATIVO)
#
# Diferença do start-system.sh:
#   - Discovery e Datacenter executam DENTRO do tmux (interativos)
#   - Você pode usar comandos 'status' e 'quit' diretamente nos painéis
#   - Edge e Sensores continuam em background (logs em arquivo)
#
# Fluxo:
#   1. Verifica se tmux está instalado
#   2. Cria sessão tmux com layout 2 janelas (8 painéis)
#   3. Executa Discovery e Datacenter DENTRO do tmux (com send-keys)
#   4. Executa Edge e Sensores em background
#   5. Anexa automaticamente à sessão
#   6. Cleanup ao sair (mata apenas processos background)
#
# Uso:
#   ./scripts/start-system-interactive.sh
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
echo "║  SISTEMA DE MONITORAMENTO AMBIENTAL - MODO INTERATIVO          ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
echo -e "${GREEN}🎮 Modo Interativo Ativado:${NC}"
echo "  • Discovery e Datacenter terão comandos disponíveis"
echo "  • Digite 'status' para ver estatísticas"
echo "  • Digite 'quit' para encerrar cada serviço"
echo ""

###############################################################################
# FASE 2: CRIAR ESTRUTURA TMUX
###############################################################################

echo -e "${GREEN}📊 Criando sessão tmux com 2 janelas...${NC}"
echo ""

# Criar sessão com janela "Serviços"
tmux new-session -d -s "$TMUX_SESSION" -n "Serviços"

# Habilitar títulos de painéis IMEDIATAMENTE
tmux set-option -t "$TMUX_SESSION" pane-border-status top
tmux set-option -t "$TMUX_SESSION" pane-border-format " #{pane_title} "

###############################################################################
# JANELA 0: SERVIÇOS (Discovery, Datacenter, Edge)
###############################################################################

echo -e "${GREEN}[1/4]${NC} Iniciando Discovery Service (UDP:4000) - MODO INTERATIVO..."

# Painel 0: Discovery (INTERATIVO via send-keys)
tmux select-pane -t "$TMUX_SESSION:Serviços.0" -T "🔍 Discovery (UDP:4000)"
tmux send-keys -t "$TMUX_SESSION:Serviços.0" \
    "cd '$PROJECT_DIR' && mvn -f '$POM_FILE' exec:java -Dexec.mainClass='$DISCOVERY_CLASS'" C-m

echo -e "${GREEN}✓${NC} Discovery iniciado em modo interativo (painel 0)"
echo ""
sleep $DISCOVERY_DELAY

echo -e "${GREEN}[2/4]${NC} Iniciando Datacenter (TCP:8080, HTTP:9090) - MODO INTERATIVO..."

# Painel 1: Datacenter (INTERATIVO via send-keys)
tmux split-window -h -t "$TMUX_SESSION:Serviços"
tmux select-pane -t "$TMUX_SESSION:Serviços.1" -T "💾 Datacenter (TCP:8080)"
tmux send-keys -t "$TMUX_SESSION:Serviços.1" \
    "cd '$PROJECT_DIR' && mvn -f '$POM_FILE' exec:java -Dexec.mainClass='$DATACENTER_CLASS'" C-m

echo -e "${GREEN}✓${NC} Datacenter iniciado em modo interativo (painel 1)"
echo ""
sleep $DATACENTER_DELAY

echo -e "${GREEN}[3/4]${NC} Iniciando Edge Server (UDP:5000) - background..."

# Painel 2: Edge (background com tail do log)
tmux split-window -h -t "$TMUX_SESSION:Serviços.1"
tmux select-pane -t "$TMUX_SESSION:Serviços.2" -T "🌐 Edge (UDP:5000)"

# Edge continua em background
mvn -f "$POM_FILE" exec:java \
    -Dexec.mainClass="$EDGE_CLASS" \
    -Dexec.cleanupDaemonThreads=false \
    > "$LOG_DIR/edge.log" 2>&1 &
EDGE_PID=$!
echo "$EDGE_PID" > "$PID_DIR/edge.pid"

# Mostrar log no painel
tmux send-keys -t "$TMUX_SESSION:Serviços.2" "tail -f '$LOG_DIR/edge.log'" C-m

sleep $EDGE_DELAY
echo -e "${GREEN}✓${NC} Edge Server iniciado (PID: $EDGE_PID)"
echo ""

# Layout tiled para distribuir igualmente os 3 painéis
tmux select-layout -t "$TMUX_SESSION:Serviços" tiled

###############################################################################
# JANELA 1: SENSORES (Sensor 1, 2, 3, 4, 999)
###############################################################################

echo -e "${GREEN}[4/4]${NC} Iniciando Sensores (5 processos) - background..."

# Criar janela "Sensores"
tmux new-window -t "$TMUX_SESSION" -n "Sensores"

# Painel 0: Sensor 1
tmux select-pane -t "$TMUX_SESSION:Sensores.0" -T "📡 Sensor 1 (Parque)"

# Painel 1: Sensor 2
tmux split-window -h -t "$TMUX_SESSION:Sensores"
tmux select-pane -t "$TMUX_SESSION:Sensores.1" -T "📡 Sensor 2 (Industrial)"

# Painel 2: Sensor 3
tmux split-window -h -t "$TMUX_SESSION:Sensores.1"
tmux select-pane -t "$TMUX_SESSION:Sensores.2" -T "📡 Sensor 3 (Comercial)"

# Painel 3: Sensor 4 (dividir verticalmente do painel 0)
tmux split-window -v -t "$TMUX_SESSION:Sensores.0"
tmux select-pane -t "$TMUX_SESSION:Sensores.3" -T "📡 Sensor 4 (Residencial)"

# Painel 4: Sensor 999 (dividir horizontalmente do painel 3)
tmux split-window -h -t "$TMUX_SESSION:Sensores.3"
tmux select-pane -t "$TMUX_SESSION:Sensores.4" -T "⚠️ Sensor 999 (Malicioso)"

# Layout tiled para distribuir os 5 painéis
tmux select-layout -t "$TMUX_SESSION:Sensores" tiled

# Iniciar sensores em background e mostrar logs nos painéis
painel_idx=0
for sensor_config in "${SENSORS[@]}"; do
    IFS='|' read -r sensor_id nome localizacao token <<< "$sensor_config"
    
    # Construir argumentos com aspas para campos com espaços
    sensor_args="$sensor_id \"$nome\" \"$localizacao\" $token $EDGE_HOST $EDGE_PORT"
    
    # Iniciar sensor em background
    mvn -f "$POM_FILE" exec:java \
        -Dexec.mainClass="$SENSOR_CLASS" \
        -Dexec.args="$sensor_args" \
        -Dexec.cleanupDaemonThreads=false \
        > "$LOG_DIR/$sensor_id.log" 2>&1 &
    
    SENSOR_PID=$!
    echo "$SENSOR_PID" > "$PID_DIR/$sensor_id.pid"
    
    # Mostrar log no painel correspondente
    tmux send-keys -t "$TMUX_SESSION:Sensores.$painel_idx" "tail -f '$LOG_DIR/$sensor_id.log'" C-m
    
    echo -e "${GREEN}  ✓${NC} $sensor_id iniciado (PID: $SENSOR_PID)"
    sleep $SENSOR_DELAY
    
    ((painel_idx++))
done
echo ""

echo -e "${GREEN}✅ Todos os serviços iniciados com sucesso!${NC}"
echo ""
sleep 2

###############################################################################
# FASE 3: VOLTAR PARA JANELA SERVIÇOS
###############################################################################

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
echo "║              ENTRANDO NO MONITOR DO SISTEMA (INTERATIVO)       ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Layout: 2 janelas tmux com 8 painéis                          ║"
echo "║                                                                ║"
echo "║  JANELA 0 [Serviços]:                                          ║"
echo "║    • Discovery (INTERATIVO) - digite 'status', 'quit'          ║"
echo "║    • Datacenter (INTERATIVO) - digite 'status', 'quit'         ║"
echo "║    • Edge (logs apenas)                                        ║"
echo "║                                                                ║"
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
echo "║    Ctrl+B D          - Desanexar (processos continuam)         ║"
echo "║    Ctrl+D            - Sair e parar sistema                    ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  🎮 MODO INTERATIVO:                                           ║"
echo "║    • Navegue até Discovery/Datacenter e digite comandos        ║"
echo "║    • 'status' mostra estatísticas                              ║"
echo "║    • 'quit' encerra o serviço                                  ║"
echo "║    • Ctrl+C também funciona                                    ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
sleep 3

# Anexar à sessão tmux
tmux attach-session -t "$TMUX_SESSION"

###############################################################################
# FASE 5: CLEANUP AO SAIR
###############################################################################

echo ""
echo -e "${YELLOW}🛑 Parando serviços em background (Edge + Sensores)...${NC}"
echo -e "${YELLOW}   (Discovery e Datacenter já foram encerrados manualmente)${NC}"
echo ""

# Matar APENAS processos em background (Edge e Sensores)
# Discovery e Datacenter já foram encerrados pelo usuário com 'quit' ou Ctrl+C
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
