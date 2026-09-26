package ru.dta.check.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CheckSummaryResponse(
        @JsonProperty("check_id") UUID checkId,
        @JsonProperty("checked_at") Instant checkedAt,
        @JsonProperty("record_type") String recordType,
        String status,
        @JsonProperty("documents_count") long documentsCount) {
}
