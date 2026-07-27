package com.commonsengine.payment.ledger

import com.commonsengine.payment.domain.LedgerEvent
import com.commonsengine.payment.domain.TransactionId
import com.commonsengine.payment.infrastructure.persistence.LedgerEventRepository
import com.commonsengine.payment.infrastructure.persistence.toDomain
import com.commonsengine.payment.infrastructure.persistence.toEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 不可篡改账本——事件溯源模式
 *
 * 所有资金流动记录为不可变事件，追加写入，不可修改/删除。
 * 全体成员有权查阅（章程第 4.3 条）。
 *
 * 使用 PostgreSQL append-only 表持久化。
 */
@Component
open class LedgerBook(
    private val repository: LedgerEventRepository,
) {

    /** 追加事件（不可修改、不可删除） */
    @Transactional
    open fun append(event: LedgerEvent) {
        repository.save(event.toEntity())
    }

    /** 查询某笔交易的所有事件 */
    @Transactional(readOnly = true)
    open fun findByTransaction(txId: TransactionId): List<LedgerEvent> =
        repository.findByTransactionId(txId.value).map { it.toDomain() }

    /** 查询全部事件（公开审计） */
    @Transactional(readOnly = true)
    open fun findAll(): List<LedgerEvent> =
        repository.findAll().map { it.toDomain() }

    /** 事件总数 */
    @Transactional(readOnly = true)
    open fun size(): Int = repository.findAll().size
}
