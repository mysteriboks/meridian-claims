package com.meridian.claims.service;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * MailService implementation.
 * When claims.mail.enabled=false (default), logs the message — no SMTP required.
 * When claims.mail.enabled=true, delegates to the Spring JavaMailSender bean.
 */
@Service
public class LoggingMailService implements MailService {

    private static final Logger LOG = Logger.getLogger(LoggingMailService.class);

    @Value("${claims.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${claims.mail.from:noreply@meridian.local}")
    private String fromAddress;

    private JavaMailSender javaMailSender;

    // Optional — only wired when mail is enabled; not @Autowired to avoid startup failure.
    public void setJavaMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!mailEnabled) {
            LOG.info("[DEV-MAIL] to=" + to + " subject=" + subject + "\n" + body);
            return;
        }
        if (javaMailSender == null) {
            LOG.warn("MailService: mail enabled but JavaMailSender not wired — logging instead. to=" + to);
            LOG.info("[MAIL-FALLBACK] to=" + to + " subject=" + subject);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            javaMailSender.send(msg);
            LOG.info("Mail sent to=" + to + " subject=" + subject);
        } catch (Exception e) {
            LOG.error("Mail send failed to=" + to, e);
        }
    }
}
