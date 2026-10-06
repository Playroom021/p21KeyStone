package com.keyStone.Playroom021.exception;

/** Thrown for bad query parameters such as an unknown sort field or out-of-range page size (maps to HTTP 400). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
