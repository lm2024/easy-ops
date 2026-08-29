package com.ops.agent.arthas;

/**
 * Arthas 命令类型枚举与白名单
 * MVP 阶段只允许白名单内的命令
 */
public enum ArthasCommandType {
    DASHBOARD("dashboard", "实时面板"),
    MEMORY("memory", "内存信息"),
    VERSION("version", "Arthas版本"),
    JVM("jvm", "JVM信息"),
    THREAD("thread", "线程信息"),
    TRACE("trace", "方法追踪"),
    WATCH("watch", "方法观测"),
    MONITOR("monitor", "方法监控"),
    STACK("stack", "调用栈"),
    TT("tt", "时空隧道"),
    RESET("reset", "清除增强"),
    HEAPDUMP("heapdump", "堆转储"),
    PROFILER("profiler", "火焰图采样"),
    VMTOOL("vmtool", "JVMTI工具"),
    JAD("jad", "反编译"),
    SC("sc", "搜索类"),
    SM("sm", "搜索方法"),
    DUMP("dump", "导出类字节码"),
    CLASSLOADER("classloader", "类加载器"),
    VMOPTION("vmoption", "JVM选项"),
    PERFCOUNTER("perfcounter", "性能计数器"),
    SYSENV("sysenv", "环境变量"),
    SYSPROP("sysprop", "系统属性"),
    LOGGER("logger", "日志配置"),
    OPTIONS("options", "Arthas选项"),
    MBEAN("mbean", "MBean信息"),
    // 以下为高危命令，仅内部使用或明确禁用，不对外开放
    STOP("stop", "停止Arthas(仅内部detach使用)"),
    REDEFINE("redefine", "热更新(禁用)"),
    RETRANSFORM("retransform", "类重转换(禁用)"),
    SHUTDOWN("shutdown", "关闭JVM(禁用)"),
    OGNL("ognl", "OGNL执行(禁用)"),
    MC("mc", "内存编译(禁用)");

    private final String command;
    private final String description;

    ArthasCommandType(String command, String description) {
        this.command = command;
        this.description = description;
    }

    public String getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 对外放开的命令白名单。
     * 注意：STOP 不在其中——detach 时由 ArthasSessionManager 直接调用 HTTP API，
     * 不经过 /exec 接口。若对外放开，任何用户都能执行 stop 卸载掉别人的诊断会话。
     */
    private static final java.util.Set<String> ALLOWED = new java.util.HashSet<>();
    static {
        ALLOWED.add(DASHBOARD.command);
        ALLOWED.add(MEMORY.command);
        ALLOWED.add(VERSION.command);
        ALLOWED.add(JVM.command);
        ALLOWED.add(THREAD.command);
        ALLOWED.add(TRACE.command);
        ALLOWED.add(WATCH.command);
        ALLOWED.add(MONITOR.command);
        ALLOWED.add(STACK.command);
        ALLOWED.add(TT.command);
        ALLOWED.add(RESET.command);
        ALLOWED.add(HEAPDUMP.command);
        ALLOWED.add(PROFILER.command);
        ALLOWED.add(VMTOOL.command);
        ALLOWED.add(JAD.command);
        ALLOWED.add(SC.command);
        ALLOWED.add(SM.command);
        ALLOWED.add(DUMP.command);
        ALLOWED.add(CLASSLOADER.command);
        ALLOWED.add(VMOPTION.command);
        ALLOWED.add(PERFCOUNTER.command);
        ALLOWED.add(SYSENV.command);
        ALLOWED.add(SYSPROP.command);
        ALLOWED.add(LOGGER.command);
        ALLOWED.add(OPTIONS.command);
        ALLOWED.add(MBEAN.command);
    }

    /**
     * 命令分隔符。Arthas 支持用分号/换行串联多条命令，
     * 若不拦截，"thread ;stop" 这类输入会以 thread 通过白名单、顺带执行 stop。
     *
     * <p>这里刻意不拦截 & 和 | ：watch/trace/monitor 的条件表达式会用到 "&&"，
     * 管道 "|" 也是 Arthas 4 的正常语法（如 thread | grep），拦截会误伤正常用法。
     */
    private static final char[] COMMAND_SEPARATORS = {';', '\n', '\r'};

    /**
     * 判断命令是否允许执行。
     * 同时拦截命令串联，避免通过分隔符夹带白名单外的命令。
     */
    public static boolean isAllowed(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        String trimmed = command.trim();
        for (char sep : COMMAND_SEPARATORS) {
            if (trimmed.indexOf(sep) >= 0) {
                return false;
            }
        }
        return ALLOWED.contains(detect(trimmed));
    }

    /**
     * 提取命令名（第一个空格前的片段）
     */
    public static String detect(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "unknown";
        }
        return command.trim().split("\\s+")[0];
    }
}
