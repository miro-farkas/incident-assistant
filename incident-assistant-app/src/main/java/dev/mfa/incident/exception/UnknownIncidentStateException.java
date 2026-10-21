package dev.mfa.incident.exception;

import dev.mfa.incident.model.IncidentState;
import java.util.EnumSet;

/** A request named a state that does not exist. The name itself is not kept: it is client input. */
public final class UnknownIncidentStateException extends IncidentException {

  public UnknownIncidentStateException() {
    super("Unknown incident state; expected one of " + EnumSet.allOf(IncidentState.class));
  }
}
