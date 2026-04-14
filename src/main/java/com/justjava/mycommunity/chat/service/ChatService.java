package com.justjava.mycommunity.chat.service;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.ChatMessage;
import com.justjava.mycommunity.chat.dto.ConversationDto;
import com.justjava.mycommunity.chat.entity.Conversation;
import com.justjava.mycommunity.chat.entity.Message;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.ConversationRepository;
import com.justjava.mycommunity.chat.repository.MessageRepository;
import com.justjava.mycommunity.userManagement.UserRepository;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.organization.Channel;
import com.justjava.mycommunity.organization.TownHall;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.justjava.mycommunity.util.MappingUtils.mapUsersToDTO;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AuthenticationManager authenticationManager;
    private final SimpMessagingTemplate messagingTemplate;


    public List<UserDTO> getUsers() {
        String loginUser = (String) authenticationManager.get("sub");
        return mapUsersToDTO(userRepository.findAll().stream()
                .filter(user -> (!loginUser.equalsIgnoreCase(user.getUserId())))
                .toList());
    }

    public List<UserDTO> getPublicUsers(){
        String loginUser = (String) authenticationManager.get("sub");
        return mapUsersToDTO(userRepository.findAll().stream()
                .filter(user -> Boolean.FALSE.equals(user.getPrivacy()))
                .filter(user -> (!loginUser.equalsIgnoreCase(user.getUserId())))
                .toList());
    }

    @Transactional
    public List<ConversationDto> getConversations(String userId) {
        List<Conversation> conversations = conversationRepository.findAllByMembers_UserId(userId);
        return mapConversationsToDTO(conversations, userId);
    }

    @Transactional
    public Conversation createConversation(List<String> conversationIds) {
        Optional<Conversation> conversation1 = conversationRepository.findConversationByExactUserIds(conversationIds, conversationIds.size());
        if (conversation1.isPresent()) {
            return conversation1.get();
        }
        Set<User> users = userRepository.findAllByUserIdIn(conversationIds);
        Conversation conversation = new Conversation();
        if (users.size() > 2) {
            conversation.setGroup(true);
        }
        conversation = conversationRepository.save(conversation);
        conversation.setMembers(users);
        conversation = conversationRepository.save(conversation);
        return conversation;
    }

    public void sendTownHallMessage(ChatMessage message) {
        User user = userRepository.findByUserId(message.getSenderId());
        TownHall townHall = user.getOrganization().getTownHall();
        String destination = "/topic/townhall/" + townHall.getId();
        messagingTemplate.convertAndSend(destination, message);
    }

    public void sendChannelMessage(ChatMessage message) {
        User user = userRepository.findByUserId(message.getSenderId());
        Channel channel = user.getOrganization().getChannel();
        if (user.getIsOrgAdmin()) {
            String destination = "/topic/townhall/" + channel.getId();
            messagingTemplate.convertAndSend(destination, message);
        }
    }

    @Async
    @Transactional
    public void newMessage(ChatMessage chatMessage) {
//        Optional<Conversation> conversation = conversationRepository.findById(chatMessage.getConversationId());
        List<String> userIds = List.of(chatMessage.getSenderId(),chatMessage.getReceiverId());
        Optional<Conversation> conversation = conversationRepository
                .findConversationByExactUserIds(userIds, 2);
//        User user = userRepository.findByUserId(chatMessage.getSenderId());
        if (conversation.isPresent()) {
            Message message = new Message();
            message.setConversation(conversation.get());
            message.setSenderId(chatMessage.getSenderId());
            message.setContent(chatMessage.getContent());
            conversation.get().getMessages().add(message);
//            user.getMessages().add(message);
            messageRepository.save(message);

        }else {
            Conversation newConversation = createConversation(userIds);
            Message message = new Message();
            message.setConversation(newConversation);
            message.setSenderId(chatMessage.getSenderId());
            message.setContent(chatMessage.getContent());
            newConversation.getMessages().add(message);
            messageRepository.save(message);
        }
    }

    @Transactional
    public void sendSupportMessage(ChatMessage chatMessage) {

        Conversation conversation = conversationRepository.findById(chatMessage.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        Message message = new Message();
        message.setConversation(conversation);
        message.setSenderId(chatMessage.getSenderId());
        message.setContent(chatMessage.getContent());
        message.setAttachmentUrl(chatMessage.getAttachmentUrl());
        messageRepository.save(message);
        conversation.getMessages().add(message);
        conversationRepository.save(conversation);
    }

    public List<ConversationDto> mapConversationsToDTO(List<Conversation> conversations, String userId) {
        List<ConversationDto> dtos = new ArrayList<>();
        for (Conversation conversation : conversations) {
            ConversationDto conversationDto = new ConversationDto();
            conversationDto.setId(conversation.getId());
            conversationDto.setGroup(conversation.getGroup());
            conversationDto.setCreatedAt(conversation.getCreatedAt());
            conversationDto.setMessages(mapMessagesToDTO(conversation.getMessages(), userId));
            if (conversation.getGroup()) {
                conversationDto.setTitle(conversation.getTitle());
            }else {
                conversationDto.setReceiverId(conversation.getReceiverId(userId));
                conversationDto.setReceiverName(conversation.getReceiverName(userId));
                conversationDto.setTitle(conversation.getReceiverName(userId));
            }
            dtos.add(conversationDto);
        }
        return dtos;
    }

    private List<ConversationDto.MessageDto> mapMessagesToDTO(List<Message> messages, String userId) {
        List<ConversationDto.MessageDto> messageDtos = new ArrayList<>();
        for (Message m : messages) {
            ConversationDto.MessageDto messageDto = new ConversationDto.MessageDto();
            messageDto.setContent(m.getContent());
            messageDto.setAttachmentUrl(m.getAttachmentUrl());
            messageDto.setSender(m.getSender(userId));
            messageDto.setSentAt(m.getSentAt());
            messageDtos.add(messageDto);
        }
        return messageDtos;
    }

    @Transactional
    public String deleteConversation(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow(() -> new RuntimeException("Not found"));
        conversation.getMembers().clear();
        conversationRepository.save(conversation);
        conversationRepository.delete(conversation);
        return null;
    }
}
