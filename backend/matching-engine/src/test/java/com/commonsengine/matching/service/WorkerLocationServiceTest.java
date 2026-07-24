package com.commonsengine.matching.service;

import com.commonsengine.matching.infrastructure.persistence.WorkerLocationEntity;
import com.commonsengine.platform.exception.NotFoundException;
import com.commonsengine.platform.geo.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 劳动者位置服务测试——从 Kotlin WorkerLocationServiceTest 转换。
 *
 * <p>适配 Java WorkerLocationService API：
 * <ul>
 *   <li>{@code updateLocation(workerId, lat, lng, activeOrderCount)}</li>
 *   <li>{@code findNearbyWorkers(centerLat, centerLng, radiusMeters)} → List&lt;WorkerLocationEntity&gt;</li>
 *   <li>{@code getLocation(workerId)} → GeoPoint</li>
 * </ul>
 *
 * <p>注意：Java API 没有按 maxActiveOrders 过滤的参数，故 D（满单）相关断言改为
 * 验证位置结果与坐标往返。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkerLocationServiceTest {

    @Autowired
    private WorkerLocationService service;

    // 北京中关村附近
    private static final double ZGC_LAT = 39.9847;
    private static final double ZGC_LNG = 116.3076;

    @BeforeEach
    void setup() {
        // 劳动者 A：中关村 100 米内
        service.updateLocation("w-A", 39.9850, 116.3080, 0);
        // 劳动者 B：中关村 500 米内
        service.updateLocation("w-B", 39.9820, 116.3050, 0);
        // 劳动者 C：5 公里外（不应出现在 1km 搜索结果中）
        service.updateLocation("w-C", 40.0200, 116.3500, 0);
        // 劳动者 D：中关村附近，但满单
        service.updateLocation("w-D", 39.9848, 116.3078, 3);
    }

    @Test
    void findNearbyWorkersReturnsWorkersWithinRadius() {
        List<WorkerLocationEntity> nearby = service.findNearbyWorkers(ZGC_LAT, ZGC_LNG, 1000.0);

        // A 和 B 在 1km 内，C 在 5km 外；D 也在 1km 内（Java API 不按订单数过滤）
        assertTrue(nearby.size() >= 2);
        assertTrue(nearby.stream().anyMatch(e -> e.getWorkerId().equals("w-A")));
        assertTrue(nearby.stream().anyMatch(e -> e.getWorkerId().equals("w-B")));
        assertFalse(nearby.stream().anyMatch(e -> e.getWorkerId().equals("w-C")));
    }

    @Test
    void findNearbyWorkersReturnsEmptyWhenNoOneInRange() {
        // 北京天安门（距中关村约 10km）
        List<WorkerLocationEntity> nearby = service.findNearbyWorkers(39.9087, 116.3974, 1000.0);
        assertTrue(nearby.isEmpty());
    }

    @Test
    void upsertLocationUpdatesExistingWorker() {
        service.updateLocation("w-A", 39.9900, 116.3200, 2);

        GeoPoint loc = service.getLocation("w-A");
        assertEquals(39.9900, loc.lat(), 0.0001);
        assertEquals(116.3200, loc.lng(), 0.0001);
    }

    @Test
    void getLocationThrowsForNonExistentWorker() {
        assertThrows(NotFoundException.class,
                () -> service.getLocation("no-such-worker"));
    }

    @Test
    void boundingBoxComputesCorrectBounds() {
        double[] bbox = WorkerLocationService.boundingBox(39.9847, 116.3076, 1000.0);

        // [minLat, maxLat, minLng, maxLng]
        assertEquals(4, bbox.length);
        assertTrue(bbox[0] < 39.9847, "minLat 应小于中心纬度");
        assertTrue(bbox[1] > 39.9847, "maxLat 应大于中心纬度");
        assertTrue(bbox[2] < 116.3076, "minLng 应小于中心经度");
        assertTrue(bbox[3] > 116.3076, "maxLng 应大于中心经度");
    }
}
