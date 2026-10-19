package dev.mfa.incident.messaging;

import dev.mfa.incident.config.OutboxRelayConfig;
import dev.mfa.incident.dto.OutboxRecord;
import dev.mfa.incident.exception.OutboxPublishException;
import dev.mfa.incident.service.OutboxPublisher;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/**
 * Publishes outbox records to {@code ops.incidents.events.v1} (channel {@code incident-events}).
 */
@ApplicationScoped
public class KafkaOutboxPublisher implements OutboxPublisher {

  private final Emitter<String> emitter;
  private final Duration sendTimeout;

  public KafkaOutboxPublisher(
      @Channel("incident-events") Emitter<String> emitter, OutboxRelayConfig config) {
    this.emitter = emitter;
    this.sendTimeout = config.sendTimeout();
  }

  @Override
  public void publish(OutboxRecord outboxRecord) {
    CompletableFuture<Void> acknowledged = new CompletableFuture<>();
    Message<String> message =
        Message.of(
            outboxRecord.payload(),
            Metadata.of(
                OutgoingKafkaRecordMetadata.<String>builder().withKey(outboxRecord.key()).build()),
            () -> {
              acknowledged.complete(null);
              return CompletableFuture.completedFuture(null);
            },
            reason -> {
              acknowledged.completeExceptionally(reason);
              return CompletableFuture.completedFuture(null);
            });
    try {
      emitter.send(message);
      acknowledged.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new OutboxPublishException(
          outboxRecord.eventId(), "no acknowledgement within " + sendTimeout, e);
    } catch (ExecutionException e) {
      throw new OutboxPublishException(
          outboxRecord.eventId(), "rejected by the broker: " + e.getCause().getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OutboxPublishException(outboxRecord.eventId(), "interrupted", e);
    } catch (RuntimeException e) {
      throw new OutboxPublishException(outboxRecord.eventId(), e.getMessage(), e);
    }
  }
}
