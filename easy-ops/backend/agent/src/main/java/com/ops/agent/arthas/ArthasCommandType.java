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
    HEAPDUMP("heapdump", "堆转储"),
    PROFILER("profiler", "火焰图采样"),
    VMTOOL("vmtool", "JVMTI工具"),
    JAD("jad", "反编译"),
    SC("sc", "搜索类"),
    SM("sm", "搜索方法"),
    CLASSLOADER("classloader", "类加载器"),
    VMOPTION("vmoption", "JVM选项"),
    PERFCOUNTER("perfcounter", "性能计数器"),
    SYSENV("sysenv", "环境变量"),
    SYSPROP("sysprop", "系统属性"),
    STOP("stop", "停止Arthas(内部使用)"),
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

    private static final java.util.Set<String> ALLOWED = new java.util.HashSet<>();
    static {
        ALLOWED.add(DASHBOARD.command);
        ALLOWED.add(MEMORY.command);
        ALLOWED.add(VERSION.command);
        ALLOWED.add(JVM.command);
        ALLOWED.add(THREAD.command);
        ALLOWED.add(TRACE.command);
        ALLOWED.add(HEAPDUMP.command);
        ALLOWED.add(PROFILER.command);
        ALLOWED.add(VMTOOL.command);
        ALLOWED.add(JAD.command);
        ALLOWED.add(SC.command);
        ALLOWED.add(SM.command);
        ALLOWED.add(CLASSLOADER.command);
        ALLOWED.add(VMOPTION.command);
        ALLOWED.add(PERFCOUNTER.command);
        ALLOWED.add(SYSENV.command);
        ALLOWED.add(SYSPROP.command);
        ALLOWED.add(STOP.command);
    }

    public static boolean isAllowed(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        String cmdName = command.trim().split("\\s+")[0];
        return ALLOWED.contains(cmdName);
    }

    public static String detect(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "unknown";
        }
        return command.trim().split("\\s+")[0];
    }
}
