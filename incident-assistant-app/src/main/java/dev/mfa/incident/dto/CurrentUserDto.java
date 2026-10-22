package dev.mfa.incident.dto;

import java.util.List;

/**
 * The authenticated user, so the UI can greet them and hide actions they may not take (A-0010). The
 * API still checks every role.
 *
 * @param name display name from the token: {@code name}, else {@code preferred_username}, else the
 *     principal name
 * @param roles the user's {@code incident-*} roles, sorted
 */
public record CurrentUserDto(String name, List<String> roles) {}
