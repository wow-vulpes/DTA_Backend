package ru.dta.check.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class RecordCheckerResultTest {

    private final RecordChecker checker = new RecordChecker(new MaterialTypeDetector());

    @ParameterizedTest
    @EnumSource(RecordType.class)
    void completeSetWithMultipleWarningsRemainsComplete(RecordType type) {
        List<MaterialMetadata> materials = new ArrayList<>(List.of(
                new MaterialMetadata("дневник наблюдений.pdf", 1),
                new MaterialMetadata("отчет о занятии.docx", 1),
                new MaterialMetadata("обратная связь родителя.jpg", 1),
                new MaterialMetadata("заключение специалиста.pdf", 1),
                new MaterialMetadata("scan.exe", 20971521)));

        CheckResult result = checker.check(type, materials);

        assertThat(result.status()).isEqualTo(CheckStatus.COMPLETE);
        assertThat(result.issues()).containsExactly(
                warning("Недопустимый формат файла: «scan.exe»", 4),
                warning("Размер файла превышает 20 МБ: «scan.exe»", 4),
                warning("Не удалось определить тип материала: «scan.exe»", 4));
    }

    @Test
    void collectsAllViolationsAndMissingTypesInStableOrder() {
        List<MaterialMetadata> materials = List.of(
                new MaterialMetadata("scan.exe", 20971521),
                new MaterialMetadata("отчет о занятии.pdf", 0));

        CheckResult result = checker.check(RecordType.WEEKLY, materials);

        assertThat(result.status()).isEqualTo(CheckStatus.INCOMPLETE);
        assertThat(result.issues()).containsExactly(
                warning("Недопустимый формат файла: «scan.exe»", 0),
                warning("Размер файла превышает 20 МБ: «scan.exe»", 0),
                warning("Не удалось определить тип материала: «scan.exe»", 0),
                warning("Пустой файл: «отчет о занятии.pdf»", 1),
                missing("дневник наблюдений"),
                missing("отчёт о занятии"),
                missing("обратная связь родителя"),
                missing("заключение специалиста"));
        assertThat(checker.check(RecordType.WEEKLY, materials)).isEqualTo(result);
    }

    @Test
    void keepsDocumentOrderAndOriginalMetadataWithoutChangingInput() {
        List<MaterialMetadata> materials = new ArrayList<>(List.of(
                new MaterialMetadata("SCAN.jpg", 123),
                new MaterialMetadata("Обратная_связь_родителя.PNG", 456),
                new MaterialMetadata("дневник наблюдений.xlsx", 789)));
        List<MaterialMetadata> original = List.copyOf(materials);

        CheckResult result = checker.check(RecordType.DAILY, materials);

        assertThat(materials).containsExactlyElementsOf(original);
        assertThat(result.documents()).extracting(CheckedMaterial::metadata).containsExactlyElementsOf(original);
        assertThat(result.documents().get(0).detectedType()).isEmpty();
        assertThat(result.documents().get(1).detectedType()).contains(MaterialType.PARENT_FEEDBACK);
        assertThat(result.documents().get(2).detectedType()).contains(MaterialType.OBSERVATION_DIARY);
        materials.clear();
        assertThat(result.documents()).extracting(CheckedMaterial::metadata).containsExactlyElementsOf(original);
    }

    @Test
    void independentChecksDoNotRetainPreviousMaterialsOrIssues() {
        List<MaterialMetadata> complete = List.of(
                new MaterialMetadata("дневник наблюдений.pdf", 1),
                new MaterialMetadata("отчет о занятии.pdf", 1),
                new MaterialMetadata("обратная связь родителя.pdf", 1));

        CheckResult first = checker.check(RecordType.DAILY, complete);
        CheckResult empty = checker.check(RecordType.DAILY, List.of());
        CheckResult repeated = checker.check(RecordType.DAILY, complete);

        assertThat(first.status()).isEqualTo(CheckStatus.COMPLETE);
        assertThat(empty.status()).isEqualTo(CheckStatus.INCOMPLETE);
        assertThat(empty.documents()).isEmpty();
        assertThat(empty.issues()).hasSize(3);
        assertThat(repeated).isEqualTo(first);
    }

    private CheckIssue warning(String message, int index) {
        return new CheckIssue(IssueLevel.WARNING, message, OptionalInt.of(index));
    }

    private CheckIssue missing(String label) {
        return new CheckIssue(IssueLevel.ERROR, "Отсутствует обязательный материал: " + label, OptionalInt.empty());
    }
}
