package com.commonsengine.rating.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评价 Repository。
 */
@Repository
public interface RatingRepository extends JpaRepository<RatingEntity, Long> {

    /** 查询某人收到的所有评价 */
    List<RatingEntity> findByRateeId(String rateeId);

    /** 查询某人发出的所有评价 */
    List<RatingEntity> findByRaterId(String raterId);

    /** 查询某笔交易的所有评价（双方向） */
    List<RatingEntity> findByTransactionId(String transactionId);
}
