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
        // 六位数字验证码内容
        String code;
        // 过期时间戳（毫秒），到期后视为失效并清理
        long expireAt;
        // 错误尝试次数（用于限制暴力试错），成功或过期后重置
        int wrongAttempts;
        ResetEntry(String code, long expireAt){
            this.code = code;
            this.expireAt = expireAt;
            this.wrongAttempts = 0;
        }
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
    // 验证码有效期（秒），默认 5 分钟，可通过配置项覆盖
    @org.springframework.beans.factory.annotation.Value("${password-reset.code-ttl-seconds:300}")
    private long codeTtlSeconds;
    // 校验失败最大次数，达到后直接作废验证码，默认 5 次
    @org.springframework.beans.factory.annotation.Value("${password-reset.max-verify-attempts:5}")
    private int maxVerifyAttempts;
    // 验证码长度，默认 6（仅数字）
    @org.springframework.beans.factory.annotation.Value("${password-reset.code-length:6}")
    private int codeLength;

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
        // 生成指定位数的数字验证码（默认 6 位）。
        int bound = (int) Math.pow(10, Math.max(1, codeLength));
        String fmt = "%0" + Math.max(1, codeLength) + "d";
        String code = String.format(fmt, new Random().nextInt(bound));
        long expire = Instant.now().plusSeconds(Math.max(60, codeTtlSeconds)).toEpochMilli();
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
        // 过期直接作废
        if (Instant.now().toEpochMilli() > e.expireAt) { store.remove(email); return false; }
        // 基础校验：仅允许数字且长度匹配，避免无效输入浪费尝试次数
        if (code == null || code.isBlank()) return false;
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{" + Math.max(1, codeLength) + "}")) {
            // 非法格式不计入失败次数，直接拒绝
            return false;
        }
        boolean ok = e.code.equals(trimmed);
        if (ok) {
            // 成功后作废，防止重放
            store.remove(email);
            return true;
        }
        // 错误一次，计数 +1；达到阈值后直接作废，防止暴力猜测
        e.wrongAttempts += 1;
        if (e.wrongAttempts >= Math.max(1, maxVerifyAttempts)) {
            store.remove(email);
        } else {
            store.put(email, e);
        }
        return false;
    }

    /**
     * 仅进行验证码有效性检查（不消费验证码）。
     *
     * 使用场景：
     * - 前端在“发送验证码”后，用户输入邮箱验证码并点击“验证验证码”按钮时调用；
     * - 若验证通过，不移除验证码，保留供后续“重置密码”环节消费；
     * - 若验证失败，累计错误次数，达到阈值作废验证码以限制暴力尝试。
     *
     * @param email 验证码对应的邮箱。
     * @param code 用户提交的验证码（期望 6 位数字）。
     * @return 验证通过返回 {@code true}；未生成、已过期、或不匹配返回 {@code false}。
     */
    public boolean checkCode(String email, String code) {
        ResetEntry e = store.get(email);
        if (e == null) return false;
        // 已过期则清理后返回失败
        if (Instant.now().toEpochMilli() > e.expireAt) { store.remove(email); return false; }
        if (code == null || code.isBlank()) return false;
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{" + Math.max(1, codeLength) + "}")) {
            // 非法格式直接拒绝（不累计失败）
            return false;
        }
        boolean ok = e.code.equals(trimmed);
        if (ok) {
            // 不消费验证码，仅告知有效；后续 /reset 将消费
            return true;
        }
        // 失败计数与作废逻辑与 verifyCode 保持一致
        e.wrongAttempts += 1;
        if (e.wrongAttempts >= Math.max(1, maxVerifyAttempts)) {
            store.remove(email);
        } else {
            store.put(email, e);
        }
        return false;
    }
}