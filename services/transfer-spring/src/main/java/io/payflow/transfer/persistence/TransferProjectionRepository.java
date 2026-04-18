package io.payflow.transfer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferProjectionRepository extends JpaRepository<TransferProjection, String> {}
