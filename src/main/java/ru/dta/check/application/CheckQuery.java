package ru.dta.check.application;

import java.time.Instant;
import ru.dta.check.domain.RecordType;
import ru.dta.check.domain.CheckStatus;

public record CheckQuery(RecordType recordType, CheckStatus status, Instant from, Instant to, int page, int size) {
}
