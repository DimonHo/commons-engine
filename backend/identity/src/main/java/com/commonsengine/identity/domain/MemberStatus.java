package com.commonsengine.identity.domain;

/**
 * 成员状态。
 */
public enum MemberStatus {
    /** 活跃 */
    ACTIVE,
    /** 暂停（违反规则/调查中） */
    SUSPENDED,
    /** 退社 */
    WITHDRAWN,
}
