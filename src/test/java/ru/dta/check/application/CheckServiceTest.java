package ru.dta.check.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.MaterialTypeDetector;
import ru.dta.check.domain.RecordChecker;
import ru.dta.check.domain.RecordType;
import ru.dta.check.domain.CheckStatus;
import ru.dta.check.persistence.CheckEntity;
import ru.dta.check.persistence.CheckRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckServiceTest {

    private final CheckRepository repository = mock(CheckRepository.class);
    private final Instant now = Instant.parse("2026-09-26T12:00:00Z");
    private final CheckService service = new CheckService(new RecordChecker(new MaterialTypeDetector()),
            repository, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void savesCompleteResultWithControlledTime() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CheckEntity result = service.createCheck(RecordType.DAILY, List.of(
                new MaterialMetadata("observation diary.pdf", 1024),
                new MaterialMetadata("session report.docx", 2048),
                new MaterialMetadata("parent feedback.png", 1)));
        assertThat(result.getStatus()).isEqualTo(CheckStatus.COMPLETE);
        assertThat(result.getCheckedAt()).isEqualTo(now);
        assertThat(result.getIssues()).isEmpty();
        assertThat(result.getStatusLabel()).isEqualTo("Комплект полный");
        assertThat(result.getReason()).isEqualTo("Все обязательные материалы присутствуют.");
        verify(repository).save(result);
    }

    @Test
    void savesIncompleteResultAsNormalOutcome() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CheckEntity result = service.createCheck(RecordType.WEEKLY,
                List.of(new MaterialMetadata("scan.jpg", 1)));
        assertThat(result.getStatus()).isEqualTo(CheckStatus.INCOMPLETE);
        assertThat(result.getIssues()).hasSize(5);
        assertThat(result.getStatusLabel()).isEqualTo("Комплект неполный");
        verify(repository).save(result);
    }

    @Test
    void doesNotHidePersistenceFailure() {
        RuntimeException failure = new IllegalStateException("database failure");
        when(repository.save(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.createCheck(RecordType.DAILY, List.of())).isSameAs(failure);
    }
}
