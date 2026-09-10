package com.freightfox.chatapp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    private String message;
    private String roomId;
    private String status;
    private String timestamp;

    public static ApiResponse success(String message) {
        return ApiResponse.builder()
                .status("success")
                .message(message)
                .build();
    }

    public static ApiResponse success(String message, String roomId) {
        return ApiResponse.builder()
                .status("success")
                .roomId(roomId)
                .message(message)
                .build();
    }

    public static ApiResponse error(String message) {
        return ApiResponse.builder()
                .status("error")
                .message(message)
                .build();
    }

    public static ApiResponse error(String message, String timestamp) {
        return ApiResponse.builder()
                .status("error")
                .message(message)
                .timestamp(timestamp)
                .build();
    }
}
