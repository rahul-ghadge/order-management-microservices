package com.orderms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email notification dispatcher.
 * Set {@code notification.email.enabled=true} and configure SMTP properties
 * in {@code application.yml} to enable real email delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${notification.email.from:noreply@orderms.com}")
    private String fromAddress;

    @Value("${notification.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Sends a plain-text email. In demo mode (emailEnabled=false), logs the email instead.
     */
    public void sendEmail(String to, String subject, String body) {
        if (!emailEnabled) {
            log.info("[EMAIL-SIMULATED] To={} | Subject={} | Body preview: {}",
                    to, subject, body.substring(0, Math.min(80, body.length())));
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Email sent → {}", to);
    }
}
