package ru.dta.check.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;
import ru.dta.check.domain.CheckIssue;
import ru.dta.check.domain.CheckResult;
import ru.dta.check.domain.CheckStatus;
import ru.dta.check.domain.CheckedMaterial;
import ru.dta.check.domain.IssueLevel;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.MaterialType;
import ru.dta.check.domain.RecordType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckEntityTest {

    @Test
    void preservesResultAndConnectsChildrenToSameCheck() {
        Instant time = Instant.parse("2026-09-26T10:00:00Z");
        CheckResult result = new CheckResult(CheckStatus.INCOMPLETE, List.of(
                new CheckedMaterial(new MaterialMetadata("дневник наблюдений.pdf", 1025),
                        Optional.of(MaterialType.OBSERVATION_DIARY)),
                new CheckedMaterial(new MaterialMetadata("scan.png", 0), Optional.empty())), List.of(
                        new CheckIssue(IssueLevel.WARNING, "Пустой файл", OptionalInt.of(1)),
                        new CheckIssue(IssueLevel.ERROR, "Нет отчёта", OptionalInt.empty())));

        CheckEntity entity = CheckEntity.fromResult(RecordType.WEEKLY, result, time, "Неполная", "Нет отчёта");

        assertThat(entity.getRecordType()).isEqualTo(RecordType.WEEKLY);
        assertThat(entity.getStatus()).isEqualTo(CheckStatus.INCOMPLETE);
        assertThat(entity.getCheckedAt()).isEqualTo(time);
        assertThat(entity.getStatusLabel()).isEqualTo("Неполная");
        assertThat(entity.getReason()).isEqualTo("Нет отчёта");
        assertThat(entity.getDocuments()).extracting(CheckDocumentEntity::getPosition).containsExactly(0, 1);
        assertThat(entity.getDocuments()).extracting(CheckDocumentEntity::getName)
                .containsExactly("дневник наблюдений.pdf", "scan.png");
        assertThat(entity.getDocuments()).extracting(CheckDocumentEntity::getSizeBytes).containsExactly(1025L, 0L);
        assertThat(entity.getDocuments()).extracting(CheckDocumentEntity::getDetectedType)
                .containsExactly(MaterialType.OBSERVATION_DIARY, null);
        assertThat(entity.getIssues()).extracting(CheckIssueEntity::getDocumentIndex).containsExactly(1, null);
        assertThat(entity.getIssues()).extracting(CheckIssueEntity::getLevel)
                .containsExactly(IssueLevel.WARNING, IssueLevel.ERROR);
        assertThat(entity.getDocuments()).allSatisfy(document -> assertThat(document.getCheck()).isSameAs(entity));
        assertThat(entity.getIssues()).allSatisfy(issue -> assertThat(issue.getCheck()).isSameAs(entity));
        assertThatThrownBy(() -> entity.getDocuments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> entity.getIssues().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
