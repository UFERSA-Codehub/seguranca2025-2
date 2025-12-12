#!/bin/bash

###############################################################################
# Configuracoes Distribuidas - Sistema de Monitoramento Ambiental
#
# Configure os IPs das 3 instancias WSL antes de executar os scripts.
# Use 'hostname -I' em cada WSL para obter o IP.
###############################################################################

# IPs das instancias WSL (CONFIGURE ESTES VALORES)
# Exemplo: WSL1_IP="172.25.123.45"
WSL1_IP="${WSL1_IP:-localhost}"  # DMZ: PacketFilter, ReverseProxy, Discovery, IDS
WSL2_IP="${WSL2_IP:-localhost}"  # Internal: Auth, Edge, Datacenter
WSL3_IP="${WSL3_IP:-localhost}"  # External: Sensors, Clients

# Validar que IPs foram configurados
validate_ips() {
    if [[ "$WSL1_IP" == "localhost" || "$WSL2_IP" == "localhost" || "$WSL3_IP" == "localhost" ]]; then
        echo -e "${YELLOW}⚠️  AVISO: IPs ainda configurados como 'localhost'${NC}"
        echo ""
        echo "Para teste distribuido real, configure os IPs:"
        echo "  export WSL1_IP=<ip-wsl1>  # DMZ"
        echo "  export WSL2_IP=<ip-wsl2>  # Internal"
        echo "  export WSL3_IP=<ip-wsl3>  # External"
        echo ""
        echo "Ou edite este arquivo diretamente."
        echo ""
        read -p "Continuar com localhost? (s/n): " resp
        [[ "$resp" =~ ^[Ss]$ ]] || exit 1
    fi
}

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
POM_FILE="$PROJECT_DIR/core/pom.xml"
API_POM_FILE="$PROJECT_DIR/api/pom.xml"
DASHBOARD_DIR="$PROJECT_DIR/dashboard"
PID_DIR="$PROJECT_DIR/core/.system-pids"
LOG_DIR="$PROJECT_DIR/core/.system-pids"

# Criar diretorios se nao existirem
mkdir -p "$PID_DIR"
mkdir -p "$LOG_DIR"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Classes Java - Servidores
DISCOVERY_CLASS="com.project.server.discovery.ServerDiscovery"
IDS_CLASS="com.project.server.ids.ServerIDS"
AUTH_CLASS="com.project.server.auth.ServerAuth"
EDGE_CLASS="com.project.server.edge.ServerEdge"
DATACENTER_CLASS="com.project.server.datacenter.ServerDatacenter"
PROXY_CLASS="com.project.server.firewall.ReverseProxy"
PFILTER_CLASS="com.project.server.firewall.PacketFilter"
TRACE_COLLECTOR_CLASS="com.project.collector.TraceCollector"

# Classes Java - Clientes
SENSOR_CLASS="com.project.client.sensor.Sensor"
MALICIOUS_SENSOR_CLASS="com.project.client.sensor.MaliciousSensor"
CLIENT_APP_CLASS="com.project.client.ClientApp"

# Delays (segundos)
DISCOVERY_DELAY=2
IDS_DELAY=1
AUTH_DELAY=2
EDGE_DELAY=2
DATACENTER_DELAY=2
PROXY_DELAY=2
PFILTER_DELAY=2
SENSOR_DELAY=1

# Sensores (ID|PASSWORD)
declare -a SENSORS=(
    "SENSOR_001|senha123"
    "SENSOR_002|senha456"
    "SENSOR_003|senha789"
    "SENSOR_004|senha321"
)

###############################################################################
# Portas por Zona
###############################################################################

# DMZ Ports (WSL1)
DISCOVERY_PORT="4000"
DISCOVERY_PF_PORT="3040"      # PacketFilter UDP for external clients
DISCOVERY_RP_PORT="3041"      # ReverseProxy UDP for internal services
IDS_PORT="3002"
PFILTER_TCP_BASE="3000"       # 3000, 3010, 3020, 3030
PROXY_TCP_BASE="3001"         # 3001, 3011, 3021, 3031

# Internal Ports (WSL2)
AUTH_PORT="4001"
EDGE_PORT="5000"
EDGE_IDS_PORT="5001"
DATACENTER_EDGE_PORT="8080"
DATACENTER_CLIENT_PORT="9090"
DATACENTER_BROWSER_PORT="9091"

###############################################################################
# Enderecos por Zona (para conexoes cross-WSL)
###############################################################################

# DMZ Host (onde PacketFilter/ReverseProxy estao)
DMZ_HOST="$WSL1_IP"

# Internal Host (onde Auth/Edge/Datacenter estao)
INTERNAL_HOST="$WSL2_IP"

# Funcao para mostrar configuracao atual
show_config() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║           CONFIGURACAO DISTRIBUIDA DO SISTEMA                  ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
    echo -e "${BLUE}WSL1 (DMZ):${NC} $WSL1_IP"
    echo "  - Discovery      UDP:$DISCOVERY_PORT"
    echo "  - PacketFilter   TCP:$PFILTER_TCP_BASE-3030, UDP:$DISCOVERY_PF_PORT"
    echo "  - ReverseProxy   TCP:$PROXY_TCP_BASE-3031, UDP:$DISCOVERY_RP_PORT"
    echo "  - IDS            TCP:$IDS_PORT"
    echo ""
    echo -e "${BLUE}WSL2 (Internal):${NC} $WSL2_IP"
    echo "  - Auth           TCP:$AUTH_PORT"
    echo "  - Edge           TCP:$EDGE_PORT, IDS:$EDGE_IDS_PORT"
    echo "  - Datacenter     TCP:$DATACENTER_EDGE_PORT, CLI:$DATACENTER_CLIENT_PORT, HTTP:$DATACENTER_BROWSER_PORT"
    echo ""
    echo -e "${BLUE}WSL3 (External):${NC} $WSL3_IP"
    echo "  - Sensors        (connect to DMZ:$DISCOVERY_PF_PORT)"
    echo "  - Clients        (connect to DMZ:$DISCOVERY_PF_PORT)"
    echo ""
}
