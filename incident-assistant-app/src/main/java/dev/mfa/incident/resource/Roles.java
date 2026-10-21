package dev.mfa.incident.resource;

/** Roles of the approval API (A-0006), granted in the identity provider. */
final class Roles {

  /** Reads incidents. */
  static final String VIEWER = "incident-viewer";

  /** Decides on remediation proposals; also reads incidents. */
  static final String APPROVER = "incident-approver";

  private Roles() {}
}
