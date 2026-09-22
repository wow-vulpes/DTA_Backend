package ru.dta.check.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialMetadataTest {

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 20971520, 20971521, Long.MAX_VALUE})
    void acceptsNonNegativeSizesWithoutApplyingBusinessLimit(long sizeBytes) {
        MaterialMetadata metadata = new MaterialMetadata("Дневник_наблюдений.PDF", sizeBytes);

        assertThat(metadata.filename()).isEqualTo("Дневник_наблюдений.PDF");
        assertThat(metadata.sizeBytes()).isEqualTo(sizeBytes);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsNegativeSize(long sizeBytes) {
        assertThatThrownBy(() -> new MaterialMetadata("file.pdf", sizeBytes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlankFilename(String filename) {
        assertThatThrownBy(() -> new MaterialMetadata(filename, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullFilename() {
        assertThatThrownBy(() -> new MaterialMetadata(null, 1)).isInstanceOf(NullPointerException.class);
    }
}
