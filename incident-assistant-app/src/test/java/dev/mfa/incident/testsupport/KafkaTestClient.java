package dev.mfa.incident.testsupport;

import static org.awaitility.Awaitility.await;

import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Produces to and reads from the Dev Services broker the application is connected to. */
@ApplicationScoped
public class KafkaTestClient {

  public static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final KafkaCompanion companion;

  public KafkaTestClient(@ConfigProperty(name = "kafka.bootstrap.servers") String servers) {
    this.companion = new KafkaCompanion(servers);
  }

  public void send(String topic, String key, String value) {
    companion
        .produceStrings()
        .fromRecords(new ProducerRecord<>(topic, key, value))
        .awaitCompletion(TIMEOUT);
  }

  /**
   * Reads the topic from the beginning until {@code count} records match, and returns the matching
   * records in topic order per partition.
   */
  public List<ConsumerRecord<String, String>> awaitRecords(
      String topic, Predicate<ConsumerRecord<String, String>> filter, int count) {
    try (ConsumerTask<String, String> task =
        companion
            .consumeStrings()
            .withGroupId("test-" + UUID.randomUUID())
            .withOffsetReset("earliest")
            .fromTopics(topic)) {
      await()
          .atMost(TIMEOUT)
          .until(() -> task.getRecords().stream().filter(filter).count() >= count);
      return task.getRecords().stream().filter(filter).toList();
    }
  }

  public static String header(ConsumerRecord<?, ?> consumerRecord, String name) {
    Header header = consumerRecord.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  @PreDestroy
  void close() {
    companion.close();
  }
}
