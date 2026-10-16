
package com.nexusai.core;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestContext {
    private static final ThreadLocal<String> REQUEST_ID =
            new ThreadLocal<>();

    private static final Pattern SAFE_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestContext() {}

    public static String start(String suppliedId) {
        String id = suppliedId != null
                && SAFE_ID.matcher(suppliedId).matches()
                ? suppliedId
                : UUID.randomUUID().toString();

        REQUEST_ID.set(id);
        return id;
    }

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
