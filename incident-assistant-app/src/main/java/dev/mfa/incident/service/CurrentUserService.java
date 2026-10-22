package dev.mfa.incident.service;

import dev.mfa.incident.dto.CurrentUserDto;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.json.JsonString;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Describes the user of the current request for the UI (A-0010). */
@RequestScoped
public class CurrentUserService {

  /** Only the roles of this application are shown to the UI. */
  static final String ROLE_PREFIX = "incident-";

  private static final List<String> NAME_CLAIMS = List.of("name", "preferred_username");

  private final SecurityIdentity identity;

  public CurrentUserService(SecurityIdentity identity) {
    this.identity = identity;
  }

  public CurrentUserDto currentUser() {
    List<String> roles =
        identity.getRoles().stream().filter(role -> role.startsWith(ROLE_PREFIX)).sorted().toList();
    return new CurrentUserDto(displayName(), roles);
  }

  /**
   * The principal name is the token subject (P-0009), which means nothing to a person; the profile
   * claims of the token are preferred when the principal is a token.
   */
  private String displayName() {
    String principalName = identity.getPrincipal().getName();
    if (!(identity.getPrincipal() instanceof JsonWebToken token)) {
      return principalName;
    }
    return NAME_CLAIMS.stream()
        .flatMap(claim -> Stream.ofNullable(token.getClaim(claim)))
        .map(CurrentUserService::claimText)
        .flatMap(Optional::stream)
        .findFirst()
        .orElse(principalName);
  }

  private static Optional<String> claimText(Object claim) {
    String text = claim instanceof JsonString json ? json.getString() : claim.toString();
    return text.isBlank() ? Optional.empty() : Optional.of(text);
  }
}
