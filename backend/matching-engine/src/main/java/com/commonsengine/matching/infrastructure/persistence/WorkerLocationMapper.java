package com.commonsengine.matching.infrastructure.persistence;

import com.commonsengine.platform.geo.GeoPoint;

/**
 * WorkerLocationEntity → 领域模型映射器（Kotlin extension → Java static Mapper）。
 *
 * 仅 toDomain（单向）：位置更新走 upsert，不需要反向映射。
 * 纯静态工具类，无需 Spring 管理。
 */
public final class WorkerLocationMapper {

    private WorkerLocationMapper() {
    }

    /**
     * 将实体转换为领域 GeoPoint。
     *
     * @param e 实体
     * @return 劳动者当前坐标
     */
    public static GeoPoint toDomain(WorkerLocationEntity e) {
        if (e == null) {
            return null;
        }
        return new GeoPoint(e.getLat(), e.getLng());
    }
}
