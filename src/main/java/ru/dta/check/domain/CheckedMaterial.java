package ru.dta.check.domain;

import java.util.Optional;
import java.util.Objects;

/** Пустой detectedType обозначает неизвестное или неоднозначное имя. */
public record CheckedMaterial(MaterialMetadata metadata, Optional<MaterialType> detectedType) {

    public CheckedMaterial {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(detectedType, "detectedType");
    }
}
