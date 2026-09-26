package ru.dta.check.api;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.MaterialTypeDetector;
import ru.dta.check.domain.RecordChecker;
import ru.dta.check.domain.RecordType;
import ru.dta.check.persistence.CheckEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CheckResponseMapperTest {

    @ParameterizedTest
    @CsvSource({"0, 0.00", "1, 0.00", "6, 0.01", "1024, 1.00", "1536, 1.50"})
    void mapsDetectedTypeAndRoundsSizeToTwoDecimalPlaces(long bytes, String expected) {
        CheckEntity entity = CheckEntity.fromResult(RecordType.DAILY,
                new RecordChecker(new MaterialTypeDetector()).check(RecordType.DAILY,
                        List.of(new MaterialMetadata("observation diary.pdf", bytes))),
                Instant.EPOCH, "Комплект неполный", "Отсутствуют материалы.");
        CheckResponse response = Mappers.getMapper(CheckResponseMapper.class).toResponse(entity);
        assertThat(response.documents().getFirst().sizeKb()).isEqualTo(expected);
        assertThat(response.documents().getFirst().detectedType()).isEqualTo("observation_diary");
        assertThat(response.extracted()).isNull();
        assertThat(response.checkedAt()).isEqualTo(Instant.EPOCH);
    }
}
