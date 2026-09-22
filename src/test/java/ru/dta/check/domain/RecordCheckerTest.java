package ru.dta.check.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordCheckerTest {

    private final RecordChecker checker = new RecordChecker(new MaterialTypeDetector());

    @ParameterizedTest
    @EnumSource(RecordType.class)
    void acceptsCompleteRecord(RecordType type) {
        CheckResult result = checker.check(type, completeMaterials(type));

        assertThat(result.status()).isEqualTo(CheckStatus.COMPLETE);
        assertThat(result.issues()).isEmpty();
        assertThat(result.documents()).hasSize(type == RecordType.DAILY ? 3 : 4);
    }

    @ParameterizedTest
    @CsvSource({
            "DAILY, 0, дневник наблюдений",
            "DAILY, 1, отчёт о занятии",
            "DAILY, 2, обратная связь родителя",
            "WEEKLY, 0, дневник наблюдений",
            "WEEKLY, 1, отчёт о занятии",
            "WEEKLY, 2, обратная связь родителя",
            "WEEKLY, 3, заключение специалиста"
    })
    void reportsEachMissingRequiredMaterial(RecordType type, int missingIndex, String label) {
        List<MaterialMetadata> materials = completeMaterials(type);
        materials.remove(missingIndex);

        CheckResult result = checker.check(type, materials);

        assertThat(result.status()).isEqualTo(CheckStatus.INCOMPLETE);
        assertThat(result.issues()).containsExactly(new CheckIssue(IssueLevel.ERROR,
                "Отсутствует обязательный материал: " + label, OptionalInt.empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "docx", "xlsx", "jpg", "png", "PDF", "DoCx", "XLSX", "JPG", "PNG"})
    void acceptsAllowedExtensionsIgnoringCase(String extension) {
        List<MaterialMetadata> materials = completeMaterials(RecordType.DAILY);
        materials.set(0, new MaterialMetadata("дневник наблюдений." + extension, 1));

        assertThat(checker.check(RecordType.DAILY, materials).issues()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"дневник наблюдений.exe", "дневник наблюдений.pdf.exe",
            "дневник наблюдений", "дневник наблюдений.", "дневник наблюдений.jpeg"})
    void excludesInvalidFormatFromCompleteness(String filename) {
        List<MaterialMetadata> materials = completeMaterials(RecordType.DAILY);
        materials.set(0, new MaterialMetadata(filename, 1));

        CheckResult result = checker.check(RecordType.DAILY, materials);

        assertThat(result.status()).isEqualTo(CheckStatus.INCOMPLETE);
        assertThat(result.issues()).containsExactly(
                new CheckIssue(IssueLevel.WARNING, "Недопустимый формат файла: «" + filename + "»", OptionalInt.of(0)),
                new CheckIssue(IssueLevel.ERROR,
                        "Отсутствует обязательный материал: дневник наблюдений", OptionalInt.empty()));
        assertThat(result.documents().getFirst().detectedType()).contains(MaterialType.OBSERVATION_DIARY);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 20971519, 20971520})
    void acceptsSizeUpToLimit(long size) {
        List<MaterialMetadata> materials = completeMaterials(RecordType.DAILY);
        materials.set(0, new MaterialMetadata("дневник наблюдений.pdf", size));

        assertThat(checker.check(RecordType.DAILY, materials).issues()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {20971521, Long.MAX_VALUE})
    void excludesOversizedMaterial(long size) {
        assertInvalidSize(size, "Размер файла превышает 20 МБ: «дневник наблюдений.pdf»");
    }

    @Test
    void excludesEmptyMaterial() {
        assertInvalidSize(0, "Пустой файл: «дневник наблюдений.pdf»");
    }

    @ParameterizedTest
    @ValueSource(strings = {"scan_0041.jpg", "дневник наблюдений отчет о занятии.pdf"})
    void unknownOrAmbiguousNameDoesNotReplaceRequiredMaterial(String filename) {
        List<MaterialMetadata> materials = completeMaterials(RecordType.DAILY);
        materials.set(0, new MaterialMetadata(filename, 1));

        CheckResult result = checker.check(RecordType.DAILY, materials);

        assertThat(result.documents().getFirst().detectedType()).isEmpty();
        assertThat(result.issues()).containsExactly(
                new CheckIssue(IssueLevel.WARNING,
                        "Не удалось определить тип материала: «" + filename + "»", OptionalInt.of(0)),
                new CheckIssue(IssueLevel.ERROR,
                        "Отсутствует обязательный материал: дневник наблюдений", OptionalInt.empty()));
    }

    @ParameterizedTest
    @EnumSource(RecordType.class)
    void emptyListReportsAllRequiredMaterials(RecordType type) {
        CheckResult result = checker.check(type, List.of());

        assertThat(result.status()).isEqualTo(CheckStatus.INCOMPLETE);
        assertThat(result.documents()).isEmpty();
        assertThat(result.issues()).hasSize(type == RecordType.DAILY ? 3 : 4)
                .allSatisfy(issue -> {
                    assertThat(issue.level()).isEqualTo(IssueLevel.ERROR);
                    assertThat(issue.documentIndex()).isEmpty();
                });
    }

    @Test
    void duplicateCannotReplaceAnotherType() {
        List<MaterialMetadata> materials = completeMaterials(RecordType.DAILY);
        materials.set(2, materials.getFirst());

        assertThat(checker.check(RecordType.DAILY, materials).issues()).containsExactly(
                new CheckIssue(IssueLevel.ERROR,
                        "Отсутствует обязательный материал: обратная связь родителя", OptionalInt.empty()));
    }

    @Test
    void acceptsDuplicatesAndOptionalSpecialistConclusion() {
        List<MaterialMetadata> materials = completeMaterials(RecordType.WEEKLY);
        materials.add(materials.getFirst());

        CheckResult result = checker.check(RecordType.DAILY, materials);

        assertThat(result.issues()).isEmpty();
        assertThat(result.documents()).hasSize(5);
    }

    @Test
    void validCopySatisfiesRequirementDespiteInvalidCopy() {
        List<MaterialMetadata> materials = completeMaterials(RecordType.DAILY);
        materials.add(new MaterialMetadata("дневник наблюдений.exe", 1));

        CheckResult result = checker.check(RecordType.DAILY, materials);

        assertThat(result.status()).isEqualTo(CheckStatus.COMPLETE);
        assertThat(result.issues()).containsExactly(new CheckIssue(IssueLevel.WARNING,
                "Недопустимый формат файла: «дневник наблюдений.exe»", OptionalInt.of(3)));
    }

    @Test
    void rejectsNullRecordType() {
        assertThatThrownBy(() -> checker.check(null, List.of())).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullMaterials() {
        assertThatThrownBy(() -> checker.check(RecordType.DAILY, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullMaterialElement() {
        List<MaterialMetadata> materials = Arrays.asList(new MaterialMetadata("file.pdf", 1), null);

        assertThatThrownBy(() -> checker.check(RecordType.DAILY, materials)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullDetector() {
        assertThatThrownBy(() -> new RecordChecker(null)).isInstanceOf(NullPointerException.class);
    }

    private void assertInvalidSize(long size, String warning) {
        List<MaterialMetadata> materials = completeMaterials(RecordType.DAILY);
        materials.set(0, new MaterialMetadata("дневник наблюдений.pdf", size));

        CheckResult result = checker.check(RecordType.DAILY, materials);

        assertThat(result.status()).isEqualTo(CheckStatus.INCOMPLETE);
        assertThat(result.issues()).containsExactly(
                new CheckIssue(IssueLevel.WARNING, warning, OptionalInt.of(0)),
                new CheckIssue(IssueLevel.ERROR,
                        "Отсутствует обязательный материал: дневник наблюдений", OptionalInt.empty()));
    }

    private List<MaterialMetadata> completeMaterials(RecordType type) {
        List<MaterialMetadata> materials = new ArrayList<>(List.of(
                new MaterialMetadata("дневник наблюдений.pdf", 100),
                new MaterialMetadata("отчет о занятии.docx", 200),
                new MaterialMetadata("обратная связь родителя.jpg", 300)));
        if (type == RecordType.WEEKLY) {
            materials.add(new MaterialMetadata("заключение специалиста.pdf", 400));
        }
        return materials;
    }
}
