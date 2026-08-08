package com.ops.agent.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Nginx access log。
 * 兼容两种格式：
 * 1) 引号分隔字段（旧 main 格式，双引号包裹，按位置取值）；
 * 2) JSON 格式（escape=json，字段带名，推荐，新增字段不破坏解析）。
 */
public class NginxLogParser {

    private static final Pattern QUOTED_FIELD = Pattern.compile("\"([^\"]*)\"");
    private static final ObjectMapper JSON = new ObjectMapper();

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

        // ===== 扩展字段（按需采集，缺省为空/0）=====
        public String remoteUser = "";
        public String userAgent = "";
        public String referer = "";
        public long bodyBytes;
        public String upstreamAddr = "";
        public int upstreamStatus;
        public long upstreamConnectTimeMs;
        public long upstreamHeaderTimeMs;
        public long requestLength;
        public String cacheStatus = "";
        public String scheme = "";
        public String sslProtocol = "";
        public String sslCipher = "";
        public String host = "";
        public long msec;
    }

    /**
     * 解析一行访问日志；失败返回 null。
     */
    public ParsedLine parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("{")) {
            return parseJson(trimmed);
        }
        return parseQuoted(trimmed);
    }

    // ===================== JSON 格式 =====================

    @SuppressWarnings("unchecked")
    private ParsedLine parseJson(String line) {
        Map<String, Object> m;
        try {
            m = JSON.readValue(line, Map.class);
        } catch (Exception e) {
            return null;
        }
        if (m == null || m.isEmpty()) {
            return null;
        }
        ParsedLine parsed = new ParsedLine();
        parsed.timestampMs = parseTime(str(m.get("time")));
        if (parsed.timestampMs <= 0) {
            // 退而用 msec 毫秒时间戳
            long ms = toLong(m.get("msec"), -1L);
            if (ms > 0) {
                parsed.timestampMs = ms;
            } else {
                return null;
            }
        }
        String xff = str(m.get("xff"));
        String remoteAddr = str(m.get("remote_addr"));
        parsed.clientIp = resolveClientIp(remoteAddr, xff);
        parsed.method = orDefault(str(m.get("method")), "GET");
        String uri = str(m.get("uri"));
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        // uri 去掉查询参数，保持与旧逻辑一致
        int q = uri.indexOf('?');
        parsed.uri = q > 0 ? uri.substring(0, q) : uri;
        parsed.status = toInt(m.get("status"), 0);
        parsed.requestTimeMs = toMillis(str(m.get("request_time")));
        parsed.upstreamTimeMs = toMillis(str(m.get("upstream_time")));

        parsed.remoteUser = str(m.get("remote_user"));
        parsed.userAgent = str(m.get("user_agent"));
        parsed.referer = str(m.get("referer"));
        parsed.bodyBytes = toLong(m.get("body_bytes"), 0L);
        parsed.upstreamAddr = str(m.get("upstream_addr"));
        parsed.upstreamStatus = toInt(m.get("upstream_status"), 0);
        parsed.upstreamConnectTimeMs = toMillis(str(m.get("upstream_connect_time")));
        parsed.upstreamHeaderTimeMs = toMillis(str(m.get("upstream_header_time")));
        parsed.requestLength = toLong(m.get("request_length"), 0L);
        parsed.cacheStatus = str(m.get("cache_status"));
        parsed.scheme = str(m.get("scheme"));
        parsed.sslProtocol = str(m.get("ssl_protocol"));
        parsed.sslCipher = str(m.get("ssl_cipher"));
        parsed.host = str(m.get("host"));
        parsed.msec = toLong(m.get("msec"), 0L);
        return parsed;
    }

    // ===================== 引号分隔格式（旧，兼容）=====================

    private ParsedLine parseQuoted(String line) {
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
        // 旧格式下标：6=body_bytes,7=referer,8=user_agent,9=upstream_addr,12=host
        if (fields.size() > 6) {
            parsed.bodyBytes = toLong(fields.get(6), 0L);
        }
        if (fields.size() > 7) {
            parsed.referer = safe(fields.get(7));
        }
        if (fields.size() > 8) {
            parsed.userAgent = safe(fields.get(8));
        }
        if (fields.size() > 9) {
            parsed.upstreamAddr = safe(fields.get(9));
        }
        if (fields.size() > 12) {
            parsed.host = safe(fields.get(12));
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

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String orDefault(String value, String def) {
        return (value == null || value.isEmpty()) ? def : value;
    }

    private int parseInt(String text, int defaultValue) {
        return toInt(text, defaultValue);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
