package ru.dta.check.api;

import java.util.List;

public record CheckListResponse(List<CheckSummaryResponse> items, int page, int size, long total) {
}
