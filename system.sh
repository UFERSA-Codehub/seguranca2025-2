#!/bin/bash

###############################################################################
# Sistema de Monitoramento Ambiental
#
# Uso:
#   ./system.sh           - Inicia o sistema
#   ./system.sh -t        - Inicia com tracing + dashboard
#   ./system.sh --stop    - Para o sistema
#   ./system.sh --help    - Mostra ajuda
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

case "$1" in
    -t|--trace)
        export TRACE_ENABLED=true
        exec "$SCRIPT_DIR/scripts/start-system-interactive.sh"
        ;;
    --stop|-s)
        exec "$SCRIPT_DIR/scripts/stop-system.sh"
        ;;
    --help|-h)
        echo "Uso: ./system.sh [opcao]"
        echo ""
        echo "Opcoes:"
        echo "  (nenhuma)    Inicia o sistema"
        echo "  -t, --trace  Inicia com tracing + dashboard"
        echo "  -s, --stop   Para o sistema"
        echo "  -h, --help   Mostra esta ajuda"
        echo ""
        echo "Dentro do tmux, pressione Ctrl+B ? para ver atalhos."
        ;;
    *)
        exec "$SCRIPT_DIR/scripts/start-system-interactive.sh"
        ;;
esac
