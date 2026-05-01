package com.justjava.mycommunity.account;

import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.community.repository.CommunityGroupMembershipRepository;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.userManagement.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationManager {
    private final UserRepository userRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final CommunityGroupMembershipRepository communityGroupMembershipRepository;

    public AuthenticationManager(UserRepository userRepository,
                                 CommunityMembershipRepository communityMembershipRepository,
                                 CommunityGroupMembershipRepository communityGroupMembershipRepository) {
        this.userRepository = userRepository;
        this.communityMembershipRepository = communityMembershipRepository;
        this.communityGroupMembershipRepository = communityGroupMembershipRepository;
    }

    public Object get(String fieldName){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof DefaultOidcUser defaultOidcUser)) {
            return null;
        }
        return defaultOidcUser.getClaims().get(fieldName);
    }

    public Boolean isAdmin() {
        try {
            String userId = (String) get("sub");
            if (userId == null) {
                System.out.println("AuthenticationManager.isAdmin(): userId is null");
                return false;
            }

            User user = userRepository.findByUserId(userId);
            if (user == null) {
                System.out.println("AuthenticationManager.isAdmin(): User not found for userId: " + userId);
                return false;
            }

            if (user.getUserGroup() == null) {
                System.out.println("AuthenticationManager.isAdmin(): User " + userId + " has no user groups");
                return false;
            }

            boolean isAdmin = user.getUserGroup().stream().anyMatch(g -> "admin".equalsIgnoreCase(g.getGroupName()));
            System.out.println("AuthenticationManager.isAdmin(): User " + userId + " admin status: " + isAdmin);
            return isAdmin;
        } catch (Exception e) {
            System.out.println("AuthenticationManager.isAdmin(): Exception occurred: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Boolean isSupportAdmin() {
        try {
            String userId = (String) get("sub");
            if (userId == null) {
                System.out.println("AuthenticationManager.isSupportAdmin(): userId is null");
                return false;
            }

            User user = userRepository.findByUserId(userId);
            if (user == null) {
                System.out.println("AuthenticationManager.isSupportAdmin(): User not found for userId: " + userId);
                return false;
            }

            if (user.getUserGroup() == null) {
                System.out.println("AuthenticationManager.isSupportAdmin(): User " + userId + " has no user groups");
                return false;
            }

            boolean isSupportAdmin = user.getUserGroup().stream().anyMatch(g -> "support".equalsIgnoreCase(g.getGroupName()));
            System.out.println("AuthenticationManager.isSupportAdmin(): User " + userId + " support admin status: " + isSupportAdmin);
            return isSupportAdmin;
        } catch (Exception e) {
            System.out.println("AuthenticationManager.isSupportAdmin(): Exception occurred: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Boolean isCommunityAdmin() {
        try {
            String userId = (String) get("sub");
            if (userId == null) return false;
            return communityMembershipRepository.isUserAdminOfAnyCommunity(userId);
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean isGroupAdmin() {
        try {
            String userId = (String) get("sub");
            if (userId == null) return false;
            return !communityGroupMembershipRepository.findAdminGroupIdsByUserId(userId).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
