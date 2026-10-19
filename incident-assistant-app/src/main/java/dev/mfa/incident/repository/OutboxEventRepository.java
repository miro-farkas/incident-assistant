package dev.mfa.incident.repository;

import dev.mfa.incident.model.OutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OutboxEventRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

  /**
   * Key of the PostgreSQL advisory lock that admits one outbox relay at a time across instances
   * (P-0003). The value is arbitrary but must not be used for any other advisory lock; it spells
   * "outbox" in ASCII.
   */
  static final long RELAY_LOCK_KEY = 0x6f75_7462_6f78L;

  /**
   * Takes the relay lock for the current transaction without waiting. PostgreSQL releases it at
   * commit or rollback, also when the connection of a crashed instance is closed.
   *
   * @return {@code false} if another transaction holds it
   */
  public boolean tryLockRelay() {
    return (Boolean)
        getEntityManager()
            .createNativeQuery("select pg_try_advisory_xact_lock(:key)", Boolean.class)
            .setParameter("key", RELAY_LOCK_KEY)
            .getSingleResult();
  }

  /** The oldest unpublished events in relay order. */
  public List<OutboxEvent> listUnpublished(int limit) {
    return find("publishedAt is null", Sort.by("sequenceNumber")).page(0, limit).list();
  }
}
