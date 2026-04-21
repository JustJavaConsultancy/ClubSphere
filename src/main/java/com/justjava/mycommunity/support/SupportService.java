package com.justjava.mycommunity.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.entity.Conversation;
import com.justjava.mycommunity.chat.repository.ConversationRepository;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SupportService {

    private final TicketRepository ticketRepository;
    private final AuthenticationManager authenticationManager;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ChatService chatService;
    private final AISupportService aISupportService;

    public SupportService(TicketRepository ticketRepository, AuthenticationManager authenticationManager,
                          ObjectMapper objectMapper, UserRepository userRepository, ConversationRepository conversationRepository,
                          ChatService chatService, AISupportService aISupportService){

        this.ticketRepository = ticketRepository;
        this.authenticationManager = authenticationManager;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.chatService = chatService;
        this.aISupportService = aISupportService;
    }

    @Transactional
    public void createTicket(Map<String, Object> formData){
        String loginUser;
        if (formData.get("userId") == null ){
            loginUser = (String) authenticationManager.get("sub");
            formData.put("userId", loginUser);
        } else
            loginUser = formData.get("userId").toString();

        Ticket ticket = objectMapper.convertValue(formData, Ticket.class);

        Conversation conversation = new Conversation();
        conversation.setTitle("Support");
        conversation.setMembers(userRepository.findAllByUserIdIn(List.of(loginUser)));
        conversation = conversationRepository.save(conversation);
        ticket.setConversation(conversation);
        ticketRepository.save(ticket);
    }

    public List<Ticket> getTickets(){
        String loginUser = (String) authenticationManager.get("sub");
        List<Ticket> tickets = getTicketByUserId(loginUser);

        if (authenticationManager.isSupportAdmin()){
            tickets = getAgentTickets(loginUser);
        }

//        System.out.println("This is the list of my tickets:::" + tickets);
        return tickets;
    }

    public List<Ticket> getAgentTickets(String agentId){
        return ticketRepository.findByAgentUserIdOrderByLastUpdatedDesc(agentId);
    }

    public List<Ticket> getAllTickets(){
        return ticketRepository.findAll();
    }

    public Ticket getTicketById(Long id){
        return ticketRepository.findById(id).orElse(null);
    }

    public List<Ticket> getTicketByUserId(String userId){
        return ticketRepository.findByUserIdOrderByLastUpdatedDesc(userId);
    }

    public List<Ticket> getAllUnassignedTicket(){
        return ticketRepository.findByAgentUserIdIsNullOrderByLastUpdatedDesc();
    }

    public void updateTicketWithAgentUser(String agentUserId, Ticket ticket){
        ticket.setAgentUserId(agentUserId);
        ticketRepository.save(ticket);
    }

    @Transactional
    public TicketDTO getSingleTicket(Long id, String userId){
        Ticket ticket = getTicketById(id);
        TicketDTO ticketDTO = mapTicketToDTO(ticket);

        ticketDTO.setConversation(chatService.mapConversationsToDTO(Collections.singletonList(ticket.getConversation()), userId).getFirst());
        return ticketDTO;
    }

    @Transactional
    public void claimTicket(Long ticketId, String agentUserId){
        Ticket ticket = getTicketById(ticketId);
        if (ticket == null) throw new EntityNotFoundException("Ticket Not Found");
        ticket.setAgentUserId(agentUserId);
        ticket.setStatus("Open");
        Conversation conversation = ticket.getConversation();
        conversation.getMembers().add(userRepository.findByUserId(agentUserId));
        conversation = conversationRepository.save(conversation);
        ticketRepository.save(ticket);
        aISupportService.addAISupportMessage(
                agentUserId, ticket.getSubject()+": "+ ticket.getDescription(), conversation.getId());
    }

    @Transactional
    public void sendSystemMessage(Long conversationId, String senderId, String content) {
        com.justjava.mycommunity.chat.ChatMessage chatMessage = new com.justjava.mycommunity.chat.ChatMessage();
        chatMessage.setConversationId(conversationId);
        chatMessage.setSenderId(senderId);
        chatMessage.setContent(content);
        chatService.sendSupportMessage(chatMessage);
    }

    @Transactional
    public void closeTicket(Long ticketId){
        Ticket ticket = getTicketById(ticketId);
        if (ticket == null) throw new EntityNotFoundException("Ticket Not Found");
        ticket.setStatus("Closed");
        ticketRepository.save(ticket);
    }

    public TicketDTO mapTicketToDTO(Ticket currentTicket){
        TicketDTO ticketDto = new TicketDTO();
        ticketDto.setId(currentTicket.getId());
        ticketDto.setSubject(currentTicket.getSubject());
        ticketDto.setDescription(currentTicket.getDescription());
        ticketDto.setPriority(currentTicket.getPriority());
        ticketDto.setStatus(currentTicket.getStatus());
        ticketDto.setUserId(currentTicket.getUserId());
        ticketDto.setAttachmentUrl(currentTicket.getAttachmentUrl());
        ticketDto.setDateCreated(currentTicket.getDateCreated());
        ticketDto.setLastUpdated(currentTicket.getLastUpdated());
        ticketDto.setAgentUserId(currentTicket.getAgentUserId());

        return ticketDto;
    }
}
