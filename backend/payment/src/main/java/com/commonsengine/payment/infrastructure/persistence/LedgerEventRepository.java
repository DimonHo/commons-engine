package com.commonsengine.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 账本事件 Repository。
 */
@Repository
public interface LedgerEventRepository extends JpaRepository<LedgerEventEntity, Long> {

    List<LedgerEventEntity> findByTransactionId(String transactionId);

    long countByEventType(String eventType);
}
