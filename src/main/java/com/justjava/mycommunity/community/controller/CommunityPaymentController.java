package com.justjava.mycommunity.community.controller;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.community.CommunityService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
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

    // 🔹 Subscribe (HTMX — called from community page)
    @PostMapping("/subscribe")
    @ResponseBody
    public String subscribe(@RequestParam("communityId") Long communityId,
                            @RequestParam("amount") BigDecimal amount) {
        try {
            String userId = (String) authenticationManager.get("sub");

            communityService.startSubscription(userId, communityId, amount);

            return "<div class='text-green-600 font-medium'>✅ Subscription started successfully!</div>";
        } catch (IllegalStateException e) {
            return "<div class='text-amber-600 font-medium'>⚠️ " + e.getMessage() + "</div>";
        } catch (SecurityException e) {
            return "<div class='text-red-600 font-medium'>❌ " + e.getMessage() + "</div>";
        } catch (Exception e) {
            return "<div class='text-red-600 font-medium'>❌ Error: " + e.getMessage() + "</div>";
        }
    }

    // 🔹 Cancel subscription
    @PostMapping("/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelSubscription(@RequestParam("subscriptionId") Long subscriptionId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String userId = (String) authenticationManager.get("sub");
            communityService.cancelSubscription(userId, subscriptionId);
            response.put("success", true);
            response.put("message", "Subscription cancelled successfully");
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(403).body(response);
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to cancel subscription");
            return ResponseEntity.status(500).body(response);
        }
    }

    // 🔹 Admin: community subscriptions JSON (for HTMX)
    @GetMapping("/subscriptions/list")
    @ResponseBody
    public List<Map<String, Object>> communitySubscriptionsList(@RequestParam("communityId") Long communityId) {
        return communityService.getCommunitySubscriptions(communityId);
    }

    // 🔹 User: My Subscriptions page (all communities)
    @GetMapping("/my-subscriptions")
    public String mySubscriptionsPage(Model model) {

        String userId = (String) authenticationManager.get("sub");

        List<Map<String, Object>> subscriptions =
                communityService.getUserSubscriptions(userId);

        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("currentPath", "/subscription/my-subscriptions");
        model.addAttribute("userId", userId);
        model.addAttribute("usersName", authenticationManager.get("name"));

        return "my-subscriptions";
    }

    // 🔹 User: My Subscriptions JSON (for HTMX)
    @GetMapping("/my-subscriptions/data")
    @ResponseBody
    public List<Map<String, Object>> mySubscriptionsData() {
        String userId = (String) authenticationManager.get("sub");
        return communityService.getUserSubscriptions(userId);
    }

    // 🔹 Mobile: My Subscriptions page
    @GetMapping("/mobile/my-subscriptions")
    public String mobileMySubscriptionsPage(Model model) {

        String userId = (String) authenticationManager.get("sub");

        List<Map<String, Object>> subscriptions =
                communityService.getUserSubscriptions(userId);

        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("currentPath", "/subscription/mobile/my-subscriptions");
        model.addAttribute("userId", userId);
        model.addAttribute("usersName", authenticationManager.get("name"));

        return "mobile-my-subscriptions";
    }
}
