package dev.mfa.incident.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.mfa.incident.dto.IngestionReportDto.SkippedFile;
import dev.mfa.incident.dto.KnowledgeDirectoryDto;
import dev.mfa.incident.dto.KnowledgeFileDto;
import dev.mfa.incident.model.DocumentType;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Reads the Markdown files of the knowledge directory with their YAML front matter (A-0004,
 * A-0013): {@code service} (optional), {@code type} ({@code runbook} or {@code postmortem}), {@code
 * title}, {@code updated} ({@code YYYY-MM-DD}). A file that does not match is reported, not read.
 */
@ApplicationScoped
public class KnowledgeFileReader {

  static final String EXTENSION = ".md";
  static final int MAX_PATH_LENGTH = 500;
  static final int MAX_TITLE_LENGTH = 300;
  static final int MAX_SERVICE_LENGTH = 100;

  private static final String FRONT_MATTER_FENCE = "---";

  private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

  /**
   * @throws IllegalStateException if the directory does not exist or cannot be read; an ingestion
   *     must then change nothing, rather than remove every document
   */
  public KnowledgeDirectoryDto read(Path directory) {
    if (!Files.isDirectory(directory)) {
      throw new IllegalStateException("Knowledge directory not found: " + directory);
    }
    List<KnowledgeFileDto> files = new ArrayList<>();
    List<SkippedFile> skipped = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(directory)) {
      for (Path file :
          paths
              .filter(p -> p.toString().endsWith(EXTENSION) && Files.isRegularFile(p))
              .sorted()
              .toList()) {
        String path = directory.relativize(file).toString().replace('\\', '/');
        try {
          files.add(parse(path, Files.readAllBytes(file)));
        } catch (InvalidDocumentException e) {
          skipped.add(new SkippedFile(path, e.getMessage()));
        }
      }
    } catch (IOException | UncheckedIOException e) {
      throw new IllegalStateException("Knowledge directory not readable: " + directory, e);
    }
    return new KnowledgeDirectoryDto(files, skipped);
  }

  private KnowledgeFileDto parse(String path, byte[] content) {
    if (path.length() > MAX_PATH_LENGTH) {
      throw new InvalidDocumentException("path longer than " + MAX_PATH_LENGTH + " characters");
    }
    String text;
    try {
      text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
    } catch (CharacterCodingException e) {
      throw new InvalidDocumentException("not UTF-8");
    }
    text = text.replace("\r\n", "\n");
    if (text.endsWith("\n" + FRONT_MATTER_FENCE)) {
      text += "\n";
    }
    if (!text.startsWith(FRONT_MATTER_FENCE + "\n")) {
      throw new InvalidDocumentException("no front matter");
    }
    int end = text.indexOf("\n" + FRONT_MATTER_FENCE + "\n", FRONT_MATTER_FENCE.length());
    if (end < 0) {
      throw new InvalidDocumentException("front matter not closed");
    }
    JsonNode meta;
    try {
      meta = yaml.readTree(text.substring(FRONT_MATTER_FENCE.length() + 1, end));
    } catch (IOException e) {
      throw new InvalidDocumentException("front matter is not valid YAML");
    }
    if (meta == null || !meta.isObject()) {
      throw new InvalidDocumentException("front matter is not a mapping");
    }
    return new KnowledgeFileDto(
        path,
        sha256(content),
        optionalText(meta, "service", MAX_SERVICE_LENGTH),
        type(meta),
        requiredText(meta, "title", MAX_TITLE_LENGTH),
        updated(meta),
        text.substring(end + FRONT_MATTER_FENCE.length() + 2));
  }

  private static DocumentType type(JsonNode meta) {
    String type = requiredText(meta, "type", 32);
    try {
      return DocumentType.valueOf(type.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidDocumentException("type must be runbook or postmortem");
    }
  }

  private static LocalDate updated(JsonNode meta) {
    try {
      return LocalDate.parse(requiredText(meta, "updated", 10));
    } catch (DateTimeParseException e) {
      throw new InvalidDocumentException("updated must be a date YYYY-MM-DD");
    }
  }

  private static String requiredText(JsonNode meta, String field, int maxLength) {
    String value = optionalText(meta, field, maxLength);
    if (value == null) {
      throw new InvalidDocumentException(field + " is missing");
    }
    return value;
  }

  private static String optionalText(JsonNode meta, String field, int maxLength) {
    JsonNode node = meta.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    if (!node.isValueNode() || node.asText().isBlank()) {
      throw new InvalidDocumentException(field + " must be a text");
    }
    String value = node.asText().strip();
    if (value.length() > maxLength) {
      throw new InvalidDocumentException(field + " longer than " + maxLength + " characters");
    }
    return value;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /** A file that is not a knowledge document; the message names the problem, not the content. */
  private static final class InvalidDocumentException extends RuntimeException {
    InvalidDocumentException(String message) {
      super(message);
    }
  }
}
