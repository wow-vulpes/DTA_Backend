package ru.dta.check.application;

import java.time.Clock;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dta.check.domain.CheckResult;
import ru.dta.check.domain.CheckStatus;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.RecordChecker;
import ru.dta.check.domain.RecordType;
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
}
