package io.payflow.transfer.persistence;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface TransferProjectionRepository extends JpaRepository<TransferProjection, String> {}
