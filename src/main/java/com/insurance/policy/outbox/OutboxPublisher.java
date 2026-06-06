package com.insurance.policy.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.policy.Listener.RabbitMQConfig;
import com.insurance.policy.domain.OutboxEvent;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            try {
                PolicyRequest payload = objectMapper.readValue(event.getPayload(), PolicyRequest.class);
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, payload);
                event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                outboxRepository.save(event);
                log.info("Published outbox event {} of type {}", event.getId(), event.getEventType());
            } catch (JsonProcessingException e) {
                log.error("Corrupt payload for outbox event {}: {}", event.getId(), e.getMessage());
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                outboxRepository.save(event);
            } catch (AmqpException e) {
                log.error("Failed to publish outbox event {} to RabbitMQ: {}", event.getId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                    log.warn("Outbox event {} failed after {} retries", event.getId(), event.getMaxRetries());
                }
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Unexpected error publishing outbox event {}: {}", event.getId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                }
                outboxRepository.save(event);
            }
        }
    }
}
