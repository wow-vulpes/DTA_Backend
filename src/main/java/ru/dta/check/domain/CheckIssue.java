package ru.dta.check.domain;

import java.util.OptionalInt;
import java.util.Objects;

/** Индекс документа начинается с нуля; для нарушения комплектности он отсутствует. */
public record CheckIssue(IssueLevel level, String message, OptionalInt documentIndex) {

    public CheckIssue {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(documentIndex, "documentIndex");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (documentIndex.isPresent() && documentIndex.getAsInt() < 0) {
            throw new IllegalArgumentException("documentIndex must not be negative");
        }
    }
}
