package dev.mfa.incident.notification.dto;

import java.util.List;

/**
 * A configured rule with its recipient list resolved.
 *
 * @param recipients distinct addresses in configuration order
 */
public record NotificationRuleDto(String name, String state, List<String> recipients) {}
