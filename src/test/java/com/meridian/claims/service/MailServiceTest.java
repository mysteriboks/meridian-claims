package com.meridian.claims.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

public class MailServiceTest {

    private LoggingMailService mailService;

    @Before
    public void setUp() {
        mailService = new LoggingMailService();
        ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@test.local");
    }

    @Test
    public void send_devMode_doesNotThrowAndDoesNotSendSmtp() {
        ReflectionTestUtils.setField(mailService, "mailEnabled", false);
        // Must not throw even with no JavaMailSender wired
        mailService.send("to@test.local", "Test Subject", "Test Body");
    }

    @Test
    public void send_enabledMode_delegatesToJavaMail() {
        JavaMailSender javaMailSender = Mockito.mock(JavaMailSender.class);
        mailService.setJavaMailSender(javaMailSender);
        ReflectionTestUtils.setField(mailService, "mailEnabled", true);

        mailService.send("to@test.local", "Hello", "Body text");

        Mockito.verify(javaMailSender).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    public void send_enabledButNoMailSender_doesNotThrow() {
        ReflectionTestUtils.setField(mailService, "mailEnabled", true);
        // javaMailSender is null — must log + not throw
        mailService.send("to@test.local", "Subject", "Body");
    }
}
