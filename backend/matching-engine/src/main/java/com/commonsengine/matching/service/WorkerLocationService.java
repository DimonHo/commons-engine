package com.commonsengine.matching.service;

import com.commonsengine.matching.infrastructure.persistence.WorkerLocationEntity;
import com.commonsengine.matching.infrastructure.persistence.WorkerLocationRepository;
import com.commonsengine.platform.exception.NotFoundException;
import com.commonsengine.platform.geo.GeoPoint;
import com.commonsengine.platform.geo.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 劳动者位置服务（#63）
 *
 * 职责：
 * 1. 更新劳动者实时位置（upsert）
 * 2. 计算边界框（boundingBox）——将圆形搜索区域转换为矩形，用于 DB 索引预筛
 * 3. findNearbyWorkers——边界框预筛 + Haversine 精筛
 *
 * 两阶段查询优化：
 * - 第一阶段：DB 层用 BETWEEN 边界框过滤（走 lat/lng 索引）
 * - 第二阶段：应用层用 Haversine 精确计算球面距离，排除圆外点
 */
@Service
public class WorkerLocationService {

    private static final Logger log = LoggerFactory.getLogger(WorkerLocationService.class);

    /** 一度纬度 ≈ 111 公里 */
    private static final double METERS_PER_DEGREE_LAT = 111_000.0;

    private final WorkerLocationRepository repository;

    public WorkerLocationService(WorkerLocationRepository repository) {
        this.repository = repository;
    }

    /**
     * 更新（或创建）劳动者位置——upsert 语义。
     *
     * @return 更新后的实体
     */
    @Transactional
    public WorkerLocationEntity updateLocation(String workerId, double lat, double lng,
                                                int activeOrderCount) {
        WorkerLocationEntity entity = repository.findByWorkerId(workerId)
                .orElseGet(() -> {
                    WorkerLocationEntity e = new WorkerLocationEntity();
                    e.setWorkerId(workerId);
                    return e;
                });
        entity.setLat(lat);
        entity.setLng(lng);
        entity.setActiveOrderCount(activeOrderCount);
        entity.setUpdatedAt(Instant.now());
        WorkerLocationEntity saved = repository.save(entity);
        log.debug("劳动者位置已更新 worker={} lat={} lng={} active={}",
                workerId, lat, lng, activeOrderCount);
        return saved;
    }

    @Transactional(readOnly = true)
    public GeoPoint getLocation(String workerId) {
        return repository.findByWorkerId(workerId)
                .map(e -> new GeoPoint(e.getLat(), e.getLng()))
                .orElseThrow(() -> new NotFoundException("WorkerLocation", workerId));
    }

    /**
     * 计算以 (centerLat, centerLng) 为圆心、radiusMeters 为半径的边界框。
     *
     * @return [minLat, maxLat, minLng, maxLng]
     */
    public static double[] boundingBox(double centerLat, double centerLng, double radiusMeters) {
        double latDelta = radiusMeters / METERS_PER_DEGREE_LAT;
        // 经度跨度随纬度变化——cos(lat) 越小（高纬度），一度经度的距离越短
        double cosLat = Math.max(Math.cos(Math.toRadians(centerLat)), 0.01);
        double lngDelta = radiusMeters / (METERS_PER_DEGREE_LAT * cosLat);

        return new double[]{
                centerLat - latDelta,
                centerLat + latDelta,
                centerLng - lngDelta,
                centerLng + lngDelta
        };
    }

    /**
     * 查找圆心 (center) 半径 radiusMeters 内的劳动者。
     *
     * 两阶段：边界框 DB 预筛 → Haversine 精筛。
     */
    @Transactional(readOnly = true)
    public List<WorkerLocationEntity> findNearbyWorkers(double centerLat, double centerLng,
                                                         double radiusMeters) {
        double[] bbox = boundingBox(centerLat, centerLng, radiusMeters);
        List<WorkerLocationEntity> candidates = repository.findInBoundingBox(
                bbox[0], bbox[1], bbox[2], bbox[3]
        );

        GeoPoint center = new GeoPoint(centerLat, centerLng);
        return candidates.stream()
                .filter(e -> {
                    GeoPoint loc = new GeoPoint(e.getLat(), e.getLng());
                    return GeoUtils.isWithinRadius(center, loc, radiusMeters);
                })
                .toList();
    }
}
