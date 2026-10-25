package dev.mfa.incident.resource;

/** Roles of the API (A-0006, A-0004), granted in the identity provider. */
final class Roles {

  /** Reads incidents. */
  static final String VIEWER = "incident-viewer";

  /** Decides on remediation proposals; also reads incidents. */
  static final String APPROVER = "incident-approver";

  /** Runs the knowledge base ingestion (A-0004). */
  static final String KNOWLEDGE_ADMIN = "knowledge-admin";

  private Roles() {}
}
