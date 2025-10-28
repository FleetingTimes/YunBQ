package com.yunbq.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

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