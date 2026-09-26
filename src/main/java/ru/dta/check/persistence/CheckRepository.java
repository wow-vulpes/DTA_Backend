package ru.dta.check.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckRepository extends JpaRepository<CheckEntity, UUID> {
}
