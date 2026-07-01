package com.commonsengine.payment.ledger

import com.commonsengine.payment.domain.LedgerEvent
import com.commonsengine.payment.domain.TransactionId
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 不可篡改账本——事件溯源模式
 *
 * 所有资金流动记录为不可变事件，追加写入，不可修改/删除。
 * 全体成员有权查阅（章程第 4.3 条）。
 *
 * 当前使用内存存储（MVP），后续替换为 PostgreSQL（append-only 表）。
 */
@Component
open class LedgerBook {

    private val events = CopyOnWriteArrayList<LedgerEvent>()

    /** 追加事件（不可修改、不可删除） */
    fun append(event: LedgerEvent) {
        events.add(event)
    }

    /** 查询某笔交易的所有事件 */
    fun findByTransaction(txId: TransactionId): List<LedgerEvent> =
        events.filter { it.transactionId == txId }

    /** 查询全部事件（公开审计） */
    fun findAll(): List<LedgerEvent> = events.toList()

    /** 事件总数 */
    fun size(): Int = events.size
}
