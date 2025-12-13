package com.project.tracing;

public class NoOpTracer implements IMessageTracer {
    @Override
    public void trace(TraceEvent event) {
        // Não faz nada - usado quando tracing está desabilitado
    }
}
