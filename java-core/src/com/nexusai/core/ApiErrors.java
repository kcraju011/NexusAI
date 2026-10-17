
package com.nexusai.core;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;

public final class ApiErrors {
    private ApiErrors() {}

    public static void send(
            HttpExchange exchange,
            int status,
            String code,
            String message
    ) throws IOException {
        String requestId = RequestContext.getRequestId();

        String body = "{"
                + "\"error\":" + AppLogger.quote(message) + ","
                + "\"code\":" + AppLogger.quote(code) + ","
                + "\"requestId\":" + AppLogger.quote(requestId)
                + "}";

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=utf-8"
        );

        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
