package dev.mfa.incident.notification.testsupport;

import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Produces to the Dev Services broker the application is connected to. */
@ApplicationScoped
public class KafkaTestClient {

  public static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final KafkaCompanion companion;

  public KafkaTestClient(@ConfigProperty(name = "kafka.bootstrap.servers") String servers) {
    this.companion = new KafkaCompanion(servers);
  }

  /** Sends an incident event keyed by its incident, as incident-assistant-app does. */
  public void sendEvent(UUID incidentId, String payload) {
    companion
        .produceStrings()
        .fromRecords(new ProducerRecord<>(IncidentEvents.TOPIC, incidentId.toString(), payload))
        .awaitCompletion(TIMEOUT);
  }

  @PreDestroy
  void close() {
    companion.close();
  }
}
