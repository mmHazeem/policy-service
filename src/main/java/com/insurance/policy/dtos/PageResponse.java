package com.insurance.policy.dtos;

import java.util.List;

public record PageResponse<T>(
    List<T> data,
    int page,
    int size,
    long total,
    int totalPages
) {}