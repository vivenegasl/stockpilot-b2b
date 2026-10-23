package com.stockpilot.inventory.exception;

import com.stockpilot.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidStockAdjustmentException extends BusinessException {

    public InvalidStockAdjustmentException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "invalid-stock-adjustment");
    }

    public InvalidStockAdjustmentException(int currentTotal, int reserved, int delta) {
        super(String.format("Invalid stock adjustment: delta of %d would reduce total (%d) below reserved quantity (%d).",
                delta, currentTotal, reserved), HttpStatus.BAD_REQUEST, "invalid-stock-adjustment");
    }
}
