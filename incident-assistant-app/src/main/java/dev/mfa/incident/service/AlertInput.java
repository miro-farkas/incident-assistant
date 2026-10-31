package dev.mfa.incident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;

/**
 * The alert fields the AI steps give the model (A-0014, A-0015), taken from the stored alert;
 * everything else stays out of the prompts. Also recorded as the step input.
 */
final class AlertInput {

  private static final String[] FIELDS = {
    "alertName", "source", "service", "severity", "summary", "description"
  };

  private AlertInput() {}

  /** The text fields that are present and not blank, and the labels as strings. */
  static ObjectNode of(ObjectMapper json, String alertPayload) {
    ObjectNode input = json.createObjectNode();
    JsonNode alert;
    try {
      alert = json.readTree(alertPayload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored alert is not JSON", e);
    }
    for (String field : FIELDS) {
      JsonNode value = alert.path(field);
      if (value.isTextual() && !value.asText().isBlank()) {
        input.put(field, value.asText());
      }
    }
    ObjectNode labels = input.putObject("labels");
    for (Iterator<Map.Entry<String, JsonNode>> it = alert.path("labels").fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> label = it.next();
      labels.put(label.getKey(), label.getValue().asText());
    }
    return input;
  }

  /** {@code null} for a field the alert lacks; the prompts then say it was not reported. */
  static String text(ObjectNode input, String field) {
    JsonNode value = input.get(field);
    return value == null || !value.isTextual() ? null : value.asText();
  }
}
