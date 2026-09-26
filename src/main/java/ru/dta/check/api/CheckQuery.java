package ru.dta.check.api;

import java.time.Instant;

public record CheckQuery(String recordType, String status, Instant from, Instant to, int page, int size) {
}
