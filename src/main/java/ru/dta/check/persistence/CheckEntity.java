package ru.dta.check.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.dta.check.domain.CheckStatus;
import ru.dta.check.domain.CheckResult;
import ru.dta.check.domain.RecordType;

@Entity
@Table(name = "checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 16)
    private RecordType recordType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckStatus status;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "status_label", nullable = false, columnDefinition = "text")
    private String statusLabel;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @OneToMany(mappedBy = "check", cascade = CascadeType.ALL)
    @OrderBy("position ASC")
    private List<CheckDocumentEntity> documents = new ArrayList<>();

    @OneToMany(mappedBy = "check", cascade = CascadeType.ALL)
    @OrderBy("position ASC")
    private List<CheckIssueEntity> issues = new ArrayList<>();

    public static CheckEntity fromResult(RecordType recordType, CheckResult result, Instant checkedAt,
            String statusLabel, String reason) {
        Objects.requireNonNull(result, "result");
        CheckEntity entity = new CheckEntity();
        entity.recordType = Objects.requireNonNull(recordType, "recordType");
        entity.status = result.status();
        entity.checkedAt = Objects.requireNonNull(checkedAt, "checkedAt");
        entity.statusLabel = Objects.requireNonNull(statusLabel, "statusLabel");
        entity.reason = Objects.requireNonNull(reason, "reason");
        for (int position = 0; position < result.documents().size(); position++) {
            entity.documents.add(new CheckDocumentEntity(entity, position, result.documents().get(position)));
        }
        for (int position = 0; position < result.issues().size(); position++) {
            entity.issues.add(new CheckIssueEntity(entity, position, result.issues().get(position)));
        }
        return entity;
    }

    public List<CheckDocumentEntity> getDocuments() {
        return List.copyOf(documents);
    }

    public List<CheckIssueEntity> getIssues() {
        return List.copyOf(issues);
    }
}
