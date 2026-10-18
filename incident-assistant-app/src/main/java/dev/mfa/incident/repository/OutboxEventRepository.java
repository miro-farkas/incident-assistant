package dev.mfa.incident.repository;

import dev.mfa.incident.model.OutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class OutboxEventRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {}
