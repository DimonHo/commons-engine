package com.commonsengine.identity.infrastructure.persistence;

import com.commonsengine.platform.domain.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 劳动者档案 JPA 实体——对应 worker_profiles 表。
 *
 * <p>serviceTypes 以逗号分隔的字符串存储。
 */
@Entity
@Table(name = "worker_profiles")
public class WorkerProfileEntity {

    @Id
    @Column(name = "member_id", length = 36)
    private String memberId;

    @Column(name = "service_types", nullable = false, length = 200)
    private String serviceTypesCsv;

    @Column(name = "work_region", nullable = false, length = 100)
    private String workRegion;

    @Column(name = "license_number", length = 100)
    private String licenseNumber;

    @Column(name = "rating", nullable = false)
    private double rating;

    @Column(name = "total_completed_orders", nullable = false)
    private int totalCompletedOrders;

    /** JPA 要求的无参构造器 */
    public WorkerProfileEntity() {
    }

    public WorkerProfileEntity(String memberId, String serviceTypesCsv, String workRegion,
                               String licenseNumber, double rating, int totalCompletedOrders) {
        this.memberId = memberId;
        this.serviceTypesCsv = serviceTypesCsv;
        this.workRegion = workRegion;
        this.licenseNumber = licenseNumber;
        this.rating = rating;
        this.totalCompletedOrders = totalCompletedOrders;
    }

    /** 将 serviceTypesCsv 解析为服务类型集合 */
    public Set<ServiceType> serviceTypes() {
        if (serviceTypesCsv == null || serviceTypesCsv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(serviceTypesCsv.split(","))
                .filter(s -> !s.isBlank())
                .map(s -> ServiceType.valueOf(s.trim()))
                .collect(Collectors.toSet());
    }

    // ── getters / setters ──────────────────────────────

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getServiceTypesCsv() {
        return serviceTypesCsv;
    }

    public void setServiceTypesCsv(String serviceTypesCsv) {
        this.serviceTypesCsv = serviceTypesCsv;
    }

    public String getWorkRegion() {
        return workRegion;
    }

    public void setWorkRegion(String workRegion) {
        this.workRegion = workRegion;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public int getTotalCompletedOrders() {
        return totalCompletedOrders;
    }

    public void setTotalCompletedOrders(int totalCompletedOrders) {
        this.totalCompletedOrders = totalCompletedOrders;
    }
}
