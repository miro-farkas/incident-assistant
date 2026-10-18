package dev.mfa.incident.repository;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Database settings from application.properties are in effect on real connections. */
@QuarkusTest
class DatabaseConfigurationTest {

  private static final String CONFIGURED_STATEMENT_TIMEOUT = "10s";
  private static final String DATASOURCE_HEALTH_CHECK = "Database connections health check";

  @Inject EntityManager entityManager;

  @Test
  void connectionsUseConfiguredStatementTimeout() {
    String timeout =
        QuarkusTransaction.requiringNew()
            .call(
                () ->
                    (String)
                        entityManager
                            .createNativeQuery("show statement_timeout")
                            .getSingleResult());

    assertThat(timeout).isEqualTo(CONFIGURED_STATEMENT_TIMEOUT);
  }

  @Test
  void readinessIncludesDatabase() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("status", equalTo("UP"))
        .body("checks.name", hasItem(DATASOURCE_HEALTH_CHECK));
  }
}
