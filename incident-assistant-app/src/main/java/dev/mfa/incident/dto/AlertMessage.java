package dev.mfa.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.hibernate.validator.constraints.URL;

/**
 * A firing alert as received on {@code ops.alerts.v1}; schema in {@code docs/schemas/alert-v1.json}
 * (A-0003). Validated with Jakarta Bean Validation before ingestion; unknown JSON properties are
 * ignored.
 *
 * @param source system that raised the alert
 * @param fingerprint alert identity within the source
 * @param severity severity as reported by the source, not the triaged one
 * @param service affected service as reported by the source
 */
public record AlertMessage(
    @NotBlank @Size(max = NewIncidentDto.MAX_SOURCE_LENGTH) String source,
    @NotBlank @Size(max = NewIncidentDto.MAX_FINGERPRINT_LENGTH) String fingerprint,
    @NotBlank @Size(max = AlertMessage.MAX_ALERT_NAME_LENGTH) String alertName,
    @NotBlank @Size(max = AlertMessage.MAX_SUMMARY_LENGTH) String summary,
    @NotNull Instant startsAt,
    @Size(max = AlertMessage.MAX_SEVERITY_LENGTH) String severity,
    @Size(max = AlertMessage.MAX_SERVICE_LENGTH) String service,
    @Size(max = AlertMessage.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = AlertMessage.MAX_LABELS)
        Map<
                @NotBlank @Size(max = AlertMessage.MAX_LABEL_KEY_LENGTH) String,
                @NotNull @Size(max = AlertMessage.MAX_LABEL_VALUE_LENGTH) String>
            labels,
    @Size(max = AlertMessage.MAX_GENERATOR_URL_LENGTH) @URL String generatorUrl) {

  public static final int MAX_ALERT_NAME_LENGTH = 200;
  public static final int MAX_SUMMARY_LENGTH = 500;
  public static final int MAX_SEVERITY_LENGTH = 32;
  public static final int MAX_SERVICE_LENGTH = 100;
  public static final int MAX_DESCRIPTION_LENGTH = 4000;
  public static final int MAX_LABELS = 50;
  public static final int MAX_LABEL_KEY_LENGTH = 100;
  public static final int MAX_LABEL_VALUE_LENGTH = 500;
  public static final int MAX_GENERATOR_URL_LENGTH = 2000;
}
