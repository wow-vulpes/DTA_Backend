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
import ru.dta.check.domain.MaterialType;
import ru.dta.check.domain.CheckedMaterial;

@Entity
@Table(name = "check_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_check_document_position", columnNames = {"check_id", "position"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    private CheckEntity check;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_type", length = 32)
    private MaterialType detectedType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    CheckDocumentEntity(CheckEntity check, int position, CheckedMaterial material) {
        this.check = check;
        this.position = position;
        this.name = material.metadata().filename();
        this.detectedType = material.detectedType().orElse(null);
        this.sizeBytes = material.metadata().sizeBytes();
    }
}
