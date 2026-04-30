package com.orderms.common.exception;

import lombok.Getter;

/**
 * Base runtime exception for all microservice-specific exceptions.
 */
@Getter
public class BaseException extends RuntimeException {

    private final String errorCode;
    private final int    httpStatus;

    public BaseException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }

    public BaseException(String message, String errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }
}
