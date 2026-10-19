package dev.mfa.incident.messaging;

import dev.mfa.incident.dto.AlertIngestionResult.IncidentOpened;
import dev.mfa.incident.dto.AlertIngestionResult.OccurrenceRecorded;
import dev.mfa.incident.service.AlertIngestionService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Consumes {@code ops.alerts.v1} (A-0003). A record is acknowledged once its alert is committed. An
 * exception, such as an {@code InvalidAlertException} from the reader, is logged by the connector
 * and sends the record to {@code ops.alerts.v1.dlq} with the exception message in the {@code
 * dead-letter-reason} header (configured in {@code application.properties}).
 */
@ApplicationScoped
public class AlertConsumer {

  private static final Logger LOG = Logger.getLogger(AlertConsumer.class);

  private final AlertPayloadReader reader;
  private final AlertIngestionService ingestion;

  public AlertConsumer(AlertPayloadReader reader, AlertIngestionService ingestion) {
    this.reader = reader;
    this.ingestion = ingestion;
  }

  @Incoming("alerts")
  @RunOnVirtualThread
  public void consume(byte[] payload) {
    switch (ingestion.ingest(reader.read(payload))) {
      case IncidentOpened opened -> LOG.infof("Opened incident %s", opened.incidentId());
      case OccurrenceRecorded occurrence ->
          LOG.debugf(
              "Recorded occurrence %s of open incident %s",
              occurrence.occurrenceId(), occurrence.incidentId());
    }
  }
}
