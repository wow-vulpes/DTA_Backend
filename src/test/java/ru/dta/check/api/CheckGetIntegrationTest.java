package ru.dta.check.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class CheckGetIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CheckRepository repository;

    @BeforeEach
    void clearData() {
        repository.deleteAll();
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
        CheckEntity entity = CheckEntity.fromResult(type,
                new RecordChecker(new MaterialTypeDetector()).check(type,
                        List.of(new MaterialMetadata("scan.jpg", 100))),
                checkedAt, status.equals("COMPLETE") ? "Комплект полный" : "Комплект неполный",
                status.equals("COMPLETE") ? "Готово." : "Нет материалов.");
        return repository.saveAndFlush(entity).getId();
    }
}
