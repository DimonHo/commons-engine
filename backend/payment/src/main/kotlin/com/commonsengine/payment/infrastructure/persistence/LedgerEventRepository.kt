package com.commonsengine.payment.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LedgerEventRepository : JpaRepository<LedgerEventEntity, Long> {

    fun findByTransactionId(transactionId: String): List<LedgerEventEntity>

    fun countByEventType(eventType: String): Long
}
