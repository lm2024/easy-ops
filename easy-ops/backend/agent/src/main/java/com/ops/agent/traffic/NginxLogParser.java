package com.ops.agent.traffic;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Nginx access log（双引号分隔字段，兼容 main 格式）。
 */
public class NginxLogParser {

    private static final Pattern QUOTED_FIELD = Pattern.compile("\"([^\"]*)\"");

    /**
     * 解析结果
     */
    public static class ParsedLine {
        public long timestampMs;
        public String clientIp;
        public String method;
        public String uri;
        public int status;
        public long requestTimeMs;
        public long upstreamTimeMs;
    }

    /**
     * 解析一行访问日志；失败返回 null。
     */
    public ParsedLine parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        List<String> fields = extractQuotedFields(line);
        if (fields.size() < 6) {
            return null;
        }
        ParsedLine parsed = new ParsedLine();
        parsed.timestampMs = parseTime(fields.get(0));
        if (parsed.timestampMs <= 0) {
            return null;
        }
        String remoteAddr = safe(fields.get(1));
        String xff = fields.size() > 2 ? safe(fields.get(2)) : "";
        parsed.clientIp = resolveClientIp(remoteAddr, xff);
        String request = fields.size() > 4 ? safe(fields.get(4)) : "";
        parseRequest(request, parsed);
        if (parsed.uri == null || parsed.uri.isEmpty()) {
            return null;
        }
        parsed.status = parseInt(fields.size() > 5 ? fields.get(5) : "0", 0);
        if (fields.size() > 10) {
            parsed.requestTimeMs = toMillis(fields.get(10));
        }
        if (fields.size() > 11) {
            parsed.upstreamTimeMs = toMillis(fields.get(11));
        }
        return parsed;
    }

    private List<String> extractQuotedFields(String line) {
        List<String> fields = new ArrayList<String>();
        Matcher matcher = QUOTED_FIELD.matcher(line);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        return fields;
    }

    private String resolveClientIp(String remoteAddr, String xff) {
        if (xff != null && !xff.isEmpty() && !"-".equals(xff)) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return remoteAddr == null || remoteAddr.isEmpty() ? "-" : remoteAddr;
    }

    private void parseRequest(String request, ParsedLine parsed) {
        if (request == null || request.isEmpty() || "-".equals(request)) {
            return;
        }
        int space = request.indexOf(' ');
        if (space <= 0) {
            parsed.uri = request;
            return;
        }
        parsed.method = request.substring(0, space).trim();
        String rest = request.substring(space + 1).trim();
        int httpIdx = rest.lastIndexOf(" HTTP/");
        String path = httpIdx > 0 ? rest.substring(0, httpIdx).trim() : rest;
        int q = path.indexOf('?');
        parsed.uri = q > 0 ? path.substring(0, q) : path;
    }

    private long parseTime(String raw) {
        if (raw == null || raw.isEmpty() || "-".equals(raw)) {
            return 0L;
        }
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "dd/MMM/yyyy:HH:mm:ss Z"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setTimeZone(TimeZone.getDefault());
                ParsePosition pos = new ParsePosition(0);
                Date date = sdf.parse(raw, pos);
                if (date != null) {
                    return date.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private long toMillis(String secondsText) {
        try {
            double sec = Double.parseDouble(secondsText);
            if (sec < 0) {
                return 0L;
            }
            return Math.round(sec * 1000D);
        } catch (Exception e) {
            return 0L;
        }
    }

    private int parseInt(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
