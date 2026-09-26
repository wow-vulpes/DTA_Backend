package ru.dta.check.api;

import java.util.List;

public class InvalidCheckRequestException extends RuntimeException {

    private final List<FieldErrorResponse> fieldErrors;

    public InvalidCheckRequestException(List<FieldErrorResponse> fieldErrors) {
        super("Проверьте поля запроса.");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldErrorResponse> getFieldErrors() {
        return fieldErrors;
    }
}
