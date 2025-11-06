package com.yunbq.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
/**
 * 邮件服务（MailService）
 * 职责：
 * - 发送邮箱绑定或验证相关的简单邮件（验证码）；
 * - 当未配置邮件服务时，采用控制台日志输出作为回退，便于开发与联调。
 *
 * 配置说明：
 * - 依赖 Spring Boot 的 `spring.mail.*` 配置；
 * - 当 `JavaMailSender` 未注入或 `spring.mail.username` 为空时，视为未配置；
 * - 生产环境建议开启 SMTP 账号与应用专用密码，并限制频率、防止滥用。
 */
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    /**
     * 发送邮箱绑定验证码
     * 行为：
     * - 若邮件服务已配置，发送主题为“云便签邮箱绑定验证码”的文本邮件；
     * - 若未配置，则在日志与控制台输出验证码作为回退；
     * - 捕获发送异常并记录错误日志，同时仍输出回退信息，保障联调。
     *
     * 参数：
     * - to：收件人邮箱地址；
     * - code：验证码字符串（建议 6 位）。
     */
    public void sendBindEmailCode(String to, String code) {
        // Fallback to console if mail isn't configured
        if (mailSender == null || from == null || from.isBlank()) {
            log.warn("Mail not configured. Verification code for {}: {}", to, code);
            System.out.println("[MailService] send code " + code + " to " + to);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject("云便签邮箱绑定验证码");
            msg.setText("您的验证码是：" + code + "，5分钟内有效。若非本人操作请忽略。");
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send mail to {}: {}", to, e.getMessage());
            // As a fallback, still log the code so front-end can be tested
            System.out.println("[MailService] (fallback) send code " + code + " to " + to);
        }
    }
}