package com.ops.common.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 日志行级别识别与过滤工具。
 */
public final class LogLevelUtil {

  private static final Pattern LEVEL_PATTERN =
      Pattern.compile("\\s(DEBUG|TRACE|INFO|WARN|ERROR|FATAL)\\s");

  private LogLevelUtil() {}

  /**
   * 判断日志行是否匹配指定级别；level 为 null/空/ALL 时返回 true。
   */
  public static boolean matches(String line, String level) {
    if (level == null || level.trim().isEmpty() || "ALL".equalsIgnoreCase(level.trim())) {
      return true;
    }
    String expected = level.trim().toUpperCase(Locale.ROOT);
    String actual = extractLevel(line);
    if (actual == null) {
      return false;
    }
    if ("ERROR".equals(expected)) {
      return "ERROR".equals(actual) || "FATAL".equals(actual);
    }
    return expected.equals(actual);
  }

  /**
   * 从标准 Logback/Spring Boot 日志行提取级别。
   */
  public static String extractLevel(String line) {
    if (line == null || line.isEmpty()) {
      return null;
    }
    java.util.regex.Matcher matcher = LEVEL_PATTERN.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }
}
