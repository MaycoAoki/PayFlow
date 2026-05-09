package io.payflow.account.persistence;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistoryEntry, Long> {

    List<TransactionHistoryEntry> findByAccountIdOrderByOccurredAtAsc(String accountId);

    boolean existsByAccountIdAndOccurredAt(String accountId, Instant occurredAt);

    @Modifying
    @Transactional
    @Query("DELETE FROM TransactionHistoryEntry t WHERE t.accountId = :accountId")
    void deleteByAccountId(@Param("accountId") String accountId);
}
