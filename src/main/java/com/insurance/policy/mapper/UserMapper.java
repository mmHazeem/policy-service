package com.insurance.policy.mapper;

import com.insurance.policy.domain.User;
import com.insurance.policy.dtos.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    User toEntity(User user);

    UserResponse toResponse(User user);
    List<UserResponse> toResponseList(List<User> users);
}
