package dev.mfa.incident.repository;

import org.hibernate.exception.ConstraintViolationException;

/** Recognises violations of named database constraints in persistence failures. */
final class ConstraintViolations {

  private ConstraintViolations() {}

  static boolean violates(Throwable failure, String constraintName) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException violation) {
        return constraintName.equalsIgnoreCase(violation.getConstraintName());
      }
    }
    return false;
  }
}
