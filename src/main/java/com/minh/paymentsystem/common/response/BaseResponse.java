package com.minh.paymentsystem.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> {
    private final boolean success;
    private final T data;
    private final ApiError error;
    @Builder.Default
    private final Instant timestamp = Instant.now();

    public static <T> BaseResponse<T> success(T data) {
        return BaseResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> BaseResponse<T> success() {
        return success(null);
    }

    public static <T> BaseResponse<T> error(String code, String message) {
        return BaseResponse.<T>builder()
                .success(false)
                .error(new ApiError(code, message))
                .build();
    }
}

