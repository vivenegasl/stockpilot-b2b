package com.stockpilot.shared.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "conflict");
    }

    public ConflictException(String message, String errorType) {
        super(message, HttpStatus.CONFLICT, errorType);
    }
}
