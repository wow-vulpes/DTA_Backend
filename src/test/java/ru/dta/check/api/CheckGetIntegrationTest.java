package ru.dta.check.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ru.dta.PostgresTestConfiguration;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.MaterialTypeDetector;
import ru.dta.check.domain.RecordChecker;
import ru.dta.check.domain.RecordType;
import ru.dta.check.persistence.CheckEntity;
import ru.dta.check.persistence.CheckRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class CheckGetIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CheckRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void clearData() {
        jdbc.update("DELETE FROM checks");
    }

    @Test
    void listsNewestFirstWithPaginationAndFilters() throws Exception {
        save(RecordType.DAILY, Instant.parse("2026-09-26T12:00:00Z"), "COMPLETE");
        UUID newest = save(RecordType.WEEKLY, Instant.parse("2026-09-26T13:00:00Z"), "INCOMPLETE");
        save(RecordType.DAILY, Instant.parse("2026-09-26T11:00:00Z"), "COMPLETE");

        mvc.perform(get("/api/checks").param("record_type", "weekly").param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].check_id").value(newest.toString()))
                .andExpect(jsonPath("$.items[0].record_type").value("weekly"))
                .andExpect(jsonPath("$.items[0].status").value("incomplete"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void filtersByTimeRangeAndReturnsEmptyPage() throws Exception {
        save(RecordType.DAILY, Instant.parse("2026-09-26T12:00:00Z"), "COMPLETE");
        mvc.perform(get("/api/checks").param("from", "2026-09-27T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void returnsFullDetailAndNotFound() throws Exception {
        UUID id = save(RecordType.DAILY, Instant.parse("2026-09-26T12:00:00Z"), "INCOMPLETE");
        mvc.perform(get("/api/checks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.check_id").value(id.toString()))
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].name").value("scan.jpg"));

        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/checks/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("check_not_found"))
                .andExpect(jsonPath("$.message").value(containsString(missing.toString())));
    }

    @Test
    void rejectsInvalidQuery() throws Exception {
        mvc.perform(get("/api/checks").param("size", "101"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    private UUID save(RecordType type, Instant checkedAt, String status) {
        List<MaterialMetadata> materials = new ArrayList<>();
        if (status.equals("COMPLETE")) {
            materials.add(new MaterialMetadata("observation diary.pdf", 100));
            materials.add(new MaterialMetadata("session report.docx", 200));
            materials.add(new MaterialMetadata("parent feedback.png", 300));
            if (type == RecordType.WEEKLY) {
                materials.add(new MaterialMetadata("specialist conclusion.xlsx", 400));
            }
        } else {
            materials.add(new MaterialMetadata("scan.jpg", 100));
        }
        CheckEntity entity = CheckEntity.fromResult(type,
                new RecordChecker(new MaterialTypeDetector()).check(type, materials),
                checkedAt, status.equals("COMPLETE") ? "Комплект полный" : "Комплект неполный",
                status.equals("COMPLETE") ? "Готово." : "Нет материалов.");
        return repository.saveAndFlush(entity).getId();
    }

    @Test
    void listsAcrossPagesWithCorrectCounts() throws Exception {
        Instant noon = Instant.parse("2026-09-26T12:00:00Z");
        UUID old = save(RecordType.DAILY, noon.minusSeconds(1), "INCOMPLETE");
        UUID middle = save(RecordType.WEEKLY, noon, "COMPLETE");
        UUID newest = save(RecordType.DAILY, noon.plusSeconds(1), "COMPLETE");
        mvc.perform(get("/api/checks").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].check_id").value(newest.toString()))
                .andExpect(jsonPath("$.items[0].documents_count").value(3))
                .andExpect(jsonPath("$.items[1].check_id").value(middle.toString()))
                .andExpect(jsonPath("$.items[1].documents_count").value(4));
        mvc.perform(get("/api/checks").param("size", "2").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].check_id").value(old.toString()));
        mvc.perform(get("/api/checks").param("page", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void sortsEqualTimestampsByUuidDescending() throws Exception {
        Instant noon = Instant.parse("2026-09-26T12:00:00Z");
        UUID first = save(RecordType.DAILY, noon, "INCOMPLETE");
        UUID second = save(RecordType.DAILY, noon, "INCOMPLETE");
        List<String> expected = List.of(first.toString(), second.toString()).stream()
                .sorted(Comparator.reverseOrder()).toList();
        mvc.perform(get("/api/checks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].check_id").value(expected.get(0)))
                .andExpect(jsonPath("$.items[1].check_id").value(expected.get(1)));
    }

    @Test
    void combinesFiltersWithInclusiveFromAndExclusiveTo() throws Exception {
        Instant noon = Instant.parse("2026-09-26T12:00:00Z");
        save(RecordType.DAILY, noon, "COMPLETE");
        save(RecordType.WEEKLY, noon, "INCOMPLETE");
        save(RecordType.WEEKLY, noon.minusSeconds(1), "COMPLETE");
        UUID included = save(RecordType.WEEKLY, noon, "COMPLETE");
        save(RecordType.WEEKLY, noon.plusSeconds(1), "COMPLETE");
        mvc.perform(get("/api/checks").param("record_type", "weekly").param("status", "complete")
                        .param("from", noon.toString()).param("to", noon.plusSeconds(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].check_id").value(included.toString()));
    }

    @ParameterizedTest
    @CsvSource({"record_type,weekly", "status,complete", "from,2026-09-26T12:00:00Z",
            "to,2026-09-26T12:00:00Z"})
    void eachOptionalFilterWorksAlone(String field, String value) throws Exception {
        Instant noon = Instant.parse("2026-09-26T12:00:00Z");
        save(RecordType.DAILY, noon.minusSeconds(1), "INCOMPLETE");
        save(RecordType.WEEKLY, noon, "COMPLETE");
        mvc.perform(get("/api/checks").param(field, value))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listDoesNotFetchDocumentOrIssueCollections() throws Exception {
        for (int index = 0; index < 5; index++) {
            save(RecordType.DAILY, Instant.EPOCH.plusSeconds(index), "INCOMPLETE");
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get("/api/checks").param("size", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items[0].documents_count").value(1));
        assertThat(statistics.getCollectionFetchCount()).isZero();
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"size,0,422", "page,-1,422", "page,hello,400", "from,yesterday,400",
            "record_type,monthly,422", "status,unknown,422"})
    void invalidParametersHaveClientErrors(String field, String value, int expected) throws Exception {
        mvc.perform(get("/api/checks").param(field, value))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.field_errors").isArray());
    }

    @Test
    void rejectsMalformedUuidAndReversedRange() throws Exception {
        mvc.perform(get("/api/checks/not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_parameter"));
        mvc.perform(get("/api/checks").param("from", "2026-09-26T12:00:00Z")
                        .param("to", "2026-09-26T12:00:00Z"))
                .andExpect(status().is(422));
    }
}
