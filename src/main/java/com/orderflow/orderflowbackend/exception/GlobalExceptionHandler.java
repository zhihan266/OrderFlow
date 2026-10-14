package com.orderflow.orderflowbackend.exception;

import com.orderflow.orderflowbackend.common.result.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(
            BusinessException e
    ){

        return Result.error(
                e.getCode(),
                e.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(
            Exception e
    ){
        e.printStackTrace();

        return Result.error(
                500,
                "服务器内部错误"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(
            MethodArgumentNotValidException e
    ){

        String message = e
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("参数校验失败");
        return Result.error(
                400,message
        );

    }
}
