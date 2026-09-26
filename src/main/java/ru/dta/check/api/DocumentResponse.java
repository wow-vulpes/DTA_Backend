package ru.dta.check.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentResponse(
        String name,
        @JsonProperty("detected_type") @JsonInclude(JsonInclude.Include.ALWAYS) String detectedType,
        @JsonProperty("size_kb") BigDecimal sizeKb) {
}
