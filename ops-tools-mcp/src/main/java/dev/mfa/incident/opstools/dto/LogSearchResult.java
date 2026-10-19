package dev.mfa.incident.opstools.dto;

import java.util.List;

/**
 * Log entries matching a search, newest first.
 *
 * @param totalMatches number of matching entries in the searched period
 * @param truncated whether more entries matched than returned
 */
public record LogSearchResult(
    String service, int totalMatches, List<LogEntry> entries, boolean truncated) {

  public LogSearchResult {
    entries = List.copyOf(entries);
  }
}
