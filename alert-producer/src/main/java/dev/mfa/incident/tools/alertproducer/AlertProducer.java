package dev.mfa.incident.tools.alertproducer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Manual testing tool (P-0017): produces alerts (docs/schemas/alert-v1.json) to ops.alerts.v1 of
 * the local broker at a fixed rate. Run with {@code ./mvnw -q -pl alert-producer compile
 * exec:java}; stop with Ctrl+C.
 */
public class AlertProducer {

  // --- configuration ---------------------------------------------------------------------------
  static final String BOOTSTRAP_SERVERS = "localhost:9092";
  static final String TOPIC = "ops.alerts.v1";

  /** Alerts sent per minute, spread evenly. */
  static final int ALERTS_PER_MINUTE = 12;

  /** Total alerts to send; 0 sends until stopped. */
  static final long TOTAL_ALERTS = 20;

  /**
   * Size of the fingerprint pool. Alerts with the same source and fingerprint belong to the same
   * open incident, so a small pool produces mostly duplicates and a large one mostly new incidents.
   */
  static final int DISTINCT_FINGERPRINTS = 20;

  // ---------------------------------------------------------------------------------------------

  private static final Duration REPORT_INTERVAL = Duration.ofSeconds(1);
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

  record AlertType(String alertName, String service, String severity, String summary) {}

  static final List<AlertType> ALERT_TYPES =
      List.of(
          new AlertType("HighErrorRate", "checkout", "critical", "Error rate above 5%"),
          new AlertType("HighLatency", "payments", "warning", "p99 latency above 2s"),
          new AlertType("PodCrashLooping", "inventory", "critical", "Pod restarted 5 times in 10m"),
          new AlertType("DiskAlmostFull", "postgres", "warning", "Disk usage above 90%"),
          new AlertType("ConsumerLagHigh", "orders", "warning", "Consumer lag above 10000"));

  void main() {
    Properties kafkaProps = createKafkaProperties();

    AtomicLong acked = new AtomicLong();
    AtomicLong failed = new AtomicLong();
    KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);
    Thread main = Thread.currentThread();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  main.interrupt();
                  producer.close(CLOSE_TIMEOUT);
                  System.out.printf("stopped: %d acked, %d failed%n", acked.get(), failed.get());
                }));

    System.out.printf(
        "producing %d alerts/min to %s on %s (%s), %d fingerprints; Ctrl+C to stop%n",
        ALERTS_PER_MINUTE,
        TOPIC,
        BOOTSTRAP_SERVERS,
        TOTAL_ALERTS == 0 ? "unlimited" : TOTAL_ALERTS + " total",
        DISTINCT_FINGERPRINTS);

    long intervalNanos = TimeUnit.MINUTES.toNanos(1) / ALERTS_PER_MINUTE;
    long next = System.nanoTime();
    long lastReport = next - REPORT_INTERVAL.toNanos();
    long sent = 0;
    try {
      while (TOTAL_ALERTS == 0 || sent < TOTAL_ALERTS) {
        long wait = next - System.nanoTime();
        if (wait > 0) {
          TimeUnit.NANOSECONDS.sleep(wait);
        }
        next += intervalNanos;

        int fp = ThreadLocalRandom.current().nextInt(DISTINCT_FINGERPRINTS);
        AlertType type = ALERT_TYPES.get(ThreadLocalRandom.current().nextInt(ALERT_TYPES.size()));
        String fingerprint = "manual-" + type.alertName().toLowerCase() + "-" + fp;
        producer.send(
            new ProducerRecord<>(TOPIC, "prometheus:" + fingerprint, json(type, fingerprint)),
            (metadata, e) -> {
              if (e == null) {
                acked.incrementAndGet();
              } else {
                failed.incrementAndGet();
                System.err.println("send failed: " + e.getMessage());
              }
            });
        sent++;

        long now = System.nanoTime();
        if (now - lastReport >= REPORT_INTERVAL.toNanos()) {
          System.out.printf("sent %d, acked %d, failed %d%n", sent, acked.get(), failed.get());
          lastReport = now;
        }
      }
      producer.flush();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static Properties createKafkaProperties() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    return props;
  }

  static String json(AlertType type, String fingerprint) {
    return """
    {"source":"prometheus","fingerprint":"%s","alertName":"%s","summary":"%s",\
    "startsAt":"%s","severity":"%s","service":"%s",\
    "labels":{"generator":"alert-producer","team":"sre"}}\
    """
        .formatted(
            fingerprint,
            type.alertName(),
            type.summary(),
            Instant.now(),
            type.severity(),
            type.service());
  }
}
