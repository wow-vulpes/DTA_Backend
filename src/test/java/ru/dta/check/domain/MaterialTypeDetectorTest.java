package ru.dta.check.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialTypeDetectorTest {

    private final MaterialTypeDetector detector = new MaterialTypeDetector();

    @ParameterizedTest
    @CsvSource({
            "дневник_наблюдений_15-03.xlsx, OBSERVATION_DIARY",
            "отчёт о занятии.docx, SESSION_REPORT",
            "обратная связь родителя.jpg, PARENT_FEEDBACK",
            "заключение специалиста.pdf, SPECIALIST_CONCLUSION",
            "observation_diary.xlsx, OBSERVATION_DIARY",
            "session_report.docx, SESSION_REPORT",
            "parent_feedback.png, PARENT_FEEDBACK",
            "specialist_conclusion.pdf, SPECIALIST_CONCLUSION",
            "ДНЕВНИК НАБЛЮДЕНИЙ.PDF, OBSERVATION_DIARY",
            "ОТЧЁТ-О-ЗАНЯТИИ.PDF, SESSION_REPORT",
            "SESSION_REPORT.PDF, SESSION_REPORT",
            "дневник__--наблюдений.pdf, OBSERVATION_DIARY",
            "дневник   наблюдений.pdf, OBSERVATION_DIARY",
            "копия дневник наблюдений за день.pdf, OBSERVATION_DIARY",
            "дневник наблюдений observation diary.pdf, OBSERVATION_DIARY",
            "дневник наблюдений дневник наблюдений.pdf, OBSERVATION_DIARY",
            "дневник наблюдений, OBSERVATION_DIARY",
            "дневник наблюдений.exe, OBSERVATION_DIARY",
            "дневник наблюдений.pdf.exe, OBSERVATION_DIARY",
            "15.03.дневник наблюдений.pdf, OBSERVATION_DIARY"
    })
    void detectsRecognizedNames(String filename, MaterialType expected) {
        assertThat(detector.detect(filename)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "scan_0041.jpg", "дневник.xlsx", "myobservation diary.pdf",
            "observation diaryextra.pdf", "супердневник наблюдений.pdf",
            "дневник наблюденийextra.pdf", "дневник наблюдений2.pdf",
            "наблюдений дневник.pdf", ".pdf", "file.observation diary",
            "дневник наблюдений отчет о занятии.pdf",
            "parent feedback specialist conclusion.pdf"
    })
    void returnsEmptyForUnknownOrAmbiguousNames(String filename) {
        assertThat(detector.detect(filename)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"дневник\tнаблюдений.pdf", "дневник\u00a0наблюдений.pdf"})
    void recognizesWhitespaceSeparators(String filename) {
        assertThat(detector.detect(filename)).contains(MaterialType.OBSERVATION_DIARY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlankFilename(String filename) {
        assertThatThrownBy(() -> detector.detect(filename)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    void rejectsNullFilename(String filename) {
        assertThatThrownBy(() -> detector.detect(filename)).isInstanceOf(NullPointerException.class);
    }
}
