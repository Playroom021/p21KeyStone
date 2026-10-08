package com.keyStone.Playroom021.exception;

/** Thrown when a request is valid but conflicts with current state (maps to HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
