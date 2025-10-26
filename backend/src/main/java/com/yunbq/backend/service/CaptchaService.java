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
    static class CaptchaEntry {
        String code;
        long expireAt;
        CaptchaEntry(String code, long expireAt){ this.code = code; this.expireAt = expireAt; }
    }

    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    public record Captcha(String id, String dataUrl) {}

    public Captcha generate() {
        String code = randomCode(5);
        String id = UUID.randomUUID().toString();
        long expire = Instant.now().plusSeconds(180).toEpochMilli();
        store.put(id, new CaptchaEntry(code.toLowerCase(), expire));
        String dataUrl = renderDataUrl(code);
        return new Captcha(id, dataUrl);
    }

    public boolean verify(String id, String input) {
        if (id == null || input == null) return false;
        CaptchaEntry entry = store.get(id);
        if (entry == null) return false;
        if (Instant.now().toEpochMilli() > entry.expireAt) { store.remove(id); return false; }
        boolean ok = entry.code.equals(input.toLowerCase());
        if (ok) store.remove(id);
        return ok;
    }

    private String randomCode(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<len;i++) sb.append(chars.charAt((int)(Math.random()*chars.length())));
        return sb.toString();
    }

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