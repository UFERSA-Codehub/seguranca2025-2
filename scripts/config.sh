#!/bin/bash

###############################################################################
# Configurações do Sistema de Monitoramento Ambiental
###############################################################################

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
POM_FILE="$PROJECT_DIR/pom.xml"
PID_DIR="$PROJECT_DIR/.system-pids"
LOG_DIR="$PROJECT_DIR/.system-pids"

# Criar diretórios se não existirem
mkdir -p "$PID_DIR"
mkdir -p "$LOG_DIR"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Classes Java
DISCOVERY_CLASS="com.project.server.discovery.DiscoveryServerApp"
DATACENTER_CLASS="com.project.server.datacenter.DatacenterApp"
EDGE_CLASS="com.project.server.edge.EdgeServerApp"
SENSOR_CLASS="com.project.sensors.SensorApp"

# Delays (segundos)
DISCOVERY_DELAY=1
DATACENTER_DELAY=3
EDGE_DELAY=2
SENSOR_DELAY=1

# Configuração de sensores (ID|Nome|Localização|Senha)
# Senhas devem corresponder às configuradas em GestorAutenticacao.java
declare -a SENSORS=(
    "SENSOR_001|Sensor Parque Central|Parque Central|senha123"
    "SENSOR_002|Sensor Zona Industrial|Zona Industrial|industrial456"
    "SENSOR_003|Sensor Centro Comercial|Centro Comercial|comercial789"
    "SENSOR_004|Sensor Área Residencial|Área Residencial|residencial321"
    "SENSOR_999|Sensor Invasor|Local Desconhecido|senha_errada"
)

# Configuração do Edge Server
EDGE_HOST="127.0.0.1"
EDGE_PORT="5000"

# Nome da sessão tmux
TMUX_SESSION="system-monitor"
