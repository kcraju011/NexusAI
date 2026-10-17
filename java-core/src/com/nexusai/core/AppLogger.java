
package com.nexusai.core;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AppLogger {
    private static final Logger LOGGER =
            Logger.getLogger("com.nexusai");

    private AppLogger() {}

    public static void info(String event, String message) {
        write(Level.INFO, event, message, null);
    }

    public static void warn(String event, String message) {
        write(Level.WARNING, event, message, null);
    }

    public static void error(
            String event,
            String message,
            Throwable cause
    ) {
        write(Level.SEVERE, event, message, cause);
    }

    private static void write(
            Level level,
            String event,
            String message,
            Throwable cause
    ) {
        String requestId = RequestContext.getRequestId();

        StringBuilder json = new StringBuilder("{")
                .append("\"timestamp\":").append(quote(Instant.now().toString()))
                .append(",\"level\":").append(quote(level.getName()))
                .append(",\"event\":").append(quote(event))
                .append(",\"message\":").append(quote(message));

        if (requestId != null) {
            json.append(",\"requestId\":").append(quote(requestId));
        }

        json.append("}");

        if (cause == null) {
            LOGGER.log(level, json.toString());
        } else {
            // The stack trace goes to the server logs, not the HTTP response.
            LOGGER.log(level, json.toString(), cause);
        }
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
