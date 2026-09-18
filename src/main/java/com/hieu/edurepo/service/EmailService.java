package com.hieu.edurepo.service;

import com.hieu.edurepo.enums.OtpPurpose;
import com.hieu.edurepo.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.time.Duration;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public enum OtpDeliveryStatus {
        SUCCESS,
        CONFIGURATION_ERROR,
        DELIVERY_FAILED
    }

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String username;
    private final String password;
    private final boolean authenticationRequired;
    private final OperationalMetrics metrics;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${spring.mail.password:}") String password,
                        @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean authenticationRequired,
                        OperationalMetrics metrics) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
        this.authenticationRequired = authenticationRequired;
        this.metrics = metrics;
    }

    public boolean sendOtp(String email, String code, OtpPurpose purpose) {
        return sendOtpStatus(email, code, purpose) == OtpDeliveryStatus.SUCCESS;
    }

    public OtpDeliveryStatus sendOtpStatus(String email, String code, OtpPurpose purpose) {
        long started = System.nanoTime();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || (authenticationRequired
                && (username == null || username.isBlank() || password == null || password.isBlank()))) {
            metrics.otpEmail(purpose, "SKIPPED", OperationalMetrics.MailFailureType.CONFIGURATION,
                    elapsed(started));
            log.warn("SMTP is not configured; OTP delivery skipped purpose={}", purpose);
            return OtpDeliveryStatus.CONFIGURATION_ERROR;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (username != null && !username.isBlank()) message.setFrom(username);
        message.setTo(email);
        message.setSubject("Ma OTP EduRepo");
        message.setText("Ma OTP " + purposeLabel(purpose) + " cua ban la: " + code
                + "\nMa co hieu luc trong 10 phut. Neu ban khong thuc hien yeu cau nay, hay bo qua email.");
        try {
            sender.send(message);
            metrics.otpEmail(purpose, "SUCCESS", null, elapsed(started));
            return OtpDeliveryStatus.SUCCESS;
        } catch (MailException exception) {
            OperationalMetrics.MailFailureType failureType = isTimeout(exception)
                    ? OperationalMetrics.MailFailureType.TIMEOUT
                    : OperationalMetrics.MailFailureType.MAIL_PROVIDER;
            metrics.otpEmail(purpose, "FAILED", failureType, elapsed(started));
            log.warn("OTP delivery failed purpose={} recipient={} failureType={} exception={}",
                    purpose, maskEmail(email), failureType, exception.getClass().getSimpleName());
            log.debug("SMTP diagnostic for failed OTP delivery", exception);
            return OtpDeliveryStatus.DELIVERY_FAILED;
        }
    }

    private Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String purposeLabel(OtpPurpose purpose) {
        return purpose == OtpPurpose.REGISTER ? "dang ky tai khoan" : "lay lai mat khau";
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "unknown";
        int separator = email.indexOf('@');
        if (separator <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(separator);
    }
}
