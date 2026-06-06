package com.insurance.policy.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.policy.domain.OutboxEvent;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private OutboxRepository outboxRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;

    private OutboxPublisher outboxPublisher;

    @Captor
    private ArgumentCaptor<OutboxEvent> eventCaptor;

    private OutboxEvent pendingEvent;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        outboxPublisher = new OutboxPublisher(outboxRepository, rabbitTemplate, objectMapper);
    }

    @Test
    void shouldPublishPendingEventsAndMarkPublished() throws Exception {
        pendingEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("POLICY")
                .aggregateId("POL-001")
                .eventType("POLICY_CREATED")
                .payload("""
                        {"policyNumber":"POL-001","policyHolder":"Jane Doe","coverageAmount":50000,"startDate":"2026-06-07"}
                        """)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .build();

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(List.of(pendingEvent));

        outboxPublisher.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(any(), any(), any(PolicyRequest.class));
        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();
        assertEquals(OutboxEvent.OutboxStatus.PUBLISHED, saved.getStatus());
    }

    @Test
    void shouldRetryWhenPublishingFails() throws Exception {
        pendingEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("POLICY")
                .aggregateId("POL-001")
                .eventType("POLICY_CREATED")
                .payload("""
                        {"policyNumber":"POL-001","policyHolder":"Jane Doe","coverageAmount":50000,"startDate":"2026-06-07"}
                        """)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .build();

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(List.of(pendingEvent));
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(PolicyRequest.class));

        outboxPublisher.publishPendingEvents();

        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();
        assertEquals(1, saved.getRetryCount());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, saved.getStatus());
    }

    @Test
    void shouldMarkFailedAfterMaxRetriesExceeded() throws Exception {
        pendingEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("POLICY")
                .aggregateId("POL-001")
                .eventType("POLICY_CREATED")
                .payload("""
                        {"policyNumber":"POL-001","policyHolder":"Jane Doe","coverageAmount":50000,"startDate":"2026-06-07"}
                        """)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(4)
                .maxRetries(5)
                .build();

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(List.of(pendingEvent));
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(PolicyRequest.class));

        outboxPublisher.publishPendingEvents();

        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();
        assertEquals(OutboxEvent.OutboxStatus.FAILED, saved.getStatus());
        assertEquals(5, saved.getRetryCount());
    }
}
