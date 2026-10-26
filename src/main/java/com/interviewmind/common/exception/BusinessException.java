package com.interviewmind.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode,errorCode.getMessage());
    }
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
    public BusinessException(ErrorCode errorCode,String message,Throwable throwable) {
        super(message,throwable);
        this.code = errorCode.getCode();
    }
}
