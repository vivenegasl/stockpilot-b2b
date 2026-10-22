package com.stockpilot.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base domain exception carrying HTTP semantic status and problem details.
 */
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorType;

    protected BusinessException(String message, HttpStatus status, String errorType) {
        super(message);
        this.status = status;
        this.errorType = errorType;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorType() {
        return errorType;
    }
}
