package ru.dta.check.domain;

import java.util.List;
import java.util.Objects;

public record CheckResult(CheckStatus status, List<CheckedMaterial> documents, List<CheckIssue> issues) {

    public CheckResult {
        Objects.requireNonNull(status, "status");
        documents = List.copyOf(documents);
        issues = List.copyOf(issues);
    }
}
