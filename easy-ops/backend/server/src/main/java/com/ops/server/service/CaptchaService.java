package com.ops.server.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录验证码服务（增强版：5位字符 + 角度扭曲 + 密集干扰 + 噪点）
 */
@Service
public class CaptchaService {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 54;
    private static final int EXPIRE_MS = 5 * 60 * 1000;
    private static final int CODE_LEN = 5;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // 每组颜色搭配（字符色 + 背景色）
    private static final Color[][] COLOR_SCHEMES = {
            {new Color(232, 255, 89), new Color(12, 12, 14)},
            {new Color(129, 199, 132), new Color(14, 14, 18)},
            {new Color(255, 183, 77), new Color(16, 16, 20)},
            {new Color(100, 181, 246), new Color(10, 14, 18)},
    };

    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    /**
     * 生成验证码，返回 id 与 base64 图片
     */
    public Map<String, String> generate() {
        cleanupExpired();
        String code = randomCode(CODE_LEN);
        String id = UUID.randomUUID().toString().replace("-", "");
        store.put(id, new CaptchaEntry(code.toLowerCase(), System.currentTimeMillis() + EXPIRE_MS));
        Map<String, String> result = new ConcurrentHashMap<>();
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
        Color[] scheme = COLOR_SCHEMES[random.nextInt(COLOR_SCHEMES.length)];
        Color charColor = scheme[0];
        Color bgColor = scheme[1];

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 背景
        g.setColor(bgColor);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 背景噪点（约 120 个随机灰点）
        for (int i = 0; i < 120; i++) {
            int gray = 60 + random.nextInt(100);
            g.setColor(new Color(gray, gray, gray));
            g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 1, 1);
        }

        // 干扰线（12~16 条）
        int lineCount = 12 + random.nextInt(5);
        for (int i = 0; i < lineCount; i++) {
            int r = 70 + random.nextInt(120);
            int gr = 70 + random.nextInt(120);
            int b = 70 + random.nextInt(120);
            g.setColor(new Color(r, gr, b));
            g.setStroke(new BasicStroke(1.0f + random.nextFloat() * 1.2f));
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(WIDTH);
            int y2 = random.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        // 曲线干扰弧线（3~5 条）
        int arcCount = 3 + random.nextInt(3);
        for (int i = 0; i < arcCount; i++) {
            int r = 80 + random.nextInt(80);
            int gr = 80 + random.nextInt(80);
            int b = 80 + random.nextInt(80);
            g.setColor(new Color(r, gr, b));
            int ax = random.nextInt(WIDTH / 2);
            int ay = random.nextInt(HEIGHT);
            g.drawArc(ax, ay, WIDTH / 2 + random.nextInt(WIDTH / 2), HEIGHT / 2 + random.nextInt(HEIGHT / 2),
                    random.nextInt(180), 90 + random.nextInt(90));
        }

        // 绘制字符（逐个旋转 + 位移 + 变形）
        Font baseFont = new Font("Monospaced", Font.BOLD, 28);
        int totalWidth = code.length() * 26;
        int startX = (WIDTH - totalWidth) / 2 + 8;

        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            int fontSize = 26 + random.nextInt(8);
            Font font = new Font("Monospaced", Font.BOLD, fontSize);
            // 创建单字符临图，应用旋转变换
            BufferedImage charImg = new BufferedImage(40, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D cg = charImg.createGraphics();
            cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 随机旋转 -25° ~ +25°
            double angle = Math.toRadians(-25 + random.nextInt(51));
            int cx = 20, cy = HEIGHT / 2;

            AffineTransform at = new AffineTransform();
            at.translate(cx, cy);
            at.rotate(angle);
            at.translate(-cx, -cy);
            cg.setTransform(at);

            cg.setFont(font);
            cg.setColor(charColor);
            cg.drawString(String.valueOf(ch), 6, 36 - random.nextInt(8));
            cg.dispose();

            // 覆盖到主画布
            g.drawImage(charImg, startX + i * 26 - 6, 4, null);
        }

        // 前景随机噪点（约 40 个）
        for (int i = 0; i < 40; i++) {
            g.setColor(new Color(255, 255, 255, 30 + random.nextInt(80)));
            g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 1, 1);
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
