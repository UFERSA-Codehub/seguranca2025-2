#!/bin/bash

###############################################################################
# Para todos os processos do sistema
#
# Uso:
#   ./scripts/stop-system.sh           - Para todos os processos
#   ./scripts/stop-system.sh --force   - Forca parada imediata (SIGKILL)
###############################################################################

source "$(dirname "$0")/config.sh"

FORCE="${1:-}"

echo ""
echo -e "${YELLOW}Parando sistema...${NC}"
echo ""

stopped_count=0

# Passo 1: Parar processos com PID files
echo -e "${YELLOW}[1/3] Parando processos registrados...${NC}"

shopt -s nullglob
for pid_file in "$PID_DIR"/*.pid; do
    if [ -f "$pid_file" ]; then
        pid=$(cat "$pid_file")
        service_name=$(basename "$pid_file" .pid)
        
        if ps -p "$pid" > /dev/null 2>&1; then
            echo -e "  ${YELLOW}•${NC} Parando $service_name (PID: $pid)..."
            
            if [ "$FORCE" = "--force" ]; then
                kill -9 "$pid" 2>/dev/null
            else
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
            
            echo -e "  ${GREEN}✓${NC} $service_name parado"
            ((stopped_count++))
        else
            echo -e "  ${YELLOW}•${NC} $service_name ja estava parado"
        fi
        
        rm -f "$pid_file"
    fi
done
shopt -u nullglob

if [ $stopped_count -eq 0 ]; then
    echo -e "  ${YELLOW}•${NC} Nenhum processo registrado encontrado"
fi

# Passo 2: Matar processos Maven orfaos do projeto
echo ""
echo -e "${YELLOW}[2/3] Verificando processos Maven orfaos...${NC}"

orphan_count=0
while IFS= read -r pid; do
    if [ -n "$pid" ]; then
        echo -e "  ${YELLOW}•${NC} Matando processo Maven orfao (PID: $pid)..."
        kill -9 "$pid" 2>/dev/null
        ((orphan_count++))
    fi
done < <(pgrep -f "mvn.*seguranca2025-2" 2>/dev/null)

if [ $orphan_count -eq 0 ]; then
    echo -e "  ${GREEN}✓${NC} Nenhum processo orfao encontrado"
else
    echo -e "  ${GREEN}✓${NC} $orphan_count processo(s) orfao(s) eliminado(s)"
fi

# Passo 2.5: Liberar portas do sistema (TraceCollector, servidores, etc)
echo ""
echo -e "${YELLOW}[2.5/3] Liberando portas do sistema...${NC}"

PORTS_TO_CHECK=(3000 3001 3002 3005 3010 3011 3020 3021 3030 3031 4000 4001 5000 5001 6000 6001 8080 9090)
port_pids=()

for port in "${PORTS_TO_CHECK[@]}"; do
    pid=$(lsof -t -i :"$port" 2>/dev/null)
    if [ -n "$pid" ]; then
        port_pids+=("$pid")
    fi
done

if [ ${#port_pids[@]} -gt 0 ]; then
    unique_pids=($(echo "${port_pids[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))
    echo -e "  ${YELLOW}•${NC} Liberando ${#unique_pids[@]} processo(s) nas portas..."
    kill -9 "${unique_pids[@]}" 2>/dev/null
    echo -e "  ${GREEN}✓${NC} Portas liberadas"
else
    echo -e "  ${GREEN}✓${NC} Nenhum processo nas portas do sistema"
fi

# Passo 3: Matar sessao tmux
echo ""
echo -e "${YELLOW}[3/3] Encerrando sessao tmux...${NC}"

if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    tmux kill-session -t "$TMUX_SESSION"
    echo -e "  ${GREEN}✓${NC} Sessao '$TMUX_SESSION' encerrada"
else
    echo -e "  ${YELLOW}•${NC} Sessao '$TMUX_SESSION' nao existe"
fi

# Resumo
echo ""
echo -e "${GREEN}Sistema encerrado.${NC}"
echo -e "  Processos parados: $stopped_count"
echo -e "  Orfaos eliminados: $orphan_count"
echo ""
