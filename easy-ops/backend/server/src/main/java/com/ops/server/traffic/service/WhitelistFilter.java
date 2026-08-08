package com.ops.server.traffic.service;

import com.ops.common.model.NginxSourceWhitelistModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志源白名单过滤器：在查询侧排除命中白名单的维度值。
 * 匹配维度由 type 决定（IP / URI / URI_PREFIX / METHOD），匹配方式由 matchMode 决定（EXACT / PREFIX / CONTAINS）。
 * 同一维度下的多个白名单值取「或」关系——任一命中即排除。
 */
public class WhitelistFilter {

    /** 白名单维度类型 */
    public static final String TYPE_IP = "IP";
    public static final String TYPE_URI = "URI";
    public static final String TYPE_URI_PREFIX = "URI_PREFIX";
    public static final String TYPE_METHOD = "METHOD";

    /** 匹配方式 */
    public static final String MODE_EXACT = "EXACT";
    public static final String MODE_PREFIX = "PREFIX";
    public static final String MODE_CONTAINS = "CONTAINS";

    final List<String> ipExact = new ArrayList<String>();
    private final List<String> ipPrefix = new ArrayList<String>();
    private final List<String> ipContains = new ArrayList<String>();

    final List<String> uriExact = new ArrayList<String>();
    private final List<String> uriPrefix = new ArrayList<String>();
    private final List<String> uriContains = new ArrayList<String>();

    final List<String> methodExact = new ArrayList<String>();

    public static WhitelistFilter from(List<NginxSourceWhitelistModel> whitelist) {
        WhitelistFilter f = new WhitelistFilter();
        if (whitelist == null) {
            return f;
        }
        for (NginxSourceWhitelistModel w : whitelist) {
            if (w == null || w.getEnabled() == null || w.getEnabled() != 1) {
                continue;
            }
            String value = w.getMatchValue() == null ? "" : w.getMatchValue();
            String mode = w.getMatchMode() == null ? MODE_EXACT : w.getMatchMode();
            if (TYPE_IP.equals(w.getType())) {
                if (MODE_PREFIX.equals(mode)) {
                    f.ipPrefix.add(value);
                } else if (MODE_CONTAINS.equals(mode)) {
                    f.ipContains.add(value);
                } else {
                    f.ipExact.add(value);
                }
            } else if (TYPE_URI_PREFIX.equals(w.getType())) {
                f.uriPrefix.add(value);
            } else if (TYPE_URI.equals(w.getType())) {
                if (MODE_PREFIX.equals(mode)) {
                    f.uriPrefix.add(value);
                } else if (MODE_CONTAINS.equals(mode)) {
                    f.uriContains.add(value);
                } else {
                    f.uriExact.add(value);
                }
            } else if (TYPE_METHOD.equals(w.getType())) {
                f.methodExact.add(value.toUpperCase());
            }
        }
        return f;
    }

    public boolean isEmpty() {
        return ipExact.isEmpty() && ipPrefix.isEmpty() && ipContains.isEmpty()
                && uriExact.isEmpty() && uriPrefix.isEmpty() && uriContains.isEmpty()
                && methodExact.isEmpty();
    }

    public boolean isIpBlocked(String ip) {
        if (ip == null) {
            return false;
        }
        for (String v : ipExact) {
            if (v.equals(ip)) {
                return true;
            }
        }
        for (String v : ipPrefix) {
            if (ip.startsWith(v)) {
                return true;
            }
        }
        for (String v : ipContains) {
            if (ip.contains(v)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUriBlocked(String uri) {
        if (uri == null) {
            return false;
        }
        for (String v : uriExact) {
            if (v.equals(uri)) {
                return true;
            }
        }
        for (String v : uriPrefix) {
            if (uri.startsWith(v)) {
                return true;
            }
        }
        for (String v : uriContains) {
            if (uri.contains(v)) {
                return true;
            }
        }
        return false;
    }

    public boolean isMethodBlocked(String method) {
        if (method == null) {
            return false;
        }
        String m = method.toUpperCase();
        for (String v : methodExact) {
            if (v.equals(m)) {
                return true;
            }
        }
        return false;
    }

    /** SQL 侧用：IP 前缀/包含已转成 LIKE 模式（v% / %v%） */
    public List<String> getIpLike() {
        List<String> like = new ArrayList<String>();
        for (String v : ipPrefix) {
            like.add(v + "%");
        }
        for (String v : ipContains) {
            like.add("%" + v + "%");
        }
        return like;
    }

    /** SQL 侧用：URI 前缀/包含已转成 LIKE 模式 */
    public List<String> getUriLike() {
        List<String> like = new ArrayList<String>();
        for (String v : uriPrefix) {
            like.add(v + "%");
        }
        for (String v : uriContains) {
            like.add("%" + v + "%");
        }
        return like;
    }
}
