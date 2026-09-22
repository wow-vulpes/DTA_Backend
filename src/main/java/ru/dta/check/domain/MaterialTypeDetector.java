package ru.dta.check.domain;

import java.util.Optional;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class MaterialTypeDetector {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s_-]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Map<MaterialType, Pattern> NAME_PATTERNS = Map.of(
            MaterialType.OBSERVATION_DIARY, phrasePattern("дневник наблюдений", "observation diary"),
            MaterialType.SESSION_REPORT, phrasePattern("отчет о занятии", "session report"),
            MaterialType.PARENT_FEEDBACK, phrasePattern("обратная связь родителя", "parent feedback"),
            MaterialType.SPECIALIST_CONCLUSION, phrasePattern("заключение специалиста", "specialist conclusion")
    );

    /** Возвращает тип только при однозначном совпадении с согласованным словарём. */
    public Optional<MaterialType> detect(String filename) {
        Objects.requireNonNull(filename, "filename");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        int extensionIndex = filename.lastIndexOf('.');
        String basename = extensionIndex >= 0 ? filename.substring(0, extensionIndex) : filename;
        String normalized = SEPARATORS.matcher(basename.toLowerCase(Locale.ROOT).replace('ё', 'е'))
                .replaceAll(" ");
        EnumSet<MaterialType> matches = EnumSet.noneOf(MaterialType.class);
        NAME_PATTERNS.forEach((type, pattern) -> {
            if (pattern.matcher(normalized).find()) {
                matches.add(type);
            }
        });
        return matches.size() == 1 ? Optional.of(matches.iterator().next()) : Optional.empty();
    }

    private static Pattern phrasePattern(String russian, String english) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + Pattern.quote(russian) + "|"
                + Pattern.quote(english) + ")(?![\\p{L}\\p{N}])");
    }
}
