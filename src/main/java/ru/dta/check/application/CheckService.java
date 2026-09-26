package ru.dta.check.application;

import java.time.Clock;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dta.check.domain.CheckResult;
import ru.dta.check.domain.CheckStatus;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.RecordChecker;
import ru.dta.check.domain.RecordType;
import ru.dta.check.api.CheckQuery;
import ru.dta.check.api.CheckNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import ru.dta.check.persistence.CheckEntity;
import ru.dta.check.persistence.CheckRepository;

@Service
@RequiredArgsConstructor
public class CheckService {

    private final RecordChecker recordChecker;
    private final CheckRepository checkRepository;
    private final Clock clock;

    @Transactional
    public CheckEntity createCheck(RecordType recordType, List<MaterialMetadata> materials) {
        CheckResult result = recordChecker.check(recordType, materials);
        boolean complete = result.status() == CheckStatus.COMPLETE;
        String label = complete ? "Комплект полный" : "Комплект неполный";
        String reason = complete
                ? "Все обязательные материалы присутствуют."
                : "Отсутствуют обязательные материалы: см. нарушения уровня error.";
        if (complete && !result.issues().isEmpty()) {
            reason = "Все обязательные материалы присутствуют; есть предупреждения.";
        }
        return checkRepository.save(CheckEntity.fromResult(recordType, result, clock.instant(), label, reason));
    }

    public CheckListPage findChecks(CheckQuery query) {
        RecordType type = query.recordType() == null ? null : parseRecordType(query.recordType());
        CheckStatus status = query.status() == null ? null : parseStatus(query.status());
        Specification<CheckEntity> specification = (root, criteria, builder) -> builder.conjunction();
        if (type != null) {
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("recordType"), type));
        }
        if (status != null) {
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("status"), status));
        }
        if (query.from() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.greaterThanOrEqualTo(root.get("checkedAt"), query.from()));
        }
        if (query.to() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.lessThan(root.get("checkedAt"), query.to()));
        }
        Page<CheckEntity> page = checkRepository.findAll(specification, PageRequest.of(query.page(), query.size(),
                Sort.by(Sort.Direction.DESC, "checkedAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new CheckListPage(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public CheckEntity getCheck(UUID checkId) {
        return checkRepository.findById(checkId).orElseThrow(() -> new CheckNotFoundException(checkId));
    }

    private RecordType parseRecordType(String value) {
        try {
            return RecordType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Недопустимый record_type: " + value, exception);
        }
    }

    private CheckStatus parseStatus(String value) {
        try {
            return CheckStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Недопустимый status: " + value, exception);
        }
    }
}
