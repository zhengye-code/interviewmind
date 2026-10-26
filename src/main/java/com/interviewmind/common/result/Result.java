package com.interviewmind.common.result;

import com.interviewmind.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class Result<T> {
    private final int code;
    private final String message;
    private final T data;
    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    //成功
    public static <T> Result<T> success(T data) {
        return new Result<>(200,"success",data);
    }
    public static <T> Result<T> success() {
        return success(null);
    }
    //失败
    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(),errorCode.getMessage(),null);
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(),message,null);
    }
    public static <T> Result<T> error(int code,String message) {
        return new Result<>(code,message,null);
    }
    //辅助
    public boolean isSuccess() {
        return getCode() == 200;
    }
}
