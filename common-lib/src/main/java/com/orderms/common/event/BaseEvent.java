package com.orderms.common.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all Kafka domain events.
 * Every event carries a unique event ID, source service, and timestamp.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public abstract class BaseEvent implements Serializable {

    /** Unique event identifier. */
    private String eventId = UUID.randomUUID().toString();

    /** Service that produced this event. */
    private String source;

    /** ISO-8601 timestamp of event creation. */
    private Instant occurredAt = Instant.now();
}
