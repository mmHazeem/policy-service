# PIT Mutation Coverage Improvement Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kill all SURVIVED mutations and cover high-priority NO_COVERAGE areas identified by the PIT mutation report.

**Architecture:** The codebase uses a layered Spring Boot service with a MapStruct mapper, Mockito-based unit tests, and an outbox pattern for async messaging. Surviving mutations fall into three categories: (1) `PolicyService.createPolicy` — return value, side effect, and debug logging gaps, (2) `PolicyService.correlationId` — MDC conditional uncovered, (3) `PolicyService.getPolicyById` — entirely untested. Additional NO_COVERAGE areas exist in the outbox publisher, exception handlers, and generated mappers.

**Tech Stack:** Java 21, Spring Boot 3.x, JUnit 5 + Mockito, PIT 1.19.1, MapStruct

---

### Task 1: Fix `createPolicy` return null mutation

**Files:**
- Modify: `src/test/java/com/insurance/policy/policy_service/PolicyServiceTest.java`
- No production code changes needed

#### Analysis

Mutation at `PolicyService.java:122` — `replaced return value with null for com/insurance/policy/policy_service/PolicyService::createPolicy → SURVIVED`

This mutation survives because the test `shouldSavePolicyWithCorrectCalculatedPremium` calls `createPolicy` but never captures or asserts on its return value. The mutant returns `null` but the test still passes because it only verifies the captured argument to `repository.save()`.

Similarly, `shouldIncrementCounterWhenPolicyCreated` calls `createPolicy` and checks the counter, but doesn't check the return value either. `shouldThrowExceptionWhenDatabaseFails` calls and expects an exception — fine.

Fix: Add return value assertions in both `shouldSavePolicyWithCorrectCalculatedPremium` and `shouldIncrementCounterWhenPolicyCreated`.

- [ ] **Step 1: Add return value assertion to `shouldSavePolicyWithCorrectCalculatedPremium`**

```java
@Test
void shouldSavePolicyWithCorrectCalculatedPremium() {
    Policy savedEntity = new Policy();
    savedEntity.setId(UUID.randomUUID());
    PolicyResponse expectedResponse = new PolicyResponse(
            savedEntity.getId(), "J-100", "Jan Max",
            new BigDecimal("1000"), new BigDecimal("5.000"),
            VALID_REQUEST.startDate(), "DRAFT");

    when(mapper.toEntity(any())).thenReturn(new Policy());
    when(repository.save(any())).thenReturn(savedEntity);
    when(mapper.toResponse(savedEntity)).thenReturn(expectedResponse);

    PolicyResponse result = policyService.createPolicy(VALID_REQUEST);

    verify(repository).save(policyCaptor.capture());
    assertEquals(new BigDecimal("5.000"), policyCaptor.getValue().getPremiumAmount());
    assertNotNull(result);
    assertEquals("J-100", result.policyNumber());
}
```

- [ ] **Step 2: Add return value assertion to `shouldIncrementCounterWhenPolicyCreated`**

```java
@Test
void shouldIncrementCounterWhenPolicyCreated() {
    Policy savedEntity = new Policy();
    savedEntity.setId(UUID.randomUUID());
    PolicyResponse expectedResponse = new PolicyResponse(
            savedEntity.getId(), "J-100", "Jan Max",
            new BigDecimal("1000"), new BigDecimal("5.000"),
            VALID_REQUEST.startDate(), "DRAFT");

    when(mapper.toEntity(any())).thenReturn(new Policy());
    when(repository.save(any())).thenReturn(savedEntity);
    when(mapper.toResponse(savedEntity)).thenReturn(expectedResponse);

    PolicyResponse result = policyService.createPolicy(VALID_REQUEST);

    assertNotNull(result);
    double count = registry.get("insurance.policies.created").counter().count();
    assertEquals(1.0, count);
}
```

- [ ] **Step 3: Run tests and verify all pass**

Run: `mvn test -pl . -Dtest="PolicyServiceTest" -DfailIfNoTests=false`
Expected: All tests pass (14+, including the new assertions)

- [ ] **Step 4: Run PIT report to verify mutation is killed**

Run: `mvn pitest:mutationCoverage -DtargetTests="com.insurance.policy.policy_service.PolicyServiceTest" -Dfeatures="+EXPORT"`

Check `target/pit-reports/com.insurance.policy.policy_service/PolicyService.java.html` for `createPolicy:122` — should now show KILLED.

---

### Task 2: Fix `createPolicy` `setStatus` side-effect mutation

**Files:**
- Modify: `src/test/java/com/insurance/policy/policy_service/PolicyServiceTest.java`
- No production code changes needed

#### Analysis

Mutation at `PolicyService.java:114` — `removed call to com/insurance/policy/domain/Policy::setStatus → SURVIVED`

This mutation survives because the existing test that catches the saved policy object only checks `premiumAmount` but never verifies the status was set to DRAFT.

Fix: Add assertion on the captured policy's status in `shouldSavePolicyWithCorrectCalculatedPremium`.

- [ ] **Step 1: Add status assertion to the captured policy**

```java
// In shouldSavePolicyWithCorrectCalculatedPremium, after the premium assertion:
assertEquals(new BigDecimal("5.000"), policyCaptor.getValue().getPremiumAmount());
assertEquals(Policy.PolicyStatus.DRAFT, policyCaptor.getValue().getStatus());
```

- [ ] **Step 2: Run tests to verify**

Run: `mvn test -pl . -Dtest="PolicyServiceTest" -DfailIfNoTests=false`
Expected: All tests pass

- [ ] **Step 3: Run PIT to verify mutation killed**

Run: `mvn pitest:mutationCoverage -DtargetTests="com.insurance.policy.policy_service.PolicyServiceTest" -Dfeatures="+EXPORT"`

Check: `createPolicy:114` — should now show KILLED.

---

### Task 3: Fix `createPolicy` `System.out.println` surviving mutations

**Files:**
- Modify: `src/main/java/com/insurance/policy/policy_service/PolicyService.java`

#### Analysis

Mutations at `PolicyService.java:119, 121` — `removed call to java/io/PrintStream::println → SURVIVED`

These are debug `System.out.println` statements. The codebase already uses `@Slf4j` with proper logging. These printlns are noise — removing them eliminates the survivng mutations cleanly.

- [ ] **Step 1: Remove debug println statements from `createPolicy`**

In `PolicyService.java`, remove lines 118-119 and 121 (the println statements and their comments):

```java
// Before (lines 116-122):
Policy savedPolicy = repository.save(policy);

// Increment the metric every time a policy is successfully saved
System.out.println("Incrementing counter...");
policyCreationCounter.increment();
System.out.println("Metric incremented!");
return mapper.toResponse(savedPolicy);

// After:
Policy savedPolicy = repository.save(policy);

policyCreationCounter.increment();
return mapper.toResponse(savedPolicy);
```

- [ ] **Step 2: Run tests to verify**

Run: `mvn test -pl . -Dtest="PolicyServiceTest" -DfailIfNoTests=false`
Expected: All tests pass

- [ ] **Step 3: Run PIT to verify mutations are gone**

Run: `mvn pitest:mutationCoverage -DtargetTests="com.insurance.policy.policy_service.PolicyServiceTest" -Dfeatures="+EXPORT"`

Check: Lines 119/121 should no longer appear in the mutation report for `createPolicy`.

---

### Task 4: Fix `correlationId` surviving mutations

**Files:**
- Modify: `src/test/java/com/insurance/policy/policy_service/PolicyServiceTest.java`
- No production code changes needed

#### Analysis

Mutations at `PolicyService.java:136`:
1. `removed conditional - replaced equality check with false → SURVIVED`
2. `replaced return value with "" for com/insurance/policy/policy_service/PolicyService::correlationId → SURVIVED`

The `correlationId()` method is private and only called by `createPolicyAsync`. The test `shouldSaveOutboxEventWhenCreatePolicyAsyncIsCalled` covers this path but doesn't assert on the correlationId value in the saved outbox event — so both the null-check conditional and the empty-string return can be mutated without test failure.

Fix: Assert that the outbox event's correlationId is non-null and looks like a UUID.

Additionally, add a test for the MDC branch to force the conditional in the other direction: when MDC has a correlationId, it should be used instead of a random UUID.

- [ ] **Step 1: Add correlationId assertion to `shouldSaveOutboxEventWhenCreatePolicyAsyncIsCalled`**

```java
// After the existing assertions, add:
assertNotNull(saved.getCorrelationId());
assertTrue(saved.getCorrelationId().matches(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
```

Add the import: `import static org.junit.jupiter.api.Assertions.assertTrue;`

- [ ] **Step 2: Run tests to verify**

Run: `mvn test -pl . -Dtest="PolicyServiceTest" -DfailIfNoTests=false`
Expected: All tests pass

- [ ] **Step 3: Run PIT to verify `correlationId` mutations are killed**

Run: `mvn pitest:mutationCoverage -DtargetTests="com.insurance.policy.policy_service.PolicyServiceTest" -Dfeatures="+EXPORT"`

Check: `correlationId:136` — both mutations should now show KILLED.

---

### Task 5: Add tests for `getPolicyById` (NO_COVERAGE → KILLED)

**Files:**
- Modify: `src/test/java/com/insurance/policy/policy_service/PolicyServiceTest.java`
- No production code changes needed

#### Analysis

`PolicyService.getPolicyById` at line 83 has two NO_COVERAGE mutations (`replaced return value with null` for the method and its lambda). The method is entirely untested.

Fix: Add two tests — one for the happy path (policy found), one for the not-found path.

- [ ] **Step 1: Add `getPolicyById` tests**

```java
@Test
void shouldReturnPolicyWhenFoundById() {
    UUID id = UUID.randomUUID();
    Policy policy = new Policy();
    policy.setId(id);
    policy.setPolicyNumber("POL-001");
    policy.setPolicyHolder("John Doe");

    PolicyResponse expected = new PolicyResponse(
            id, "POL-001", "John Doe",
            new BigDecimal("1000"), new BigDecimal("5"),
            LocalDate.now(), "DRAFT");

    when(repository.findById(id)).thenReturn(Optional.of(policy));
    when(mapper.toResponse(policy)).thenReturn(expected);

    PolicyResponse result = policyService.getPolicyById(id);

    assertEquals(expected, result);
    verify(repository).findById(id);
    verify(mapper).toResponse(policy);
}

@Test
void shouldThrowWhenPolicyNotFoundById() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThrows(PolicyNotFoundException.class,
            () -> policyService.getPolicyById(id));
    verify(repository).findById(id);
    verifyNoInteractions(mapper);
}
```

Add import if not already present: `import static org.mockito.Mockito.verifyNoInteractions;`

- [ ] **Step 2: Run tests to verify**

Run: `mvn test -pl . -Dtest="PolicyServiceTest" -DfailIfNoTests=false`
Expected: All tests pass

- [ ] **Step 3: Run PIT to verify NO_COVERAGE → KILLED**

Run: `mvn pitest:mutationCoverage -DtargetTests="com.insurance.policy.policy_service.PolicyServiceTest" -Dfeatures="+EXPORT"`

Check: `getPolicyById:83` and `lambda$getPolicyById$0:85` — should both show KILLED.

---

### Task 6: Add OutboxPublisher JsonProcessingException path test

**Files:**
- Modify: `src/test/java/com/insurance/policy/outbox/OutboxPublisherTest.java`
- No production code changes needed

#### Analysis

The `OutboxPublisherTest` covers the happy path (rabbit publish succeeds → PUBLISHED), the retry path (AmqpException → retry count incremented), and the max retries exceeded path. But it never tests the `JsonProcessingException` branch (corrupt payload), which leaves multiple NO_COVERAGE mutations.

The mutations at lines 43-44 (setting the correlationId header and returning message) are inside a lambda passed to `convertAndSend`. Since `rabbitTemplate` is mocked and `doAnswer` isn't used to invoke the lambda, the lambda is never executed during testing — hence NO_COVERAGE. Fixing this requires using `doAnswer` to actually invoke the `MessagePostProcessor` lambda.

The mutation at line 51 (`setStatus(FAILED)` on corrupt payload, line 51) is also uncovered.

- [ ] **Step 1: Add test for corrupt payload / JsonProcessingException path**

```java
@Test
void shouldMarkFailedWhenPayloadIsCorrupt() throws Exception {
    pendingEvent = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("POLICY")
            .aggregateId("POL-001")
            .eventType("POLICY_CREATED")
            .payload("{invalid json}")
            .status(OutboxEvent.OutboxStatus.PENDING)
            .retryCount(0)
            .maxRetries(5)
            .build();

    when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING))
            .thenReturn(List.of(pendingEvent));

    outboxPublisher.publishPendingEvents();

    verify(outboxRepository, times(2)).save(eventCaptor.capture());
    OutboxEvent saved = eventCaptor.getValue();
    assertEquals(OutboxEvent.OutboxStatus.FAILED, saved.getStatus());
}
```

Note: `outboxRepository.save` is called twice in this path — once during the initial save in `shouldRetryWhenPublishingFails` was called once, but here `assertEquals` gets called on the latest. Actually looking more carefully: the `catch (JsonProcessingException)` saves once (line 52). The test verifies the single save with `FAILED` status.

Wait, looking at the existing test code: `verify(outboxRepository).save(eventCaptor.capture())` catches a single save. The corrupt payload path only saves once. So this should work.

- [ ] **Step 2: Run OutboxPublisher tests**

Run: `mvn test -pl . -Dtest="OutboxPublisherTest" -DfailIfNoTests=false`
Expected: All tests pass

- [ ] **Step 3: Run PIT on OutboxPublisher**

Run: `mvn pitest:mutationCoverage -DtargetTests="com.insurance.policy.outbox.OutboxPublisherTest" -Dfeatures="+EXPORT"`

Check: `OutboxPublisher.java` — the JsonProcessingException NO_COVERAGE mutations should now be KILLED. The header lambda (lines 43-44) may still be NO_COVERAGE since it's inside a lambda that the mock doesn't invoke — that requires a more sophisticated approach and may need to remain as a known gap.

---

### Task 7: Add GlobalExceptionHandler tests (NO_COVERAGE → KILLED)

**Files:**
- Create: `src/test/java/com/insurance/policy/exception/GlobalExceptionHandlerTest.java`
- No production code changes needed

#### Analysis

The `GlobalExceptionHandler` has multiple NO_COVERAGE mutations: `handleNotFound`, `handleAlreadyExists`, `handleIllegalArgument`, `handleInvalidTransition`, `handlePropertyReference`, `handleAllExceptions`. Only `handleValidationExceptions` and `handleAccessDenied` are covered (via controller tests).

Fix: Add direct unit tests for each handler method.

- [ ] **Step 1: Create `GlobalExceptionHandlerTest`**

```java
package com.insurance.policy.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleNotFound() {
        PolicyNotFoundException ex = new PolicyNotFoundException("POL-001");

        ResponseEntity<ApiError> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("POL-001"));
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    void shouldHandleAlreadyExists() {
        PolicyAlreadyExistsException ex = new PolicyAlreadyExistsException("POL-001");

        ResponseEntity<ApiError> response = handler.handleAlreadyExists(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
    }

    @Test
    void shouldHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<ApiError> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Invalid argument"));
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void shouldHandleInvalidTransition() {
        InvalidPolicyTransitionException ex =
                new InvalidPolicyTransitionException(Policy.PolicyStatus.DRAFT, Policy.PolicyStatus.CANCELLED);

        ResponseEntity<ApiError> response = handler.handleInvalidTransition(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void shouldHandlePropertyReference() {
        PropertyReferenceException ex = new PropertyReferenceException("invalidProp", com.insurance.policy.domain.Policy.class, List.of("validProp"));

        ResponseEntity<ApiError> response = handler.handlePropertyReference(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("invalidProp"));
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void shouldHandleAllExceptions() {
        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<ApiError> response = handler.handleAllExceptions(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
    }

    @Test
    void shouldHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<ApiError> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
    }
}
```

Add necessary import: `import java.util.List;`

- [ ] **Step 2: Run tests to verify**

Run: `mvn test -pl . -Dtest="GlobalExceptionHandlerTest" -DfailIfNoTests=false`
Expected: All tests pass

- [ ] **Step 3: Run PIT to verify NO_COVERAGE → KILLED**

Run: `mvn pitest:mutationCoverage -DtargetTests="com.insurance.policy.exception.GlobalExceptionHandlerTest" -Dfeatures="+EXPORT"`

Check: All handler methods should now show KILLED.

---

### Task 8: Add UserDetailsServiceImpl tests (NO_COVERAGE → KILLED)

**Files:**
- Create: `src/test/java/com/insurance/policy/policy_service/UserDetailsServiceImplTest.java`
- No production code changes needed

#### Analysis

`UserDetailsServiceImpl.loadUserByUsername` and `getAllUsers` are entirely untested.

- [ ] **Step 1: Create `UserDetailsServiceImplTest`**

```java
package com.insurance.policy.policy_service;

import com.insurance.policy.domain.User;
import com.insurance.policy.dtos.UserResponse;
import com.insurance.policy.mapper.UserMapper;
import com.insurance.policy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new UserDetailsServiceImpl(userRepository, userMapper);
    }

    @Test
    void shouldLoadUserByUsername() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("testuser");

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown"));
    }

    @Test
    void shouldGetAllUsers() {
        User user = new User();
        user.setUsername("user1");
        UserResponse response = new UserResponse("user1", "user1@test.com");

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userMapper.toResponseList(List.of(user))).thenReturn(List.of(response));

        List<UserResponse> result = userDetailsService.getAllUsers();

        assertEquals(1, result.size());
        assertEquals("user1", result.get(0).username());
    }
}
```

- [ ] **Step 2: Run tests to verify**

Run: `mvn test -pl . -Dtest="UserDetailsServiceImplTest" -DfailIfNoTests=false`
Expected: All tests pass

- [ ] **Step 3: Run PIT to verify NO_COVERAGE → KILLED**

Run: `mvn pitest:mutationCoverage -DtargetTests="com.insurance.policy.policy_service.UserDetailsServiceImplTest" -Dfeatures="+EXPORT"`

Check: `UserDetailsServiceImpl` — mutations should now be KILLED.

---

### Task 9: Run full verification

- [ ] **Step 1: Run all tests**

```bash
mvn test
```

Expected: All tests pass.

- [ ] **Step 2: Run full PIT report**

```bash
mvn pitest:mutationCoverage -Dfeatures="+EXPORT"
```

Expected: Overall mutation coverage should increase significantly. The
`PolicyService.java` report should show 0 SURVIVED mutations (only possibly
remaining survivors are the `println` statements if not removed).

- [ ] **Step 3: Commit all changes**

```bash
git add -A
git commit -m "test: improve mutation coverage - kill surviving PIT mutations

- Add return value and status assertions to createPolicy tests
- Remove debug System.out.println statements
- Add correlationId assertions to outbox event test
- Add getPolicyById success and not-found tests
- Add OutboxPublisher corrupt payload test
- Add GlobalExceptionHandler tests for all handler methods
- Add UserDetailsServiceImpl tests"
```
