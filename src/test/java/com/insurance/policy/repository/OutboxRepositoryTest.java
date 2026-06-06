package com.insurance.policy.repository;

import com.insurance.policy.BaseContainerTest;
import com.insurance.policy.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxRepositoryTest extends BaseContainerTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindPendingEventsOrderedByCreatedAt() {
        OutboxEvent event1 = OutboxEvent.builder()
                .aggregateType("POLICY")
                .aggregateId("POL-001")
                .eventType("POLICY_CREATED")
                .payload("{}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .maxRetries(5)
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .aggregateType("POLICY")
                .aggregateId("POL-002")
                .eventType("POLICY_CREATED")
                .payload("{}")
                .status(OutboxEvent.OutboxStatus.PUBLISHED)
                .maxRetries(5)
                .build();

        entityManager.persist(event1);
        entityManager.persist(event2);
        entityManager.flush();

        List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING);

        assertEquals(1, pending.size());
        assertEquals("POL-001", pending.get(0).getAggregateId());
    }

    @Test
    void shouldReturnEmptyWhenNoPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING);

        assertEquals(0, pending.size());
    }
}
