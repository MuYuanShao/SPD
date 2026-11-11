package com.hospital.spd.common;

import java.util.Set;
import java.util.regex.Pattern;

/** Validates text fields before write commands normalize or persist them. */
public final class WriteFieldWhitespace {
    private static final String ERROR_MESSAGE = "不支持空格及换行符存在！";
    private static final Pattern WHITESPACE = Pattern.compile("(?U)[\\s\\uFEFF]");
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private WriteFieldWhitespace() {}

    public static void requireValid(String value) {
        if (value != null && WHITESPACE.matcher(value).find()) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
    }

    public static void requireCsvRecord(String line) {
        requireValid(line);
        boolean quoted = false;
        boolean fieldStart = true;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"' && (quoted || fieldStart)) {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') index++;
                else quoted = !quoted;
            }
            fieldStart = !quoted && character == ',';
        }
        if (quoted) throw new IllegalArgumentException(ERROR_MESSAGE);
    }

    static boolean applies(String method, String path) {
        return WRITE_METHODS.contains(method) && !path.equals("/mcp") && !path.startsWith("/mcp/");
    }
}
