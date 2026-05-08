package com.justjava.mycommunity.community.controller;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.community.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/subscription/invoice")
@RequiredArgsConstructor
public class InvoiceBackedSubscriptionController {

    private final CommunityService communityService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@RequestParam Long communityId,
                                                         @RequestParam(required = false) BigDecimal amount) {
        String userId = (String) authenticationManager.get("sub");
        communityService.startSubscription(userId, communityId, amount);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscription activated and invoice generated for current billing cycle."
        ));
    }
}
