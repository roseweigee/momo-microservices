package com.demo.order.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, String> {
}
