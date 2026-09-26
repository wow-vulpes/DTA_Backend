package ru.dta.check.persistence;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.dta.PostgresTestConfiguration;
import ru.dta.check.domain.CheckIssue;
import ru.dta.check.domain.CheckResult;
import ru.dta.check.domain.IssueLevel;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.MaterialTypeDetector;
import ru.dta.check.domain.RecordChecker;
import ru.dta.check.domain.RecordType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class CheckPersistenceTest {

    private static final Instant CHECKED_AT = Instant.parse("2026-09-26T10:00:00.123Z");

    @Autowired
    private CheckRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Flyway flyway;

    private TransactionTemplate transaction;

    @BeforeEach
    void resetData() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM checks");
    }

    @Test
    void migrationIsValidAndNotRepeated() {
        flyway.validate();

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Long.class))
                .isEqualTo(1L);
    }

    @ParameterizedTest
    @EnumSource(RecordType.class)
    void roundTripsCompleteResultInSeparateTransactions(RecordType type) {
        CheckResult result = new RecordChecker(new MaterialTypeDetector()).check(type, List.of(
                new MaterialMetadata("дневник наблюдений.pdf", 1025),
                new MaterialMetadata("отчет о занятии.docx", 200),
                new MaterialMetadata("обратная связь родителя.jpg", 300),
                new MaterialMetadata("заключение специалиста.pdf", 400)));

        UUID id = transaction.execute(status -> repository.saveAndFlush(entity(type, result)).getId());

        transaction.executeWithoutResult(status -> {
            CheckEntity loaded = repository.findById(id).orElseThrow();
            assertThat(loaded.getRecordType()).isEqualTo(type);
            assertThat(loaded.getStatus()).isEqualTo(result.status());
            assertThat(loaded.getCheckedAt()).isEqualTo(CHECKED_AT);
            assertThat(loaded.getStatusLabel()).isEqualTo("Метка результата");
            assertThat(loaded.getReason()).isEqualTo("Причина результата");
            assertThat(loaded.getIssues()).isEmpty();
            assertThat(loaded.getDocuments()).extracting(CheckDocumentEntity::getName)
                    .containsExactlyElementsOf(result.documents().stream()
                            .map(document -> document.metadata().filename()).toList());
            assertThat(loaded.getDocuments()).extracting(CheckDocumentEntity::getPosition).containsExactly(0, 1, 2, 3);
            assertThat(loaded.getDocuments()).extracting(CheckDocumentEntity::getSizeBytes)
                    .containsExactly(1025L, 200L, 300L, 400L);
            assertThat(loaded.getDocuments()).extracting(CheckDocumentEntity::getId).doesNotHaveDuplicates()
                    .doesNotContainNull();
        });
    }

    @Test
    void roundTripsUnknownTypeAndBothKindsOfIssueReferences() {
        CheckResult result = incompleteResult();
        UUID id = transaction.execute(status -> repository.saveAndFlush(entity(RecordType.DAILY, result)).getId());

        transaction.executeWithoutResult(status -> {
            CheckEntity loaded = repository.findById(id).orElseThrow();
            assertThat(loaded.getStatus()).isEqualTo(result.status());
            assertThat(loaded.getDocuments().getFirst().getDetectedType()).isNull();
            assertThat(loaded.getIssues()).extracting(CheckIssueEntity::getMessage)
                    .containsExactlyElementsOf(result.issues().stream().map(CheckIssue::message).toList());
            assertThat(loaded.getIssues()).extracting(CheckIssueEntity::getDocumentIndex)
                    .containsExactly(0, null, null, null);
            assertThat(loaded.getIssues()).extracting(CheckIssueEntity::getPosition).containsExactly(0, 1, 2, 3);
            assertThat(loaded.getIssues()).extracting(CheckIssueEntity::getLevel)
                    .containsExactly(IssueLevel.WARNING, IssueLevel.ERROR, IssueLevel.ERROR, IssueLevel.ERROR);
            assertThat(loaded.getIssues()).extracting(CheckIssueEntity::getId).doesNotHaveDuplicates()
                    .doesNotContainNull();
        });
    }

    @Test
    void repeatedInputCreatesIndependentChecks() {
        CheckResult result = incompleteResult();
        UUID first = transaction.execute(status -> repository.saveAndFlush(entity(RecordType.DAILY, result)).getId());
        UUID second = transaction.execute(status -> repository.saveAndFlush(entity(RecordType.DAILY, result)).getId());

        assertThat(first).isNotNull().isNotEqualTo(second);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(rowCount("check_documents")).isEqualTo(2);
        assertThat(rowCount("check_issues")).isEqualTo(8);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "UPDATE check_documents SET size_bytes = -1 | 23514",
            "UPDATE check_documents SET position = -1 | 23514",
            "UPDATE check_documents SET name = '' | 23514",
            "UPDATE check_documents SET detected_type = 'INVALID' | 23514",
            "UPDATE check_documents SET check_id = '00000000-0000-0000-0000-000000000001' | 23503",
            "UPDATE checks SET record_type = 'INVALID' | 23514",
            "UPDATE checks SET status = 'INVALID' | 23514",
            "UPDATE checks SET checked_at = NULL | 23502",
            "UPDATE checks SET status_label = '' | 23514",
            "UPDATE checks SET reason = '' | 23514",
            "UPDATE check_issues SET level = 'INVALID' | 23514",
            "UPDATE check_issues SET message = '' | 23514",
            "UPDATE check_issues SET document_index = -1 | 23514",
            "UPDATE check_issues SET position = 0 | 23505"
    })
    void databaseRejectsInvalidDataAndRollsBackEntireCheck(String sql, String sqlState) {
        assertSqlFailure(() -> transaction.executeWithoutResult(status -> {
            repository.saveAndFlush(entity(RecordType.DAILY, incompleteResult()));
            jdbc.update(sql);
        }), sqlState);

        assertEmptyDatabase();
    }

    @Test
    void documentPositionMustBeUniqueWithinCheck() {
        assertSqlFailure(() -> transaction.executeWithoutResult(status -> {
            repository.saveAndFlush(entity(RecordType.DAILY, incompleteResult()));
            jdbc.update("""
                    INSERT INTO check_documents(id, check_id, position, name, size_bytes)
                    SELECT ?, check_id, position, name, size_bytes FROM check_documents
                    """, UUID.randomUUID());
        }), "23505");

        assertEmptyDatabase();
    }

    @Test
    void issueCannotReferenceDocumentOfAnotherCheck() {
        UUID first = transaction.execute(status ->
                repository.saveAndFlush(entity(RecordType.DAILY, incompleteResult())).getId());
        CheckResult result = incompleteResult();
        CheckResult invalid = new CheckResult(result.status(), List.of(), List.of(
                new CheckIssue(IssueLevel.WARNING, "Чужой документ", OptionalInt.of(0))));

        assertSqlFailure(() -> transaction.executeWithoutResult(status -> {
            repository.saveAndFlush(entity(RecordType.DAILY, invalid));
            entityManager.clear();
        }), "23503");

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.existsById(first)).isTrue();
        assertThat(rowCount("check_documents")).isEqualTo(1);
        assertThat(rowCount("check_issues")).isEqualTo(4);
    }

    private CheckResult incompleteResult() {
        return new RecordChecker(new MaterialTypeDetector())
                .check(RecordType.DAILY, List.of(new MaterialMetadata("scan.jpg", 100)));
    }

    private CheckEntity entity(RecordType type, CheckResult result) {
        return CheckEntity.fromResult(type, result, CHECKED_AT, "Метка результата", "Причина результата");
    }

    private long rowCount(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private void assertEmptyDatabase() {
        assertThat(repository.count()).isZero();
        assertThat(rowCount("check_documents")).isZero();
        assertThat(rowCount("check_issues")).isZero();
    }

    private void assertSqlFailure(Runnable action, String sqlState) {
        assertThatThrownBy(action::run).satisfies(error -> {
            Throwable cause = error;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo(sqlState);
        });
    }
}
