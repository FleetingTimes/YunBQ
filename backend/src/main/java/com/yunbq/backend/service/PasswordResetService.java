package com.yunbq.backend.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetService {
    /**
     * 密码找回验证码生成与校验服务。
     * <p>
     * 该服务提供：
     * - 为邮箱生成 6 位数字验证码，默认有效期 5 分钟，存储于内存（进程级）并通过邮件发送。
     * - 校验验证码并在校验成功或过期后移除，避免重复使用。
     * - 简易发送频率限制：同一邮箱 60 秒内不重复发送，每小时最多 5 次，以防滥用。
     * <p>
     * 线程安全性：使用 {@link ConcurrentHashMap} 存储验证码与频率窗口，满足并发访问需求。
     * 部署注意：该实现为轻量演示/单实例方案；生产建议改为持久化或 Redis 存储，并将频率限制迁移到统一的风控组件。
     */
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
    /**
     * 判断是否允许向该邮箱发送新的验证码。
     *
     * @param email 邮箱地址，作为频率窗口的 key。
     * @return 允许发送返回 {@code true}；触达频率限制返回 {@code false}。
     * @implNote 规则：60 秒最小间隔；每小时最多 5 次。窗口到期后自动重置计数。
     */
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

    private final MailService mailService;

    public PasswordResetService(MailService mailService) {
        this.mailService = mailService;
    }

    /**
     * 为指定邮箱生成找回密码验证码并触发发送。
     *
     * @param email 接收验证码的邮箱地址；需与账号绑定邮箱一致。
     * @return 生成的 6 位数字验证码（主要用于测试或本地开发回显，生产环境不应直接返回给客户端）。
     * @throws RuntimeException 当触达频率限制时抛出异常，提示稍后重试。
     * @implNote 有效期 5 分钟；邮箱发送由 {@link MailService#sendBindEmailCode(String, String)} 执行，若邮件通道不可用，建议增加降级策略（如日志回退）。
     */
    public String createCode(String email) {
        if (!canSend(email)) {
            throw new RuntimeException("发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        long expire = Instant.now().plusSeconds(300).toEpochMilli();
        store.put(email, new ResetEntry(code, expire));
        // 发送邮件或控制台回退
        mailService.sendBindEmailCode(email, code);
        return code;
    }

    /**
     * 校验邮箱对应的验证码是否有效。
     *
     * @param email 验证码对应的邮箱。
     * @param code 用户提交的验证码（期望 6 位数字）。
     * @return 验证通过返回 {@code true}；未生成、已过期、或不匹配返回 {@code false}。
     * @implNote 一次性校验：成功后立即移除存储，防止重复使用；过期时也会清理记录以控制内存。
     */
    public boolean verifyCode(String email, String code) {
        ResetEntry e = store.get(email);
        if (e == null) return false;
        if (Instant.now().toEpochMilli() > e.expireAt) { store.remove(email); return false; }
        boolean ok = e.code.equals(code);
        if (ok) store.remove(email);
        return ok;
    }
}