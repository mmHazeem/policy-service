# Pagination for GET /policies

## Problem

`GET /api/v1/policies` returns the full `policies` table as `List<PolicyResponse>`. With 10M+ policies this causes unbounded memory usage, slow responses, and DB strain. A scalability anti-pattern.

## Solution

Add offset-based pagination using Spring Data's `Pageable` internally, exposed via a custom `PageResponse<T>` wrapper.

## Approach

**Approach 1 (selected):** Spring Data `Pageable` internally, custom `PageResponse<T>` externally.

**Why not Approach 2 (manual LIMIT/OFFSET):** Boilerplate, reinvents sorting, no reuse of Spring Data's spec builder.

## Files Changed

| File | Change |
|------|--------|
| `dtos/PageResponse.java` | **New** — generic record: `data`, `page`, `size`, `total`, `totalPages` |
| `PolicyRepository.java` | No change — `JpaRepository` already has `findAll(Pageable)` |
| `mapper/PolicyMapper.java` | Add `toResponsePage(Page<Policy>)` mapping `Page` → `PageResponse` |
| `policy_service/PolicyService.java` | `getAllPolicies(Pageable)` → returns `PageResponse<PolicyResponse>` |
| `web/PolicyController.java` | `list(@PageableDefault(size=20) Pageable)` returns `PageResponse` |
| Tests (3 files) | Update assertions for paginated response shape |

## API Contract

```
GET /api/v1/policies?page=0&size=20&sort=policyNumber,asc
```

```json
{
  "data": [{ ... }],
  "page": 0,
  "size": 20,
  "total": 150,
  "totalPages": 8
}
```

## Data Flow

```
Controller (Pageable) → Service (Pageable) → Repository.findAll(Pageable) → Page<Policy>
  → Mapper.toResponsePage() → PageResponse<PolicyResponse>
```

## Defaults

- Default page: 0
- Default size: 20
- Max size: enforced by Spring (can add `@Max` constraint if needed)
