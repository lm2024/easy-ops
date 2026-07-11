package com.ops.server.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录验证码服务（图形验证码 + 过期清理）
 */
@Service
public class CaptchaService {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int EXPIRE_MS = 5 * 60 * 1000;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<String, CaptchaEntry>();

    /**
     * 生成验证码，返回 id 与 base64 图片
     */
    public Map<String, String> generate() {
        cleanupExpired();
        String code = randomCode(4);
        String id = UUID.randomUUID().toString().replace("-", "");
        store.put(id, new CaptchaEntry(code.toLowerCase(), System.currentTimeMillis() + EXPIRE_MS));
        Map<String, String> result = new ConcurrentHashMap<String, String>();
        result.put("captchaId", id);
        result.put("imageBase64", renderImage(code));
        return result;
    }

    /**
     * 校验验证码（一次性使用）
     */
    public boolean verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            return false;
        }
        CaptchaEntry entry = store.get(captchaId);
        if (entry == null || System.currentTimeMillis() > entry.expireAt) {
            return false;
        }
        if (!entry.code.equals(captchaCode.trim().toLowerCase())) {
            return false;
        }
        store.remove(captchaId);
        return true;
    }

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String renderImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(15, 15, 16));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setFont(new Font("Monospaced", Font.BOLD, 24));
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(80 + random.nextInt(100), 80 + random.nextInt(100), 80 + random.nextInt(100)));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(232, 255, 89));
            g.drawString(String.valueOf(code.charAt(i)), 18 + i * 24, 28 + random.nextInt(6));
        }
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, CaptchaEntry> e : store.entrySet()) {
            if (e.getValue().expireAt < now) {
                store.remove(e.getKey());
            }
        }
    }

    private static final class CaptchaEntry {
        private final String code;
        private final long expireAt;

        private CaptchaEntry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }
}
