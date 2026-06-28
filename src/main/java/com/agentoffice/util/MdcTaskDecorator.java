package com.agentoffice.util;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Wraps Runnable/Callable to propagate MDC context (especially traceId) across threads.
 */
public class MdcTaskDecorator {

    public static Runnable wrap(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }

    public static <T> java.util.concurrent.Callable<T> wrap(java.util.concurrent.Callable<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return task.call();
            } finally {
                MDC.clear();
            }
        };
    }
}
