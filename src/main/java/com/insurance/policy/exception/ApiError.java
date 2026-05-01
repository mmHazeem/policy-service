package com.insurance.policy.exception;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ApiError {
    private String message;
    private int status;
    private LocalDateTime timestamp;
    private Map<String, String> errors;
}
