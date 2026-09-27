package ru.dta.check.application;

import java.time.Instant;
import java.util.UUID;
import ru.dta.check.domain.RecordType;
import ru.dta.check.domain.CheckStatus;

public record CheckSummary(UUID checkId, Instant checkedAt, RecordType recordType,
        CheckStatus status, long documentsCount) {
}
