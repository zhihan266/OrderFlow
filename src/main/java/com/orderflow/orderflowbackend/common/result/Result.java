package com.orderflow.orderflowbackend.common.result;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data){
        return new Result<>(
                200,
                "success",
                data
        );
    }
    public static <T> Result<T> success(){
        return new Result<>(
                200,
                "success",
                null
        );
    }
    public static <T> Result<T> error(
            Integer code,
            String message
    ){
        return new Result<>(
                code,
                message,
                null
        );
    }
}
