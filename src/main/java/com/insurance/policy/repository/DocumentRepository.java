package com.insurance.policy.repository;

import com.insurance.policy.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByPolicyIdOrderByUploadedAtDesc(UUID policyId);
}
