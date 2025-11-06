package com.yunbq.backend.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {
    /**
     * 简易图形验证码服务。
     * <p>
     * 职责：
     * - 生成短期有效的验证码（默认 180 秒），返回对应的 `id` 与 Base64 PNG 的 `dataUrl`；
     * - 校验用户提交的验证码并在成功或过期后移除，防止重放；
     * - 采用进程内 {@link ConcurrentHashMap} 存储，适合单实例或轻量场景。
     * <p>
     * 安全注意：
     * - 仅作为基础防护；建议在生产环境结合限流、失败计数、风控与更复杂的干扰渲染；
     * - 多实例部署应改为 Redis 等共享存储并设置合理的 TTL；
     * - 图片采用前端 `<img src="data:image/png;base64,...">` 展示，避免额外静态文件存储。
     */
    static class CaptchaEntry {
        String code;
        long expireAt;
        CaptchaEntry(String code, long expireAt){ this.code = code; this.expireAt = expireAt; }
    }

    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    public record Captcha(String id, String dataUrl) {}

    /**
     * 生成验证码并返回图片数据 URL。
     *
     * @return {@link Captcha} 记录，包含唯一标识 {@code id} 与 PNG Base64 的 {@code dataUrl}。
     * @implNote 有效期默认 180 秒；验证码内容使用无歧义字符集，大小写不敏感（校验时统一转换为小写）。
     */
    public Captcha generate() {
        String code = randomCode(5);
        String id = UUID.randomUUID().toString();
        long expire = Instant.now().plusSeconds(180).toEpochMilli();
        store.put(id, new CaptchaEntry(code.toLowerCase(), expire));
        String dataUrl = renderDataUrl(code);
        return new Captcha(id, dataUrl);
    }

    /**
     * 校验验证码。
     *
     * @param id    验证码生成时返回的唯一标识。
     * @param input 用户输入的验证码文本（大小写不敏感）。
     * @return 校验通过返回 {@code true}；未生成、已过期或不匹配返回 {@code false}。
     * @implNote 一次性使用：验证成功后立即移除；若已过期也会清理存储。
     */
    public boolean verify(String id, String input) {
        if (id == null || input == null) return false;
        CaptchaEntry entry = store.get(id);
        if (entry == null) return false;
        if (Instant.now().toEpochMilli() > entry.expireAt) { store.remove(id); return false; }
        boolean ok = entry.code.equals(input.toLowerCase());
        if (ok) store.remove(id);
        return ok;
    }

    /**
     * 生成包含易辨识字符的随机验证码。
     *
     * @param len 验证码长度（建议 4–6）。
     * @return 随机验证码字符串（使用不含易混淆字符的集合）。
     */
    private String randomCode(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<len;i++) sb.append(chars.charAt((int)(Math.random()*chars.length())));
        return sb.toString();
    }

    /**
     * 将验证码渲染为 PNG 并返回 Base64 Data URL。
     *
     * @param code 验证码文本。
     * @return 形如 {@code data:image/png;base64,<...>} 的数据 URL，可直接赋值给 `<img src>`。
     * @throws RuntimeException 当图像写入失败时抛出运行时异常。
     * @implNote 渲染包含简单噪点与随机偏移；字体与颜色可根据 UI 调整。
     */
    private String renderDataUrl(String code) {
        int w = 120, h = 40;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(245,247,250)); g.fillRect(0,0,w,h);
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        // noise lines
        g.setColor(new Color(200,200,200));
        for (int i=0;i<5;i++) g.drawLine((int)(Math.random()*w), (int)(Math.random()*h), (int)(Math.random()*w), (int)(Math.random()*h));
        // draw code with slight offset
        g.setColor(new Color(80,80,80));
        for (int i=0;i<code.length();i++) {
            int x = 14 + i*22; int y = 26 + (int)(Math.random()*6-3);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
        }
        g.dispose();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            throw new RuntimeException("生成验证码失败", e);
        }
    }
}