package com.commonsengine.identity.infrastructure.persistence;

import com.commonsengine.identity.domain.Model.WorkerProfile;

import java.util.stream.Collectors;

/**
 * WorkerProfile entity ↔ domain 映射器。
 */
public final class WorkerProfileMapper {

    private WorkerProfileMapper() {
    }

    /** Entity → Domain */
    public static WorkerProfile toDomain(WorkerProfileEntity e) {
        return new WorkerProfile(
                new com.commonsengine.identity.domain.Model.MemberId(e.getMemberId()),
                e.serviceTypes(),
                e.getWorkRegion(),
                e.getLicenseNumber(),
                e.getRating(),
                e.getTotalCompletedOrders()
        );
    }

    /** Domain → Entity（新建） */
    public static WorkerProfileEntity toEntity(WorkerProfile p) {
        String serviceTypesCsv = p.serviceTypes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        return new WorkerProfileEntity(
                p.memberId().value(),
                serviceTypesCsv,
                p.workRegion(),
                p.licenseNumber(),
                p.rating(),
                p.totalCompletedOrders()
        );
    }
}
