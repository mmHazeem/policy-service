package com.insurance.policy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.LocalDateTime;

public class PolicyNotFoundException extends RuntimeException {
    public ResponseEntity<ApiError> handleNotFound(PolicyNotFoundException ex) {
        return new ResponseEntity<>(
                new ApiError(
                        ex.getMessage(),
                        HttpStatus.NOT_FOUND.value(),
                        LocalDateTime.now(),
                        null
                ),
                HttpStatus.NOT_FOUND
        );
    }
}