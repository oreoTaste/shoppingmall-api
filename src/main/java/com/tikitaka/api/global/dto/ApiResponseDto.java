package com.tikitaka.api.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// JSON으로 변환 시 null인 필드는 응답에 포함시키지 않음
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponseDto<T> {

    private final boolean success;
    private final String message;
    private final T data; // 제네릭 타입으로 어떤 종류의 데이터든 담을 수 있음

    // 성공 시 데이터와 함께 반환
    private ApiResponseDto(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    // 실패 시 메시지만 반환
    private ApiResponseDto(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.data = null; // 실패 시 데이터는 null
    }

    /**
     * 성공 응답을 생성합니다. (데이터 포함)
     * @param message 응답 메시지
     * @param data 반환할 데이터
     */
    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(true, message, data);
    }

    /**
     * 성공 응답을 생성합니다. (데이터 미포함)
     * @param message 응답 메시지
     */
    public static <T> ApiResponseDto<T> success(String message) {
        return new ApiResponseDto<>(true, message, null);
    }

    /**
     * 실패 응답을 생성합니다.
     * @param message 실패 메시지
     */
    public static ApiResponseDto<?> fail(String message) {
        return new ApiResponseDto<>(false, message);
    }

    // Getter
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}