package ru.dta.check.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ru.dta.PostgresTestConfiguration;
import ru.dta.check.persistence.CheckRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class CheckApiIntegrationTest {

    private static final String BOUNDARY = "dta-test-boundary";
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private CheckRepository repository;

    @MockitoSpyBean
    private Clock clock;

    @BeforeEach
    void clearData() {
        jdbc.update("DELETE FROM checks");
    }

    @ParameterizedTest
    @ValueSource(strings = {"daily", "weekly"})
    void createsCompleteResultAndPersistsAllMetadata(String type) throws Exception {
        doReturn(Instant.parse("2026-09-26T12:00:00.123456789Z")).when(clock).instant();
        List<Upload> files = new ArrayList<>(List.of(new Upload("observation diary.pdf", 1024),
                new Upload("session report.docx", 1), new Upload("parent feedback.png", 1)));
        if (type.equals("weekly")) {
            files.add(new Upload("specialist conclusion.xlsx", 1));
        }
        HttpResponse<String> response = send(type, files);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(response.body());
        UUID id = UUID.fromString(body.path("check_id").asText());
        assertThat(body.path("status").asText()).isEqualTo("complete");
        assertThat(body.path("extracted").isNull()).isTrue();
        assertThat(body.path("issues").size()).isZero();
        assertThat(body.path("documents").size()).isEqualTo(files.size());
        assertThat(body.path("documents").get(0).path("detected_type").asText()).isEqualTo("observation_diary");
        assertThat(body.path("documents").get(0).path("size_kb").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(body.path("checked_at").asText()).endsWith("Z");
        HttpRequest getRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/checks/" + id))
                .timeout(Duration.ofSeconds(10)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> detail = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(json.readTree(detail.body())).isEqualTo(body);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM checks WHERE id = ?", String.class, id))
                .isEqualTo("COMPLETE");
        assertThat(jdbc.queryForList("SELECT name FROM check_documents WHERE check_id = ? ORDER BY position",
                String.class, id)).containsExactlyElementsOf(files.stream().map(Upload::name).toList());
    }

    @Test
    void completeWithWarningAndRepeatedRequestsHaveIndependentIds() throws Exception {
        List<Upload> files = List.of(new Upload("observation diary.pdf", 1), new Upload("session report.pdf", 1),
                new Upload("parent feedback.pdf", 1), new Upload("scan.jpg", 1));
        HttpResponse<String> first = send("daily", files);
        HttpResponse<String> second = send("daily", files);
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(first.body());
        assertThat(body.path("status").asText()).isEqualTo("complete");
        assertThat(body.path("issues").get(0).path("level").asText()).isEqualTo("warning");
        assertThat(body.path("reason").asText()).contains("предупреждения");
        assertThat(body.path("check_id").asText())
                .isNotEqualTo(json.readTree(second.body()).path("check_id").asText());
        assertThat(count("checks")).isEqualTo(2);
        assertThat(count("check_documents")).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 20 * 1024 * 1024 + 1})
    void businessSizeWarningProducesPersistedIncompleteResult(int size) throws Exception {
        HttpResponse<String> response = send("daily", List.of(new Upload("observation diary.pdf", size)));
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("incomplete");
        assertThat(body.path("issues").get(0).path("level").asText()).isEqualTo("warning");
        assertThat(jdbc.queryForObject("SELECT size_bytes FROM check_documents", Long.class)).isEqualTo(size);
    }

    @Test
    void supportsUnicodeFilenameBoundaryThroughRealHttp() throws Exception {
        String accepted = "😀".repeat(251) + ".pdf";
        HttpResponse<String> response = send("daily", List.of(new Upload(accepted, 1)));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json.readTree(response.body()).path("documents").get(0).path("name").asText())
                .isEqualTo(accepted);
        assertThat(send("daily", List.of(new Upload("😀" + accepted, 1))).statusCode()).isEqualTo(422);
        assertThat(count("checks")).isEqualTo(1);
    }

    @Test
    void invalidFieldsDoNotWriteToDatabase() throws Exception {
        assertThat(send("monthly", List.of(new Upload("scan.jpg", 1))).statusCode()).isEqualTo(422);
        assertEmptyDatabase();
    }

    @Test
    void technicalFileLimitIsEnforcedByRealMultipartParser() throws Exception {
        HttpResponse<String> response = send("daily", List.of(new Upload("scan.jpg", 25 * 1024 * 1024 + 1)));
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("payload_too_large");
        assertEmptyDatabase();
    }

    @Test
    void requestLimitIncludesMultipartOverhead() throws Exception {
        List<Upload> files = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            files.add(new Upload("scan.jpg", 24 * 1024 * 1024));
        }
        assertThat(send("daily", files).statusCode()).isEqualTo(413);
        assertEmptyDatabase();
    }

    @Test
    void malformedMultipartHasSafeError() throws Exception {
        HttpResponse<String> response = sendBody("multipart/form-data", HttpRequest.BodyPublishers.ofString("broken"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("invalid_multipart");
        assertEmptyDatabase();
    }

    @Test
    void rollsBackServiceTransactionAfterActualDatabaseInserts() throws Exception {
        AtomicBoolean inserted = new AtomicBoolean();
        doAnswer(invocation -> {
            entityManager.persist(invocation.getArgument(0));
            entityManager.flush();
            assertThat(count("checks")).isEqualTo(1);
            assertThat(count("check_documents")).isEqualTo(1);
            assertThat(count("check_issues")).isEqualTo(4);
            inserted.set(true);
            throw new IllegalStateException("forced failure after flush");
        }).when(repository).save(any());
        HttpResponse<String> response = send("daily", List.of(new Upload("scan.jpg", 1)));
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(inserted).isTrue();
        assertThat(response.body()).doesNotContain("forced failure");
        assertEmptyDatabase();
    }

    @Test
    void unavailableDatabaseReturns503WithoutDetails() throws Exception {
        doThrow(new DataAccessResourceFailureException("secret connection details")).when(repository).save(any());
        HttpResponse<String> response = send("daily", List.of(new Upload("scan.jpg", 1)));
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain("secret");
        assertEmptyDatabase();
    }

    private HttpResponse<String> send(String type, List<Upload> files) throws Exception {
        List<HttpRequest.BodyPublisher> parts = new ArrayList<>();
        parts.add(text("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"record_type\"\r\n\r\n"
                + type + "\r\n"));
        for (Upload file : files) {
            parts.add(text("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
                    + file.name() + "\"\r\nContent-Type: application/octet-stream\r\n\r\n"));
            parts.add(HttpRequest.BodyPublishers.ofByteArray(new byte[file.size()]));
            parts.add(text("\r\n"));
        }
        parts.add(text("--" + BOUNDARY + "--\r\n"));
        return sendBody("multipart/form-data; boundary=" + BOUNDARY,
                HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new)));
    }

    private HttpRequest.BodyPublisher text(String value) {
        return HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> sendBody(String contentType, HttpRequest.BodyPublisher body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/checks"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", contentType).POST(body).build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private void assertEmptyDatabase() {
        assertThat(count("checks")).isZero();
        assertThat(count("check_documents")).isZero();
        assertThat(count("check_issues")).isZero();
    }

    private record Upload(String name, int size) {
    }
}
