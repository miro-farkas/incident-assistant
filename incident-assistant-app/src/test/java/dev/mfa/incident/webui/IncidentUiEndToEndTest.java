package dev.mfa.incident.webui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.testsupport.RealKeycloakProfile;
import dev.mfa.incident.testsupport.WorkflowDriver;
import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The web UI in a real browser against a real Keycloak (A-0010, P-0019): code flow login, live
 * update after a decision, session cookie, CSRF header, Content Security Policy and logout.
 */
@QuarkusTest
@TestProfile(RealKeycloakProfile.class)
@WithPlaywright
class IncidentUiEndToEndTest {

  /** Covers the outbox relay interval, the Kafka round trip and the stream (A-0009). */
  private static final Duration LIVE_TIMEOUT = Duration.ofSeconds(30);

  private static final String SESSION_COOKIE = "q_session";
  private static final String PROBLEM_DETAILS = "application/problem+json";

  @InjectPlaywright BrowserContext sharedContext;

  @TestHTTPResource("/")
  URI root;

  @Inject WorkflowDriver workflow;

  private BrowserContext browser;
  private final List<String> consoleErrors = new ArrayList<>();

  @BeforeAll
  static void waitLongerForLiveUpdates() {
    PlaywrightAssertions.setDefaultAssertionTimeout(LIVE_TIMEOUT.toMillis());
  }

  /** A fresh browser profile per test, so no test starts with another test's session. */
  @BeforeEach
  void openBrowser() {
    KeycloakClientRedirects.allow(root);
    // The stream consumer must be assigned its partitions before the test produces events, as it
    // starts at the latest offset (A-0009); readiness includes the channel.
    given().when().get("/q/health/ready").then().statusCode(200);
    Browser chromium = sharedContext.browser();
    browser = chromium.newContext();
    browser.onConsoleMessage(
        message -> {
          if ("error".equals(message.type())) {
            consoleErrors.add(message.text());
          }
        });
  }

  @AfterEach
  void closeBrowser() {
    browser.close();
  }

  @Test
  void approverApprovesAndTheStateChangesLive() {
    IncidentDto pending = workflow.awaitingApproval();
    String row = "incident-" + pending.id();

    Page list = browser.newPage();
    Response first =
        list.navigate(root.resolve("/?state=AWAITING_APPROVAL&state=APPROVED").toString());
    assertThat(first.headers().get("content-security-policy")).contains("script-src 'self';");
    signIn(list, "approver");

    assertThat(list.getByTestId("live-status")).hasText("Live");
    // Before the login the browser logs the expected 401 of GET /api/v1/me; from here on the
    // console must stay free of errors, CSP violations included.
    consoleErrors.clear();
    assertThat(list.getByTestId(row)).hasClass("awaiting-approval");
    assertThat(list.url())
        .isEqualTo(root.resolve("/?state=AWAITING_APPROVAL&state=APPROVED").toString());

    Page detail = browser.newPage();
    detail.navigate(root.resolve("/incidents/" + pending.id()).toString());
    assertThat(detail.getByTestId("incident-state")).hasText("Awaiting approval");
    detail.getByLabel("Comment (optional)").fill("Rollback is safe");
    detail.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Approve")).click();
    assertThat(detail.getByTestId("incident-state")).hasText("Approved");

    // The list page was not touched: the change reached it through the event stream.
    assertThat(list.getByTestId(row)).not().hasClass("awaiting-approval");
    assertThat(list.getByTestId(row)).containsText("Approved");
    assertThat(consoleErrors).as("browser console errors (CSP violations among them)").isEmpty();
  }

  @Test
  void sessionCookieIsProtectedAndSessionRequestsNeedTheCsrfHeader() {
    IncidentDto pending = workflow.awaitingApproval();
    Page page = browser.newPage();
    page.navigate(root.toString());
    signIn(page, "approver");
    assertThat(page.getByTestId("live-status")).hasText("Live");

    List<Cookie> session =
        browser.cookies().stream().filter(c -> c.name.startsWith(SESSION_COOKIE)).toList();
    assertThat(session)
        .isNotEmpty()
        .allSatisfy(
            cookie -> {
              assertThat(cookie.httpOnly).as("HttpOnly").isTrue();
              assertThat(cookie.secure).as("Secure").isTrue();
              assertThat(cookie.sameSite).isEqualTo(SameSiteAttribute.STRICT);
            });
    String cookies =
        session.stream().map(c -> c.name + "=" + c.value).collect(Collectors.joining("; "));

    given()
        .header("Cookie", cookies)
        .contentType(ContentType.JSON)
        .header("If-Match", "\"" + pending.version() + "\"")
        .body("{\"decision\": \"APPROVE\"}")
        .when()
        .post("/api/v1/incidents/" + pending.id() + "/decision")
        .then()
        .statusCode(403)
        .contentType(PROBLEM_DETAILS);
    given()
        .header("Cookie", cookies)
        .when()
        .get("/api/v1/incidents/" + pending.id())
        .then()
        .statusCode(200);
  }

  @Test
  void viewerSeesNoDecisionControlsAndLogoutEndsBothSessions() {
    IncidentDto pending = workflow.awaitingApproval();
    Page page = browser.newPage();
    page.navigate(root.resolve("/incidents/" + pending.id()).toString());
    signIn(page, "viewer");

    assertThat(page.getByTestId("incident-state")).hasText("Awaiting approval");
    assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Approve")))
        .hasCount(0);

    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sign out")).click();

    // Without the Keycloak session the UI cannot sign in silently again: the login form shows.
    assertThat(page.locator("#username")).isVisible();
    assertThat(browser.cookies().stream().map(c -> c.name))
        .noneMatch(n -> n.startsWith(SESSION_COOKIE));
  }

  /** Fills the Keycloak login form the UI sent the browser to. */
  private static void signIn(Page page, String user) {
    try {
      page.locator("#username").waitFor();
    } catch (PlaywrightException e) {
      throw new AssertionError(
          "No login form at " + page.url() + ":\n" + page.locator("body").innerText(), e);
    }
    page.locator("#username").fill(user);
    page.locator("#password").fill(user);
    page.locator("#kc-login").click();
  }
}
