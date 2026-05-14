package com.justjava.mycommunity.network;

import com.justjava.mycommunity.chat.dto.CommentDTO;
import com.justjava.mycommunity.chat.dto.PostDTO;
import com.justjava.mycommunity.chat.entity.Conversation;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.chat.repository.ConversationRepository;
import com.justjava.mycommunity.chat.repository.PostRepository;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.posts.Comment;
import com.justjava.mycommunity.posts.Post;
import com.justjava.mycommunity.posts.PostLevel;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.justjava.mycommunity.util.MappingUtils.mapPosts;
import static com.justjava.mycommunity.util.MappingUtils.mapUsersToDTO;

@Service
@RequiredArgsConstructor
public class NetworkNewService {

    private final NetworkRepository networkRepository;
    private final NetworkMembershipRepository membershipRepository;
    private final NetworkConnectionRequestRepository connectionRequestRepository;
    private final NetworkConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final PostRepository postRepository;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ═══════════════════════════════════════════════════════════════════
    //  1. NETWORK CRUD — community admin only
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Create a new network inside a community.
     * Only community ADMIN / CREATOR can do this.
     * The admin is automatically added as OWNER of the network.
     */
    @Transactional
    public NetworkDTO createNetwork(CreateNetworkRequest request, String adminUserId) {
        assertCommunityAdmin(adminUserId, request.getCommunityId());

        User admin = resolveUser(adminUserId);
        Community community = communityRepository.findById(request.getCommunityId())
                .orElseThrow(() -> new EntityNotFoundException("Club not found"));

        Network network = new Network();
        network.setName(request.getName());
        network.setDescription(request.getDescription());
        network.setCommunity(community);
        network.setCreatedBy(admin);
        network = networkRepository.save(network);

        // Auto-add the admin as OWNER
        NetworkMembership ownership = NetworkMembership.of(network, admin, NetworkRole.OWNER);
        membershipRepository.save(ownership);

        return toDTO(network, adminUserId);
    }

    @Transactional(readOnly = true)
    public NetworkDTO getNetworkById(Long networkId, String currentUserId) {
        Network network = networkRepository.findById(networkId)
                .orElseThrow(() -> new EntityNotFoundException("Network not found"));
        return toDTO(network, currentUserId);
    }

    /** Networks the current user belongs to in a specific community. */
    @Transactional(readOnly = true)
    public List<NetworkDTO> getUserNetworksInCommunity(String userId, Long communityId) {
        List<Network> networks = networkRepository.findByMembershipInCommunity(userId, communityId);
        return networks.stream().map(n -> toDTO(n, userId)).toList();
    }

    /** All networks in a community (for admin browsing). */
    @Transactional(readOnly = true)
    public List<NetworkDTO> getAllNetworksInCommunity(Long communityId, String currentUserId) {
        List<Network> networks = networkRepository.findByCommunity_Id(communityId);
        return networks.stream().map(n -> toDTO(n, currentUserId)).toList();
    }

    @Transactional
    public NetworkDTO updateNetwork(Long networkId, CreateNetworkRequest request, String adminUserId) {
        Network network = networkRepository.findById(networkId)
                .orElseThrow(() -> new EntityNotFoundException("Network not found"));
        assertCommunityAdmin(adminUserId, network.getCommunity().getId());

        network.setName(request.getName());
        network.setDescription(request.getDescription());
        networkRepository.save(network);
        return toDTO(network, adminUserId);
    }

    @Transactional
    public void deleteNetwork(Long networkId, String adminUserId) {
        Network network = networkRepository.findById(networkId)
                .orElseThrow(() -> new EntityNotFoundException("Network not found"));
        assertCommunityAdmin(adminUserId, network.getCommunity().getId());
        networkRepository.delete(network);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2. MEMBERSHIP — admin adds/removes members
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Admin adds a user to a network.
     * The target user must be an approved member of the network's community.
     */
    @Transactional
    public void addMember(Long networkId, String targetUserId, String adminUserId) {
        Network network = networkRepository.findById(networkId)
                .orElseThrow(() -> new EntityNotFoundException("Network not found"));
        assertCommunityAdmin(adminUserId, network.getCommunity().getId());

        if (membershipRepository.existsByNetwork_IdAndUser_UserId(networkId, targetUserId)) {
            throw new IllegalStateException("User is already a member of this network");
        }

        boolean inCommunity = communityMembershipRepository.existsByUserIdAndCommunityIdAndStatus(
                targetUserId, network.getCommunity().getId(), MembershipStatus.APPROVED);
        if (!inCommunity) {
            throw new IllegalStateException("User must be an approved community member first");
        }

        User user = resolveUser(targetUserId);
        NetworkMembership membership = NetworkMembership.of(network, user, NetworkRole.MEMBER);
        membershipRepository.save(membership);
    }

    /** Admin removes a user from a network. */
    @Transactional
    public void removeMember(Long networkId, String targetUserId, String adminUserId) {
        Network network = networkRepository.findById(networkId)
                .orElseThrow(() -> new EntityNotFoundException("Network not found"));
        assertCommunityAdmin(adminUserId, network.getCommunity().getId());

        if (!membershipRepository.existsByNetwork_IdAndUser_UserId(networkId, targetUserId)) {
            throw new EntityNotFoundException("User is not a member of this network");
        }
        membershipRepository.deleteByNetwork_IdAndUser_UserId(networkId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getNetworkMembers(Long networkId) {
        List<User> users = membershipRepository.findUsersByNetworkId(networkId);
        return mapUsersToDTO(users);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3. POSTS & COMMENTS within a Network
    // ═══════════════════════════════════════════════════════════════════

    /** Create a post inside a network. Only members can post. */
    @Transactional
    public Post createNetworkPost(Long networkId, PostMessage dto) {
        assertNetworkMember(networkId, dto.getUserId());
        User user = resolveUser(dto.getUserId());

        Post post = new Post();
        post.setContent(dto.getContent());
        post.setUser(user);
        post.setPostLevel(PostLevel.NETWORK);
        post.setPostLevelId(networkId);
        post.setPrivacy(user.getPrivacy());

        if (dto.getFile() != null) {
            try { post.setPicture(dto.getFile().getBytes()); }
            catch (Exception e) { throw new IllegalArgumentException("Could not read uploaded file", e); }
        }

        post = postRepository.save(post);

        PostDTO postDTO = new PostDTO();
        postDTO.setPostID(post.getId());
        postDTO.setContent(post.getContent());
        postDTO.setUserFullName(user.getFullName());
        postDTO.setUserEmail(user.getEmail());
        postDTO.setDateCreated(String.valueOf(post.getDateCreated()));
        postDTO.setPostLevelId(networkId);
        messagingTemplate.convertAndSend("/topic/network/" + networkId + "/posts", postDTO);

        return post;
    }

    @Transactional(readOnly = true)
    public List<PostDTO> getNetworkPosts(Long networkId) {
        List<Post> posts = postRepository.findAllByPostLevelAndPostLevelIdOrderByDateCreatedDesc(
                PostLevel.NETWORK, networkId);
        return mapPosts(posts);
    }

    @Transactional
    public CommentDTO createNetworkComment(Long networkId, CommentDTO dto) {
        assertNetworkMember(networkId, dto.getUserId());

        Post post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));
        if (post.getPostLevel() != PostLevel.NETWORK || !networkId.equals(post.getPostLevelId())) {
            throw new IllegalStateException("Post does not belong to this network");
        }

        User user = resolveUser(dto.getUserId());
        Comment comment = new Comment();
        comment.setContent(dto.getComment());
        comment.setUser(user);
        post.getComments().add(comment);
        postRepository.save(post);

        dto.setSenderFullName(user.getFullName());
        dto.setDateCreated(String.valueOf(java.time.LocalDateTime.now()));
        messagingTemplate.convertAndSend("/topic/network/" + networkId + "/comment", dto);
        return dto;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4. CONNECTION REQUESTS — user-to-user within a network
    // ═══════════════════════════════════════════════════════════════════

    /** User sends a connection request to another member of the same network. */
    @Transactional
    public void sendConnectionRequest(Long networkId, String requesterUserId, String receiverUserId) {
        if (requesterUserId.equals(receiverUserId)) {
            throw new IllegalStateException("Cannot connect with yourself");
        }
        assertNetworkMember(networkId, requesterUserId);
        assertNetworkMember(networkId, receiverUserId);

        if (connectionRepository.existsBetween(networkId, requesterUserId, receiverUserId)) {
            throw new IllegalStateException("Already connected with this user in this network");
        }
        if (connectionRequestRepository.existsPendingBetween(networkId, requesterUserId, receiverUserId)) {
            throw new IllegalStateException("A pending connection request already exists");
        }

        User requester = resolveUser(requesterUserId);
        User receiver = resolveUser(receiverUserId);
        Network network = networkRepository.findById(networkId)
                .orElseThrow(() -> new EntityNotFoundException("Network not found"));

        NetworkConnectionRequest request = new NetworkConnectionRequest();
        request.setNetwork(network);
        request.setRequester(requester);
        request.setReceiver(receiver);
        request.setStatus(NetworkConnectionRequest.ConnectionStatus.PENDING);
        connectionRequestRepository.save(request);
    }

    /**
     * Accept a connection request.
     * Creates a NetworkConnection record + a Conversation for messaging.
     */
    @Transactional
    public void acceptConnectionRequest(Long requestId, String currentUserId) {
        NetworkConnectionRequest request = connectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Connection request not found"));

        if (!request.getReceiver().getUserId().equals(currentUserId)) {
            throw new SecurityException("Only the receiver can accept this request");
        }

        request.setStatus(NetworkConnectionRequest.ConnectionStatus.ACCEPTED);
        connectionRequestRepository.save(request);

        // Create or find existing conversation
        String userId1 = request.getRequester().getUserId();
        String userId2 = request.getReceiver().getUserId();
        Conversation conversation = getOrCreateConversation(userId1, userId2);

        // Create the NetworkConnection that links the network, users, and conversation
        NetworkConnection connection = new NetworkConnection();
        connection.setNetwork(request.getNetwork());
        connection.setUser1(request.getRequester());
        connection.setUser2(request.getReceiver());
        connection.setConversation(conversation);
        connectionRepository.save(connection);
    }

    /** Reject a connection request. */
    @Transactional
    public void rejectConnectionRequest(Long requestId, String currentUserId) {
        NetworkConnectionRequest request = connectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Connection request not found"));

        if (!request.getReceiver().getUserId().equals(currentUserId)) {
            throw new SecurityException("Only the receiver can reject this request");
        }

        request.setStatus(NetworkConnectionRequest.ConnectionStatus.REJECTED);
        connectionRequestRepository.save(request);
    }

    /** Get all pending connection requests for the current user (incoming). */
    @Transactional(readOnly = true)
    public List<ConnectionRequestDTO> getPendingConnectionRequests(String userId) {
        List<NetworkConnectionRequest> requests = connectionRequestRepository
                .findByReceiver_UserIdAndStatus(userId, NetworkConnectionRequest.ConnectionStatus.PENDING);
        return requests.stream().map(this::toConnectionRequestDTO).toList();
    }

    /** Get pending connection requests within a specific network. */
    @Transactional(readOnly = true)
    public List<ConnectionRequestDTO> getPendingConnectionRequestsInNetwork(Long networkId, String userId) {
        List<NetworkConnectionRequest> requests = connectionRequestRepository
                .findByNetwork_IdAndReceiver_UserIdAndStatus(
                        networkId, userId, NetworkConnectionRequest.ConnectionStatus.PENDING);
        return requests.stream().map(this::toConnectionRequestDTO).toList();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5. CONNECTIONS — for the messages page
    // ═══════════════════════════════════════════════════════════════════

    /** All accepted connections for a user across all networks. */
    @Transactional(readOnly = true)
    public List<NetworkConnectionDTO> getUserConnections(String userId) {
        List<NetworkConnection> connections = connectionRepository.findAllByUser(userId);
        return connections.stream().map(c -> toConnectionDTO(c, userId)).toList();
    }

    /** Look up which network a conversation belongs to (for the messages page header). */
    @Transactional(readOnly = true)
    public NetworkConnection getConnectionForConversation(Long conversationId) {
        return connectionRepository.findByConversation_Id(conversationId).orElse(null);
    }

    /** Get connected users within a specific network. */
    @Transactional(readOnly = true)
    public List<UserDTO> getConnectedUsersInNetwork(Long networkId, String userId) {
        List<NetworkConnection> connections = connectionRepository.findByNetworkAndUser(networkId, userId);
        List<User> connectedUsers = connections.stream()
                .map(c -> c.getOtherUser(userId))
                .toList();
        return mapUsersToDTO(connectedUsers);
    }

    /** Members the current user is NOT yet connected with (for "connect" buttons). */
    @Transactional(readOnly = true)
    public List<UserDTO> getUnconnectedMembers(Long networkId, String currentUserId) {
        List<User> allMembers = membershipRepository.findUsersByNetworkId(networkId);
        List<NetworkConnection> myConnections = connectionRepository.findByNetworkAndUser(networkId, currentUserId);

        Set<String> excludeUserIds = new HashSet<>();
        excludeUserIds.add(currentUserId); // exclude self
        for (NetworkConnection conn : myConnections) {
            excludeUserIds.add(conn.getUser1().getUserId());
            excludeUserIds.add(conn.getUser2().getUserId());
        }

        // Also exclude users with pending requests (either direction)
        List<NetworkConnectionRequest> pendingIncoming = connectionRequestRepository
                .findByNetwork_IdAndReceiver_UserIdAndStatus(
                        networkId, currentUserId, NetworkConnectionRequest.ConnectionStatus.PENDING);
        for (NetworkConnectionRequest req : pendingIncoming) {
            excludeUserIds.add(req.getRequester().getUserId());
        }
        // Check outgoing pending
        for (User member : allMembers) {
            if (connectionRequestRepository.existsPendingBetween(networkId, currentUserId, member.getUserId())) {
                excludeUserIds.add(member.getUserId());
            }
        }

        List<User> unconnected = allMembers.stream()
                .filter(u -> !excludeUserIds.contains(u.getUserId()))
                .toList();
        return mapUsersToDTO(unconnected);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private User resolveUser(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
        return user;
    }

    private void assertCommunityAdmin(String userId, Long communityId) {
        boolean isAdmin = communityMembershipRepository.isUserCommunityAdmin(userId, communityId);
        if (!isAdmin) {
            throw new SecurityException("Only community admins/creators can perform this action");
        }
    }

    private void assertNetworkMember(Long networkId, String userId) {
        if (!membershipRepository.existsByNetwork_IdAndUser_UserId(networkId, userId)) {
            throw new IllegalStateException("User is not a member of this network");
        }
    }

    private Conversation getOrCreateConversation(String userId1, String userId2) {
        List<String> userIds = List.of(userId1, userId2);
        Optional<Conversation> existing = conversationRepository.findConversationByExactUserIds(userIds, 2);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user1 = resolveUser(userId1);
        User user2 = resolveUser(userId2);
        Conversation conversation = new Conversation();
        conversation.setGroup(false);
        conversation.getMembers().add(user1);
        conversation.getMembers().add(user2);
        return conversationRepository.save(conversation);
    }

    private NetworkDTO toDTO(Network network, String currentUserId) {
        String currentUserRole = null;
        if (currentUserId != null) {
            currentUserRole = membershipRepository
                    .findByNetwork_IdAndUser_UserId(network.getId(), currentUserId)
                    .map(m -> m.getRole().name())
                    .orElse(null);
        }

        return NetworkDTO.builder()
                .id(network.getId())
                .name(network.getName())
                .description(network.getDescription())
                .communityId(network.getCommunity().getId())
                .communityName(network.getCommunity().getName())
                .createdByUserId(network.getCreatedBy().getUserId())
                .createdByName(network.getCreatedBy().getFullName())
                .memberCount((int) membershipRepository.countByNetwork_Id(network.getId()))
                .currentUserRole(currentUserRole)
                .build();
    }

    private ConnectionRequestDTO toConnectionRequestDTO(NetworkConnectionRequest req) {
        return ConnectionRequestDTO.builder()
                .id(req.getId())
                .networkId(req.getNetwork().getId())
                .networkName(req.getNetwork().getName())
                .requesterUserId(req.getRequester().getUserId())
                .requesterName(req.getRequester().getFullName())
                .receiverUserId(req.getReceiver().getUserId())
                .receiverName(req.getReceiver().getFullName())
                .status(req.getStatus().name())
                .build();
    }

    private NetworkConnectionDTO toConnectionDTO(NetworkConnection conn, String currentUserId) {
        User otherUser = conn.getOtherUser(currentUserId);
        Community community = conn.getNetwork().getCommunity();
        return NetworkConnectionDTO.builder()
                .connectionId(conn.getId())
                .networkId(conn.getNetwork().getId())
                .networkName(conn.getNetwork().getName())
                .communityId(community.getId())
                .communityName(community.getName())
                .connectedUserId(otherUser.getUserId())
                .connectedUserName(otherUser.getFullName())
                .conversationId(conn.getConversation() != null ? conn.getConversation().getId() : null)
                .build();
    }
}


