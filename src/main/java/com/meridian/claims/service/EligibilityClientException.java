package com.meridian.claims.service;

/** Thrown when a real-time eligibility (270/271) round trip cannot be completed. */
public class EligibilityClientException extends Exception {

    public EligibilityClientException(String message) {
        super(message);
    }

    public EligibilityClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
