package ru.dta.check.application;

import java.util.List;

public record CheckListPage(List<CheckSummary> items, int page, int size, long total) {
    public CheckListPage {
        items = List.copyOf(items);
    }
}
