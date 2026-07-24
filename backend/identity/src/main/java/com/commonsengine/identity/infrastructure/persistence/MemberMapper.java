package com.commonsengine.identity.infrastructure.persistence;

import com.commonsengine.identity.domain.MemberRole;
import com.commonsengine.identity.domain.Model.Member;
import com.commonsengine.identity.domain.Model.MemberId;
import com.commonsengine.identity.domain.MemberStatus;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Member entity ↔ domain 映射器。
 *
 * <p>Kotlin 版使用扩展函数 {@code MemberEntity.toDomain()} / {@code Member.toEntity()}，
 * Java 版等价为 {@code MemberMapper} 的静态方法。
 */
public final class MemberMapper {

    private MemberMapper() {
    }

    /** Entity → Domain */
    public static Member toDomain(MemberEntity e) {
        return new Member(
                new MemberId(e.getId()),
                e.getName(),
                e.getPhone(),
                e.roles(),
                e.getRegisteredAt(),
                e.memberStatus(),
                e.getLaborShares()
        );
    }

    /** Domain → Entity（新建） */
    public static MemberEntity toEntity(Member m) {
        String rolesCsv = m.roles().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        return new MemberEntity(
                m.id().value(),
                m.name(),
                m.phone(),
                rolesCsv,
                m.registeredAt(),
                m.status().name(),
                m.laborShares()
        );
    }

    /** 将成员状态名称写回 entity（用于状态变更） */
    public static void assignStatus(MemberEntity e, MemberStatus status) {
        e.setStatus(status.name());
    }
}
