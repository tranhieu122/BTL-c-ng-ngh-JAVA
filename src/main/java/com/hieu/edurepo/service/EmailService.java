package com.hieu.edurepo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String username;
    private final String password;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${spring.mail.password:}") String password) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
    }

    public boolean sendOtp(String email, String code, String purpose) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("SMTP chua duoc cau hinh, bo qua gui OTP {} cho {}", purpose, email);
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(username);
        message.setTo(email);
        message.setSubject("Ma OTP EduRepo");
        message.setText("Ma OTP " + purpose + " cua ban la: " + code
                + "\nMa co hieu luc trong 10 phut. Neu ban khong thuc hien yeu cau nay, hay bo qua email.");
        try {
            sender.send(message);
            return true;
        } catch (MailException exception) {
            log.warn("Khong gui duoc OTP {} cho {}. Kiem tra lai cau hinh SMTP.", purpose, email, exception);
            return false;
        }
    }
}
