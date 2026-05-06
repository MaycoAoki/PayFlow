package io.payflow.account.persistence;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface AccountProjectionRepository extends JpaRepository<AccountProjection, String> {}
