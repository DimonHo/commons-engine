package com.commonsengine.identity.api;

import com.commonsengine.identity.domain.Model.Member;
import com.commonsengine.identity.domain.Model.MemberId;
import com.commonsengine.identity.domain.MemberRole;
import com.commonsengine.identity.domain.Model.WorkerProfile;
import com.commonsengine.identity.service.MembershipService;
import com.commonsengine.platform.domain.ServiceType;
import com.commonsengine.platform.support.EnumParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员管理 REST API。
 */
@RestController
@RequestMapping("/api/v1/members")
public class MembershipController {

    private final MembershipService service;

    public MembershipController(MembershipService service) {
        this.service = service;
    }

    /** 注册新成员 */
    @PostMapping("/register")
    public MemberResponse register(@Valid @RequestBody RegisterRequest body) {
        Member member = service.register(
                body.name(),
                body.phone(),
                EnumParser.parseAll(body.roles(), MemberRole.class)
        );
        return toResponse(member);
    }

    /** 查询成员详情 */
    @GetMapping("/{id}")
    public MemberResponse findById(@PathVariable String id) {
        return service.findById(new MemberId(id))
                .map(MembershipController::toResponse)
                .orElse(null);
    }

    /** 查询全部成员 */
    @GetMapping
    public List<MemberResponse> findAll() {
        return service.findAll().stream()
                .map(MembershipController::toResponse)
                .toList();
    }

    /** 统计 */
    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        Map<MemberRole, Integer> statistics = service.roleStatistics();
        Map<String, Integer> result = new LinkedHashMap<>();
        statistics.forEach((role, count) -> result.put(role.name(), count));
        return result;
    }

    /** 注册劳动者档案 */
    @PostMapping("/{id}/worker-profile")
    public WorkerProfileResponse registerWorkerProfile(@PathVariable String id,
                                                       @Valid @RequestBody WorkerProfileRequest body) {
        WorkerProfile profile = new WorkerProfile(
                new MemberId(id),
                EnumParser.parseAll(body.serviceTypes(), ServiceType.class),
                body.workRegion(),
                body.licenseNumber(),
                5.0,
                0
        );
        WorkerProfile saved = service.registerWorkerProfile(profile);
        return new WorkerProfileResponse(
                saved.memberId().value(),
                saved.serviceTypes().stream().map(Enum::name).toList(),
                saved.workRegion(),
                saved.rating(),
                saved.totalCompletedOrders()
        );
    }

    // ── Domain → Response 转换 ──────────────────────────

    private static MemberResponse toResponse(Member m) {
        return new MemberResponse(
                m.id().value(),
                m.name(),
                m.roles().stream().map(Enum::name).toList(),
                m.status().name(),
                m.registeredAt().toString(),
                m.laborShares()
        );
    }

    // ── DTO ──────────────────────────────────────────────

    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank String phone,
            @NotEmpty List<String> roles
    ) {
    }

    public record MemberResponse(
            String id,
            String name,
            List<String> roles,
            String status,
            String registeredAt,
            int laborShares
    ) {
    }

    public record WorkerProfileRequest(
            @NotEmpty List<String> serviceTypes,
            @NotBlank String workRegion,
            String licenseNumber
    ) {
    }

    public record WorkerProfileResponse(
            String memberId,
            List<String> serviceTypes,
            String workRegion,
            double rating,
            int totalCompletedOrders
    ) {
    }
}
