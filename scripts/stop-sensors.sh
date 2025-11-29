#!/bin/bash

###############################################################################
# Script para parar/reiniciar sensores
#
# Uso:
#   ./scripts/stop-sensors.sh         - Para todos os sensores
#   ./scripts/stop-sensors.sh restart - Para e reinicia os sensores
###############################################################################

source "$(dirname "$0")/config.sh"

ACTION="${1:-stop}"

echo ""
echo -e "${YELLOW}🛑 Parando sensores...${NC}"
echo ""

stopped_count=0

for sensor_config in "${SENSORS[@]}"; do
    IFS='|' read -r sensor_id password <<< "$sensor_config"
    pid_file="$PID_DIR/$sensor_id.pid"
    
    if [ -f "$pid_file" ]; then
        pid=$(cat "$pid_file")
        
        if ps -p "$pid" > /dev/null 2>&1; then
            echo -e "  ${YELLOW}•${NC} Parando $sensor_id (PID: $pid)..."
            kill -TERM "$pid" 2>/dev/null
            
            # Aguardar término graceful (max 3s)
            for i in {1..3}; do
                if ! ps -p "$pid" > /dev/null 2>&1; then
                    break
                fi
                sleep 1
            done
            
            # Force kill se ainda estiver rodando
            if ps -p "$pid" > /dev/null 2>&1; then
                kill -9 "$pid" 2>/dev/null
            fi
            
            echo -e "  ${GREEN}✓${NC} $sensor_id parado"
            ((stopped_count++))
        else
            echo -e "  ${YELLOW}•${NC} $sensor_id já estava parado"
        fi
        
        rm -f "$pid_file"
    else
        echo -e "  ${YELLOW}•${NC} $sensor_id não encontrado (sem PID file)"
    fi
done

echo ""
echo -e "${GREEN}✅ $stopped_count sensor(es) parado(s)${NC}"
echo ""

# Reiniciar se solicitado
if [ "$ACTION" = "restart" ]; then
    echo -e "${GREEN}🔄 Reiniciando sensores...${NC}"
    echo ""
    
    for sensor_config in "${SENSORS[@]}"; do
        IFS='|' read -r sensor_id password <<< "$sensor_config"
        
        sensor_args="$sensor_id $password $DISCOVERY_HOST $DISCOVERY_PORT"
        
        mvn -f "$POM_FILE" exec:java \
            -Dexec.mainClass="$SENSOR_CLASS" \
            -Dexec.args="$sensor_args" \
            -Dexec.cleanupDaemonThreads=false \
            > "$LOG_DIR/$sensor_id.log" 2>&1 &
        
        SENSOR_PID=$!
        echo "$SENSOR_PID" > "$PID_DIR/$sensor_id.pid"
        
        echo -e "  ${GREEN}✓${NC} $sensor_id reiniciado (PID: $SENSOR_PID)"
        sleep $SENSOR_DELAY
    done
    
    echo ""
    echo -e "${GREEN}✅ Sensores reiniciados${NC}"
    echo ""
fi
