package com.commonsengine.identity.infrastructure.persistence;

import com.commonsengine.identity.domain.MemberRole;
import com.commonsengine.identity.domain.MemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员 JPA 实体——对应 members 表。
 *
 * <p>与领域模型 {@code Member} 分离，保持领域纯净。
 * Repository 层通过 {@link MemberMapper} 负责 entity ↔ domain 的映射。
 *
 * <p>JPA 要求无参构造器与可变字段，故本类使用普通 Java 类（非 record）。
 */
@Entity
@Table(name = "members")
public class MemberEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "roles", nullable = false, length = 200)
    private String rolesCsv;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "labor_shares", nullable = false)
    private int laborShares;

    /** JPA 要求的无参构造器 */
    public MemberEntity() {
    }

    public MemberEntity(String id, String name, String phone, String rolesCsv,
                        Instant registeredAt, String status, int laborShares) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.rolesCsv = rolesCsv;
        this.registeredAt = registeredAt;
        this.status = status;
        this.laborShares = laborShares;
    }

    /** 将 rolesCsv 解析为角色集合 */
    public Set<MemberRole> roles() {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rolesCsv.split(","))
                .filter(s -> !s.isBlank())
                .map(s -> MemberRole.valueOf(s.trim()))
                .collect(Collectors.toSet());
    }

    /** 解析成员状态 */
    public MemberStatus memberStatus() {
        return MemberStatus.valueOf(status);
    }

    // ── getters / setters ──────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRolesCsv() {
        return rolesCsv;
    }

    public void setRolesCsv(String rolesCsv) {
        this.rolesCsv = rolesCsv;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getLaborShares() {
        return laborShares;
    }

    public void setLaborShares(int laborShares) {
        this.laborShares = laborShares;
    }
}
