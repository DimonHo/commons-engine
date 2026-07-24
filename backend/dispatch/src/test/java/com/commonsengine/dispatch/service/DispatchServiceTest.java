package com.commonsengine.dispatch.service;

import com.commonsengine.dispatch.domain.Model.DispatchTask;
import com.commonsengine.dispatch.domain.Model.RouteSuggestion;
import com.commonsengine.dispatch.domain.Model.TaskStatus;
import com.commonsengine.dispatch.domain.Model.TimeSlot;
import com.commonsengine.dispatch.domain.Model.VisitPoint;
import com.commonsengine.dispatch.domain.Model.WorkerPreferences;
import com.commonsengine.platform.exception.BusinessRuleException;
import com.commonsengine.platform.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调度服务测试——从 Kotlin DispatchServiceTest 转换。
 *
 * <p>适配 Java DispatchService API：
 * <ul>
 *   <li>{@code createTask(serviceType, pickupLat, pickupLng)}</li>
 *   <li>{@code assignTask(taskId, workerId)}</li>
 *   <li>{@code optimizeRoute(originLat, originLng, points, workerId)} → RouteSuggestion</li>
 *   <li>{@code savePreferences / findPreferences}</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DispatchServiceTest {

    @Autowired
    private DispatchService service;

    // ── 路径优化 ────────────────────────────────────────

    @Test
    void optimizeSinglePickupSingleDropoff() {
        // 天安门起点
        double originLat = 39.9042;
        double originLng = 116.4074;
        List<double[]> points = List.of(
                new double[]{39.9100, 116.4100},   // ~700m
                new double[]{39.9200, 116.4200}    // 再 ~1.4km
        );

        RouteSuggestion route = service.optimizeRoute(originLat, originLng, points, "w1");

        assertEquals(2, route.visits().size());
        assertTrue(route.totalDistanceMeters() > 0);
        assertTrue(route.estimatedDurationMinutes() > 0);
        assertTrue(route.strategy().contains("nearest_neighbor"));
    }

    @Test
    void optimizeMultiPickupOrdersByNearestFirst() {
        double originLat = 39.9042;
        double originLng = 116.4074;
        double nearLat = 39.9050, nearLng = 116.4080;
        double midLat = 39.9100, midLng = 116.4100;
        double farLat = 39.9200, farLng = 116.4200;

        // 故意乱序：far, near, mid
        List<double[]> points = List.of(
                new double[]{farLat, farLng},
                new double[]{nearLat, nearLng},
                new double[]{midLat, midLng}
        );

        RouteSuggestion route = service.optimizeRoute(originLat, originLng, points, "w1");

        // 最近邻应先访问 near
        VisitPoint first = route.visits().get(0);
        assertEquals(nearLat, first.lat(), 0.0001);
        assertEquals(nearLng, first.lng(), 0.0001);
    }

    @Test
    void emptyWaypointsReturnsZeroDistance() {
        RouteSuggestion route = service.optimizeRoute(39.0, 116.0, List.of(), "w1");
        assertEquals(0.0, route.totalDistanceMeters());
        assertEquals(0, route.visits().size());
    }

    // ── 持久化测试 ─────────────────────────────────────

    @Test
    void createTaskPersistsAndCanBeFound() {
        DispatchTask task = service.createTask("FOOD_DELIVERY", 30.5728, 104.0668);

        assertNotNull(task.id());
        assertEquals("FOOD_DELIVERY", task.serviceType());
        assertEquals(30.5728, task.pickupLat(), 0.0001);
        assertEquals(104.0668, task.pickupLng(), 0.0001);
        assertEquals(TaskStatus.PENDING, task.status());
        assertNull(task.assignedWorkerId());
    }

    @Test
    void assignTaskTransitionsStatusToAssigned() {
        DispatchTask task = service.createTask("RIDE_HAILING", 30.0, 104.0);
        DispatchTask assigned = service.assignTask(task.id(), "worker-1");

        assertEquals(TaskStatus.ASSIGNED, assigned.status());
        assertEquals("worker-1", assigned.assignedWorkerId());
    }

    @Test
    void cannotAssignAlreadyAssignedTask() {
        DispatchTask task = service.createTask("RIDE_HAILING", 30.0, 104.0);
        service.assignTask(task.id(), "worker-1");

        // 已 ASSIGNED 的任务不能再次分配
        assertThrows(BusinessRuleException.class,
                () -> service.assignTask(task.id(), "worker-2"));
    }

    @Test
    void findPendingTasksReturnsOnlyPending() {
        DispatchTask t1 = service.createTask("RIDE_HAILING", 30.0, 104.0);
        DispatchTask t2 = service.createTask("FOOD_DELIVERY", 31.0, 105.0);
        service.assignTask(t1.id(), "worker-1");  // t1 → ASSIGNED

        List<DispatchTask> pending = service.findPendingTasks();
        assertTrue(pending.stream().allMatch(t -> t.status() == TaskStatus.PENDING));
        assertTrue(pending.stream().anyMatch(t -> t.id().equals(t2.id())));
        assertFalse(pending.stream().anyMatch(t -> t.id().equals(t1.id())));
    }

    @Test
    void findPreferencesThrowsForNonExistent() {
        // 不存在的偏好 → NotFoundException
        assertThrows(NotFoundException.class,
                () -> service.findPreferences("no-such-worker"));
    }

    @Test
    void savePreferencesPersistsAndReloadsAllFields() {
        Instant start = Instant.parse("2026-01-01T08:00:00Z");
        Instant end = Instant.parse("2026-01-01T12:00:00Z");
        WorkerPreferences prefs = new WorkerPreferences(
                "worker-prefs-1",
                Set.of("RIDE_HAILING", "FOOD_DELIVERY"),
                new TimeSlot(start, end),
                5000.0
        );

        WorkerPreferences saved = service.savePreferences(prefs);
        WorkerPreferences found = service.findPreferences("worker-prefs-1");

        assertNotNull(found);
        assertEquals("worker-prefs-1", found.workerId());
        assertEquals(2, found.serviceTypes().size());
        assertTrue(found.serviceTypes().contains("RIDE_HAILING"));
        assertEquals(5000.0, found.maxServiceRadiusMeters(), 0.01);
        assertNotNull(found.workingHours());
    }

    @Test
    void savePreferencesUpdatesExistingRecord() {
        Instant start = Instant.parse("2026-01-01T08:00:00Z");
        Instant end = Instant.parse("2026-01-01T12:00:00Z");
        WorkerPreferences prefs = new WorkerPreferences(
                "worker-prefs-update",
                Set.of("RIDE_HAILING"),
                new TimeSlot(start, end),
                3000.0
        );
        service.savePreferences(prefs);

        // 更新
        WorkerPreferences updated = new WorkerPreferences(
                "worker-prefs-update",
                Set.of("FOOD_DELIVERY"),
                new TimeSlot(start, end),
                8000.0
        );
        service.savePreferences(updated);

        WorkerPreferences found = service.findPreferences("worker-prefs-update");
        assertNotNull(found);
        assertTrue(found.serviceTypes().contains("FOOD_DELIVERY"));
        assertEquals(8000.0, found.maxServiceRadiusMeters(), 0.01);
    }
}
