package com.justjava.mycommunity.network;

import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.ChatGroupRepository;
import com.justjava.mycommunity.chat.repository.ChatGroupRequestRepository;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.organization.Channel;
import com.justjava.mycommunity.organization.TownHall;
import com.justjava.mycommunity.organization.TownHallChannelService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.justjava.mycommunity.util.MappingUtils.mapChatRequestToDTO;
import static com.justjava.mycommunity.util.MappingUtils.mapUsersToDTO;

@Service
@RequiredArgsConstructor
public class NetworkService {

    private final ChatGroupRepository chatGroupRepository;
    private final UserRepository userRepository;
    private final ChatGroupRequestRepository chatGroupRequestRepository;
    private final TownHallChannelService townHallChannelService;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final CommunityRepository communityRepository;

    // ======================== COMMUNITY-SCOPED METHODS ========================

    /**
     * Get or lazily create a ChatGroup for a user in a specific community.
     */
    @Transactional
    public ChatGroup getOrCreateChatGroup(String userId, Long communityId) {
        Optional<ChatGroup> existing = chatGroupRepository.findByAdminUser_UserIdAndCommunity_Id(userId, communityId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Lazily create a ChatGroup for this user in this community
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

        ChatGroup chatGroup = new ChatGroup();
        Channel channel = townHallChannelService.createChannel(
                user.getFirstName() + "-" + communityId + "-Default",
                user.getFullName() + "-" + communityId + "-Channel");
        TownHall townHall = townHallChannelService.createTownHall(
                user.getFirstName() + "-" + communityId + "-Default",
                user.getFullName() + "-" + communityId + "-Town Hall");

        chatGroup.setName(user.getFullName() + " Chat Group (" + community.getName() + ")");
        chatGroup.setChannel(channel);
        chatGroup.setTownHall(townHall);
        chatGroup.setAdminUser(user);
        chatGroup.setCommunity(community);
        return chatGroupRepository.save(chatGroup);
    }

    /**
     * Get connected users for a specific community.
     */
    @Transactional
    public List<UserDTO> getChatGroupUsers(String userId, Long communityId) {
        ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserIdAndCommunity_Id(userId, communityId)
                .orElse(null);

        if (chatGroup == null) {
            return Collections.emptyList();
        }

        Set<User> users = chatGroup.getUsers().stream()
                .filter(user -> Boolean.FALSE.equals(user.getPrivacy()))
                .collect(Collectors.toSet());

        return mapUsersToDTO(users);
    }

    /**
     * Get users not in the user's chat group for a specific community.
     */
    @Transactional
    public List<UserDTO> getUsersNotInChatGroup(String userId, Long communityId) {
        ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserIdAndCommunity_Id(userId, communityId)
                .orElse(null);

        if (chatGroup == null) {
            // No group yet means all users are "not in chat group"
            List<User> allUsers = userRepository.findAll();
            return mapUsersToDTO(allUsers);
        }

        List<String> userIds = chatGroup.getUsers().stream().map(User::getUserId).toList();
        // Include the admin user too
        userIds = new java.util.ArrayList<>(userIds);
        userIds.add(userId);
        List<User> usersNotInChatGroup = userRepository.findAllByUserIdNotIn(userIds);

        return mapUsersToDTO(usersNotInChatGroup);
    }

    /**
     * Request to connect with a user within a specific community.
     */
    public void requestToJoinChatGroup(String requestingUserId, String currentUserId, Long communityId) {
        // Validate that both users are in this specific community
        boolean requestorInCommunity = communityMembershipRepository
                .existsByUserIdAndCommunityIdAndStatus(requestingUserId, communityId,
                        com.justjava.mycommunity.community.MembershipStatus.APPROVED);
        boolean currentInCommunity = communityMembershipRepository
                .existsByUserIdAndCommunityIdAndStatus(currentUserId, communityId,
                        com.justjava.mycommunity.community.MembershipStatus.APPROVED);

        if (!requestorInCommunity || !currentInCommunity) {
            throw new IllegalStateException("Both users must be approved members of this community to connect");
        }

        User user = Optional.ofNullable(userRepository.findByUserId(requestingUserId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));

        // Get or create the current user's ChatGroup for this community
        ChatGroup chatGroup = getOrCreateChatGroup(currentUserId, communityId);

        System.out.println(" The chatGroup=======================" + chatGroup);
        var chatGroupRequest = chatGroupRequestRepository.findByChatGroup_IdAndUser_UserId(chatGroup.getId(), user.getUserId());
        if (chatGroupRequest != null) return;

        ChatGroupRequest request = new ChatGroupRequest();
        request.setChatGroup(chatGroup);
        request.setUser(user);
        request.setStatus("P");
        chatGroupRequestRepository.save(request);
    }

    /**
     * Approve a chat group request - community-aware cross-linking.
     */
    @Transactional
    public void approveChatGroupRequest(Long chatGroupRequestId) {
        ChatGroupRequest request = chatGroupRequestRepository.findById(chatGroupRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Chat group request does not exist"));
        request.setStatus("A");

        Long groupId = request.getChatGroup().getId();
        addUserToGroup(request.getUser().getUserId(), groupId);

        // Get the community from the owner's group for cross-linking
        Long communityId = request.getChatGroup().getCommunity() != null
                ? request.getChatGroup().getCommunity().getId()
                : null;

        if (communityId != null) {
            // Cross-link: find (or create) the requestor's group for the same community
            ChatGroup requestorGroup = getOrCreateChatGroup(request.getUser().getUserId(), communityId);
            String adminUserId = request.getChatGroup().getAdminUser().getUserId();

            ChatGroupRequest chatGroupRequest = chatGroupRequestRepository
                    .findByChatGroup_IdAndUser_UserId(requestorGroup.getId(), adminUserId);
            if (chatGroupRequest != null) {
                chatGroupRequest.setStatus("A");
                chatGroupRequestRepository.save(chatGroupRequest);
            }

            // Add the owner to the requestor's community-scoped group
            requestorGroup.getUsers().add(request.getChatGroup().getAdminUser());
            request.getChatGroup().getAdminUser().getChatGroup().add(requestorGroup);
        } else {
            // Fallback for legacy groups without community (backward compatibility)
            ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserId(request.getUser().getUserId()).orElse(null);
            if (chatGroup != null) {
                Long fallbackGroupId = chatGroup.getId();
                String adminUserId = request.getChatGroup().getAdminUser().getUserId();
                ChatGroupRequest chatGroupRequest = chatGroupRequestRepository
                        .findByChatGroup_IdAndUser_UserId(fallbackGroupId, adminUserId);
                if (chatGroupRequest != null) {
                    chatGroupRequest.setStatus("A");
                    chatGroupRequestRepository.save(chatGroupRequest);
                }
            }
        }

        chatGroupRequestRepository.save(request);
    }

    @Transactional
    public void rejectChatGroupRequest(Long chatGroupRequestId) {
        ChatGroupRequest request = chatGroupRequestRepository.findById(chatGroupRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Chat group request does not exist"));

        request.setStatus("R");
        chatGroupRequestRepository.save(request);

        System.out.println("Chat group request " + chatGroupRequestId + " has been rejected");
    }

    /**
     * Get pending chat group requests for a specific community.
     */
    @Transactional
    public Object getChatGroupRequests(String userId, Long communityId) {
        List<ChatGroupRequest> requests = chatGroupRequestRepository
                .findByChatGroup_AdminUser_UserIdAndChatGroup_Community_IdAndStatus(userId, communityId, "P");
        return mapChatRequestToDTO(requests);
    }

    @Transactional
    public void addUserToGroup(String userId, Long groupId) {
        ChatGroup ownersGroup = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group does not exist"));
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));

        // Add the user to the owner's group
        user.getChatGroup().add(ownersGroup);
        ownersGroup.getUsers().add(user);
    }

    // ======================== LEGACY METHODS (backward compatibility) ========================

    /**
     * @deprecated Use {@link #getChatGroupUsers(String, Long)} instead.
     * Returns connections from the first found ChatGroup (legacy behavior).
     */
    @Transactional
    public List<UserDTO> getChatGroupUsers(String userId) {
        ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserId(userId)
                .orElse(null);

        if (chatGroup == null) {
            return Collections.emptyList();
        }

        Set<User> users = chatGroup.getUsers().stream()
                .filter(user -> Boolean.FALSE.equals(user.getPrivacy()))
                .collect(Collectors.toSet());

        return mapUsersToDTO(users);
    }

    /**
     * @deprecated Use {@link #getChatGroupRequests(String, Long)} instead.
     */
    @Transactional
    public Object getChatGroupRequests(String userId) {
        List<ChatGroupRequest> requests = chatGroupRequestRepository
                .findByChatGroup_AdminUser_UserIdAndStatus(userId, "P");
        return mapChatRequestToDTO(requests);
    }

    /**
     * @deprecated Use {@link #requestToJoinChatGroup(String, String, Long)} instead.
     */
    public void requestToJoinChatGroup(String requestingUserId, String currentUserId) {
        if (!communityMembershipRepository.areUsersInSameCommunity(requestingUserId, currentUserId)) {
            throw new IllegalStateException("Users must be members of the same community to connect");
        }

        User user = Optional.ofNullable(userRepository.findByUserId(requestingUserId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserId(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User's Group does not exist"));

        var chatGroupRequest = chatGroupRequestRepository.findByChatGroup_IdAndUser_UserId(chatGroup.getId(), user.getUserId());
        if (chatGroupRequest != null) return;
        ChatGroupRequest request = new ChatGroupRequest();
        request.setChatGroup(chatGroup);
        request.setUser(user);
        request.setStatus("P");
        chatGroupRequestRepository.save(request);
    }

    // ======================== UTILITY METHODS ========================

    /**
     * Create a default ChatGroup for users (legacy - no community set).
     * New users will get community-scoped groups lazily via getOrCreateChatGroup.
     */
    public void createChatGroupForUsers(List<User> users) {
        for (User user : users) {
            if (!chatGroupRepository.existsByAdminUser(user)) {
                ChatGroup chatGroup = new ChatGroup();
                Channel channel = townHallChannelService.createChannel(
                        user.getFirstName() + "- Default", user.getFullName() + "- Channel");
                TownHall townHall = townHallChannelService.createTownHall(
                        user.getFirstName() + "- Default", user.getFullName() + "- Town Hall");
                chatGroup.setName(user.getFullName() + " Chat Group");
                chatGroup.setChannel(channel);
                chatGroup.setTownHall(townHall);
                chatGroup.setAdminUser(user);
                chatGroupRepository.save(chatGroup);
            }
        }
    }


    public void updateChatGroup(CreateChatDTO vo, Long id) {
        ChatGroup chatGroup = chatGroupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));
        chatGroup.setName(vo.getGroupName());
        chatGroup.setDescription(vo.getGroupDescription());
        chatGroupRepository.save(chatGroup);
    }

    @Transactional
    public void deleteChatGroup(Long groupId) {
        ChatGroup chatGroup = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Chat Group does not exist"));
        chatGroup.setAdminUser(null);
        chatGroup.getUsers().clear();
        chatGroupRepository.deleteById(groupId);
    }
}
