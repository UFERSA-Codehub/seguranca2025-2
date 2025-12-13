#!/bin/bash

###############################################################################
# Sistema de Monitoramento Ambiental
#
# Uso:
#   ./system.sh           - Inicia o sistema (com sensores locais)
#   ./system.sh -t        - Inicia com tracing + dashboard
#   ./system.sh -s        - Inicia modo servidor (sem sensores - para Windows)
#   ./system.sh -t -s     - Tracing + modo servidor
#   ./system.sh --stop    - Para o sistema
#   ./system.sh --help    - Mostra ajuda
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Parse arguments
TRACE_ENABLED=false
SKIP_SENSORS=false
ARGS=""

for arg in "$@"; do
    case "$arg" in
        -t|--trace)
            TRACE_ENABLED=true
            ;;
        -s|--server)
            SKIP_SENSORS=true
            ARGS="$ARGS -s"
            ;;
        --stop)
            exec "$SCRIPT_DIR/scripts/stop-system.sh"
            ;;
        --help|-h)
            echo "Uso: ./system.sh [opcoes]"
            echo ""
            echo "Opcoes:"
            echo "  (nenhuma)      Inicia o sistema com sensores locais"
            echo "  -t, --trace    Inicia com tracing + dashboard"
            echo "  -s, --server   Modo servidor (sem sensores - execute do Windows)"
            echo "  --stop         Para o sistema"
            echo "  -h, --help     Mostra esta ajuda"
            echo ""
            echo "Combinacoes:"
            echo "  ./system.sh -t -s    Tracing + modo servidor"
            echo ""
            echo "Para rodar sensores do Windows:"
            echo "  1. Inicie: ./system.sh -s"
            echo "  2. Veja o IP exibido"
            echo "  3. No Windows: scripts\\start-windows-clients.bat <IP>"
            echo ""
            echo "Dentro do tmux, pressione Ctrl+B H para ver atalhos."
            exit 0
            ;;
    esac
done

export TRACE_ENABLED
exec "$SCRIPT_DIR/scripts/start-system-interactive.sh" $ARGS
