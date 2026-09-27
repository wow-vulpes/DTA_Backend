package ru.dta.check.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dta.check.domain.CheckResult;
import ru.dta.check.domain.CheckStatus;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.RecordChecker;
import ru.dta.check.domain.RecordType;
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

    @Transactional(readOnly = true)
    public CheckListPage findChecks(CheckQuery query) {
        RecordType type = query.recordType();
        CheckStatus status = query.status();
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
        Map<UUID, Long> counts = page.isEmpty() ? Map.of()
                : checkRepository.countDocuments(page.getContent().stream().map(CheckEntity::getId).toList())
                        .stream().collect(Collectors.toMap(CheckRepository.DocumentCount::getCheckId,
                                CheckRepository.DocumentCount::getTotal));
        List<CheckSummary> items = page.stream().map(check -> new CheckSummary(check.getId(), check.getCheckedAt(),
                check.getRecordType(), check.getStatus(), counts.getOrDefault(check.getId(), 0L))).toList();
        return new CheckListPage(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CheckEntity getCheck(UUID checkId) {
        CheckEntity check = checkRepository.findById(checkId).orElseThrow(() -> new CheckNotFoundException(checkId));
        // Загружаем обе коллекции внутри транзакции; open-in-view остаётся выключенным.
        check.getDocuments();
        check.getIssues();
        return check;
    }
}
