package com.meridian.claims.service;

/**
 * Unchecked exception representing a business-layer failure. The service layer
 * throws this for rule violations, invalid state transitions, and any wrapped
 * lower-layer failure that the business logic could not handle.
 *
 * Controllers translate ServiceException into user-facing error views; the
 * raw cause is logged but never shown to the browser (see Phase 8 hardening).
 */
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
