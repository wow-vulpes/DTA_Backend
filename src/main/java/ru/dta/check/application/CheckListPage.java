package ru.dta.check.application;

import java.util.List;

import ru.dta.check.persistence.CheckEntity;

public record CheckListPage(List<CheckEntity> items, int page, int size, long total) {
}
