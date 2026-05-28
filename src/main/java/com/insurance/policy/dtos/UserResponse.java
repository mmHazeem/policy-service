package com.insurance.policy.dtos;

import com.insurance.policy.domain.User;

import java.util.UUID;

public record UserResponse(UUID uuid,String username,
                           User.Role role){}
