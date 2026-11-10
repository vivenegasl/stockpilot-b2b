package com.stockpilot.ordering.exception;

import com.stockpilot.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidOrderStateException extends BusinessException {

    public InvalidOrderStateException(String orderNumber, String currentStatus, String targetAction) {
        super(String.format("Cannot perform '%s' on Order '%s' because its current state is '%s'.",
                        targetAction, orderNumber, currentStatus),
                HttpStatus.CONFLICT,
                "invalid-order-state");
    }
}
