package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.sovkora.conversation.application.service.CheckRateLimitService;
import com.krizaka.sovkora.conversation.application.service.DocumentReadService;
import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.DocumentReading;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadRefusal;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadResult;
import com.krizaka.sovkora.conversation.domain.model.reader.ReaderRouting;
import com.krizaka.sovkora.conversation.domain.port.DocumentReader;
import com.krizaka.sovkora.conversation.infrastructure.adapter.persistence.JdbcDocumentReadLedgerAdapter;
import com.krizaka.sovkora.conversation.infrastructure.adapter.persistence.JdbcReaderRoutingStoreAdapter;
import com.krizaka.sovkora.test.support.AbstractContainerIntegrationTest;
import com.krizaka.users.domain.model.User;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code POST /api/v1/document-reads} (krizaka-internal#4198) over the real schema: the Flyway
 * migrations of this service applied to a database of its own, the routing read from the seeded
 * {@code document_reader_setting} rows, one {@code document_read_measurement} line per page.
 *
 * <p>The reader is a stand-in for the worker that reads what a reader would by the routing it is
 * sent: the default of the page's kind. So what is proved here is the door's own work, that the
 * routing it sends is the database's at the time of the call; the worker honouring that routing is
 * proved in {@code workers/reader/test_reader_server.py}, the wire between the two in {@code
 * HttpDocumentReaderAdapterTest}.
 */
@SpringJUnitConfig(DocumentReadControllerIT.NoBeans.class)
class DocumentReadControllerIT extends AbstractContainerIntegrationTest {

  /** The shared base wires the container's ports into the Environment; no bean is needed. */
  @Configuration
  static class NoBeans {}

  private static final String DATABASE = "document_reads_check";
  private static final String MARIE = "11111111-1111-4111-8111-111111111111";
  private static final String PAGE = "UEFHRQ==";
  private static final String BODY =
      "{\"pages\":[{\"image\":\""
          + PAGE
          + "\",\"kind\":\"photo\"},{\"image\":\""
          + PAGE
          + "\",\"kind\":\"pdf-scan\"}]}";

  private static JdbcTemplate jdbc;

  @Autowired private Environment environment;

  /** The routings the stand-in reader was sent, in order. */
  private final List<ReaderRouting> sent = new ArrayList<>();

  /** Reads every page with the default of its kind, at the cost the routing gives that reader. */
  private final DocumentReader byRouting =
      (pages, routing) -> {
        sent.add(routing);
        return new ReadResult.Readings(
            pages.stream().map(page -> readingOf(page, routing)).toList());
      };

  @BeforeEach
  void migrate() throws Exception {
    if (jdbc != null) {
      return;
    }
    String url = environment.getRequiredProperty("spring.datasource.url");
    String user = environment.getRequiredProperty("spring.datasource.username");
    String password = environment.getRequiredProperty("spring.datasource.password");
    String admin = url.substring(0, url.lastIndexOf('/') + 1) + "postgres";
    String own = url.substring(0, url.lastIndexOf('/') + 1) + DATABASE;
    try (Connection connection = DriverManager.getConnection(admin, user, password);
        Statement statement = connection.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
      statement.execute("CREATE DATABASE " + DATABASE);
    }
    Flyway.configure()
        .dataSource(own, user, password)
        .locations("classpath:db/migration")
        .table("flyway_history_conversation")
        .load()
        .migrate();
    jdbc = new JdbcTemplate(new DriverManagerDataSource(own, user, password));
  }

  @AfterEach
  void reset() {
    SecurityContextHolder.clearContext();
    jdbc.execute("TRUNCATE document_read_measurement");
    jdbc.update(
        "UPDATE document_reader_setting SET setting_value = 'paddleocr', updated_by = 'test'"
            + " WHERE setting_key = 'reader.default.photo' AND setting_value <> 'paddleocr'");
  }

  @Test
  @DisplayName("no signed principal: 401, no reader is called and nothing is recorded")
  void anUnauthenticatedCallIsRefused() throws Exception {
    mvc(30, byRouting).perform(read(BODY)).andExpect(status().isUnauthorized());
    assertThat(sent).isEmpty();
    assertThat(measurements()).isZero();
  }

  @Test
  @DisplayName("a signed principal without the role: 403, no reader is called")
  void aCallerWithoutTheRoleIsRefused() throws Exception {
    authenticate("ROLE_USER");
    mvc(30, byRouting).perform(read(BODY)).andExpect(status().isForbidden());
    assertThat(sent).isEmpty();
    assertThat(measurements()).isZero();
  }

  @Test
  @DisplayName("an authenticated call is routed by the seeded rows, one reading per page in order")
  void anAuthenticatedCallIsRoutedByTheSeededSetting() throws Exception {
    authenticate("ROLE_SERVICE");
    mvc(30, byRouting)
        .perform(read(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readings.length()").value(2))
        .andExpect(jsonPath("$.readings[0].reader").value("paddleocr"))
        .andExpect(jsonPath("$.readings[0].lines[0].box.left").value(0.1))
        .andExpect(jsonPath("$.readings[1].reader").value("paddleocr"));
    assertThat(sent).hasSize(1);
    assertThat(sent.getFirst().settings())
        .containsEntry("reader.default.photo", "paddleocr")
        .containsEntry("reader.fallback", "tesseract")
        .containsEntry("reader.page-budget", "12")
        .containsEntry("reader.cost-micros.textract", "1500");
  }

  @Test
  @DisplayName("changing the setting row changes the reader of the next call, with no restart")
  void changingTheRowChangesTheReader() throws Exception {
    authenticate("ROLE_SERVICE");
    MockMvc mvc = mvc(30, byRouting);
    mvc.perform(read(BODY)).andExpect(jsonPath("$.readings[0].reader").value("paddleocr"));

    jdbc.update(
        "UPDATE document_reader_setting SET setting_value = 'tesseract', updated_by = 'operator-1'"
            + " WHERE setting_key = 'reader.default.photo'");

    mvc.perform(read(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readings[0].reader").value("tesseract"))
        .andExpect(jsonPath("$.readings[1].reader").value("paddleocr"));
    assertThat(
            jdbc.queryForMap(
                "SELECT old_value, new_value, changed_by FROM document_reader_setting_history"
                    + " WHERE setting_key = 'reader.default.photo' AND changed_by = 'operator-1'"))
        .containsEntry("old_value", "paddleocr")
        .containsEntry("new_value", "tesseract");
  }

  @Test
  @DisplayName("each page read records its reader, version, cost and latency, under the tenant")
  void costAndLatencyAreRecordedPerCall() throws Exception {
    authenticate("ROLE_SERVICE");
    jdbc.update(
        "UPDATE document_reader_setting SET setting_value = 'textract', updated_by = 'test'"
            + " WHERE setting_key = 'reader.default.photo'");
    mvc(30, byRouting).perform(read(BODY)).andExpect(status().isOk());

    List<Map<String, Object>> lines =
        jdbc.queryForList(
            "SELECT tenant_id, actor_id, page_kind, reader, reader_version, cost_micros,"
                + " latency_ms, fallback_from FROM document_read_measurement ORDER BY id");
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0))
        .containsEntry("tenant_id", "acme")
        .containsEntry("actor_id", MARIE)
        .containsEntry("page_kind", "photo")
        .containsEntry("reader", "textract")
        .containsEntry("reader_version", "textract-1")
        .containsEntry("cost_micros", 1500L)
        .containsEntry("latency_ms", 42L);
    assertThat(lines.get(1))
        .containsEntry("page_kind", "pdf-scan")
        .containsEntry("reader", "paddleocr")
        .containsEntry("cost_micros", 0L);
  }

  @Test
  @DisplayName("a reader's refusal is its code and status, and nothing is recorded")
  void aRefusalIsItsCodeAndNothingIsRecorded() throws Exception {
    authenticate("ROLE_SERVICE");
    DocumentReader down = (pages, routing) -> new ReadResult.Refused(ReadRefusal.READER_TIMEOUT);
    mvc(30, down)
        .perform(read(BODY))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("READER_TIMEOUT"));
    assertThat(measurements()).isZero();
  }

  @Test
  @DisplayName("a malformed page is a 400 before any reader is called")
  void aMalformedPageIsRefused() throws Exception {
    authenticate("ROLE_SERVICE");
    MockMvc mvc = mvc(30, byRouting);
    mvc.perform(read("{\"pages\":[]}")).andExpect(status().isBadRequest());
    mvc.perform(read("{\"pages\":[{\"image\":\"" + PAGE + "\",\"kind\":\"poster\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INPUT_INVALID"));
    mvc.perform(read("{\"pages\":[{\"image\":\"not base64!\",\"kind\":\"photo\"}]}"))
        .andExpect(status().isBadRequest());
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName("over the limit: 429 with Retry-After")
  void overTheLimitIs429() throws Exception {
    authenticate("ROLE_SERVICE");
    MockMvc mvc = mvc(1, byRouting);
    mvc.perform(read(BODY)).andExpect(status().isOk());
    mvc.perform(read(BODY))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  private static DocumentReading readingOf(DocumentPage page, ReaderRouting routing) {
    String reader = routing.settings().get("reader.default." + page.kind().wire());
    long cost =
        Long.parseLong(routing.settings().getOrDefault("reader.cost-micros." + reader, "0"));
    return new DocumentReading(
        "TOTAL 1234.56",
        List.of(new DocumentReading.Line("TOTAL 1234.56", 0.1, 0.4, 0.5, 0.5, 0.97)),
        reader,
        reader + "-1",
        false,
        "0".repeat(64),
        "0".repeat(64) + ":" + reader + ":" + reader + "-1",
        cost,
        42,
        null);
  }

  private static long measurements() {
    Long count = jdbc.queryForObject("SELECT count(*) FROM document_read_measurement", Long.class);
    return count == null ? 0 : count;
  }

  private static RequestBuilder read(String body) {
    return post("/api/v1/document-reads").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static MockMvc mvc(int perMinute, DocumentReader reader) {
    DocumentReadService service =
        new DocumentReadService(
            reader,
            new JdbcReaderRoutingStoreAdapter(jdbc),
            new JdbcDocumentReadLedgerAdapter(jdbc),
            new CheckRateLimitService(perMinute, Clock.systemUTC()),
            Set.of("ROLE_ADMIN", "ROLE_SERVICE"));
    return MockMvcBuilders.standaloneSetup(new DocumentReadController(service))
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .build();
  }

  /** Puts one signed-in user of the tenant {@code acme} in the security context. */
  private static void authenticate(String role) {
    Set<String> authorities = Set.of(role, "TENANT_acme");
    User user =
        new User(
            UUID.fromString(MARIE),
            "actor-" + MARIE,
            MARIE + "@sovkora.test",
            true,
            authorities,
            Map.of(),
            List.of());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            user, "token", authorities.stream().map(SimpleGrantedAuthority::new).toList()));
    SecurityContextHolder.setContext(context);
  }
}
