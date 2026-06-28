package com.ops.server.websocket;

import com.ops.server.knowledge.service.KnowledgeDocumentService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Yjs WebSocket 协作 Handler (y-websocket@1.x 协议实现)
 * 
 * 协议说明:
 * - 客户端连接后先发送 Sync Step 0 (0x00)，询问服务器状态
 * - 服务器响应: 0x00 表示"还是旧文档"，或 0x01 表示"有新文档"
 * - 0x01: 发送完整文档快照 (YDoc)
 * - 0x02: 应用增量更新 (Update)
 */
@Component
public class KbCollabHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(KbCollabHandler.class);

    /** 文档房间：docId → sessions */
    private final Map<String, Set<WebSocketSession>> docRoomSessions = new ConcurrentHashMap<>();

    /** session → docId 映射 */
    private final Map<String, String> sessionDocIdMap = new ConcurrentHashMap<>();

    /** 文档内容缓存：docId → markdown 内容 */
    private final Map<String, String> docContentCache = new ConcurrentHashMap<>();

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

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

        log.info("KbCollab WebSocket connected: sessionId={}, docId={}, roomSize={}",
                session.getId(), docId, docRoomSessions.get(docId).size());

        // 等待客户端发送第一个消息 (Sync Step 0)，然后响应
        // 这里不主动发送，等待客户端发起
    }

    @Override
    protected void handleBinaryMessage(@NotNull WebSocketSession session, @NotNull BinaryMessage message) throws IOException {
        String docId = sessionDocIdMap.get(session.getId());
        if (docId == null) {
            log.warn("KbCollab received binary message from unregistered session: {}", session.getId());
            return;
        }

        byte[] payload = message.getPayload().array();
        if (payload.length == 0) {
            log.warn("KbCollab received empty message from sessionId={}", session.getId());
            return;
        }

        byte messageType = payload[0];
        
        log.debug("KbCollab received message type 0x{:02x} from sessionId={}, docId={}, length={}", 
                messageType & 0xFF, session.getId(), docId, payload.length);

        switch (messageType) {
            case 0x00:
                // Sync Step 0: 客户端询问文档状态
                // 响应: 0x00 = 没变化，0x01 = 有完整文档
                handleSyncStep0(session, docId, payload);
                break;
                
            case 0x01:
                // 完整文档 (YDoc)
                log.debug("Received YDoc from sessionId={}, docId={}", session.getId(), docId);
                break;
                
            case 0x02:
                // 增量更新 (Update)
                handleSyncStep2(session, docId, payload);
                break;
                
            default:
                log.debug("Unknown message type 0x{:02x}, ignoring", messageType & 0xFF);
        }

        // 转发给同房间其他 session
        forwardToRoom(docId, session, payload);
    }

    /**
     * 处理 Sync Step 0: 客户端询问文档状态
     * 协议: 0x00 (无变化) 或 0x01 (有完整文档)
     */
    private void handleSyncStep0(WebSocketSession session, String docId, byte[] payload) {
        try {
            String content = docContentCache.get(docId);
            if (content == null || content.isEmpty()) {
                // 从数据库加载
                String markdown = knowledgeDocumentService.getDocumentContent(Long.parseLong(docId));
                content = markdown != null ? markdown : "";
                docContentCache.put(docId, content);
            }

            if (content.isEmpty()) {
                // 发送 0x00: 文档为空
                session.sendMessage(new BinaryMessage(new byte[]{0x00}));
                log.debug("Sent Sync Step 0 response (empty) to session={}", session.getId());
            } else {
                // 发送 0x01: 有完整文档，后跟文档内容
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(0x01); // 0x01 = 有完整文档
                
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                writeVarInt(baos, contentBytes.length);
                baos.write(contentBytes);

                session.sendMessage(new BinaryMessage(baos.toByteArray()));
                log.debug("Sent Sync Step 0 response (has doc) to session={}, docId={}, contentLength={}", 
                        session.getId(), docId, contentBytes.length);
            }
        } catch (IOException e) {
            log.error("Failed to send Sync Step 0 response to session={}", session.getId(), e);
        }
    }

    /**
     * 处理 Sync Step 2: 增量更新
     */
    private void handleSyncStep2(WebSocketSession session, String docId, byte[] payload) {
        try {
            // 跳过消息类型 (1 byte) 和长度 (varint)
            int offset = 1;
            int length = readVarInt(payload, offset);
            offset += getVarIntLength(length);

            if (offset + length <= payload.length) {
                String updateContent = new String(payload, offset, length, StandardCharsets.UTF_8);
                docContentCache.put(docId, updateContent);

                // 保存到数据库
                knowledgeDocumentService.saveDocumentContent(Long.parseLong(docId), updateContent);

                log.debug("Updated document {} with new content from sessionId={}", docId, session.getId());
            }
        } catch (Exception e) {
            log.error("Failed to handle Sync Step 2 for docId={}", docId, e);
        }
    }

    /**
     * 转发消息给同房间其他 session
     */
    private void forwardToRoom(String docId, WebSocketSession sourceSession, byte[] payload) throws IOException {
        Set<WebSocketSession> roomSessions = docRoomSessions.get(docId);
        if (roomSessions == null) return;

        for (WebSocketSession peer : roomSessions) {
            if (!peer.getId().equals(sourceSession.getId()) && peer.isOpen()) {
                peer.sendMessage(new BinaryMessage(payload));
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

    // ====== VarInt 编码/解码工具方法 ======

    private void writeVarInt(ByteArrayOutputStream out, int value) throws IOException {
        while ((value & -0x40) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private int readVarInt(byte[] data, int offset) {
        int result = 0;
        int shift = 0;
        int pos = offset;

        while (pos < data.length) {
            byte b = data[pos++];
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        return result;
    }

    private int getVarIntLength(int value) {
        int length = 1;
        while ((value & -0x40) != 0) {
            length++;
            value >>>= 7;
        }
        return length;
    }

    // ====== 内部方法 ======

    /**
     * 从 URI path 中提取文档 ID
     * 路径格式：/ws/kb-collab/{docId}
     */
    private String extractDocId(String path) {
        if (path == null) return null;
        String prefix = "/ws/kb-collab/";
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return null;
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
