package dev.mfa.incident.notification.testsupport;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads the tables of schema notification with plain JDBC, as the application's role. */
@ApplicationScoped
public class NotificationTables {

  private final AgroalDataSource dataSource;

  public NotificationTables(AgroalDataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** A row of table {@code notification}. */
  public record NotificationRow(
      String rule, String recipient, String status, int attempts, String lastError) {}

  public List<NotificationRow> notifications(UUID eventId) {
    List<NotificationRow> rows = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "select rule, recipient, status, attempts, last_error from notification"
                    + " where event_id = ? order by recipient")) {
      statement.setObject(1, eventId);
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          rows.add(
              new NotificationRow(
                  result.getString(1),
                  result.getString(2),
                  result.getString(3),
                  result.getInt(4),
                  result.getString(5)));
        }
      }
      return rows;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public boolean processed(UUID eventId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "select count(*) from processed_event where event_id = ?")) {
      statement.setObject(1, eventId);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getLong(1) > 0;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Removes every pending notification, so a test starts with an empty queue. */
  public void clearPending() {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("delete from notification where status = 'PENDING'");
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
