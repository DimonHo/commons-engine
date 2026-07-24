package com.commonsengine.identity.domain;

import com.commonsengine.platform.domain.ServiceType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 身份与会员领域模型（identity 模块）。
 *
 * <p>合作社成员是平台的真正主人——每个成员有角色（可以是多个）、投票权和劳动份额记录。
 * 这是公地引擎与资本平台的根本区别：成员不是"用户"，是所有者。
 */
public final class Model {

    private Model() {
    }

    /**
     * 身份验证状态。
     */
    public enum VerificationStatus {
        /** 未验证 */
        UNVERIFIED,
        /** 验证中 */
        PENDING,
        /** 已验证 */
        VERIFIED,
        /** 验证失败 */
        REJECTED,
    }

    /**
     * 身份验证类型。
     */
    public enum VerificationType {
        /** 手机号验证 */
        PHONE,
        /** 身份证验证 */
        ID_CARD,
        /** 行业准入证件验证 */
        LICENSE,
        /** 背景调查 */
        BACKGROUND_CHECK,
    }

    /**
     * 成员 ID（UUID 字符串）。
     */
    public record MemberId(String value) {
        public MemberId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("MemberId 不能为空");
            }
        }

        public static MemberId random() {
            return new MemberId(UUID.randomUUID().toString());
        }
    }

    /**
     * 合作社成员——平台的真正主人。
     *
     * @param id            成员 ID
     * @param name          姓名
     * @param phone         手机号（加密存储，仅用于服务运行）
     * @param roles         角色集合
     * @param registeredAt  注册时间
     * @param status        成员状态
     * @param laborShares   劳动份额（用于利润分配，非股权）
     */
    public record Member(
            MemberId id,
            String name,
            String phone,
            Set<MemberRole> roles,
            Instant registeredAt,
            MemberStatus status,
            int laborShares
    ) {
        public Member {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("成员姓名不能为空");
            }
            if (phone == null || phone.isBlank()) {
                throw new IllegalArgumentException("手机号不能为空");
            }
            if (roles == null || roles.isEmpty()) {
                throw new IllegalArgumentException("至少需要一个角色");
            }
            if (registeredAt == null) {
                registeredAt = Instant.now();
            }
            if (status == null) {
                status = MemberStatus.ACTIVE;
            }
        }
    }

    /**
     * 劳动者档案——Member 的扩展信息。
     *
     * @param memberId             所属成员 ID
     * @param serviceTypes         可提供的服务类型
     * @param workRegion           工作区域（城市/区）
     * @param licenseNumber        行业准入证件号（如网约车驾驶员证），加密存储，可空
     * @param rating               当前评分
     * @param totalCompletedOrders 累计完成订单数
     */
    public record WorkerProfile(
            MemberId memberId,
            Set<ServiceType> serviceTypes,
            String workRegion,
            String licenseNumber,
            double rating,
            int totalCompletedOrders
    ) {
        public WorkerProfile {
            if (serviceTypes == null) {
                serviceTypes = Set.of();
            }
            if (rating == 0.0) {
                rating = 5.0;
            }
        }
    }

    /**
     * 身份验证记录。
     *
     * @param memberId    所属成员 ID
     * @param type        验证类型
     * @param status      验证状态
     * @param submittedAt 提交时间
     * @param verifiedAt  验证完成时间（可空）
     * @param notes       备注（可空）
     */
    public record IdentityVerification(
            MemberId memberId,
            VerificationType type,
            VerificationStatus status,
            Instant submittedAt,
            Instant verifiedAt,
            String notes
    ) {
        public IdentityVerification {
            if (submittedAt == null) {
                submittedAt = Instant.now();
            }
        }
    }
}
