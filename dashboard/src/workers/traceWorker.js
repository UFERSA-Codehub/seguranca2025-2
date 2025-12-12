/**
 * Web Worker para gerenciar conexao WebSocket de trace events.
 * 
 * Isola o WebSocket da thread principal, evitando perda de eventos
 * quando o React-Flow bloqueia a UI durante interacoes com o canvas.
 * 
 * Protocolo de mensagens:
 * - Main -> Worker: { type: 'connect' | 'disconnect' | 'clear' }
 * - Worker -> Main: { type: 'status', connected: boolean }
 * - Worker -> Main: { type: 'events', events: TraceEvent[] }
 */

const WS_URL = 'ws://localhost:6001';
const BUFFER_FLUSH_INTERVAL_MS = 100; // Envia batch para main thread a cada 100ms
const MAX_BUFFER_SIZE = 1000; // Limite de eventos no buffer (deve ser >= backend MAX_STORED_EVENTS)

let ws = null;
let eventBuffer = [];
let flushInterval = null;
let reconnectTimeout = null;

/**
 * Envia eventos acumulados para a thread principal
 */
function flushBuffer() {
    if (eventBuffer.length === 0) return;
    
    // Ordenar por timestamp, componentId, sequenceNumber antes de enviar
    eventBuffer.sort((a, b) => {
        // 1. Timestamp (ascending para ordem cronologica)
        const tsDiff = a.timestamp - b.timestamp;
        if (tsDiff !== 0) return tsDiff;
        
        // 2. ComponentId
        const compA = a.componentId ?? a.componentType ?? '';
        const compB = b.componentId ?? b.componentType ?? '';
        const compDiff = compA.localeCompare(compB);
        if (compDiff !== 0) return compDiff;
        
        // 3. SequenceNumber
        const seqA = a.sequenceNumber ?? 0;
        const seqB = b.sequenceNumber ?? 0;
        return seqA - seqB;
    });
    
    // Enviar batch para main thread
    self.postMessage({ type: 'events', events: eventBuffer });
    eventBuffer = [];
}

/**
 * Conecta ao WebSocket server
 */
function connect() {
    if (ws && ws.readyState === WebSocket.OPEN) return;
    
    try {
        ws = new WebSocket(WS_URL);
        
        ws.onopen = () => {
            self.postMessage({ type: 'status', connected: true });
            
            // Iniciar flush periodico
            if (!flushInterval) {
                flushInterval = setInterval(flushBuffer, BUFFER_FLUSH_INTERVAL_MS);
            }
        };
        
        ws.onmessage = (event) => {
            try {
                const traceEvent = JSON.parse(event.data);
                
                // Adicionar ao buffer
                eventBuffer.push(traceEvent);
                
                // Proteção contra buffer overflow
                if (eventBuffer.length > MAX_BUFFER_SIZE) {
                    // Descartar eventos mais antigos
                    eventBuffer = eventBuffer.slice(-MAX_BUFFER_SIZE);
                }
            } catch (e) {
                // Ignorar eventos malformados
            }
        };
        
        ws.onclose = () => {
            self.postMessage({ type: 'status', connected: false });
            ws = null;
            
            // Tentar reconectar apos 3 segundos
            reconnectTimeout = setTimeout(connect, 3000);
        };
        
        ws.onerror = () => {
            // onclose sera chamado automaticamente
        };
        
    } catch (e) {
        self.postMessage({ type: 'status', connected: false });
        reconnectTimeout = setTimeout(connect, 3000);
    }
}

/**
 * Desconecta do WebSocket server
 */
function disconnect() {
    if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
    }
    
    if (flushInterval) {
        clearInterval(flushInterval);
        flushInterval = null;
    }
    
    // Flush final antes de desconectar
    flushBuffer();
    
    if (ws) {
        ws.close();
        ws = null;
    }
}

/**
 * Limpa o buffer de eventos e envia comando de clear para o servidor
 */
function clearBuffer() {
    eventBuffer = [];
    
    // Send clear command to server to clear server-side history
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'clear' }));
    }
}

// Handler de mensagens da thread principal
self.onmessage = (event) => {
    const { type } = event.data;
    
    switch (type) {
        case 'connect':
            connect();
            break;
        case 'disconnect':
            disconnect();
            break;
        case 'clear':
            clearBuffer();
            break;
        default:
            break;
    }
};
