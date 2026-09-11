package com.receiptvision.core.repository;

import java.util.Optional;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.domain.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    // ---- Owner-scoped queries ONLY. No findAll/findById without owner exists on purpose. ----

    Page<Receipt> findByOwner(AppUser owner, Pageable pageable);

    Optional<Receipt> findByIdAndOwner(Long id, AppUser owner);

    boolean existsByIdAndOwner(Long id, AppUser owner);

    void deleteByIdAndOwner(Long id, AppUser owner);

    long countByOwner(AppUser owner);

    // Used once at startup to purge legacy anonymous rows (privacy).
    @Modifying
    @Query("delete from Receipt r where r.owner is null")
    int deleteOrphans();
}
