package dev.mfa.incident.repository;

import dev.mfa.incident.model.AlertOccurrence;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/** Append-only: services only insert occurrence rows. */
@ApplicationScoped
public class AlertOccurrenceRepository implements PanacheRepositoryBase<AlertOccurrence, UUID> {}
