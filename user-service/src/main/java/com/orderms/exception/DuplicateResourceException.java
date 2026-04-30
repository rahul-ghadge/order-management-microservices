package com.orderms.exception;

import com.orderms.common.exception.BaseException;

public class DuplicateResourceException extends BaseException {
    public DuplicateResourceException(String message) {
        super(message, "DUPLICATE_RESOURCE", 409);
    }
}
