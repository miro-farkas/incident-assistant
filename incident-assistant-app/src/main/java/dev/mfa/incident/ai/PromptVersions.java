package dev.mfa.incident.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Id and version of a prompt from its {@code prompts/<name>/prompt.properties} (P-0007), recorded
 * on every step row.
 */
public record PromptVersions(String id, String version) {

  /**
   * @throws IllegalStateException if the file is missing or lacks {@code id} or {@code version}
   */
  public static PromptVersions of(String name) {
    String resource = "prompts/" + name + "/prompt.properties";
    Properties properties = new Properties();
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Prompt metadata not found: " + resource);
      }
      properties.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    String id = properties.getProperty("id");
    String version = properties.getProperty("version");
    if (id == null || id.isBlank() || version == null || version.isBlank()) {
      throw new IllegalStateException(resource + " needs id and version");
    }
    return new PromptVersions(id.strip(), version.strip());
  }
}
