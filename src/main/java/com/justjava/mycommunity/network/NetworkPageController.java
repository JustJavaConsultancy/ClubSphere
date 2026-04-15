package com.justjava.mycommunity.network;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommentDTO;
import com.justjava.mycommunity.chat.dto.PostDTO;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller for the new shared-Network feature.
 *
 * Flow:
 *   1. Admin creates networks within a community and adds members.
 *   2. Members see their networks, post, and comment.
 *   3. Members send connection requests to other network members.
 *   4. Accepted connections unlock messaging on the messages page.
 *   5. Messages page shows which network the contact belongs to.
 */
@Controller
@RequestMapping("/networks")
@RequiredArgsConstructor
public class NetworkPageController {

    private final NetworkNewService networkNewService;
    private final AuthenticationManager authenticationManager;
    private final CommunityService communityService;

    // ═══════════════════════════════════════════════════════════════════
    //  PAGE ENDPOINTS (Thymeleaf)
    // ═══════════════════════════════════════════════════════════════════

    /** Network listing page — shows all networks the user belongs to. */
    @GetMapping
    public String networksPage(Model model, HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");

        Long communityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        if (communityId == null) {
            request.getSession().setAttribute("redirectAfterSelect", "/networks");
            return "redirect:/organizations";
        }
        String communityName = (String) request.getSession().getAttribute("selectedCommunityName");
        boolean isAdmin = authenticationManager.isAdmin();

        List<NetworkDTO> myNetworks = networkNewService.getUserNetworksInCommunity(currentUserId, communityId);
        List<NetworkDTO> allNetworks = isAdmin
                ? networkNewService.getAllNetworksInCommunity(communityId, currentUserId)
                : myNetworks;

        // Pending connection requests across all networks
        List<ConnectionRequestDTO> pendingRequests = networkNewService.getPendingConnectionRequests(currentUserId);

        model.addAttribute("myNetworks", myNetworks);
        model.addAttribute("allNetworks", allNetworks);
        model.addAttribute("pendingConnectionRequests", pendingRequests);
        model.addAttribute("selectedCommunityId", communityId);
        model.addAttribute("selectedCommunityName", communityName);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentPath", "/networks");
        return "network/networks";
    }

    /** Single network detail page — members, posts, comments, connection actions. */
    @GetMapping("/{networkId}")
    public String networkDetailPage(@PathVariable Long networkId, Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        boolean isAdmin = authenticationManager.isAdmin();

        NetworkDTO network = networkNewService.getNetworkById(networkId, currentUserId);
        List<UserDTO> members = networkNewService.getNetworkMembers(networkId);
        List<PostDTO> posts = networkNewService.getNetworkPosts(networkId);
        List<UserDTO> connectedUsers = networkNewService.getConnectedUsersInNetwork(networkId, currentUserId);
        List<UserDTO> unconnectedMembers = networkNewService.getUnconnectedMembers(networkId, currentUserId);
        List<ConnectionRequestDTO> pendingRequests =
                networkNewService.getPendingConnectionRequestsInNetwork(networkId, currentUserId);

        model.addAttribute("network", network);
        model.addAttribute("members", members);
        model.addAttribute("posts", posts);
        model.addAttribute("connectedUsers", connectedUsers);
        model.addAttribute("unconnectedMembers", unconnectedMembers);
        model.addAttribute("pendingConnectionRequests", pendingRequests);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentPath", "/networks");
        return "network/network-detail";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JSON API — NETWORK CRUD (admin only)
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<NetworkDTO> createNetwork(@Valid @RequestBody CreateNetworkRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        NetworkDTO dto = networkNewService.createNetwork(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/api/{networkId}")
    @ResponseBody
    public ResponseEntity<NetworkDTO> updateNetwork(@PathVariable Long networkId,
                                                    @Valid @RequestBody CreateNetworkRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        NetworkDTO dto = networkNewService.updateNetwork(networkId, request, currentUserId);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/api/{networkId}")
    @ResponseBody
    public ResponseEntity<Void> deleteNetwork(@PathVariable Long networkId) {
        String currentUserId = (String) authenticationManager.get("sub");
        networkNewService.deleteNetwork(networkId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JSON API — MEMBERSHIP (admin adds/removes members)
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping("/api/{networkId}/members/{userId}")
    @ResponseBody
    public ResponseEntity<String> addMember(@PathVariable Long networkId,
                                            @PathVariable String userId) {
        String currentUserId = (String) authenticationManager.get("sub");
        networkNewService.addMember(networkId, userId, currentUserId);
        return ResponseEntity.ok("Member added");
    }

    @DeleteMapping("/api/{networkId}/members/{userId}")
    @ResponseBody
    public ResponseEntity<Void> removeMember(@PathVariable Long networkId,
                                             @PathVariable String userId) {
        String currentUserId = (String) authenticationManager.get("sub");
        networkNewService.removeMember(networkId, userId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/{networkId}/members")
    @ResponseBody
    public ResponseEntity<List<UserDTO>> getMembers(@PathVariable Long networkId) {
        return ResponseEntity.ok(networkNewService.getNetworkMembers(networkId));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JSON API — POSTS & COMMENTS (network members)
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping("/api/{networkId}/posts")
    @ResponseBody
    public ResponseEntity<PostDTO> createPost(@PathVariable Long networkId,
                                              @RequestParam("content") String content,
                                              @RequestParam(value = "file", required = false) MultipartFile file) {
        String currentUserId = (String) authenticationManager.get("sub");
        PostMessage postMessage = PostMessage.builder()
                .content(content)
                .userId(currentUserId)
                .file(file)
                .postLevel("NETWORK")
                .postLevelId(networkId)
                .build();
        networkNewService.createNetworkPost(networkId, postMessage);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/api/{networkId}/posts")
    @ResponseBody
    public ResponseEntity<List<PostDTO>> getPosts(@PathVariable Long networkId) {
        return ResponseEntity.ok(networkNewService.getNetworkPosts(networkId));
    }

    @PostMapping("/api/{networkId}/posts/{postId}/comments")
    @ResponseBody
    public ResponseEntity<CommentDTO> createComment(@PathVariable Long networkId,
                                                    @PathVariable Long postId,
                                                    @RequestBody CommentDTO commentDTO) {
        String currentUserId = (String) authenticationManager.get("sub");
        commentDTO.setUserId(currentUserId);
        commentDTO.setPostId(postId);
        CommentDTO result = networkNewService.createNetworkComment(networkId, commentDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JSON API — CONNECTION REQUESTS (user-to-user within a network)
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping("/api/{networkId}/connect/{targetUserId}")
    @ResponseBody
    public ResponseEntity<String> sendConnectionRequest(@PathVariable Long networkId,
                                                        @PathVariable String targetUserId) {
        String currentUserId = (String) authenticationManager.get("sub");
        networkNewService.sendConnectionRequest(networkId, currentUserId, targetUserId);
        return ResponseEntity.ok("Connection request sent");
    }

    @PostMapping("/api/connection-requests/{requestId}/accept")
    @ResponseBody
    public ResponseEntity<String> acceptConnectionRequest(@PathVariable Long requestId) {
        String currentUserId = (String) authenticationManager.get("sub");
        networkNewService.acceptConnectionRequest(requestId, currentUserId);
        return ResponseEntity.ok("Connection accepted");
    }

    @PostMapping("/api/connection-requests/{requestId}/reject")
    @ResponseBody
    public ResponseEntity<String> rejectConnectionRequest(@PathVariable Long requestId) {
        String currentUserId = (String) authenticationManager.get("sub");
        networkNewService.rejectConnectionRequest(requestId, currentUserId);
        return ResponseEntity.ok("Connection rejected");
    }

    @GetMapping("/api/connection-requests/pending")
    @ResponseBody
    public ResponseEntity<List<ConnectionRequestDTO>> getPendingRequests() {
        String currentUserId = (String) authenticationManager.get("sub");
        return ResponseEntity.ok(networkNewService.getPendingConnectionRequests(currentUserId));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JSON API — CONNECTIONS (for messages page integration)
    // ═══════════════════════════════════════════════════════════════════

    /** All accepted connections with network info — used by the messages page. */
    @GetMapping("/api/connections")
    @ResponseBody
    public ResponseEntity<List<NetworkConnectionDTO>> getUserConnections() {
        String currentUserId = (String) authenticationManager.get("sub");
        return ResponseEntity.ok(networkNewService.getUserConnections(currentUserId));
    }

    /**
     * Returns community members that are NOT yet in the given network.
     * Used by the "Add Member" modal on the network detail page.
     */
    @GetMapping("/api/{networkId}/available-members")
    @ResponseBody
    public ResponseEntity<List<UserDTO>> getAvailableMembers(@PathVariable Long networkId,
                                                             HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");

        // Derive community from the network itself so we don't depend on session state
        NetworkDTO network = networkNewService.getNetworkById(networkId, currentUserId);
        Long communityId = network.getCommunityId();
        if (communityId == null) {
            // Fallback to session (should not happen, but just in case)
            communityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        }
        if (communityId == null) {
            return ResponseEntity.badRequest().build();
        }

        List<UserDTO> communityMembers = communityService.getCommunityMembersExcludingUser(communityId, currentUserId);
        List<UserDTO> networkMembers = networkNewService.getNetworkMembers(networkId);
        java.util.Set<String> existingIds = networkMembers.stream()
                .map(UserDTO::getUserId)
                .collect(java.util.stream.Collectors.toSet());

        List<UserDTO> available = communityMembers.stream()
                .filter(u -> !existingIds.contains(u.getUserId()))
                .toList();
        return ResponseEntity.ok(available);
    }
}





