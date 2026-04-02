package com.justjava.mycommunity.support;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "supportFeignClient", url="${app.ai-url}")
public interface SupportFeignClient {
    @PostMapping("/support")
    String postAiMessage(@RequestBody String request);

    @PostMapping("/community-chat")
    String supportChat(@RequestBody Map<String, ?> request);

    @PostMapping("/community/ticket")
    String getAiTicketResponse(@RequestBody Map<String, ?> request);
}
