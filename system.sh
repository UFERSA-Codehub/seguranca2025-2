#!/bin/bash

###############################################################################
# Wrapper para scripts/start-system.sh e scripts/start-system-interactive.sh
#
# Uso:
#   ./system.sh                  - Modo daemon (todos em background)
#   ./system.sh --interactive    - Modo interativo (Discovery/Datacenter com comandos)
#   ./system.sh -i               - Atalho para --interactive
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Verificar argumentos
if [[ "$1" == "--interactive" ]] || [[ "$1" == "-i" ]]; then
    echo "🎮 Modo Interativo selecionado"
    exec "$SCRIPT_DIR/scripts/start-system-interactive.sh" "${@:2}"
else
    echo "🚀 Modo Daemon selecionado (use --interactive para modo interativo)"
    exec "$SCRIPT_DIR/scripts/start-system.sh" "$@"
fi
