#!/bin/bash

###############################################################################
# Script para parar todos os processos do sistema
#
# Uso:
#   ./scripts/stop-system.sh           - Para todos os processos
#   ./scripts/stop-system.sh --force   - Força parada imediata (SIGKILL)
#
# Use este script quando:
#   - A sessão tmux foi fechada inesperadamente
#   - Processos ficaram órfãos
#   - Necessário reiniciar o sistema limpo
###############################################################################

source "$(dirname "$0")/config.sh"

FORCE="${1:-}"

echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║              PARANDO SISTEMA DE MONITORAMENTO                  ║"
echo "╚════════════════════════════════════════════════════════════════╝"
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
            echo -e "  ${YELLOW}•${NC} $service_name já estava parado"
        fi
        
        rm -f "$pid_file"
    fi
done
shopt -u nullglob

if [ $stopped_count -eq 0 ]; then
    echo -e "  ${YELLOW}•${NC} Nenhum processo registrado encontrado"
fi

# Passo 2: Matar processos Maven órfãos do projeto
echo ""
echo -e "${YELLOW}[2/3] Verificando processos Maven órfãos...${NC}"

orphan_count=0
while IFS= read -r pid; do
    if [ -n "$pid" ]; then
        echo -e "  ${YELLOW}•${NC} Matando processo Maven órfão (PID: $pid)..."
        kill -9 "$pid" 2>/dev/null
        ((orphan_count++))
    fi
done < <(pgrep -f "mvn.*seguranca2025-2" 2>/dev/null)

if [ $orphan_count -eq 0 ]; then
    echo -e "  ${GREEN}✓${NC} Nenhum processo órfão encontrado"
else
    echo -e "  ${GREEN}✓${NC} $orphan_count processo(s) órfão(s) eliminado(s)"
fi

# Passo 3: Matar sessão tmux
echo ""
echo -e "${YELLOW}[3/3] Encerrando sessão tmux...${NC}"

if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    tmux kill-session -t "$TMUX_SESSION"
    echo -e "  ${GREEN}✓${NC} Sessão '$TMUX_SESSION' encerrada"
else
    echo -e "  ${YELLOW}•${NC} Sessão '$TMUX_SESSION' não existe"
fi

# Resumo
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    SISTEMA ENCERRADO                           ║"
echo "╠════════════════════════════════════════════════════════════════╣"
printf "║  Processos registrados parados: %-29s ║\n" "$stopped_count"
printf "║  Processos órfãos eliminados:   %-29s ║\n" "$orphan_count"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
