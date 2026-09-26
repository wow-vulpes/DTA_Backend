package ru.dta.check.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.dta.check.domain.IssueLevel;
import ru.dta.check.domain.CheckIssue;

@Entity
@Table(name = "check_issues", uniqueConstraints = {
        @UniqueConstraint(name = "uk_check_issue_position", columnNames = {"check_id", "position"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    private CheckEntity check;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IssueLevel level;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "document_index")
    private Integer documentIndex;

    CheckIssueEntity(CheckEntity check, int position, CheckIssue issue) {
        this.check = check;
        this.position = position;
        this.level = issue.level();
        this.message = issue.message();
        this.documentIndex = issue.documentIndex().isPresent() ? issue.documentIndex().getAsInt() : null;
    }
}
