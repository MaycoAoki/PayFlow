package io.payflow.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistoryEntry, Long> {

    List<TransactionHistoryEntry> findByAccountIdOrderByOccurredAtAsc(String accountId);
}
