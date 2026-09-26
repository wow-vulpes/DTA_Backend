package ru.dta.check.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CheckResponse(
        @JsonProperty("check_id") UUID checkId,
        String status,
        @JsonProperty("status_label") String statusLabel,
        String reason,
        List<IssueResponse> issues,
        List<DocumentResponse> documents,
        @JsonInclude(JsonInclude.Include.ALWAYS) Void extracted,
        @JsonProperty("checked_at") Instant checkedAt) {
}
