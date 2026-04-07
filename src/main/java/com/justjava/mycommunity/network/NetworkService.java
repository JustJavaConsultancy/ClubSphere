package com.justjava.mycommunity.network;

import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.ChatGroupRepository;
import com.justjava.mycommunity.chat.repository.ChatGroupRequestRepository;
import com.justjava.mycommunity.organization.Channel;
import com.justjava.mycommunity.organization.TownHall;
import com.justjava.mycommunity.organization.TownHallChannelService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public List<UserDTO> getChatGroupUsers(String userId) {
        ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("ChatGroup does not exist"));
        Set<User> users = chatGroup.getUsers().stream()
                .filter(user -> Boolean.FALSE.equals(user.getPrivacy()))
                .collect(Collectors.toSet());

        return mapUsersToDTO(users);
    }

    @Transactional
    public List<UserDTO> getUsersNotInChatGroup(String userId){

        ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("ChatGroup does not exist"));
        List<String> userIds = chatGroup.getUsers().stream().map(User::getUserId).toList();
        List<User>  usersNotInChatGroup = userRepository.findAllByUserIdNotIn(userIds);

        return mapUsersToDTO(usersNotInChatGroup);
    }

    public void requestToJoinChatGroup(String requestingUserId, String currentUserId) {

        User user = Optional.ofNullable(userRepository.findByUserId(requestingUserId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserId(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User's Group does not exist"));

        System.out.println(" The chatGroup======================="+chatGroup);
        var chatGroupRequest = chatGroupRequestRepository.findByChatGroup_IdAndUser_UserId(chatGroup.getId(), user.getUserId());
        if (chatGroupRequest != null) return;
        ChatGroupRequest request = new ChatGroupRequest();
        request.setChatGroup(chatGroup);
        request.setUser(user);
        request.setStatus("P"); //P = PENDING, A = APPROVED...will use Enums later
        chatGroupRequestRepository.save(request);
    }

    @Transactional
    public void approveChatGroupRequest(Long chatGroupId) {
        ChatGroupRequest request = Optional.ofNullable(chatGroupRequestRepository.findByChatGroup_Id(chatGroupId))
                .orElseThrow(() -> new EntityNotFoundException("Group does not exist"));
        request.setStatus("A");
        addUserToGroup(request.getUser().getUserId(), chatGroupId);
        ChatGroup chatGroup = chatGroupRepository.findByAdminUser_UserId(request.getUser().getUserId()).orElse(null);
        if (chatGroup != null){
            Long groupId = chatGroup.getId();
            String adminUserId = request.getChatGroup().getAdminUser().getUserId();
            ChatGroupRequest chatGroupRequest = chatGroupRequestRepository.findByChatGroup_IdAndUser_UserId(groupId, adminUserId);
            if (chatGroupRequest != null){
                chatGroupRequest.setStatus("A");
                chatGroupRequestRepository.save(chatGroupRequest);
            }
        }
        chatGroupRequestRepository.save(request);
    }

    @Transactional
    public void rejectChatGroupRequest(Long chatGroupRequestId) {
        ChatGroupRequest request = chatGroupRequestRepository.findById(chatGroupRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Chat group request does not exist"));

        // Option 1: Update status to rejected
        request.setStatus("R"); // R = REJECTED
        chatGroupRequestRepository.save(request);

        // Option 2: Delete the request entirely (uncomment if you prefer this approach)
        // chatGroupRequestRepository.delete(request);

        System.out.println("Chat group request " + chatGroupRequestId + " has been rejected");
    }

    @Transactional
    public Object getChatGroupRequests(String userId) {

        List<ChatGroupRequest> requests = chatGroupRequestRepository
                .findByChatGroup_AdminUser_UserIdAndStatus(userId,"P");
        return mapChatRequestToDTO(requests);
    }


    public void createChatGroupForUsers(List<User> users){

        for (User user : users) {
            if (!chatGroupRepository.existsByAdminUser(user)){
                ChatGroup chatGroup = new ChatGroup();
                Channel channel = townHallChannelService.createChannel(user.getFirstName()+"- Default",user.getFullName()+"- Channel");
                TownHall townHall = townHallChannelService.createTownHall(user.getFirstName()+"- Default", user.getFullName()+"- Town Hall");
                //TODO: Set Community
                chatGroup.setName(user.getFullName() + " Chat Group");
                chatGroup.setChannel(channel);
                chatGroup.setTownHall(townHall);
                chatGroup.setAdminUser(user);
                chatGroupRepository.save(chatGroup);
            }
        }
    }

    @Transactional
    public void addUserToGroup(String userId, Long groupId) {
        ChatGroup ownersGroup = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group does not exist"));
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        ChatGroup requestorsGroup = chatGroupRepository.findByAdminUser_UserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Requesting User Group does not exist"));
        //Adding the owner of the ownersGroup to the requestor's ownersGroup
        requestorsGroup.getUsers().add(ownersGroup.getAdminUser());
        ownersGroup.getAdminUser().getChatGroup().add(requestorsGroup);
        //Adding the requestor to the owner's ownersGroup
        user.getChatGroup().add(ownersGroup);
        ownersGroup.getUsers().add(user);
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
