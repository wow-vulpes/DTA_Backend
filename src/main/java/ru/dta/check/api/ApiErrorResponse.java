package ru.dta.check.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiErrorResponse(
        String code,
        String message,
        @JsonProperty("field_errors") List<FieldErrorResponse> fieldErrors) {
}
