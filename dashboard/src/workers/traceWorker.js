const WS_URL = 'ws://localhost:6001';
const BUFFER_FLUSH_INTERVAL_MS = 100;
const MAX_BUFFER_SIZE = 1000;

let ws = null;
let eventBuffer = [];
let flushInterval = null;
let reconnectTimeout = null;

function flushBuffer() {
    if (eventBuffer.length === 0) return;
    
    eventBuffer.sort((a, b) => {
        const tsDiff = a.timestamp - b.timestamp;
        if (tsDiff !== 0) return tsDiff;
        
        const compA = a.componentId ?? a.componentType ?? '';
        const compB = b.componentId ?? b.componentType ?? '';
        const compDiff = compA.localeCompare(compB);
        if (compDiff !== 0) return compDiff;
        
        const seqA = a.sequenceNumber ?? 0;
        const seqB = b.sequenceNumber ?? 0;
        return seqA - seqB;
    });
    
    self.postMessage({ type: 'events', events: eventBuffer });
    eventBuffer = [];
}

function connect() {
    if (ws && ws.readyState === WebSocket.OPEN) return;
    
    try {
        ws = new WebSocket(WS_URL);
        
        ws.onopen = () => {
            self.postMessage({ type: 'status', connected: true });
            
            if (!flushInterval) {
                flushInterval = setInterval(flushBuffer, BUFFER_FLUSH_INTERVAL_MS);
            }
        };
        
        ws.onmessage = (event) => {
            try {
                const traceEvent = JSON.parse(event.data);
                
                eventBuffer.push(traceEvent);
                
                if (eventBuffer.length > MAX_BUFFER_SIZE) {
                    eventBuffer = eventBuffer.slice(-MAX_BUFFER_SIZE);
                }
            } catch (e) {
            }
        };
        
        ws.onclose = () => {
            self.postMessage({ type: 'status', connected: false });
            ws = null;
            
            reconnectTimeout = setTimeout(connect, 3000);
        };
        
        ws.onerror = () => {
        };
        
    } catch (e) {
        self.postMessage({ type: 'status', connected: false });
        reconnectTimeout = setTimeout(connect, 3000);
    }
}

function disconnect() {
    if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
    }
    
    if (flushInterval) {
        clearInterval(flushInterval);
        flushInterval = null;
    }
    
    flushBuffer();
    
    if (ws) {
        ws.close();
        ws = null;
    }
}

function clearBuffer() {
    eventBuffer = [];
    
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'clear' }));
    }
}

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
