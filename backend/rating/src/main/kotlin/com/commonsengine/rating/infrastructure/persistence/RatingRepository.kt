package com.commonsengine.rating.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RatingRepository : JpaRepository<RatingEntity, Long> {

    /** 查询某人收到的所有评价 */
    fun findByRateeId(rateeId: String): List<RatingEntity>

    /** 查询某人发出的所有评价 */
    fun findByRaterId(raterId: String): List<RatingEntity>

    /** 查询某笔交易的所有评价（双方向） */
    fun findByTransactionId(transactionId: String): List<RatingEntity>
}
