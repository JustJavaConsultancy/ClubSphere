package com.justjava.mycommunity.userManagement;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.keycloak.KeycloakService;
import com.justjava.mycommunity.network.NetworkService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.justjava.mycommunity.util.MappingUtils.mapUsersToDTO;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    public final AuthenticationManager authenticationManager;
    private final CommunityRepository communityRepository;
    private final KeycloakService keycloakService;
    private final CommunityService communityService;
    private final NetworkService networkService;

    public UserService(final UserRepository userRepository, UserGroupRepository userGroupRepository,
                       AuthenticationManager authenticationManager,
                       CommunityRepository communityRepository, KeycloakService keycloakService, CommunityService communityService, NetworkService networkService){
        this.userRepository = userRepository;
        this.userGroupRepository = userGroupRepository;
        this.authenticationManager = authenticationManager;
        this.communityRepository = communityRepository;
        this.keycloakService = keycloakService;
        this.communityService = communityService;
        this.networkService = networkService;
    }

    public List<UserDTO> getUsers(){
        List<User> users = userRepository.findAll();
        return mapUsersToDTO(users);
    }

    public UserDTO getSingleUserByUserId(String userId){
        User singleUser = userRepository.findByUserId(userId);
        // Refresh the entity to ensure we have the latest data from database
        if (singleUser != null) {
            userRepository.flush();
            singleUser = userRepository.findByUserId(userId);
        }
        return mapUsersToDTO(Collections.singletonList(singleUser)).getFirst();
    }

    public void updateUserLevel(String level) {
        Object loginUser = authenticationManager.get("sub");
        User currentUser = userRepository.findByUserId(loginUser.toString());

//        System.out.println("This is the current user" + currentUser.getFirstName());
//        System.out.println("This is the current level" + level);
        currentUser.setLevel(level);

        userRepository.save(currentUser);
    }

    public void updateUserStatus(String status){
        Object loginUser = authenticationManager.get("sub");
        User currentUser = userRepository.findByUserId(loginUser.toString());
        boolean privacyBool = false;

        System.out.println("The user privacy is updated" + privacyBool);
        if ("private".equalsIgnoreCase(status)) {
            privacyBool = true;
        }
        currentUser.setPrivacy(privacyBool);
        userRepository.save(currentUser);
    }

    public void updateCommunityStatus(String status){
        String loginUser = (String) authenticationManager.get("sub");
        Community community = communityService.getCommunity();
        List<User> users = new ArrayList<>();

        boolean communityPrivacyBool = false;

        if (authenticationManager.isAdmin()){
            if("private".equalsIgnoreCase(status)){
                communityPrivacyBool = true;

                // Get all users in the current mycommunity using the new many-to-many relationship
                if (community != null && community.getId() != null) {
                    List<User> allCommunityUsers = userRepository.findByCommunityId(community.getId());

                    for (User user: allCommunityUsers){
                        user.setPrivacy(true);
                        users.add(user);
                    }
                    System.out.println("This is the mycommunity privacy:::" + communityPrivacyBool);
                    userRepository.saveAll(users);
                }
            }

            community.setCommunityPrivacy(communityPrivacyBool);
            communityRepository.save(community);
        }
    }

    public List<UserGroup> getUserGroups(){
        return userGroupRepository.findAll();
    }

    public UserDTO getSingleUser(String userId) {
        User client = userRepository.findByUserId(userId);
        if (client != null) {
            // Refresh the entity to ensure we have the latest data from database
            userRepository.flush();
            client = userRepository.findByUserId(userId);
            return mapUsersToDTO(Collections.singletonList(client)).getFirst();
        }
        return null;
    }

    public UserGroup getSingleGroup(String groupId){
        return userGroupRepository.findByGroupId(groupId);
    }

    public void saveAuthenticatedUser(){
        String userId = (String) authenticationManager.get("sub");
        User user = userRepository.findByUserId(userId);
        if (user == null){
            user = keycloakService.getUser(userId);
            user = userRepository.save(user);
            networkService.createChatGroupForUsers(Collections.singletonList(user));
        }
        keycloakService.updateUserGroupOfUser(user);
        userRepository.save(user);
    }
}
