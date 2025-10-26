package com.yunbq.backend.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetService {
    static class ResetEntry {
        String code;
        long expireAt;
        ResetEntry(String code, long expireAt){ this.code = code; this.expireAt = expireAt; }
    }
    private final Map<String, ResetEntry> store = new ConcurrentHashMap<>();

    // 发送频率限制：同一邮箱 60 秒内不重复发送，每小时最多 5 次
    static class RateEntry {
        long windowStart;
        int count;
        long lastSentAt;
        RateEntry(long windowStart, int count, long lastSentAt){ this.windowStart=windowStart; this.count=count; this.lastSentAt=lastSentAt; }
    }
    private final Map<String, RateEntry> rate = new ConcurrentHashMap<>();
    private boolean canSend(String email){
        long now = Instant.now().toEpochMilli();
        RateEntry re = rate.get(email);
        if (re == null) { re = new RateEntry(now, 0, 0); }
        // 60 秒间隔限制
        if (re.lastSentAt > 0 && (now - re.lastSentAt) < 60_000) return false;
        // 每小时窗口限制
        if ((now - re.windowStart) > 3_600_000) { re.windowStart = now; re.count = 0; }
        if (re.count >= 5) return false;
        re.count += 1; re.lastSentAt = now; rate.put(email, re);
        return true;
    }

    public String createCode(String email) {
        if (!canSend(email)) {
            throw new RuntimeException("发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        long expire = Instant.now().plusSeconds(300).toEpochMilli();
        store.put(email, new ResetEntry(code, expire));
        // 模拟发送邮件：实际项目中请配置 SMTP，通过 JavaMail 发送
        System.out.println("[PasswordReset] send code " + code + " to " + email);
        return code;
    }

    public boolean verifyCode(String email, String code) {
        ResetEntry e = store.get(email);
        if (e == null) return false;
        if (Instant.now().toEpochMilli() > e.expireAt) { store.remove(email); return false; }
        boolean ok = e.code.equals(code);
        if (ok) store.remove(email);
        return ok;
    }
}