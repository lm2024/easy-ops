package com.ops.agent.arthas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arthas HTTP API 端口分配器
 * 在 30000-60000 范围内分配随机端口，检测可用性
 */
@Component
public class ArthasPortAllocator {
    private static final Logger log = LoggerFactory.getLogger(ArthasPortAllocator.class);

    private static final int PORT_MIN = 30000;
    private static final int PORT_MAX = 60000;
    private static final int MAX_RETRY = 50;

    private final Set<Integer> allocated = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    /**
     * 分配一个可用端口
     * @return 可用端口号
     * @throws RuntimeException 50次尝试后仍无法分配
     */
    public synchronized int allocate() {
        for (int i = 0; i < MAX_RETRY; i++) {
            int port = PORT_MIN + random.nextInt(PORT_MAX - PORT_MIN);
            if (allocated.contains(port)) {
                continue;
            }
            if (isPortAvailable(port)) {
                allocated.add(port);
                log.debug("Arthas 端口分配成功: {}", port);
                return port;
            }
        }
        throw new RuntimeException("无法分配可用 Arthas 端口（范围 " + PORT_MIN + "-" + PORT_MAX + "）");
    }

    /**
     * 释放端口
     */
    public void release(int port) {
        allocated.remove(port);
        log.debug("Arthas 端口释放: {}", port);
    }

    /**
     * 检测端口是否可用（未被占用）
     * 能连上说明被占用，连不上说明可用
     */
    private boolean isPortAvailable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 100);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    public int getActiveCount() {
        return allocated.size();
    }
}
