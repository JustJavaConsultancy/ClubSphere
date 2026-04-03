package com.justjava.mycommunity.keycloak;

import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.network.NetworkService;
import com.justjava.mycommunity.userManagement.UserGroup;
import com.justjava.mycommunity.userManagement.UserGroupRepository;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KeycloakAdminService {

    private static final int DEFAULT_PAGE_SIZE = 100;

    private final Keycloak keycloak;
    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final NetworkService networkService;

    public KeycloakAdminService(Keycloak keycloak, UserGroupRepository userGroupRepository, UserRepository userRepository, KeycloakService keycloakService, NetworkService networkService) {
        this.keycloak = keycloak;
        this.userGroupRepository = userGroupRepository;
        this.userRepository = userRepository;
        this.keycloakService = keycloakService;
        this.networkService = networkService;
    }


    private RealmResource realm(String realmName) {
        return keycloak.realm(realmName);
    }

    private UsersResource users(String realmName) {
        return realm(realmName).users();
    }

    private RolesResource roles(String realmName) {
        return realm(realmName).roles();
    }

    private GroupsResource groups(String realmName) {
        return realm(realmName).groups();
    }

    private UserResource user(String realmName, String userId) {
        return users(realmName).get(userId);
    }

  /*
       User Creation
        */

    public String createUser(String realmName,
            String username,
            String email,
            String password,
            String firstName,
            String lastName,
            Map<String, List<String>> attributes,
                             String groupName
    ) {
        Assert.hasText(username, "Username must not be empty");
        Assert.hasText(password, "Password must not be empty");

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmailVerified(true);
        if (attributes != null && !attributes.isEmpty()) {
            user.setAttributes(attributes);
        }
        if (groupName != null && !groupName.isEmpty()) {
            user.setGroups(Collections.singletonList(groupName));
        }

        user.setCredentials(Collections.singletonList(buildPasswordCredential(password)));

        try (Response response = users(realmName).create(user)) {

            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new IllegalStateException(
                        "Failed to create user. Status: "
                                + response.getStatus()
                                + " - "
                                + response.getStatusInfo().getReasonPhrase()
                );
            }

            return extractUserId(response.getLocation());
        }
    }

    public void createUser(String realmName, Map<String, String> params) {
     createUser(realmName,
             params.get("username"),
             params.get("email"),
             "password",
             params.get("firstName"),
             params.get("lastName"),
             null,
             params.get("groups"));
    }

    private CredentialRepresentation buildPasswordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(true);
        return credential;
    }

    private String extractUserId(URI location) {
        if (location == null) {
            throw new IllegalStateException("User created but no Location header returned.");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

        /* ============================================================
       Create Group
       ============================================================ */


    public void createGroup(String realmName, String groupName, String description) {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(groupName);
        group.setDescription(description);
        groups(realmName).add(group);
    }

    /* ============================================================
       User Management
       ============================================================ */

    public void enableUser(String realmName, String userId) {
        UserRepresentation rep = user(realmName, userId).toRepresentation();
        rep.setEnabled(true);
        user(realmName, userId).update(rep);
    }

    public void disableUser(String realmName, String userId) {
        UserRepresentation rep = user(realmName, userId).toRepresentation();
        rep.setEnabled(false);
        user(realmName, userId).update(rep);
    }

    public void deleteUser(String realmName ,String userId) {
        users(realmName).delete(userId);
    }

    public Optional<UserRepresentation> findByUsername(String realmName, String username) {
        return users(realmName).search(username, 0, 1).stream().findFirst();
    }

    public Optional<UserRepresentation> findByClientId(String realmName, String clientId) {
        clientId = "clientId:"+clientId;
        return users(realmName).searchByAttributes(clientId).stream().findFirst();
    }

    public Optional<UserRepresentation> findById(String realmName, String userId) {
        try {
            return Optional.of(user(realmName, userId).toRepresentation());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public void updateUser(String realmName,
                           String userId,
                           String username,
                           String email,
                           String firstName,
                           String lastName,
                           boolean status,
                           String groupName){
        UserRepresentation user = user(realmName, userId).toRepresentation();
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setEnabled(status);
        if (groupName != null) user.setGroups(List.of(groupName));
        user(realmName, userId).update(user);

    }

    /* ============================================================
       Password & Email Actions
       ============================================================ */

    public void sendPasswordResetEmail(String realmName,String userId) {
        Assert.hasText(userId, "userId must not be empty");
        user(realmName, userId).executeActionsEmail(List.of("UPDATE_PASSWORD"));
    }

    public void sendVerifyEmail(String realmName, String userId) {
        Assert.hasText(userId, "userId must not be empty");
        user(realmName, userId).executeActionsEmail(List.of("VERIFY_EMAIL"));
    }

    public void sendVerifyAndResetPasswordEmail(String realmName, String userId) {
        Assert.hasText(userId, "userId must not be empty");
        user(realmName, userId).executeActionsEmail(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
    }

    public void resetPassword(String realmName, String userId, String newPassword, boolean temporary) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(temporary);
        user(realmName, userId).resetPassword(credential);
    }

    /* ============================================================
       Role Management
       ============================================================ */

    public void assignRealmRole(String realmName, String userId, String roleName) {
        RoleRepresentation role = roles(realmName).get(roleName).toRepresentation();
        user(realmName, userId).roles().realmLevel().add(List.of(role));
    }

    public void removeRealmRole(String realmName, String userId, String roleName) {
        RoleRepresentation role = roles(realmName).get(roleName).toRepresentation();
        user(realmName, userId).roles().realmLevel().remove(List.of(role));
    }

    public List<String> getUserRealmRoles(String realmName, String userId) {
        return user(realmName, userId)
                .roles()
                .realmLevel()
                .listAll()
                .stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toList());
    }

    /* ============================================================
       Group Management
       ============================================================ */

    public void addUserToGroup(String realmName, String userId, String groupName) {

        GroupRepresentation group = groups(realmName).groups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Group not found: " + groupName)
                );

        user(realmName, userId).joinGroup(group.getId());
    }

    public void removeUserFromGroup(String realmName, String userId, String groupName) {

        GroupRepresentation group = groups(realmName).groups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Group not found: " + groupName)
                );

        user(realmName, userId).leaveGroup(group.getId());
    }

    public List<String> getUserGroups(String realmName, String userId) {
        return user(realmName, userId)
                .groups()
                .stream()
                .map(GroupRepresentation::getName)
                .collect(Collectors.toList());
    }

    public void updateGroup(String realmName, String groupId, String groupName, String description){
        GroupRepresentation group = groups(realmName).group(groupId).toRepresentation();
        if (groupName != null)  group.setName(groupName);
        if (description != null)    group.setDescription(description);
        groups(realmName).group(groupId).update(group);
    }

    public void deleteGroup(String realmName, String groupId){
        groups(realmName).group(groupId).remove();
    }

    /* ============================================================
       Listing Users
       ============================================================ */

    public List<UserRepresentation> listUsers(String realmName, int firstResult, int maxResults) {
        return users(realmName).list(firstResult, maxResults);
    }

    public List<UserRepresentation> listAllUsers(String realmName) {

        List<UserRepresentation> allUsers = new ArrayList<>();
        int first = 0;

        while (true) {
            List<UserRepresentation> batch = users(realmName).list(first, DEFAULT_PAGE_SIZE);

            if (batch.isEmpty()) break;

            allUsers.addAll(batch);

            if (batch.size() < DEFAULT_PAGE_SIZE) break;

            first += DEFAULT_PAGE_SIZE;
        }

        return allUsers;
    }

    /* ============================================================
       Searching
       ============================================================ */

    public List<UserRepresentation> searchUsers(String realmName,
            String searchQuery,
            int firstResult,
            int maxResults
    ) {
        return users(realmName).search(searchQuery, firstResult, maxResults);
    }

    public List<UserRepresentation> searchUsersByAttribute(String realmName,
            String attributeKey,
            String attributeValue
    ) {
        Assert.hasText(attributeKey, "Attribute key must not be empty");
        Assert.hasText(attributeValue, "Attribute value must not be empty");

        return users(realmName).searchByAttributes(attributeKey + ":" + attributeValue);
    }

    public List<UserRepresentation> searchUsersByAttributes(String realmName,
            Map<String, String> attributes
    ) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyList();
        }

        String query = attributes.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        return users(realmName).searchByAttributes(query);
    }

        /* ============================================================
       User/Group Synchronization
       ============================================================ */

    @Scheduled(cron = "0 0 0/6 * * *")
    @Transactional
    public void syncKeycloak(){
        syncGroups();
        syncUsers();
    }

    public void syncGroups(){
        //TODO: implement multi realm synchronization
        log.info("\nSyncing Groups:::");
        List<GroupRepresentation> groups= groups(KeycloakResource.COMMUNITY_REALM).groups();
        List<UserGroup> d = userGroupRepository.findAllByRealm(KeycloakResource.COMMUNITY_REALM);

        Map<String, UserGroup> existingGroupsMap = userGroupRepository.findAllByRealm(KeycloakResource.COMMUNITY_REALM).stream()
                .collect(Collectors.toMap(UserGroup::getGroupName, g -> g));

        List<UserGroup> groupsToSave = new ArrayList<>();
        Set<String> realmGroupNames = new HashSet<>();
        for (GroupRepresentation group : groups) {
            String groupId = group.getId();
            String groupName = group.getName();
            String description = group.getDescription();

            realmGroupNames.add(groupName);
            UserGroup userGroup = existingGroupsMap.get(groupName);
            if (userGroup != null){
                userGroup.setGroupName(groupName);
                userGroup.setDescription(description);
                userGroup.setRealm(KeycloakResource.COMMUNITY_REALM);
                groupsToSave.add(userGroup);
            } else {
                groupsToSave.add(UserGroup.builder()
                                .groupId(groupId)
                                .groupName(groupName)
                                .description(description)
                                .realm(KeycloakResource.COMMUNITY_REALM)
                                .build());
            }
        }
        userGroupRepository.saveAll(groupsToSave);
        List<UserGroup> groupsToDelete =  existingGroupsMap.values().stream()
                .filter(group -> !realmGroupNames.contains(group.getGroupName().toLowerCase()))
                .toList();

        if (!groupsToDelete.isEmpty()) {
            groupsToDelete.forEach(keycloakService::deleteGroup);
        }
        log.info("\nDone Syncing Groups");

    }

    public void syncUsers(){
        log.info("\nSyncing Users:::");

        List<UserRepresentation> usersReps = users(KeycloakResource.COMMUNITY_REALM).list();
        Map<String, UserGroup> userGroup = userGroupRepository.findAllByRealm(KeycloakResource.COMMUNITY_REALM)
                .stream().collect(Collectors.toMap(UserGroup::getGroupName, g -> g));

        Map<String, User> savedUsersMap = userRepository.findAllByRealm(KeycloakResource.COMMUNITY_REALM)
                .stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));
        Map<String, User> usersToSave = new HashMap<>();

        for (UserRepresentation rep : usersReps){
            User user = savedUsersMap.getOrDefault(rep.getId(), new User());
            List<UserGroup> userGroups = getUserGroups(rep, userGroup);
            mapUser(rep, user, userGroups);
            user.setRealm(KeycloakResource.COMMUNITY_REALM);
            usersToSave.put(user.getUserId(), user);
        }
        //TODO: Group member count is not being set

        userRepository.saveAll(usersToSave.values());
        userGroupRepository.saveAll(userGroup.values());

        Set<String> userIds = usersToSave.keySet();
        List<User> usersToDelete = savedUsersMap.values().stream()
                .filter(user -> !userIds.contains(user.getUserId()))
                .toList();

        if (!usersToDelete.isEmpty()) {
            usersToDelete.forEach(keycloakService::deleteUser);
        }
        networkService.createChatGroupForUsers(usersToSave.values().stream().toList());
        log.info("\nDone Syncing Users");

    }

    private void mapUser(UserRepresentation rep, User user, List<UserGroup> group) {
        user.setUserId(rep.getId());
        user.setFirstName(rep.getFirstName());
        user.setLastName(rep.getLastName());
        user.setEmail(rep.getEmail());
        user.setStatus(rep.isEnabled());
        user.getUserGroup().addAll(group);
    }
    private List<UserGroup> getUserGroups(UserRepresentation rep, Map<String, UserGroup> userGroup){
        return rep.getGroups() != null ? rep.getGroups().stream()
                .map(userGroup::get)
                .collect(Collectors.toList()) : new ArrayList<>();
    }
}
