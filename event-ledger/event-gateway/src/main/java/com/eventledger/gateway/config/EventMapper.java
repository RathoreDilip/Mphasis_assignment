package com.eventledger.gateway.config;

import com.eventledger.gateway.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventMapper {

    private final ObjectMapper objectMapper;

    public Event toEntity(EventRequest request) {
        String metadataJson = null;
        if (request.getMetadata() != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata for eventId={}", request.getEventId());
            }
        }

        return Event.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .metadataJson(metadataJson)
                .status(EventStatus.PROCESSED)
                .build();
    }

    public EventResponse toResponse(Event event) {
        Map<String, Object> metadata = null;
        if (event.getMetadataJson() != null) {
            try {
                metadata = objectMapper.readValue(event.getMetadataJson(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize metadata for eventId={}", event.getEventId());
            }
        }

        return EventResponse.builder()
                .eventId(event.getEventId())
                .accountId(event.getAccountId())
                .type(event.getType())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .eventTimestamp(event.getEventTimestamp())
                .receivedAt(event.getReceivedAt())
                .status(event.getStatus().name())
                .metadata(metadata)
                .build();
    }
}