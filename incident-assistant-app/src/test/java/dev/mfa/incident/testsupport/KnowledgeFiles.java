package dev.mfa.incident.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes knowledge documents (A-0004) into a test directory. */
public final class KnowledgeFiles {

  private KnowledgeFiles() {}

  /**
   * @param service {@code null} writes a general document without the field
   */
  public static Path document(
      Path directory, String path, String service, String type, String title, String body) {
    String frontMatter =
        "---\n"
            + (service == null ? "" : "service: " + service + "\n")
            + "type: "
            + type
            + "\ntitle: "
            + title
            + "\nupdated: 2026-10-20\n---\n";
    return write(directory, path, frontMatter + body);
  }

  public static Path write(Path directory, String path, String content) {
    Path file = directory.resolve(path);
    try {
      Files.createDirectories(file.getParent());
      return Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
