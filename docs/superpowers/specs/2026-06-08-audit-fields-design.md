# Entity Audit Fields

## Problem

Entities lack data lineage — no `createdAt`, `updatedAt`, or `createdBy`. Without these, debugging, compliance, and auditing are impossible.

## Solution

Spring Data JPA's `@EntityListeners(AuditingEntityListener.class)` to auto-populate audit fields on persist/update.

## Design

**`BaseEntity`** — `@MappedSuperclass` with common audit fields:

| Field | Type | Annotation |
|-------|------|------------|
| `createdAt` | `LocalDateTime` | `@CreatedDate` |
| `updatedAt` | `LocalDateTime` | `@LastModifiedDate` |
| `createdBy` | `String` | `@CreatedBy` |

**Which entities extend it:**

| Entity | Extends BaseEntity? | Notes |
|--------|---------------------|-------|
| `Policy` | Yes | Domain entity, needs full lineage |
| `User` | Yes | Domain entity, needs full lineage |
| `OutboxEvent` | No | Infrastructure entity — keep existing `@PrePersist`/`@PreUpdate`, system-generated, no `createdBy` |

**Configuration:**

- `@EnableJpaAuditing` on `PolicyServiceApplication`
- `AuditorAware<String>` bean in config — extracts username from `SecurityContextHolder`

**Database:** Flyway migration `V3__add_audit_fields.sql` adds columns to `policies` and `users` tables.
