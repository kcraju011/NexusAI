
package com.nexusai.core;

import java.nio.file.Files;
import java.nio.file.Path;

public class ObservabilityTest {
    public static void main(String[] args) throws Exception {
        String requestId = RequestContext.start(null);

        assert requestId != null;
        assert !requestId.isBlank();
        assert RequestContext.getRequestId().equals(requestId);

        String supplied = "test-request-123";
        assert RequestContext.start(supplied).equals(supplied);

        // Unsafe IDs must be replaced with generated IDs.
        String unsafe = RequestContext.start("bad id\nforged-log");
        assert !unsafe.contains("\n");
        assert !unsafe.equals("bad id\nforged-log");

        RequestContext.clear();
        assert RequestContext.getRequestId() == null;

        Path temp = Files.createTempFile("nexusai-audit-", ".jsonl");

        try {
            AuditLogger audit = new AuditLogger(temp);
            RequestContext.start("audit-test-001");

            audit.record(
                    "test-admin",
                    "INCIDENT_CREATE",
                    "INC-TEST-001",
                    "SUCCESS"
            );

            String contents = Files.readString(temp);

            assert contents.contains("\"requestId\":\"audit-test-001\"");
            assert contents.contains("\"actor\":\"test-admin\"");
            assert contents.contains("\"action\":\"INCIDENT_CREATE\"");
            assert contents.contains("\"outcome\":\"SUCCESS\"");

            String quoted = AppLogger.quote("hello\n\"world\"");
            assert quoted.equals("\"hello\\n\\\"world\\\"\"");

            System.out.println("All observability tests passed.");
        } finally {
            RequestContext.clear();
            Files.deleteIfExists(temp);
        }
    }
}
