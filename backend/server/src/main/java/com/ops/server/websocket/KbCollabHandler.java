package com.ops.server.websocket;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Yjs WebSocket 协作 Handler (简化实现：只做消息转发)
 *
 * 简化说明:
 * - 后端只负责转发消息给同房间的其他客户端
 * - 不做任何协议处理，由 y-websocket 客户端自己处理同步协议
 */
@Component
public class KbCollabHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(KbCollabHandler.class);

    /** 文档房间：docId → sessions */
    private final Map<String, Set<WebSocketSession>> docRoomSessions = new ConcurrentHashMap<>();

    /** session → docId 映射 */
    private final Map<String, String> sessionDocIdMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            log.warn("KbCollab WebSocket connected without URI: {}", session.getId());
            closeSession(session, CloseStatus.BAD_DATA);
            return;
        }

        // 从 URI path 提取 docId: /ws/kb-collab/{docId}
        String path = uri.getPath();
        String docId = extractDocId(path);
        if (docId == null || docId.isEmpty()) {
            log.warn("KbCollab WebSocket missing docId in path: {}", path);
            closeSession(session, CloseStatus.BAD_DATA);
            return;
        }

        // 加入房间
        sessionDocIdMap.put(session.getId(), docId);
        docRoomSessions.computeIfAbsent(docId, k -> new CopyOnWriteArraySet<>()).add(session);

        log.info("KbCollab WebSocket connected: sessionId={}, docId={}, roomSize={}, sessionDocIdMap.size={}",
                session.getId(), docId, docRoomSessions.get(docId).size(), sessionDocIdMap.size());

        // 等待客户端发送第一个消息，然后转发给同房间其他客户端
    }

    @Override
    protected void handleBinaryMessage(@NotNull WebSocketSession session, @NotNull BinaryMessage message) throws IOException {
        String docId = sessionDocIdMap.get(session.getId());
        if (docId == null) {
            log.warn("KbCollab received binary message from unregistered session: {}, sessionDocIdMap.keys={}",
                    session.getId(), sessionDocIdMap.keySet());
            return;
        }

        byte[] payload = message.getPayload().array();
        if (payload.length == 0) {
            log.warn("KbCollab received empty message from sessionId={}", session.getId());
            return;
        }

        byte messageType = payload[0];

        log.debug("KbCollab received message type 0x{:02x} from sessionId={}, docId={}, length={}, roomSize={}",
                messageType & 0xFF, session.getId(), docId, payload.length,
                docRoomSessions.get(docId) != null ? docRoomSessions.get(docId).size() : 0);

        // 简化实现：只转发消息，不做协议处理
        // y-websocket 客户端会自己处理同步协议
        forwardToRoom(docId, session, payload);
    }

    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) {
        String docId = sessionDocIdMap.get(session.getId());
        if (docId == null) {
            log.warn("KbCollab received text message from unregistered session: {}", session.getId());
            return;
        }

        String payload = message.getPayload();
        if (payload == null || payload.isEmpty()) {
            log.warn("KbCollab received empty text message from sessionId={}", session.getId());
            return;
        }

        log.debug("KbCollab received text message from sessionId={}, docId={}, length={}, roomSize={}",
                session.getId(), docId, payload.length(),
                docRoomSessions.get(docId) != null ? docRoomSessions.get(docId).size() : 0);

        // 转发文本消息给同房间其他客户端
        forwardTextToRoom(docId, session, payload);
    }

    /**
     * 转发二进制消息给同房间其他 session
     */
    private void forwardToRoom(String docId, WebSocketSession sourceSession, byte[] payload) {
        Set<WebSocketSession> roomSessions = docRoomSessions.get(docId);
        if (roomSessions == null) return;

        for (WebSocketSession peer : roomSessions) {
            if (!peer.getId().equals(sourceSession.getId()) && peer.isOpen()) {
                try {
                    peer.sendMessage(new BinaryMessage(payload));
                } catch (IOException e) {
                    log.error("Failed to send binary message to session: {}", peer.getId(), e);
                    // 移除已关闭的 session
                    roomSessions.remove(peer);
                } catch (IllegalStateException e) {
                    log.warn("Session already closed, removing: {}", peer.getId());
                    roomSessions.remove(peer);
                }
            }
        }
    }

    /**
     * 转发文本消息给同房间其他 session
     */
    private void forwardTextToRoom(String docId, WebSocketSession sourceSession, String payload) {
        Set<WebSocketSession> roomSessions = docRoomSessions.get(docId);
        if (roomSessions == null) return;

        for (WebSocketSession peer : roomSessions) {
            if (!peer.getId().equals(sourceSession.getId()) && peer.isOpen()) {
                try {
                    peer.sendMessage(new TextMessage(payload));
                } catch (Exception e) {
                    log.error("Failed to send text message to session: {}", peer.getId(), e);
                    // 移除已关闭的 session
                    roomSessions.remove(peer);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        String docId = sessionDocIdMap.remove(session.getId());
        if (docId != null) {
            Set<WebSocketSession> roomSessions = docRoomSessions.get(docId);
            if (roomSessions != null) {
                roomSessions.remove(session);
                if (roomSessions.isEmpty()) {
                    docRoomSessions.remove(docId);
                    log.info("KbCollab room empty for docId={}, saving state", docId);
                } else {
                    log.info("KbCollab session closed: sessionId={}, docId={}, remaining={}",
                            session.getId(), docId, roomSessions.size());
                }
            }
        }
        log.info("KbCollab WebSocket closed: sessionId={}, docId={}, status={}",
                session.getId(), docId, status);
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) {
        log.error("KbCollab WebSocket transport error: sessionId={}", session.getId(), exception);
        try {
            afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error cleaning up KbCollab session after transport error", e);
        }
    }

    // ====== 内部方法 ======

    /**
     * 从 URI path 中提取文档 ID
     * 路径格式：/ws/kb-collab/{docId} 或 /api/ws/kb-collab/{docId}
     * 注意：y-websocket 会把 roomName 拼接到 URL 末尾，所以需要只取第一段
     */
    private String extractDocId(String path) {
        if (path == null) return null;
        String prefix = "/ws/kb-collab/";
        String docIdWithPath = null;

        // 先尝试不带 context-path 的路径
        if (path.startsWith(prefix)) {
            docIdWithPath = path.substring(prefix.length());
        } else {
            // 再尝试带 context-path 的路径（如 /api/ws/kb-collab/1）
            int idx = path.indexOf(prefix);
            if (idx >= 0) {
                docIdWithPath = path.substring(idx + prefix.length());
            }
        }

        if (docIdWithPath == null) return null;

        // 只取第一段作为 docId（y-websocket 会把 roomName 拼接到后面）
        int slashIdx = docIdWithPath.indexOf('/');
        if (slashIdx > 0) {
            return docIdWithPath.substring(0, slashIdx);
        }
        return docIdWithPath;
    }

    /**
     * 安全关闭 session
     */
    private void closeSession(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {}
    }
}
