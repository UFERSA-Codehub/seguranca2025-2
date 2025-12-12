#!/bin/bash

###############################################################################
# Configuracoes do Sistema de Monitoramento Ambiental
###############################################################################

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
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
DISCOVERY_DELAY=1
IDS_DELAY=1
AUTH_DELAY=1
EDGE_DELAY=1
DATACENTER_DELAY=1
PROXY_DELAY=1
PFILTER_DELAY=1
SENSOR_DELAY=1

# Sensores (ID|PASSWORD)
declare -a SENSORS=(
    "SENSOR_001|senha123"
    "SENSOR_002|senha456"
    "SENSOR_003|senha789"
    "SENSOR_004|senha321"
)

# Discovery Server (internal port - used by internal servers via ReverseProxy)
DISCOVERY_HOST="localhost"
DISCOVERY_PORT="4000"

# Discovery via PacketFilter (external port - used by sensors and clients)
DISCOVERY_PF_PORT="3040"

# Discovery via ReverseProxy (internal port - used by internal services)
DISCOVERY_RP_PORT="3041"

# Sessao tmux
TMUX_SESSION="system-monitor"
