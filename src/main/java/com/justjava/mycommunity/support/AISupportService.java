package com.justjava.mycommunity.support;

import com.justjava.mycommunity.chat.ChatMessage;
import com.justjava.mycommunity.chat.entity.Conversation;
import com.justjava.mycommunity.chat.entity.Message;
import com.justjava.mycommunity.chat.repository.ConversationRepository;
import com.justjava.mycommunity.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AISupportService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SupportFeignClient supportFeignClient;

    @Async
    @Transactional
    public void addAISupportMessage(String agentUserId, String prompt, Long conversationId){
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if(conversation == null) return;
        String response = getAIResponse(prompt);
        if(response.isEmpty()) return;

        Message message = new Message();
        message.setConversation(conversation);
        message.setSenderId(agentUserId);
        message.setContent(response);
        messageRepository.save(message);
        conversation.getMessages().add(message);
        conversationRepository.save(conversation);
        messagingTemplate.convertAndSend("/topic/support/" + conversation.getReceiverId(agentUserId),
                ChatMessage.builder()
                        .conversationId(conversation.getId())
                        .content(response)
                        .senderId(agentUserId)
                        .receiverId(conversation.getReceiverId(agentUserId))
                        .build());
    }

    private String getAIResponse(String prompt){
        System.out.println("Calling AI Support API");
        Map<String, String> map = new HashMap<>();
        map.put("userPrompt", prompt);

        try {
            String response = supportFeignClient.getAiTicketResponse(map);
            if (response != null){
                return response;
            }
        } catch (Exception e) {
            System.err.println("AI Support API unavailable: " + e.getMessage());
        }
        return "";
    }

    public String supportChat(String prompt, String userId){
        prompt = prompt + " UserId: " + userId; //Important for AI to know which user to attach to the ticket if one is created
        Map<String, String> map = new HashMap<>();
        map.put("userPrompt", prompt);

        System.out.println("Calling AI Support API");
        try {
            String response = supportFeignClient.supportChat(map);
            if (response != null){
                return response;
            }
        } catch (Exception e) {
            System.err.println("AI Support API unavailable: " + e.getMessage());
        }
        return "";
    }

}
