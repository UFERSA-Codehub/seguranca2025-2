#!/bin/bash

###############################################################################
# Configurações do Sistema de Monitoramento Ambiental
###############################################################################

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
POM_FILE="$PROJECT_DIR/core/pom.xml"
API_POM_FILE="$PROJECT_DIR/api/pom.xml"
DASHBOARD_DIR="$PROJECT_DIR/dashboard"
PID_DIR="$PROJECT_DIR/core/.system-pids"
LOG_DIR="$PROJECT_DIR/core/.system-pids"

# Criar diretórios se não existirem
mkdir -p "$PID_DIR"
mkdir -p "$LOG_DIR"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Classes Java - Servidores (ordem de inicializacao)
DISCOVERY_CLASS="com.project.server.discovery.ServerDiscovery"
IDS_CLASS="com.project.server.ids.ServerIDS"
AUTH_CLASS="com.project.server.auth.ServerAuth"
EDGE_CLASS="com.project.server.edge.ServerEdge"
DATACENTER_CLASS="com.project.server.datacenter.ServerDatacenter"
PROXY_CLASS="com.project.server.firewall.ReverseProxy"
PFILTER_CLASS="com.project.server.firewall.PacketFilter"

# Classes Java - Tracing
TRACE_COLLECTOR_CLASS="com.project.collector.TraceCollector"

# Classes Java - Clientes
SENSOR_CLASS="com.project.client.sensor.Sensor"
MALICIOUS_SENSOR_CLASS="com.project.client.sensor.MaliciousSensor"
CLIENT_APP_CLASS="com.project.client.ClientApp"

# Delays (segundos) - ordem critica para conexoes
DISCOVERY_DELAY=2
IDS_DELAY=2
AUTH_DELAY=2
EDGE_DELAY=2
DATACENTER_DELAY=2
PROXY_DELAY=2
PFILTER_DELAY=2
SENSOR_DELAY=1

# Configuração de sensores (ID|PASSWORD)
# Senhas devem corresponder às configuradas no ServerEdge
declare -a SENSORS=(
    "SENSOR_001|senha123"
    "SENSOR_002|senha456"
    "SENSOR_003|senha789"
    "SENSOR_004|senha321"
)

# Sensores para modo trace (menos ruido na visualizacao)
declare -a TRACE_SENSORS=(
    "SENSOR_001|senha123"
    "SENSOR_002|senha456"
)

# Configuração do Discovery Server (Sensor conecta ao Discovery primeiro)
DISCOVERY_HOST="localhost"
DISCOVERY_PORT="4000"

# Nome da sessão tmux
TMUX_SESSION="system-monitor"
