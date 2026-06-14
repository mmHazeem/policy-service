# Role-Based Access Control

## Problem

`User.Role` already defines `ADMIN` and `USER` but nothing enforces them. The `JwtAuthenticationFilter` authenticates requests and populates `SecurityContextHolder` with `ROLE_USER`/`ROLE_ADMIN` authorities, but `SecurityConfig` only requires `authenticated()` for all `/api/v1/policies/**` endpoints. Any authenticated user — regardless of role — can create, list, and change policy status. JWT is a login gate, not actual authorization.

## Constraint

Register and login must remain public (unauthenticated). The role model is fixed to `USER` and `ADMIN` — registration always creates `USER`-role accounts. No new endpoints, no API contract changes.

## Solution

### Component changes

**SecurityConfig** — add `@EnableMethodSecurity` to activate `@PreAuthorize` annotations on controller methods.

**PolicyController**:
- `POST /api/v1/policies` → `@PreAuthorize("hasRole('USER')")`
- `GET /api/v1/policies` → `@PreAuthorize("hasRole('USER')")`
- `PATCH /api/v1/policies/{id}/status` → `@PreAuthorize("hasRole('ADMIN')")`

**AuthController**:
- `GET /api/v1/auth/getall` → `@PreAuthorize("hasRole('ADMIN')")`

**GlobalExceptionHandler** — add `@ExceptionHandler(AccessDeniedException.class)` returning 403, preventing the catch-all `Exception.class` handler from returning 500.

### Access matrix

| Endpoint | Auth | USER | ADMIN |
|----------|------|------|-------|
| `POST /api/v1/auth/register` | Permit all | ✓ | ✓ |
| `POST /api/v1/auth/login` | Permit all | ✓ | ✓ |
| `GET /api/v1/auth/getall` | PreAuthorize ADMIN | 403 | ✓ |
| `POST /api/v1/policies` | PreAuthorize USER | ✓ | ✓ |
| `GET /api/v1/policies` | PreAuthorize USER | ✓ | ✓ |
| `PATCH /api/v1/policies/{id}/status` | PreAuthorize ADMIN | 403 | ✓ |

### Testing

**PolicyControllerTest** (unit, `@WebMvcTest`):
- `@WithMockUser(roles = "USER")` → can POST, GET, but PATCH returns 403
- `@WithMockUser(roles = "ADMIN")` → can PATCH status (valid/null), POST, GET
- Unauthenticated → 401

**AuthControllerTest** (unit, `@WebMvcTest`, new file):
- `@WithMockUser(roles = "ADMIN")` → can GET /getall
- `@WithMockUser(roles = "USER")` → GET /getall returns 403
- Unauthenticated → 401

**PolicyIntegrationTest** (integration, `@SpringBootTest`):
- Default registration creates USER role → PATCH status returns 403

### What does NOT change

- Register/login remain public
- API request/response shapes unchanged
- User registration always assigns `USER` role (no way to register as ADMIN)
- Existing tests all continue passing
