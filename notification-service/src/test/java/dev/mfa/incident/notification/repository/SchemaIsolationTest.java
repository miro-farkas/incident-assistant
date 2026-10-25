package dev.mfa.incident.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.notification.testsupport.ManualSenderProfile;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The module stays in schema {@code notification} (A-0011). Every test of the module runs as the
 * restricted role of production (see {@code db/test-roles.sql}), so a migration or query that
 * touched another schema would fail; this test shows the restriction is in force and that every
 * table of the module, Flyway's history included, is in the own schema.
 */
@QuarkusTest
@TestProfile(ManualSenderProfile.class)
class SchemaIsolationTest {

  private static final String PERMISSION_DENIED = "42501";

  @Inject AgroalDataSource dataSource;

  @Test
  void connectionsRunAsTheRestrictedRoleInTheOwnSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("select current_user, current_schema()")) {
      result.next();
      assertThat(result.getString(1)).isEqualTo("notification_service");
      assertThat(result.getString(2)).isEqualTo("notification");
    }
  }

  @Test
  void everyTableOfTheModuleIsInItsSchema() throws SQLException {
    List<String> tables = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "select schemaname || '.' || tablename from pg_tables"
                    + " where tableowner = 'notification_service' order by 1")) {
      while (result.next()) {
        tables.add(result.getString(1));
      }
    }
    assertThat(tables)
        .containsExactly(
            "notification.flyway_schema_history",
            "notification.incident_cursor",
            "notification.notification");
  }

  @Test
  void otherSchemasAreNotReadable() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      assertThatThrownBy(() -> statement.executeQuery("select count(*) from public.incident"))
          .isInstanceOf(SQLException.class)
          .satisfies(
              e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(PERMISSION_DENIED));
    }
  }
}
