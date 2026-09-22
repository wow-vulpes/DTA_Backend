package ru.dta.check.domain;

import java.util.Objects;

/** Метаданные файла; размер указан в байтах. */
public record MaterialMetadata(String filename, long sizeBytes) {

    public MaterialMetadata {
        Objects.requireNonNull(filename, "filename");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }
}
