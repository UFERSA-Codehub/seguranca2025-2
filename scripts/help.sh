#!/bin/bash

###############################################################################
# Exibe ajuda sobre o sistema e atalhos do tmux
###############################################################################

source "$(dirname "$0")/config.sh"

TRACE_MODE="${TRACE_ENABLED:-false}"

echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║            SISTEMA DE MONITORAMENTO AMBIENTAL (DMZ)            ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Arquitetura:                                                  ║"
echo "║    Sensor -> PacketFilter -> ReverseProxy -> Edge              ║"
echo "║                               ↓                                ║"
echo "║                              IDS                               ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Janelas tmux:                                                 ║"
echo "║                                                                ║"
echo "║  Ctrl+B 1  [Interna]:  Discovery | Auth | Edge | Datacenter    ║"
echo "║  Ctrl+B 2  [DMZ]:      PacketFilter | ReverseProxy | IDS       ║"
echo "║  Ctrl+B 3  [Sensores]: Sensor 001-004                          ║"
echo "║  Ctrl+B 4  [Testes]:   MaliciousSensor | ClientApp (manual)    ║"
if [[ "$TRACE_MODE" == "true" ]]; then
echo "║  Ctrl+B 5  [Trace]:    TraceCollector | Dashboard              ║"
fi
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Atalhos tmux:                                                 ║"
echo "║    Ctrl+B <n>        - Ir para janela n                        ║"
echo "║    Ctrl+B Setas      - Navegar entre paineis                   ║"
echo "║    Ctrl+B [          - Modo scroll (Q para sair)               ║"
echo "║    Ctrl+B Z          - Zoom no painel atual                    ║"
echo "║    Ctrl+B D          - Desanexar (processos continuam)         ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Janela 4: Pressione ENTER para iniciar testes manualmente     ║"
if [[ "$TRACE_MODE" == "true" ]]; then
echo "║  Dashboard: http://localhost:3333                              ║"
fi
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
