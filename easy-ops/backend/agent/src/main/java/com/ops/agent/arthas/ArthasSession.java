package com.ops.agent.arthas;

/**
 * Arthas 会话模型
 * 每个 attach 到目标 JVM 的 Arthas 实例对应一个会话
 */
public class ArthasSession {
    private long pid;
    private int port;
    private String sessionId;
    private String arthasVersion;
    private long attachTime;
    private long lastActiveTime;
    private transient Process arthasProcess;
    private String projectId;
    private String nodeId;
    private String workingDir;
    private volatile boolean attached;

    public long getPid() { return pid; }
    public void setPid(long pid) { this.pid = pid; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getArthasVersion() { return arthasVersion; }
    public void setArthasVersion(String arthasVersion) { this.arthasVersion = arthasVersion; }

    public long getAttachTime() { return attachTime; }
    public void setAttachTime(long attachTime) { this.attachTime = attachTime; }

    public long getLastActiveTime() { return lastActiveTime; }
    public void setLastActiveTime(long lastActiveTime) { this.lastActiveTime = lastActiveTime; }

    public Process getArthasProcess() { return arthasProcess; }
    public void setArthasProcess(Process arthasProcess) { this.arthasProcess = arthasProcess; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getWorkingDir() { return workingDir; }
    public void setWorkingDir(String workingDir) { this.workingDir = workingDir; }

    public boolean isAttached() { return attached; }
    public void setAttached(boolean attached) { this.attached = attached; }
}
