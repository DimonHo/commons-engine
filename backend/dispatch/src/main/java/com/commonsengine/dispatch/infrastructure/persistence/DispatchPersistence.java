package com.commonsengine.dispatch.infrastructure.persistence;

import com.commonsengine.dispatch.domain.Model.TaskStatus;
import com.commonsengine.dispatch.domain.Model.TimeSlot;
import com.commonsengine.dispatch.domain.Model.WorkerPreferences;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Set;

/**
 * DispatchTaskEntity + WorkerPreferencesEntity + Mappers。
 *
 * 聚合在一个文件以匹配 Kotlin 原结构（DispatchPersistence.kt）。
 * JSON 列（service_types / working_hours）通过 ObjectMapper 序列化。
 */
public final class DispatchPersistence {

    private DispatchPersistence() {
    }

    @Entity
    @Table(name = "dispatch_tasks")
    public static class DispatchTaskEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "id", columnDefinition = "uuid")
        private String id;

        @Column(name = "service_type", nullable = false)
        private String serviceType;

        @Column(name = "pickup_lat", nullable = false)
        private double pickupLat;

        @Column(name = "pickup_lng", nullable = false)
        private double pickupLng;

        @Column(name = "assigned_worker_id")
        private String assignedWorkerId;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private TaskStatus status;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        public DispatchTaskEntity() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
        }

        public double getPickupLat() {
            return pickupLat;
        }

        public void setPickupLat(double pickupLat) {
            this.pickupLat = pickupLat;
        }

        public double getPickupLng() {
            return pickupLng;
        }

        public void setPickupLng(double pickupLng) {
            this.pickupLng = pickupLng;
        }

        public String getAssignedWorkerId() {
            return assignedWorkerId;
        }

        public void setAssignedWorkerId(String assignedWorkerId) {
            this.assignedWorkerId = assignedWorkerId;
        }

        public TaskStatus getStatus() {
            return status;
        }

        public void setStatus(TaskStatus status) {
            this.status = status;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }

    @Entity
    @Table(name = "worker_preferences")
    public static class WorkerPreferencesEntity {

        @Id
        @Column(name = "worker_id")
        private String workerId;

        @Column(name = "service_types", columnDefinition = "jsonb")
        private String serviceTypesJson;

        @Column(name = "working_hours", columnDefinition = "jsonb")
        private String workingHoursJson;

        @Column(name = "max_service_radius_meters")
        private double maxServiceRadiusMeters;

        public WorkerPreferencesEntity() {
        }

        public String getWorkerId() {
            return workerId;
        }

        public void setWorkerId(String workerId) {
            this.workerId = workerId;
        }

        public String getServiceTypesJson() {
            return serviceTypesJson;
        }

        public void setServiceTypesJson(String serviceTypesJson) {
            this.serviceTypesJson = serviceTypesJson;
        }

        public String getWorkingHoursJson() {
            return workingHoursJson;
        }

        public void setWorkingHoursJson(String workingHoursJson) {
            this.workingHoursJson = workingHoursJson;
        }

        public double getMaxServiceRadiusMeters() {
            return maxServiceRadiusMeters;
        }

        public void setMaxServiceRadiusMeters(double maxServiceRadiusMeters) {
            this.maxServiceRadiusMeters = maxServiceRadiusMeters;
        }
    }

    /** DispatchTask 静态映射器 */
    public static final class DispatchTaskMapper {

        private DispatchTaskMapper() {
        }

        public static com.commonsengine.dispatch.domain.Model.DispatchTask toDomain(DispatchTaskEntity e) {
            if (e == null) {
                return null;
            }
            return new com.commonsengine.dispatch.domain.Model.DispatchTask(
                    e.getId(),
                    e.getServiceType(),
                    e.getPickupLat(),
                    e.getPickupLng(),
                    e.getAssignedWorkerId(),
                    e.getStatus(),
                    e.getCreatedAt()
            );
        }

        public static DispatchTaskEntity toEntity(
                com.commonsengine.dispatch.domain.Model.DispatchTask t) {
            DispatchTaskEntity e = new DispatchTaskEntity();
            e.setId(t.id());
            e.setServiceType(t.serviceType());
            e.setPickupLat(t.pickupLat());
            e.setPickupLng(t.pickupLng());
            e.setAssignedWorkerId(t.assignedWorkerId());
            e.setStatus(t.status());
            e.setCreatedAt(t.createdAt());
            return e;
        }
    }

    /** WorkerPreferences 映射器——依赖 ObjectMapper 处理 JSON 列 */
    public static final class WorkerPreferencesMapper {

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final ObjectMapper MAPPER2 = MAPPER; // alias
        private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
        };

        private WorkerPreferencesMapper() {
        }

        public static WorkerPreferences toDomain(WorkerPreferencesEntity e) {
            if (e == null) {
                return null;
            }
            return new WorkerPreferences(
                    e.getWorkerId(),
                    parseStringSet(e.getServiceTypesJson()),
                    parseTimeSlot(e.getWorkingHoursJson()),
                    e.getMaxServiceRadiusMeters()
            );
        }

        public static WorkerPreferencesEntity toEntity(WorkerPreferences p) {
            WorkerPreferencesEntity e = new WorkerPreferencesEntity();
            e.setWorkerId(p.workerId());
            e.setServiceTypesJson(toJson(p.serviceTypes()));
            e.setWorkingHoursJson(toJson(p.workingHours()));
            e.setMaxServiceRadiusMeters(p.maxServiceRadiusMeters());
            return e;
        }

        private static Set<String> parseStringSet(String json) {
            if (json == null || json.isBlank()) {
                return Set.of();
            }
            try {
                return MAPPER.readValue(json, STRING_SET);
            } catch (Exception ex) {
                return Set.of();
            }
        }

        private static TimeSlot parseTimeSlot(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                return MAPPER.readValue(json, TimeSlot.class);
            } catch (Exception ex) {
                return null;
            }
        }

        private static String toJson(Object value) {
            if (value == null) {
                return null;
            }
            try {
                return MAPPER.writeValueAsString(value);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
