package dev.mfa.incident.resource;

import jakarta.ws.rs.core.Response;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps the incident version to the {@code ETag} and back from {@code If-Match} (P-0009). The entity
 * tag is the version in quotes, for example {@code "3"}; only that strong form is accepted.
 */
final class IncidentVersions {

  private static final Pattern STRONG_VERSION_TAG = Pattern.compile("\"(\\d{1,18})\"");
  private static final String ANY = "*";

  private IncidentVersions() {}

  static String entityTag(long version) {
    return "\"" + version + "\"";
  }

  /**
   * @throws ProblemException {@code 428} if the header is missing or {@code *}, {@code 400} if it
   *     is not a single version tag
   */
  static long fromIfMatch(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank() || ANY.equals(ifMatch.strip())) {
      throw new ProblemException(
          Response.Status.PRECONDITION_REQUIRED,
          "If-Match with the incident version (ETag) is required");
    }
    Matcher matcher = STRONG_VERSION_TAG.matcher(ifMatch.strip());
    if (!matcher.matches()) {
      throw new ProblemException(
          Response.Status.BAD_REQUEST, "If-Match must be a single entity tag such as \"3\"");
    }
    return Long.parseLong(matcher.group(1));
  }
}
