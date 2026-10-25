package dev.mfa.incident.repository;

import dev.mfa.incident.model.KnowledgeDocument;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class KnowledgeDocumentRepository implements PanacheRepositoryBase<KnowledgeDocument, UUID> {

  /**
   * Key of the PostgreSQL advisory lock that admits one knowledge ingestion at a time across
   * instances (A-0013). Arbitrary but unique among the application's advisory locks; it spells
   * "knowledge" in ASCII, cut to 8 bytes.
   */
  static final long INGESTION_LOCK_KEY = 0x6b6e_6f77_6c65_6467L;

  private final AgroalDataSource dataSource;

  public KnowledgeDocumentRepository(AgroalDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public Optional<KnowledgeDocument> findByPath(String path) {
    return find("path", path).firstResultOptional();
  }

  /** Deletes the document; the database deletes its segments (cascade). */
  public void deleteByPath(String path) {
    delete("path", path);
  }

  /** The embedding models of the stored documents. */
  public List<String> listEmbeddingModels() {
    return getEntityManager()
        .createQuery("select distinct d.embeddingModel from KnowledgeDocument d", String.class)
        .getResultList();
  }

  /**
   * Takes the ingestion lock without waiting. It is a session lock held by a connection of its own,
   * so it spans a whole run made of many short transactions; closing the lock releases it, as does
   * PostgreSQL when the connection of a crashed instance ends.
   *
   * @return empty if another run holds it
   */
  public Optional<IngestionLock> tryLockIngestion() {
    Connection connection = null;
    try {
      connection = dataSource.getConnection();
      try (PreparedStatement lock = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
        lock.setLong(1, INGESTION_LOCK_KEY);
        try (ResultSet result = lock.executeQuery()) {
          result.next();
          if (result.getBoolean(1)) {
            return Optional.of(new IngestionLock(connection));
          }
        }
      }
      connection.close();
      return Optional.empty();
    } catch (SQLException e) {
      closeQuietly(connection, e);
      throw new IllegalStateException("Could not take the knowledge ingestion lock", e);
    }
  }

  private static void closeQuietly(Connection connection, SQLException failure) {
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (SQLException e) {
      failure.addSuppressed(e);
    }
  }

  /** The held ingestion lock; {@link #close()} releases it and returns the connection. */
  public static final class IngestionLock implements AutoCloseable {

    private final Connection connection;

    private IngestionLock(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void close() {
      try (connection;
          PreparedStatement unlock = connection.prepareStatement("select pg_advisory_unlock(?)")) {
        unlock.setLong(1, INGESTION_LOCK_KEY);
        unlock.execute();
      } catch (SQLException e) {
        throw new IllegalStateException("Could not release the knowledge ingestion lock", e);
      }
    }
  }
}
