package ru.dta.check.domain;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public class RecordChecker {

    private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "jpg", "png");

    private final MaterialTypeDetector materialTypeDetector;

    public RecordChecker(MaterialTypeDetector materialTypeDetector) {
        this.materialTypeDetector = Objects.requireNonNull(materialTypeDetector, "materialTypeDetector");
    }

    public CheckResult check(RecordType recordType, List<MaterialMetadata> materials) {
        Objects.requireNonNull(recordType, "recordType");
        List<MaterialMetadata> inputs = List.copyOf(Objects.requireNonNull(materials, "materials"));
        List<CheckedMaterial> documents = new ArrayList<>();
        List<CheckIssue> issues = new ArrayList<>();
        EnumSet<MaterialType> available = EnumSet.noneOf(MaterialType.class);

        for (int index = 0; index < inputs.size(); index++) {
            MaterialMetadata metadata = inputs.get(index);
            Optional<MaterialType> detectedType = materialTypeDetector.detect(metadata.filename());
            documents.add(new CheckedMaterial(metadata, detectedType));
            boolean validFormat = hasAllowedExtension(metadata.filename());
            boolean validSize = metadata.sizeBytes() > 0 && metadata.sizeBytes() <= MAX_SIZE_BYTES;
            if (!validFormat) {
                issues.add(warning("Недопустимый формат файла: «" + metadata.filename() + "»", index));
            }
            if (metadata.sizeBytes() == 0) {
                issues.add(warning("Пустой файл: «" + metadata.filename() + "»", index));
            } else if (metadata.sizeBytes() > MAX_SIZE_BYTES) {
                issues.add(warning("Размер файла превышает 20 МБ: «" + metadata.filename() + "»", index));
            }
            if (detectedType.isEmpty()) {
                issues.add(warning("Не удалось определить тип материала: «" + metadata.filename() + "»", index));
            }
            if (validFormat && validSize) {
                detectedType.ifPresent(available::add);
            }
        }

        EnumSet<MaterialType> required = EnumSet.of(MaterialType.OBSERVATION_DIARY,
                MaterialType.SESSION_REPORT, MaterialType.PARENT_FEEDBACK);
        if (recordType == RecordType.WEEKLY) {
            required.add(MaterialType.SPECIALIST_CONCLUSION);
        }
        required.removeAll(available);
        for (MaterialType missing : required) {
            issues.add(new CheckIssue(IssueLevel.ERROR,
                    "Отсутствует обязательный материал: " + materialLabel(missing), OptionalInt.empty()));
        }
        CheckStatus status = required.isEmpty() ? CheckStatus.COMPLETE : CheckStatus.INCOMPLETE;
        return new CheckResult(status, documents, issues);
    }

    private boolean hasAllowedExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && ALLOWED_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private CheckIssue warning(String message, int index) {
        return new CheckIssue(IssueLevel.WARNING, message, OptionalInt.of(index));
    }

    private String materialLabel(MaterialType type) {
        return switch (type) {
            case OBSERVATION_DIARY -> "дневник наблюдений";
            case SESSION_REPORT -> "отчёт о занятии";
            case PARENT_FEEDBACK -> "обратная связь родителя";
            case SPECIALIST_CONCLUSION -> "заключение специалиста";
        };
    }
}
