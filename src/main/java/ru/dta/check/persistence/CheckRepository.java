package ru.dta.check.persistence;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckRepository extends JpaRepository<CheckEntity, UUID>, JpaSpecificationExecutor<CheckEntity> {
    @Query("select d.check.id as checkId, count(d) as total from CheckDocumentEntity d "
            + "where d.check.id in :ids group by d.check.id")
    List<DocumentCount> countDocuments(@Param("ids") List<UUID> ids);

    interface DocumentCount {
        UUID getCheckId();
        long getTotal();
    }
}
