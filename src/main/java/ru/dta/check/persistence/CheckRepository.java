package ru.dta.check.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CheckRepository extends JpaRepository<CheckEntity, UUID>, JpaSpecificationExecutor<CheckEntity> {
}
