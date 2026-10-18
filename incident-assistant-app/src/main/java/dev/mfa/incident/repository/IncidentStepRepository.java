package dev.mfa.incident.repository;

import dev.mfa.incident.model.IncidentStep;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/** Append-only: services only insert step rows (P-0014). */
@ApplicationScoped
public class IncidentStepRepository implements PanacheRepositoryBase<IncidentStep, UUID> {}
