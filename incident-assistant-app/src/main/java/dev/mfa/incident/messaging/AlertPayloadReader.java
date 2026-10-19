package dev.mfa.incident.messaging;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import dev.mfa.incident.config.AlertIngestionConfig;
import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.exception.InvalidAlertException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * Turns a message value from {@code ops.alerts.v1} into a validated {@link AlertMessage}. Error
 * messages name the rule and the field but never contain received content, because they end up in
 * the dead-letter header and in logs.
 */
@ApplicationScoped
public class AlertPayloadReader {

  private final ObjectReader reader;
  private final Validator validator;
  private final AlertIngestionConfig config;

  public AlertPayloadReader(
      ObjectMapper objectMapper, Validator validator, AlertIngestionConfig config) {
    this.reader =
        objectMapper
            .readerFor(AlertMessage.class)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    this.validator = validator;
    this.config = config;
  }

  /**
   * @throws InvalidAlertException if the value is empty, too large, not a JSON object of the alert
   *     schema, or fails validation
   */
  public AlertMessage read(byte[] payload) {
    if (payload == null || payload.length == 0) {
      throw new InvalidAlertException("Alert payload is empty");
    }
    if (payload.length > config.maxPayloadBytes()) {
      throw new InvalidAlertException(
          "Alert payload has " + payload.length + " bytes, limit is " + config.maxPayloadBytes());
    }
    AlertMessage alert = parse(payload);
    if (alert == null) {
      throw new InvalidAlertException("Alert payload must be a JSON object");
    }
    Set<ConstraintViolation<AlertMessage>> violations = validator.validate(alert);
    if (!violations.isEmpty()) {
      throw new InvalidAlertException("Alert is invalid: " + describe(violations));
    }
    return alert;
  }

  private AlertMessage parse(byte[] payload) {
    try {
      return reader.readValue(payload);
    } catch (DatabindException e) {
      throw new InvalidAlertException(describeMapping(e));
    } catch (StreamReadException e) {
      throw new InvalidAlertException("Alert payload is not valid JSON" + at(e.getLocation()));
    } catch (IOException e) {
      throw new InvalidAlertException("Alert payload cannot be read");
    }
  }

  /** Names only the top-level property: deeper path elements can be label keys from the payload. */
  private static String describeMapping(DatabindException e) {
    if (e instanceof JsonMappingException mapping
        && !mapping.getPath().isEmpty()
        && mapping.getPath().getFirst().getFieldName() != null) {
      return "Alert property '"
          + mapping.getPath().getFirst().getFieldName()
          + "' has an invalid type or format";
    }
    return "Alert payload must be a single JSON object" + at(e.getLocation());
  }

  private static String at(JsonLocation location) {
    if (location == null || location.getLineNr() < 1) {
      return "";
    }
    return " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
  }

  /** Sorted, distinct {@code path: message} pairs; container element paths omit map keys. */
  private static String describe(Set<ConstraintViolation<AlertMessage>> violations) {
    Set<String> descriptions = new TreeSet<>();
    for (ConstraintViolation<AlertMessage> violation : violations) {
      descriptions.add(path(violation.getPropertyPath()) + ": " + violation.getMessage());
    }
    StringJoiner joined = new StringJoiner("; ");
    descriptions.forEach(joined::add);
    return joined.toString();
  }

  private static String path(Path path) {
    StringJoiner names = new StringJoiner(".");
    for (Path.Node node : path) {
      if (node.getName() != null) {
        names.add(node.getName());
      }
    }
    return names.toString();
  }
}
