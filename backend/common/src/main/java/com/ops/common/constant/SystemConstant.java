package com.ops.common.constant;

/**
 * 系统常量
 */
public class SystemConstant {
    public static final int HEARTBEAT_INTERVAL = 30;
    public static final int OFFLINE_THRESHOLD = 90;
    public static final int TOKEN_LENGTH = 32;
    public static final String TOKEN_HEADER = "X-Token";
    public static final String AUTH_HEADER = "Authorization";
    public static final String JWT_SECRET = "OpsPlatformSecretKey2024VeryLongSecretForJWT";
    public static final int JWT_EXPIRE_MS = 86400000;
    public static final String DEFAULT_SERVER_PORT = "8081";
    public static final String DEFAULT_AGENT_PORT = "2123";
}
