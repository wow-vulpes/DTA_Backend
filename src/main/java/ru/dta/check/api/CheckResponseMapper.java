package ru.dta.check.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import ru.dta.check.persistence.CheckEntity;
import ru.dta.check.persistence.CheckDocumentEntity;
import ru.dta.check.persistence.CheckIssueEntity;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class CheckResponseMapper {

    @Mapping(target = "checkId", source = "id")
    @Mapping(target = "extracted", ignore = true)
    public abstract CheckResponse toResponse(CheckEntity check);

    @Mapping(target = "sizeKb", source = "sizeBytes")
    public abstract DocumentResponse toDocument(CheckDocumentEntity document);

    public abstract IssueResponse toIssue(CheckIssueEntity issue);

    protected String toLowercase(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    protected BigDecimal toKilobytes(long bytes) {
        return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(1024), 2, RoundingMode.HALF_UP);
    }
}
