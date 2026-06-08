# Audit Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans

**Goal:** Add `createdAt`, `updatedAt`, `createdBy` to Policy and User entities via Spring Data JPA auditing.

**Architecture:** `@MappedSuperclass` BaseEntity with `@EntityListeners(AuditingEntityListener.class)`. `@EnableJpaAuditing` on main class. `AuditorAware<String>` bean extracts username from SecurityContext.

**Tech Stack:** Spring Data JPA, Spring Security, Flyway, Lombok

---

### Task 1: Create BaseEntity

**Files:**
- Create: `src/main/java/com/insurance/policy/domain/BaseEntity.java`

- [ ] **Step 1: Write BaseEntity**

```java
package com.insurance.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
}
```

### Task 2: Enable JPA Auditing and create AuditorAware

**Files:**
- Modify: `src/main/java/com/insurance/policy/PolicyServiceApplication.java`
- Create: `src/main/java/com/insurance/policy/config/AuditConfig.java`

- [ ] **Step 1: Add @EnableJpaAuditing to PolicyServiceApplication**

```java
package com.insurance.policy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class PolicyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PolicyServiceApplication.class, args);
    }
}
```

- [ ] **Step 2: Create AuditConfig with AuditorAware bean**

```java
package com.insurance.policy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of("SYSTEM");
            }
            return Optional.of(auth.getName());
        };
    }
}
```

### Task 3: Update Policy entity

**Files:**
- Modify: `src/main/java/com/insurance/policy/domain/Policy.java`

- [ ] **Step 1: Make Policy extend BaseEntity**

```java
@Entity
@Table(name = "policies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Policy extends BaseEntity {
    // existing fields unchanged
```

### Task 4: Update User entity

**Files:**
- Modify: `src/main/java/com/insurance/policy/domain/User.java`

- [ ] **Step 1: Make User extend BaseEntity**

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User extends BaseEntity implements UserDetails {
    // existing fields unchanged
```

### Task 5: Flyway migration

**Files:**
- Create: `src/main/resources/db/migration/V3__add_audit_fields.sql`

- [ ] **Step 1: Create migration SQL**

```sql
ALTER TABLE policies ADD COLUMN created_at TIMESTAMP;
ALTER TABLE policies ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE policies ADD COLUMN created_by VARCHAR(255);

ALTER TABLE users ADD COLUMN created_at TIMESTAMP;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE users ADD COLUMN created_by VARCHAR(255);
```

### Task 6: Verify compilation and tests

- [ ] **Step 1: Compile and run tests**

```bash
mvn test -pl . -Dtest="PolicyServiceTest,PolicyControllerTest" -DfailIfNoTests=false
```

Expected: BUILD SUCCESS, 21 tests pass

- [ ] **Step 2: Compile main source**

```bash
mvn compile -q
```

Expected: no output (clean compile)
