package com.justjava.mycommunity.posts;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommentDTO;
import com.justjava.mycommunity.chat.dto.PostDTO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.chat.repository.PostRepository;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.userManagement.UserRepository;
import com.justjava.mycommunity.util.StringUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.justjava.mycommunity.util.MappingUtils.mapPosts;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CommunityService communityService;
    private final AuthenticationManager authenticationManager;
    private final CommunityMembershipRepository communityMembershipRepository;

    private Set<Long> getApprovedCommunityIds(String userId) {
        return new HashSet<>(communityMembershipRepository.findApprovedCommunityIdsByUserId(userId));
    }

    /**
     * Robust admin check that handles various failure scenarios
     */
    private boolean isUserAdmin(String userId) {
        try {
            // First try the AuthenticationManager
            boolean isAdmin = authenticationManager.isAdmin();
            System.out.println("AuthenticationManager.isAdmin() returned: " + isAdmin);
            return isAdmin;
        } catch (Exception e) {
            System.out.println("AuthenticationManager.isAdmin() failed: " + e.getMessage());

            // Fallback: Check if user has admin role through other means
            try {
                User user = userRepository.findByUserId(userId);
                if (user != null) {
                    // Check if user has admin privileges through user group
                    if (user.getUserGroup() != null && !user.getUserGroup().isEmpty()) {
                        boolean isAdminByGroup = user.getUserGroup().stream()
                                .anyMatch(userGroup -> {
                                    String groupName = userGroup.getGroupName();
                                    return "ADMIN".equalsIgnoreCase(groupName) ||
                                            "ADMINISTRATOR".equalsIgnoreCase(groupName) ||
                                            "SUPER_ADMIN".equalsIgnoreCase(groupName) ||
                                            "admin".equalsIgnoreCase(groupName);
                                });
                        System.out.println("Fallback admin check by user groups: " + isAdminByGroup);
                        if (isAdminByGroup) {
                            System.out.println("User groups: ");
                            user.getUserGroup().forEach(group ->
                                    System.out.println("- " + group.getGroupName())
                            );
                        }
                        return isAdminByGroup;
                    }

                    // Additional fallback: check by user role if available
                    try {
                        // If there's a role field in User entity
                        Object roleField = user.getClass().getMethod("getRole").invoke(user);
                        if (roleField != null) {
                            String role = roleField.toString();
                            boolean isAdminByRole = "ADMIN".equalsIgnoreCase(role) ||
                                    "ADMINISTRATOR".equalsIgnoreCase(role);
                            System.out.println("Fallback admin check by role '" + role + "': " + isAdminByRole);
                            return isAdminByRole;
                        }
                    } catch (Exception roleEx) {
                        System.out.println("No role field found or accessible: " + roleEx.getMessage());
                    }
                }
            } catch (Exception fallbackEx) {
                System.out.println("Fallback admin check also failed: " + fallbackEx.getMessage());
            }

            // Final fallback: assume not admin if all checks fail
            System.out.println("All admin checks failed, treating user as regular user");
            return false;
        }
    }

    @Transactional
    public Post createPost(PostMessage dto) {
        System.out.println("Creating post for userId: " + dto.getUserId());
        System.out.println("Post details - Content: " + dto.getContent() + ", PostLevel: " + dto.getPostLevel() + ", PostLevelId: " + dto.getPostLevelId());

        try {
            // First, let's check if the user exists with better error handling
            User user = userRepository.findByUserId(dto.getUserId());
            if (user == null) {
                System.out.println("User not found with userId: " + dto.getUserId());
                throw new EntityNotFoundException("User does not exist with userId: " + dto.getUserId());
            }

            Set<Long> approvedCommunityIds = getApprovedCommunityIds(dto.getUserId());

            System.out.println("Found user: " + user.getFullName() + " with communities: " +
                    approvedCommunityIds.size());

            // Check if user is admin using robust method
            boolean isUserAdmin = isUserAdmin(dto.getUserId());
            System.out.println("Final admin status for user " + dto.getUserId() + ": " + isUserAdmin);

            // Validate that user can post with better error handling
            if (!isUserAdmin && !canUserPost(dto.getUserId())) {
                System.out.println("User cannot post - failed canUserPost check");
                throw new IllegalStateException("User must belong to at least one mycommunity to create posts");
            }

            Post post = new Post();
            post.setPrivacy(user.getPrivacy());
            post.setContent(dto.getContent());
            post.setUser(user);
            if (dto.getFile() != null) {
                post.setPicture(dto.getFile().getBytes());
            }

            if (dto.getPostLevel() != null){
                post.setPostLevel(PostLevel.of(dto.getPostLevel()));
                post.setPostLevelId(dto.getPostLevelId());

                // Handle different post levels
                if (PostLevel.COMMUNITY.equals(post.getPostLevel())) {
                    if (dto.getPostLevelId() != null) {
                        // Community-specific posting - validate user permission (skip for admins)
                        if (!isUserAdmin && !canUserPostToCommunity(dto.getUserId(), dto.getPostLevelId())) {
                            System.out.println("User " + dto.getUserId() + " does not have permission to post to mycommunity " + dto.getPostLevelId());
                            throw new IllegalStateException("User does not have permission to post to this mycommunity");
                        } else if (isUserAdmin) {
                            System.out.println("Admin user " + dto.getUserId() + " can post to any mycommunity, including " + dto.getPostLevelId());
                        }
                    } else {
                        // General post (COMMUNITY level with null postLevelId)
                        System.out.println("Creating general post without mycommunity association");
                    }
                }
            } else {
                post.setPostLevel(PostLevel.COMMUNITY);
                // Use the postLevelId from the DTO if provided, otherwise use user's first mycommunity
                if (dto.getPostLevelId() != null) {
                    // Validate that user can post to this specific mycommunity (skip for admins)
                    if (!isUserAdmin && !canUserPostToCommunity(dto.getUserId(), dto.getPostLevelId())) {
                        throw new IllegalStateException("User does not have permission to post to this mycommunity");
                    }
                    post.setPostLevelId(dto.getPostLevelId());
                } else {
                    // Use user's first mycommunity as fallback
                    if (!approvedCommunityIds.isEmpty()) {
                        Long firstCommunityId = approvedCommunityIds.iterator().next();
                        post.setPostLevelId(firstCommunityId);
                        System.out.println("Using user's first mycommunity: " + firstCommunityId);
                    } else if (!isUserAdmin) {
                        throw new IllegalStateException("User must belong to at least one mycommunity to create posts");
                    } else {
                        // Admin without communities - this shouldn't happen but handle gracefully
                        System.out.println("Admin user has no communities, this might indicate a data issue");
                        throw new IllegalStateException("Unable to determine target mycommunity for post");
                    }
                }
            }

            post = postRepository.save(post);
            System.out.println("Post saved successfully with ID: " + post.getId());

            PostDTO postDTO = new PostDTO();
            postDTO.setPrivacy(post.isPrivacy());
            postDTO.setDateCreated(String.valueOf(post.getDateCreated()));
            postDTO.setPostID(post.getId());
            postDTO.setContent(post.getContent());
            postDTO.setUserFullName(post.getUser().getFullName());
            postDTO.setPostLevelId(post.getPostLevelId()); // Add post level ID to DTO

            // Add mycommunity name to the post DTO
            if (post.getPostLevel() == PostLevel.COMMUNITY && post.getPostLevelId() == null) {
                // General post (COMMUNITY level with null postLevelId)
                postDTO.setCommunityName("General");
                // Don't set isGeneralPost to avoid duplicate display
            } else if (post.getPostLevelId() != null) {
                try {
                    var community = communityService.getCommunityById(post.getPostLevelId());
                    if (community != null) {
                        postDTO.setCommunityName(community.getName());
                    }
                } catch (Exception e) {
                    System.out.println("Error getting mycommunity name for post: " + e.getMessage());
                }
            }

            if (post.isPrivacy()){
                // Send private posts only to the user
                messagingTemplate.convertAndSend("/topic/posts/" + user.getUserId(), postDTO);
            } else {
                // Send to different destinations based on post level
                if (post.getPostLevel() == PostLevel.GROUP) {
                    // Send group posts to group-specific channel
                    messagingTemplate.convertAndSend("/topic/group/" + post.getPostLevelId() + "/posts", postDTO);
                } else if (post.getPostLevel() == PostLevel.NETWORK) {
                    // Send network posts to network-specific channel
                    messagingTemplate.convertAndSend("/topic/network/" + post.getPostLevelId() + "/posts", postDTO);
                } else {
                    // Send community and general posts to general channel for all users to receive
                    messagingTemplate.convertAndSend("/topic/posts/", postDTO);
                }
            }
            return post;
        } catch (IOException e) {
            System.out.println("IOException in createPost: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalArgumentException(e);
        } catch (Exception e) {
            System.out.println("Exception in createPost: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Transactional
    public List<PostDTO> getCommunityPosts(Long communityId){
        List<Post> posts = postRepository.findAllByPostLevelAndPostLevelIdOrderByDateCreatedDesc(PostLevel.COMMUNITY, communityId);
        return mapPosts(posts);
    }

    @Transactional
    public List<PostDTO> getGroupPosts(Long groupId){
        List<Post> posts = postRepository.findAllByPostLevelAndPostLevelIdOrderByDateCreatedDesc(PostLevel.GROUP, groupId);
        return mapPosts(posts);
    }

    @Transactional
    public List<PostDTO> getPosts() {
        List<Post> posts = postRepository.findAllByOrderByDateCreatedDesc();
        // Filter out GROUP and NETWORK posts for home page - show COMMUNITY and GENERAL posts
        List<Post> visiblePosts = posts.stream()
                .filter(post -> post.getPostLevel() != PostLevel.GROUP
                        && post.getPostLevel() != PostLevel.NETWORK)
                .toList();
        return mapPosts(visiblePosts);
    }

    @Transactional
    public List<PostDTO> getPostsByUser(String userId) {
        List<Post> posts = postRepository.findPostsForUser(userId);
        // Filter out GROUP and NETWORK posts for home page - show COMMUNITY and GENERAL posts
        List<Post> visiblePosts = posts.stream()
                .filter(post -> post.getPostLevel() != PostLevel.GROUP
                        && post.getPostLevel() != PostLevel.NETWORK)
                .toList();
        return mapPosts(visiblePosts);
    }

    @Transactional
    public List<PostDTO> getPostsFromUserCommunities(String userId) {
        return getPostsFromUserCommunities(userId, null);
    }

    @Transactional
    public List<PostDTO> getPostsFromUserCommunities(String userId, Long selectedCommunityId) {
        try {
            // Check if user is admin using robust method
            boolean isUserAdmin = isUserAdmin(userId);
            System.out.println("User " + userId + " admin status in getPostsFromUserCommunities: " + isUserAdmin);

            // Get user to find their communities
            User user = userRepository.findByUserId(userId);
            if (user == null) {
                throw new EntityNotFoundException("User does not exist");
            }

            Set<Long> approvedCommunityIds = getApprovedCommunityIds(userId);

            List<Post> posts = new ArrayList<>();

            if (selectedCommunityId != null) {
                // Get posts from the specific selected mycommunity only
                System.out.println("Getting posts for specific mycommunity: " + selectedCommunityId);
                List<Post> communityPosts = postRepository.findAllByPostLevelAndPostLevelIdOrderByDateCreatedDesc(
                        PostLevel.COMMUNITY, selectedCommunityId);

                // Check if user is member of this mycommunity or is admin
                boolean isMemberOfCommunity = isUserAdmin || approvedCommunityIds.contains(selectedCommunityId);

                System.out.println("User is member of mycommunity " + selectedCommunityId + ": " + isMemberOfCommunity);
                System.out.println("Found " + communityPosts.size() + " posts in mycommunity " + selectedCommunityId);

                // Only show posts if user is a member of the mycommunity or is admin
                if (isMemberOfCommunity) {
                    // Filter posts based on privacy and user's mycommunity membership
                    for (Post post : communityPosts) {
                        if (!post.isPrivacy()) { // Public posts
                            posts.add(post);
                        } else if (post.getUser().getUserId().equals(userId)) { // User's own posts
                            posts.add(post);
                        } else { // Private posts from communities user belongs to
                            posts.add(post);
                        }
                    }
                    System.out.println("Added " + posts.size() + " posts after privacy filtering");
                } else {
                    System.out.println("User is not a member of mycommunity " + selectedCommunityId + ", no posts added");
                }
            } else {
                // Get posts from all communities user belongs to ONLY (or all communities if admin)
                System.out.println("Getting posts for all user communities");

                if (isUserAdmin) {
                    // Admin sees all mycommunity posts (including general posts with null postLevelId)
                    List<Post> allCommunityPosts = postRepository.findAllByPostLevelOrderByDateCreatedDesc(PostLevel.COMMUNITY);

                    for (Post post : allCommunityPosts) {
                        if (!post.isPrivacy()) { // Public posts
                            posts.add(post);
                        } else if (post.getUser().getUserId().equals(userId)) { // User's own posts
                            posts.add(post);
                        }
                    }
                    System.out.println("Admin: Added " + posts.size() + " posts from all communities and general posts");
                } else {
                    // Regular user sees only posts from their communities
                    for (Long communityId : approvedCommunityIds) {
                        List<Post> communityPosts = postRepository.findAllByPostLevelAndPostLevelIdOrderByDateCreatedDesc(
                                PostLevel.COMMUNITY, communityId);

                        // Add all posts from user's communities (both public and private)
                        for (Post post : communityPosts) {
                            if (!post.isPrivacy()) { // Public posts
                                posts.add(post);
                            } else if (post.getUser().getUserId().equals(userId)) { // User's own posts
                                posts.add(post);
                            } else { // Private posts from communities user belongs to
                                posts.add(post);
                            }
                        }
                    }

                    // Add general posts for all users (COMMUNITY level with null postLevelId)
                    List<Post> allCommunityPosts = postRepository.findAllByPostLevelOrderByDateCreatedDesc(PostLevel.COMMUNITY);
                    for (Post post : allCommunityPosts) {
                        // Only add general posts (postLevelId is null) that haven't been added already
                        if (post.getPostLevelId() == null) {
                            if (!post.isPrivacy()) { // Public posts
                                posts.add(post);
                            } else if (post.getUser().getUserId().equals(userId)) { // User's own posts
                                posts.add(post);
                            }
                        }
                    }
                    System.out.println("Regular user: Added " + posts.size() + " posts from user communities and general posts");
                }
            }

            // Sort posts by date created (most recent first)
            posts.sort((p1, p2) -> p2.getDateCreated().compareTo(p1.getDateCreated()));

            // Map posts and add mycommunity information
            List<PostDTO> postDTOs = mapPosts(posts);

            // Add mycommunity names to posts and mark general posts
            for (PostDTO postDTO : postDTOs) {
                try {
                    // Find the post to get its mycommunity ID
                    Post originalPost = posts.stream()
                            .filter(p -> p.getId().equals(postDTO.getPostID()))
                            .findFirst()
                            .orElse(null);

                    if (originalPost != null) {
                        if (originalPost.getPostLevel() == PostLevel.COMMUNITY && originalPost.getPostLevelId() == null) {
                            // General posts (COMMUNITY level with null postLevelId)
                            postDTO.setCommunityName("General");
                            // Don't set isGeneralPost to avoid duplicate display
                        } else if (originalPost.getPostLevelId() != null) {
                            var community = communityService.getCommunityById(originalPost.getPostLevelId());
                            if (community != null) {
                                postDTO.setCommunityName(community.getName());
                                // Mark as general post if it's from a different mycommunity than selected
                                if (selectedCommunityId != null && !originalPost.getPostLevelId().equals(selectedCommunityId)) {
                                    postDTO.setIsGeneralPost(true);
                                }
                            } else {
                                postDTO.setCommunityName("Community");
                            }
                        } else {
                            postDTO.setCommunityName("Community");
                        }
                    } else {
                        postDTO.setCommunityName("Community");
                    }
                } catch (Exception e) {
                    System.out.println("Error getting mycommunity for post: " + e.getMessage());
                    postDTO.setCommunityName("Community");
                }
            }

            System.out.println("Returning " + postDTOs.size() + " posts for user " + userId);
            return postDTOs;
        } catch (Exception e) {
            System.out.println("Error getting posts from user communities: " + e.getMessage());
            e.printStackTrace();
            // Fallback to empty list if there's an error
            return new ArrayList<>();
        }
    }

    @Transactional
    public List<PostDTO> getPublicPosts() {
        List<Post> posts = postRepository.findAllByPrivacyOrderByDateCreatedDesc(false);
        return mapPosts(posts);
    }

    @Transactional
    public boolean canUserPost(String userId) {
        try {
            System.out.println("Checking if user can post: " + userId);

            // First check if user exists
            User user = userRepository.findByUserId(userId);
            if (user == null) {
                System.out.println("User not found: " + userId);
                return false;
            }

            // Global admin check using robust method
            if (isUserAdmin(userId)) {
                System.out.println("User " + userId + " is global admin - can post");
                return true;
            }

            // Check if user has ADMIN or CREATOR role in at least one community
            boolean isCommunityAdmin = communityMembershipRepository.isUserAdminOfAnyCommunity(userId);

            System.out.println("User " + userId + " community admin check:");
            System.out.println("- Is community admin/creator: " + isCommunityAdmin);

            return isCommunityAdmin;
        } catch (Exception e) {
            System.out.println("Error checking if user can post: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Transactional
    public boolean canUserPostToCommunity(String userId, Long communityId) {
        try {
            System.out.println("Checking if user " + userId + " can post to mycommunity " + communityId);

            // Global admin check using robust method
            if (isUserAdmin(userId)) {
                System.out.println("User " + userId + " is global admin - can post to any mycommunity");
                return true;
            }

            User user = userRepository.findByUserId(userId);
            if (user == null) {
                System.out.println("User " + userId + " not found");
                throw new EntityNotFoundException("User does not exist");
            }

            // Check if user has ADMIN or CREATOR role in the specific community
            boolean isCommunityAdmin = communityMembershipRepository.isUserCommunityAdmin(
                    userId,
                    communityId
            );

            System.out.println("User " + userId + " is community admin/creator of mycommunity " + communityId + ": " + isCommunityAdmin);

            return isCommunityAdmin;
        } catch (Exception e) {
            System.out.println("Error checking if user can post to mycommunity: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Transactional
    public CommentDTO createComment(CommentDTO dto){
        Post post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new EntityNotFoundException("Post does not exist"));
        User user = userRepository.findByUserId(dto.getUserId());
        if (user == null) {
            throw new EntityNotFoundException("User does not exist");
        }

        Comment comment = new Comment();
        comment.setContent(dto.getComment());
        comment.setUser(user);
        post.getComments().add(comment);

        // Putting user's name to send via websocket
        dto.setSenderFullName(user.getFullName());
        dto.setDateCreated(String.valueOf(LocalDateTime.now()));

        // Send comment to appropriate channel based on post level
        String commentDestination;
        if (post.getPostLevel() == PostLevel.GROUP) {
            commentDestination = "/topic/group/" + post.getPostLevelId() + "/comment";
        } else if (post.getPostLevel() == PostLevel.NETWORK) {
            commentDestination = "/topic/network/" + post.getPostLevelId() + "/comment";
        } else {
            commentDestination = "/topic/comment/";
        }
        messagingTemplate.convertAndSend(commentDestination, dto);

        postRepository.save(post);
        return dto;
    }

    @Transactional
    public List<CommentDTO> getComments(Long postId){
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post does not exist"));
        List<Comment> comments = post.getComments();
        List<CommentDTO> commentDTOs = new ArrayList<>();
        for (Comment comment : comments) {
            CommentDTO dto = new CommentDTO();
            dto.setComment(comment.getContent());
            dto.setSenderFullName(comment.getUser().getFullName());
            dto.setDateCreated(StringUtils.offsetDateTimeToStringDate
                    (comment.getDateCreated()==null? OffsetDateTime.now() : comment.getDateCreated()));
            commentDTOs.add(dto);
        }
        return commentDTOs;
    }
}
