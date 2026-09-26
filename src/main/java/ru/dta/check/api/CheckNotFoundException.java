package ru.dta.check.api;

import java.util.UUID;

public class CheckNotFoundException extends RuntimeException {

    public CheckNotFoundException(UUID id) {
        super("Проверка не найдена: " + id);
    }
}
