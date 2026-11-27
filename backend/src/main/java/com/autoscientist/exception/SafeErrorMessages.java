package com.autoscientist.exception;

import java.util.regex.Pattern;

public final class SafeErrorMessages {

    public static final String GENERIC = "The request could not be processed.";

    private static final String CREDENTIAL_DETAIL =
            "(?i)(eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
                    + "|Bearer\\s+\\S+"
                    + "|\\b(?:password|passwd|secret|api[_-]?key|key|authorization|jwt|token)\\b\\s*[:=]\\s*\\S+"
                    + "|token\\s*=\\s*[A-Za-z0-9._~-]+"
                    + "|(?<![A-Za-z0-9])[A-Za-z0-9_-]{32,}(?![A-Za-z0-9]))";

    private static final String PATH_DETAIL =
            "(?:[A-Za-z]:\\\\[^\\s\"<>|]+|/[A-Za-z0-9._~/-]+)";

    private static final String SQL_DETAIL =
            "(?i)\\b(?:select|insert\\s+into|update|delete\\s+from|drop\\s+table|alter\\s+table"
                    + "|create\\s+table|constraint|foreign\\s+key|sqlstate|jdbc|syntax\\s+error)\\b";

    private static final Pattern SENSITIVE_CLIENT_DETAIL = Pattern.compile(
            CREDENTIAL_DETAIL + "|" + PATH_DETAIL + "|" + SQL_DETAIL + "|(?m)^\\s*at\\s+[\\w.$]+\\(.*\\)$");

    private static final Pattern SENSITIVE_LOG_DETAIL = Pattern.compile(
            CREDENTIAL_DETAIL + "|" + PATH_DETAIL + "|" + SQL_DETAIL);

    private SafeErrorMessages() {
    }

    public static String forClient(String message) {
        if (message == null || message.isBlank() || SENSITIVE_CLIENT_DETAIL.matcher(message).find()) {
            return GENERIC;
        }
        return message.trim();
    }

    public static String forLog(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return SENSITIVE_LOG_DETAIL.matcher(message).replaceAll("[redacted]");
    }
}
