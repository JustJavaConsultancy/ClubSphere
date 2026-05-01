package com.justjava.mycommunity.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.ChatMessage;
import com.justjava.mycommunity.chat.entity.Conversation;
import com.justjava.mycommunity.chat.repository.CommunityGroupRepository;
import com.justjava.mycommunity.chat.repository.ConversationRepository;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.community.CommunityGroup;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.repository.CommunityGroupMembershipRepository;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SupportService {

    private final TicketRepository ticketRepository;
    private final AuthenticationManager authenticationManager;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ChatService chatService;
    private final AISupportService aISupportService;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final CommunityGroupMembershipRepository communityGroupMembershipRepository;
    private final CommunityRepository communityRepository;
    private final CommunityGroupRepository communityGroupRepository;

    public SupportService(TicketRepository ticketRepository, AuthenticationManager authenticationManager,
                          ObjectMapper objectMapper, UserRepository userRepository,
                          ConversationRepository conversationRepository,
                          ChatService chatService, AISupportService aISupportService,
                          CommunityMembershipRepository communityMembershipRepository,
                          CommunityGroupMembershipRepository communityGroupMembershipRepository,
                          CommunityRepository communityRepository,
                          CommunityGroupRepository communityGroupRepository) {
        this.ticketRepository = ticketRepository;
        this.authenticationManager = authenticationManager;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.chatService = chatService;
        this.aISupportService = aISupportService;
        this.communityMembershipRepository = communityMembershipRepository;
        this.communityGroupMembershipRepository = communityGroupMembershipRepository;
        this.communityRepository = communityRepository;
        this.communityGroupRepository = communityGroupRepository;
    }

    @Transactional
    public void createTicket(Map<String, Object> formData) {
        String loginUser = (String) authenticationManager.get("sub");

        boolean isSystemAdmin  = authenticationManager.isAdmin();
        boolean isSupportAdmin = authenticationManager.isSupportAdmin();

        // Parse the submitted community/group context upfront
        Object communityIdObj = formData.get("communityId");
        Object groupIdObj     = formData.get("communityGroupId");
        Long communityId = (communityIdObj != null && !communityIdObj.toString().isBlank())
                ? Long.parseLong(communityIdObj.toString()) : null;
        Long groupId = (groupIdObj != null && !groupIdObj.toString().isBlank())
                ? Long.parseLong(groupIdObj.toString()) : null;

        if (isSupportAdmin) {
            // Support admins always go to the system support queue
            formData.put("communityId", null);
            formData.put("communityGroupId", null);
        } else if (isSystemAdmin) {
            // Platform admin: keep context if provided
            if (communityId == null && groupId == null) {
                formData.put("communityId", null);
                formData.put("communityGroupId", null);
            }
        } else {
            // Regular users AND community/group admins
            if (communityId == null && groupId == null) {
                throw new IllegalArgumentException("A ticket must be linked to a community or group.");
            }

            if (communityId != null) {
                boolean isMember = communityMembershipRepository
                        .existsByUserIdAndCommunityIdAndStatus(loginUser, communityId, MembershipStatus.APPROVED);
                if (!isMember) {
                    throw new SecurityException("You must be an approved member of the community to create a ticket.");
                }
                // If the user is admin of THIS specific community, route to system support
                if (communityMembershipRepository.isUserCommunityAdmin(loginUser, communityId)) {
                    formData.put("communityId", null);
                    formData.put("communityGroupId", null);
                    communityId = null;
                    groupId = null;
                }
            }

            if (groupId != null) {
                boolean isGroupMember = communityGroupMembershipRepository.isUserMemberOfGroup(loginUser, groupId);
                if (!isGroupMember) {
                    throw new SecurityException("You must be a member of the group to create a ticket.");
                }
                // If the user is admin of THIS specific group, route to system support
                if (communityGroupMembershipRepository.isUserGroupAdmin(loginUser, groupId)) {
                    formData.put("communityId", null);
                    formData.put("communityGroupId", null);
                }
            }
        }

        Ticket ticket = objectMapper.convertValue(formData, Ticket.class);
        // Always set userId from the authenticated user — never trust the form value
        ticket.setUserId(loginUser);
        // Ensure communityId / groupId survive the convertValue (Jackson may fail on String→Long)
        Object rawCommunityId = formData.get("communityId");
        Object rawGroupId     = formData.get("communityGroupId");
        if (rawCommunityId != null && !rawCommunityId.toString().isBlank()) {
            try { ticket.setCommunityId(Long.parseLong(rawCommunityId.toString())); } catch (NumberFormatException ignored) {}
        }
        if (rawGroupId != null && !rawGroupId.toString().isBlank()) {
            try { ticket.setCommunityGroupId(Long.parseLong(rawGroupId.toString())); } catch (NumberFormatException ignored) {}
        }

        Conversation conversation = new Conversation();
        conversation.setTitle("Support");
        conversation.setMembers(userRepository.findAllByUserIdIn(List.of(loginUser)));
        conversation = conversationRepository.save(conversation);
        ticket.setConversation(conversation);
        ticketRepository.save(ticket);
    }

    /** Always returns tickets submitted by the current user (regardless of role). */
    public List<Ticket> getMySubmittedTickets() {
        String loginUser = (String) authenticationManager.get("sub");
        return getTicketByUserId(loginUser);
    }

    public List<Ticket> getTickets() {
        String loginUser = (String) authenticationManager.get("sub");

        if (authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || authenticationManager.isCommunityAdmin()) {
            return getAgentTickets(loginUser);
        }

        return getTicketByUserId(loginUser);
    }

    public List<Ticket> getAgentTickets(String agentId) {
        return ticketRepository.findByAgentUserIdOrderByLastUpdatedDesc(agentId);
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id).orElse(null);
    }

    public List<Ticket> getTicketByUserId(String userId) {
        return ticketRepository.findByUserIdOrderByLastUpdatedDesc(userId);
    }

    public List<Ticket> getAllUnassignedTicket() {
        return ticketRepository.findByAgentUserIdIsNullOrderByLastUpdatedDesc();
    }

    public void updateTicketWithAgentUser(String agentUserId, Ticket ticket) {
        ticket.setAgentUserId(agentUserId);
        ticketRepository.save(ticket);
    }

    @Transactional
    public TicketDTO getSingleTicket(Long id, String userId) {
        Ticket ticket = getTicketById(id);
        TicketDTO ticketDTO = mapTicketToDTO(ticket);
        ticketDTO.setConversation(chatService.mapConversationsToDTO(
                Collections.singletonList(ticket.getConversation()), userId).getFirst());
        return ticketDTO;
    }

    @Transactional
    public void claimTicket(Long ticketId, String agentUserId) {
        Ticket ticket = getTicketById(ticketId);
        if (ticket == null) throw new EntityNotFoundException("Ticket Not Found");
        ticket.setAgentUserId(agentUserId);
        ticket.setStatus("Open");
        Conversation conversation = ticket.getConversation();
        conversation.getMembers().add(userRepository.findByUserId(agentUserId));
        conversation = conversationRepository.save(conversation);
        ticketRepository.save(ticket);
        aISupportService.addAISupportMessage(
                agentUserId, ticket.getSubject() + ": " + ticket.getDescription(), conversation.getId());
    }

    @Transactional
    public void sendSystemMessage(Long conversationId, String senderId, String content) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setConversationId(conversationId);
        chatMessage.setSenderId(senderId);
        chatMessage.setContent(content);
        chatService.sendSupportMessage(chatMessage);
    }

    @Transactional
    public void closeTicket(Long ticketId) {
        Ticket ticket = getTicketById(ticketId);
        if (ticket == null) throw new EntityNotFoundException("Ticket Not Found");
        ticket.setStatus("Closed");
        ticketRepository.save(ticket);
    }

    public TicketDTO mapTicketToDTO(Ticket currentTicket) {
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
        ticketDto.setCommunityId(currentTicket.getCommunityId());
        ticketDto.setCommunityGroupId(currentTicket.getCommunityGroupId());
        if (currentTicket.getCommunityGroupId() != null) {
            // Group ticket: show group name + the group's parent community
            communityGroupRepository.findById(currentTicket.getCommunityGroupId()).ifPresent(g -> {
                ticketDto.setGroupName(g.getName());
                if (g.getCommunity() != null) {
                    ticketDto.setCommunityName(g.getCommunity().getName());
                }
            });
        } else if (currentTicket.getCommunityId() != null) {
            // Community-only ticket: show community name only
            communityRepository.findById(currentTicket.getCommunityId())
                    .ifPresent(c -> ticketDto.setCommunityName(c.getName()));
        }
        return ticketDto;
    }

    /**
     * Returns all unclaimed tickets scoped to communities/groups the current user administers.
     * Falls back to global unassigned if the user is a system-wide support admin.
     */
    public List<Ticket> getScopedUnclaimedTickets(String userId) {
        if (authenticationManager.isSupportAdmin()) {
            return getAllUnassignedTicket();
        }
        List<Long> adminCommunityIds = communityMembershipRepository.findAdminCommunityIdsByUserId(userId);
        List<Long> adminGroupIds     = communityGroupMembershipRepository.findAdminGroupIdsByUserId(userId);

        List<Ticket> communityTickets = adminCommunityIds.stream()
                .flatMap(cid -> ticketRepository.findByCommunityId(cid).stream())
                .filter(t -> t.getAgentUserId() == null)
                .collect(Collectors.toList());

        List<Ticket> groupTickets = adminGroupIds.stream()
                .flatMap(gid -> ticketRepository.findByCommunityGroupId(gid).stream())
                .filter(t -> t.getAgentUserId() == null)
                .collect(Collectors.toList());

        return Stream.concat(communityTickets.stream(), groupTickets.stream())
                .distinct()
                .sorted((a, b) -> b.getLastUpdated() != null && a.getLastUpdated() != null
                        ? b.getLastUpdated().compareTo(a.getLastUpdated()) : 0)
                .collect(Collectors.toList());
    }

    /** Returns claimed tickets for this agent, filtered to their admin scope */
    public List<Ticket> getScopedAgentTickets(String userId) {
        if (authenticationManager.isSupportAdmin()) {
            return getAgentTickets(userId);
        }
        return ticketRepository.findByAgentUserIdOrderByLastUpdatedDesc(userId);
    }

    /** Check whether this user (community/group admin) may claim/manage a given ticket */
    public boolean canManageTicket(String userId, Ticket ticket) {
        if (authenticationManager.isSupportAdmin() || authenticationManager.isAdmin()) return true;
        if (ticket.getCommunityId() != null) {
            if (communityMembershipRepository.isUserCommunityAdmin(userId, ticket.getCommunityId())) return true;
        }
        if (ticket.getCommunityGroupId() != null) {
            if (communityGroupMembershipRepository.isUserGroupAdmin(userId, ticket.getCommunityGroupId())) return true;
        }
        return false;
    }
}
