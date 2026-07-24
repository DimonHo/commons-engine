package com.commonsengine.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.commonsengine.identity.domain.MemberRole;
import com.commonsengine.identity.domain.MemberStatus;
import com.commonsengine.identity.domain.Model.Member;
import com.commonsengine.identity.domain.Model.MemberId;
import com.commonsengine.identity.domain.Model.WorkerProfile;
import com.commonsengine.platform.domain.ServiceType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MembershipServiceTest {

    @Autowired
    private MembershipService service;

    @Test
    void registerCreatesNewMember() {
        Member member = service.register("张三", "13800000001", Set.of(MemberRole.WORKER));

        assertEquals("张三", member.name());
        assertEquals(MemberStatus.ACTIVE, member.status());
        assertTrue(member.roles().contains(MemberRole.WORKER));
    }

    @Test
    void findByIdReturnsRegisteredMember() {
        Member member = service.register("李四", "13800000002", Set.of(MemberRole.CONSUMER));

        Optional<Member> found = service.findById(member.id());
        assertTrue(found.isPresent());
        assertEquals(member.name(), found.get().name());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<Member> found = service.findById(new MemberId("nonexistent"));
        assertTrue(found.isEmpty());
    }

    @Test
    void suspendChangesStatusToSuspended() {
        Member member = service.register("王五", "13800000003", Set.of(MemberRole.WORKER));

        Optional<Member> suspended = service.suspend(member.id(), "违反行为准则");
        assertTrue(suspended.isPresent());
        assertEquals(MemberStatus.SUSPENDED, suspended.get().status());
    }

    @Test
    void withdrawChangesStatusToWithdrawn() {
        Member member = service.register("赵六", "13800000004", Set.of(MemberRole.WORKER));

        Optional<Member> withdrawn = service.withdraw(member.id());
        assertTrue(withdrawn.isPresent());
        assertEquals(MemberStatus.WITHDRAWN, withdrawn.get().status());
    }

    @Test
    void roleStatisticsCountsActiveMembersPerRole() {
        service.register("worker1", "1", Set.of(MemberRole.WORKER));
        service.register("worker2", "2", Set.of(MemberRole.WORKER));
        service.register("consumer1", "3", Set.of(MemberRole.CONSUMER));
        service.register("both", "4", Set.of(MemberRole.WORKER, MemberRole.CONSUMER));

        Map<MemberRole, Integer> stats = service.roleStatistics();
        assertEquals(3, stats.get(MemberRole.WORKER));   // worker1, worker2, both
        assertEquals(2, stats.get(MemberRole.CONSUMER)); // consumer1, both
    }

    @Test
    void registerWorkerProfileSucceedsForExistingMember() {
        Member member = service.register("骑手小王", "13900000001", Set.of(MemberRole.WORKER));
        WorkerProfile profile = new WorkerProfile(
                member.id(),
                Set.of(ServiceType.FOOD_DELIVERY, ServiceType.ERRAND),
                "海淀区",
                null,
                0,
                0
        );

        WorkerProfile saved = service.registerWorkerProfile(profile);
        assertEquals(member.id(), saved.memberId());
        assertTrue(saved.serviceTypes().contains(ServiceType.FOOD_DELIVERY));

        // 验证可回查
        Optional<WorkerProfile> found = service.findWorkerProfile(member.id());
        assertTrue(found.isPresent());
        assertEquals("海淀区", found.get().workRegion());
    }

    @Test
    void registerWorkerProfileFailsForNonExistentMember() {
        WorkerProfile profile = new WorkerProfile(
                new MemberId("ghost"),
                Set.of(ServiceType.RIDE_HAILING),
                "朝阳区",
                null,
                0,
                0
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.registerWorkerProfile(profile));
    }

    @Test
    void registerRejectsBlankName() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.register("", "123", Set.of(MemberRole.WORKER)));
    }

    @Test
    void registerRejectsEmptyRoles() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.register("test", "123", Set.of()));
    }
}
