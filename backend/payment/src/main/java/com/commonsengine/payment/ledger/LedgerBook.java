package com.commonsengine.payment.ledger;

import com.commonsengine.payment.domain.Model.LedgerEvent;
import com.commonsengine.payment.domain.Model.TransactionId;
import com.commonsengine.payment.infrastructure.persistence.LedgerEventMapper;
import com.commonsengine.payment.infrastructure.persistence.LedgerEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 不可篡改账本——事件溯源模式。
 *
 * <p>所有资金流动记录为不可变事件，追加写入，不可修改/删除。
 * 全体成员有权查阅（章程第 4.3 条）。
 *
 * <p>使用 PostgreSQL append-only 表持久化。
 */
@Component
public class LedgerBook {

    private final LedgerEventRepository repository;

    public LedgerBook(LedgerEventRepository repository) {
        this.repository = repository;
    }

    /** 追加事件（不可修改、不可删除） */
    @Transactional
    public void append(LedgerEvent event) {
        repository.save(LedgerEventMapper.toEntity(event));
    }

    /** 查询某笔交易的所有事件 */
    @Transactional(readOnly = true)
    public List<LedgerEvent> findByTransaction(TransactionId txId) {
        return repository.findByTransactionId(txId.value()).stream()
                .map(LedgerEventMapper::toDomain)
                .toList();
    }

    /** 查询全部事件（公开审计） */
    @Transactional(readOnly = true)
    public List<LedgerEvent> findAll() {
        return repository.findAll().stream()
                .map(LedgerEventMapper::toDomain)
                .toList();
    }

    /** 事件总数 */
    @Transactional(readOnly = true)
    public int size() {
        return Math.toIntExact(repository.count());
    }
}
