package dev.mfa.incident.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InstanceHealthCheckTest {

  @Test
  void healthIsUp() {
    given().when().get("/q/health").then().statusCode(200).body("status", equalTo("UP"));
  }

  @Test
  void livenessReportsConfiguredInstanceId() {
    given()
        .when()
        .get("/q/health/live")
        .then()
        .statusCode(200)
        .body("checks.name", hasItem(InstanceHealthCheck.NAME))
        .body("checks.find { it.name == 'instance' }.data.id", equalTo("test-instance"));
  }
}
