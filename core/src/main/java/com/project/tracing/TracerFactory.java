package com.project.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracerFactory {
    private static final Logger logger = LoggerFactory.getLogger("Tracing.Factory");
    private static final String TRACE_ENABLED_PROPERTY = "trace.enabled";
    private static volatile IMessageTracer instance;

    private TracerFactory() {}

    public static IMessageTracer getTracer() {
        if (instance == null) {
            synchronized (TracerFactory.class) {
                if (instance == null) {
                    boolean enabled = Boolean.getBoolean(TRACE_ENABLED_PROPERTY);
                    if (enabled) {
                        instance = new UdpTracer();
                        logger.info("Tracing HABILITADO - eventos serão enviados via UDP");
                    } else {
                        instance = new NoOpTracer();
                        logger.debug("Tracing desabilitado - use -D{}=true para habilitar", TRACE_ENABLED_PROPERTY);
                    }
                }
            }
        }
        return instance;
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(TRACE_ENABLED_PROPERTY);
    }
}
