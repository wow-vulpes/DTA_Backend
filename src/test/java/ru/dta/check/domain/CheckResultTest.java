package ru.dta.check.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckResultTest {

    @Test
    void resultOwnsImmutableCopiesOfLists() {
        CheckedMaterial document = new CheckedMaterial(new MaterialMetadata("scan.jpg", 1), Optional.empty());
        CheckIssue issue = new CheckIssue(IssueLevel.WARNING, "Неизвестное имя", OptionalInt.of(0));
        List<CheckedMaterial> documents = new ArrayList<>(List.of(document));
        List<CheckIssue> issues = new ArrayList<>(List.of(issue));
        CheckResult result = new CheckResult(CheckStatus.COMPLETE, documents, issues);

        documents.clear();
        issues.clear();

        assertThat(result.documents()).containsExactly(document);
        assertThat(result.issues()).containsExactly(issue);
        assertThatThrownBy(() -> result.documents().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.issues().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("nullFields")
    void rejectsNullFieldsAndListElements(Runnable construction) {
        assertThatThrownBy(construction::run).isInstanceOf(NullPointerException.class);
    }

    static Stream<Runnable> nullFields() {
        return Stream.of(
                () -> new CheckResult(null, List.of(), List.of()),
                () -> new CheckResult(CheckStatus.COMPLETE, null, List.of()),
                () -> new CheckResult(CheckStatus.COMPLETE, List.of(), null),
                () -> new CheckResult(CheckStatus.COMPLETE, Arrays.asList((CheckedMaterial) null), List.of()),
                () -> new CheckResult(CheckStatus.COMPLETE, List.of(), Arrays.asList((CheckIssue) null)),
                () -> new CheckedMaterial(null, Optional.empty()),
                () -> new CheckedMaterial(new MaterialMetadata("scan.jpg", 1), null),
                () -> new CheckIssue(null, "message", OptionalInt.empty()),
                () -> new CheckIssue(IssueLevel.WARNING, null, OptionalInt.empty()),
                () -> new CheckIssue(IssueLevel.WARNING, "message", null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlankIssueMessage(String message) {
        assertThatThrownBy(() -> new CheckIssue(IssueLevel.WARNING, message, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void rejectsNegativeDocumentIndex(int index) {
        assertThatThrownBy(() -> new CheckIssue(IssueLevel.WARNING, "message", OptionalInt.of(index)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
