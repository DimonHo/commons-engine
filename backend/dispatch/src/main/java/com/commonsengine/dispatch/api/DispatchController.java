package com.commonsengine.dispatch.api;

import com.commonsengine.dispatch.domain.Model.DispatchTask;
import com.commonsengine.dispatch.domain.Model.RouteSuggestion;
import com.commonsengine.dispatch.domain.Model.TaskStatus;
import com.commonsengine.dispatch.domain.Model.WorkerPreferences;
import com.commonsengine.dispatch.service.DispatchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 调度 REST API（#66）
 *
 * 端点：
 * - POST   /api/v1/dispatch/tasks                 创建派单任务
 * - GET    /api/v1/dispatch/tasks/pending         查询待分配任务
 * - POST   /api/v1/dispatch/tasks/{id}/assign     分配任务
 * - POST   /api/v1/dispatch/preferences           保存劳动者偏好
 * - GET    /api/v1/dispatch/preferences/{workerId} 查询偏好
 * - POST   /api/v1/dispatch/optimize-route         路径优化
 */
@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {

    private final DispatchService service;

    public DispatchController(DispatchService service) {
        this.service = service;
    }

    @PostMapping("/tasks")
    public ResponseEntity<DispatchTaskResponse> createTask(
            @RequestBody DispatchTaskRequest request) {
        DispatchTask t = service.createTask(request.serviceType(), request.pickup().lat(),
                request.pickup().lng());
        return ResponseEntity.status(HttpStatus.CREATED).body(DispatchTaskResponse.from(t));
    }

    @GetMapping("/tasks/pending")
    public List<DispatchTaskResponse> pendingTasks() {
        return service.findPendingTasks().stream()
                .map(DispatchTaskResponse::from)
                .toList();
    }

    @PostMapping("/tasks/{id}/assign")
    public DispatchTaskResponse assign(@PathVariable String id,
                                        @RequestBody AssignRequest request) {
        DispatchTask t = service.assignTask(id, request.workerId());
        return DispatchTaskResponse.from(t);
    }

    @PostMapping("/preferences")
    public WorkerPreferencesResponse savePreferences(
            @RequestBody WorkerPreferencesRequest request) {
        WorkerPreferences p = new WorkerPreferences(
                request.workerId(),
                request.serviceTypes(),
                request.workingHours(),
                request.maxServiceRadiusMeters()
        );
        WorkerPreferences saved = service.savePreferences(p);
        return WorkerPreferencesResponse.from(saved);
    }

    @GetMapping("/preferences/{workerId}")
    public WorkerPreferencesResponse getPreferences(@PathVariable String workerId) {
        return WorkerPreferencesResponse.from(service.findPreferences(workerId));
    }

    @PostMapping("/optimize-route")
    public RouteSuggestionResponse optimizeRoute(@RequestBody OptimizeRouteRequest request) {
        List<double[]> points = request.visits().stream()
                .map(v -> new double[]{v.lat(), v.lng()})
                .toList();
        RouteSuggestion suggestion = service.optimizeRoute(
                request.origin().lat(),
                request.origin().lng(),
                points,
                request.workerId()
        );
        return RouteSuggestionResponse.from(suggestion);
    }

    // ── DTO records ────────────────────────────────────────

    public record GeoPointDto(double lat, double lng) {
    }

    public record TimeSlotDto(Instant start, Instant end) {
    }

    public record DispatchTaskRequest(
            @NotBlank String serviceType,
            @NotNull GeoPointDto pickup
    ) {
    }

    public record DispatchTaskResponse(
            String id,
            String serviceType,
            GeoPointDto pickup,
            String assignedWorkerId,
            TaskStatus status,
            Instant createdAt
    ) {
        static DispatchTaskResponse from(DispatchTask t) {
            return new DispatchTaskResponse(
                    t.id(),
                    t.serviceType(),
                    new GeoPointDto(t.pickupLat(), t.pickupLng()),
                    t.assignedWorkerId(),
                    t.status(),
                    t.createdAt()
            );
        }
    }

    public record WorkerPreferencesRequest(
            @NotBlank String workerId,
            Set<String> serviceTypes,
            com.commonsengine.dispatch.domain.Model.TimeSlot workingHours,
            double maxServiceRadiusMeters
    ) {
    }

    public record WorkerPreferencesResponse(
            String workerId,
            Set<String> serviceTypes,
            com.commonsengine.dispatch.domain.Model.TimeSlot workingHours,
            double maxServiceRadiusMeters
    ) {
        static WorkerPreferencesResponse from(WorkerPreferences p) {
            return new WorkerPreferencesResponse(
                    p.workerId(), p.serviceTypes(), p.workingHours(), p.maxServiceRadiusMeters()
            );
        }
    }

    public record OptimizeRouteRequest(
            @NotBlank String workerId,
            @NotNull GeoPointDto origin,
            List<GeoPointDto> visits
    ) {
    }

    public record RouteSuggestionResponse(
            String workerId,
            List<VisitDto> visits,
            double totalDistanceMeters,
            long estimatedDurationMinutes,
            String strategy
    ) {
        static RouteSuggestionResponse from(RouteSuggestion s) {
            List<VisitDto> visits = s.visits().stream()
                    .map(v -> new VisitDto(v.sequence(), v.lat(), v.lng(), v.taskId(), v.label()))
                    .toList();
            return new RouteSuggestionResponse(s.workerId(), visits,
                    s.totalDistanceMeters(), s.estimatedDurationMinutes(), s.strategy());
        }
    }

    public record VisitDto(int sequence, double lat, double lng, String taskId, String label) {
    }

    public record AssignRequest(@NotBlank String workerId) {
    }
}
