package com.justjava.mycommunity.community.controller;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.SubscriptionPlan;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class CommunityPaymentController {

    private final RuntimeService runtimeService;
    private final CommunityService communityService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/webhook/payment")
    public void handleWebhook(@RequestBody Map<String, Object> payload) {

        String paymentRef = (String) payload.get("reference");
        String status = (String) payload.get("status");
        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceBusinessKey(paymentRef)
                .messageEventSubscriptionName("payment-confirmation")
                .singleResult();

        runtimeService.messageEventReceived(
                "payment-confirmation",
                execution.getId()
        );
    }
    // 🔹 Load subscription page

    // 🔹 Load page
    @GetMapping("/subscriptions")
    public String subscriptionPage(@PathVariable Long communityId, Model model) {
        model.addAttribute("communityId", communityId);
        return "community/subscriptions";
    }
    // 🔹 Subscribe (HTMX)
    @PostMapping("/subscribe")
    @ResponseBody
    public String subscribe(@PathVariable Long communityId,
                            @RequestParam BigDecimal amount) {

        String userId = (String) authenticationManager.get("sub");

        communityService.startSubscription(userId, communityId, amount);

        return "<div class='alert alert-success'>Subscription started. Complete payment.</div>";
    }
    @GetMapping("/subscriptions/list")
    public String communitySubscriptions(@PathVariable Long communityId, Model model) {

        List<Map<String, Object>> subscriptions =
                communityService.getCommunitySubscriptions(communityId);

        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("communityId", communityId);

        return "community/subscription-list";
    }
    @GetMapping("/my-subscriptions")
    public String mySubscriptions(Model model) {

        String userId = (String) authenticationManager.get("sub");

        List<Map<String, Object>> subscriptions =
                communityService.getUserSubscriptions(userId);

        model.addAttribute("subscriptions", subscriptions);

        return "community/my-subscriptions";
    }
}
