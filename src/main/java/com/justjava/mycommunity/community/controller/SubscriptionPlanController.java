package com.justjava.mycommunity.community.controller;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.SubscriptionPlan;
import com.justjava.mycommunity.community.dto.BillingCycle;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/subscription/plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final CommunityService communityService;
    private final AuthenticationManager authenticationManager;
    private final CommunityMembershipRepository communityMembershipRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> upsertPlan(@RequestParam Long communityId,
                                                           @RequestParam BillingCycle billingCycle,
                                                           @RequestParam BigDecimal amount) {
        String userId = (String) authenticationManager.get("sub");
        boolean allowed = authenticationManager.isAdmin() ||
                communityMembershipRepository.isUserCommunityAdmin(userId, communityId);
        if (!allowed) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Only community admins can set subscription plans"));
        }

        SubscriptionPlan plan = communityService.upsertSubscriptionPlan(communityId, billingCycle, amount);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("planId", plan.getId());
        response.put("communityId", plan.getCommunityId());
        response.put("billingCycle", plan.getBillingCycle());
        response.put("amount", plan.getAmount());
        response.put("active", plan.getActive());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActivePlan(@RequestParam Long communityId) {
        return communityService.getActiveSubscriptionPlan(communityId)
                .map(plan -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("planId", plan.getId());
                    response.put("communityId", plan.getCommunityId());
                    response.put("billingCycle", plan.getBillingCycle());
                    response.put("amount", plan.getAmount());
                    response.put("active", plan.getActive());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("success", false);
                    response.put("message", "No active subscription plan configured");
                    return ResponseEntity.ok(response);
                });
    }
}
