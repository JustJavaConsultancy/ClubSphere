package com.justjava.mycommunity.community.controller;

import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final RuntimeService runtimeService;

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
}
