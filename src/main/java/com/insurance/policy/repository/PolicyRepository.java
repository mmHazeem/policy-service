package com.insurance.policy.repository;

import com.insurance.policy.domain.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID> {
    // A custom query to find policies by their unique number
    Optional<Policy> findByPolicyNumber(String policyNumber);
}
