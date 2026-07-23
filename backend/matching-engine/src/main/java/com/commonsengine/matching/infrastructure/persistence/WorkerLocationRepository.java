package com.commonsengine.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 劳动者位置 JPA Repository——含边界框空间查询。
 */
public interface WorkerLocationRepository extends JpaRepository<WorkerLocationEntity, String> {

    Optional<WorkerLocationEntity> findByWorkerId(String workerId);

    /**
     * 边界框查询——在 (minLat, minLng) ~ (maxLat, maxLng) 矩形内的劳动者。
     *
     * 利用 lat/lng 列上的 B-tree 索引快速过滤，再由应用层用 Haversine 精筛。
     * 比原生 PostGIS ST_Within 轻量，适合 MVP 阶段。
     */
    @Query("SELECT w FROM WorkerLocationEntity w " +
            "WHERE w.lat BETWEEN :minLat AND :maxLat " +
            "AND w.lng BETWEEN :minLng AND :maxLng")
    List<WorkerLocationEntity> findInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );
}
