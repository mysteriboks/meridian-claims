package com.meridian.claims.transport;

/**
 * Thrown by a {@link TransportAdapter} when a trading-partner file operation
 * (list / read / archive / write) fails — connection failure, missing path,
 * authentication failure, or I/O error.
 */
public class TransportException extends Exception {

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
