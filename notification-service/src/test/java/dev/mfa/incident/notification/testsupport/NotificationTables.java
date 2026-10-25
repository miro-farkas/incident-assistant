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
import java.util.OptionalLong;
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

  /** The newest version of the incident the service has recorded; empty before its first event. */
  public OptionalLong cursor(UUID incidentId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "select last_version from incident_cursor where incident_id = ?")) {
      statement.setObject(1, incidentId);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty();
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
