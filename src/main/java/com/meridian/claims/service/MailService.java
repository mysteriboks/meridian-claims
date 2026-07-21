package com.meridian.claims.service;

/** Sends or logs an email. The default implementation is dev-safe (log-only). */
public interface MailService {
    void send(String to, String subject, String body);
}
