
package com.nexusai.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class AuditLogger {
    private final Path logPath;

    public AuditLogger(Path logPath) {
        this.logPath = logPath.toAbsolutePath();
    }

    public synchronized void record(
            String actor,
            String action,
            String resource,
            String outcome
    ) {
        String requestId = RequestContext.getRequestId();

        String json = "{"
                + "\"timestamp\":" + AppLogger.quote(Instant.now().toString()) + ","
                + "\"requestId\":" + AppLogger.quote(requestId) + ","
                + "\"actor\":" + AppLogger.quote(actor) + ","
                + "\"action\":" + AppLogger.quote(action) + ","
                + "\"resource\":" + AppLogger.quote(resource) + ","
                + "\"outcome\":" + AppLogger.quote(outcome)
                + "}"
                + System.lineSeparator();

        try {
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(
                    logPath,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            AppLogger.error(
                    "audit_write_failed",
                    "Unable to persist an audit event",
                    exception
            );
        }
    }
}
