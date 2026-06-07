package com.insurance.policy.mapper;

import com.insurance.policy.domain.Policy;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.insurance.policy.dtos.PageResponse;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PolicyMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "premiumAmount", ignore = true)
    Policy toEntity(PolicyRequest request);

    PolicyResponse toResponse(Policy policy);

    List<PolicyResponse> toResponseList(List<Policy> policies);

    default PageResponse<PolicyResponse> toResponsePage(Page<Policy> page) {
        List<PolicyResponse> data = toResponseList(page.getContent());
        return new PageResponse<>(data, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}