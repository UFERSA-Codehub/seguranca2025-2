#!/bin/bash

###############################################################################
# Wrapper para scripts/start-system.sh
#
# Uso:
#   ./system.sh
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

exec "$SCRIPT_DIR/scripts/start-system.sh" "$@"
