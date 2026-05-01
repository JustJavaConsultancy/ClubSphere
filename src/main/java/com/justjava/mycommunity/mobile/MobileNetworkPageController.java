package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.PostDTO;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.network.*;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mobile/networks")
@RequiredArgsConstructor
public class MobileNetworkPageController {

    private final NetworkNewService networkNewService;
    private final AuthenticationManager authenticationManager;
    private final CommunityService communityService;

    /** Mobile networks listing page */
    @GetMapping
    public String networksPage(Model model, HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        Long communityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        if (communityId == null) {
            request.getSession().setAttribute("redirectAfterSelect", "/mobile/networks");
            return "redirect:/mobile/organizations";
        }
        String communityName = (String) request.getSession().getAttribute("selectedCommunityName");
        boolean isAdmin = authenticationManager.isAdmin();

        List<NetworkDTO> myNetworks = networkNewService.getUserNetworksInCommunity(currentUserId, communityId);
        List<NetworkDTO> allNetworks = isAdmin
                ? networkNewService.getAllNetworksInCommunity(communityId, currentUserId)
                : myNetworks;

        List<ConnectionRequestDTO> pendingRequests = networkNewService.getPendingConnectionRequests(currentUserId);

        model.addAttribute("myNetworks", myNetworks);
        model.addAttribute("allNetworks", allNetworks);
        model.addAttribute("pendingConnectionRequests", pendingRequests);
        model.addAttribute("selectedCommunityId", communityId);
        model.addAttribute("selectedCommunityName", communityName);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentPath", "/networks");
        return "network/mobile-networks";
    }

    /** Mobile network detail page */
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
        return "network/mobile-network-detail";
    }

    // ── JSON API endpoints (proxy to NetworkNewService) ──────────────────────

    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<NetworkDTO> createNetwork(@RequestBody CreateNetworkRequest req) {
        String currentUserId = (String) authenticationManager.get("sub");
        return ResponseEntity.status(201).body(networkNewService.createNetwork(req, currentUserId));
    }

    @PostMapping("/api/{networkId}/posts")
    @ResponseBody
    public ResponseEntity<PostDTO> createPost(@PathVariable Long networkId,
                                              @RequestParam("content") String content,
                                              @RequestParam(value = "file", required = false) MultipartFile file) {
        String currentUserId = (String) authenticationManager.get("sub");
        PostMessage msg = PostMessage.builder()
                .content(content).userId(currentUserId).file(file)
                .postLevel("NETWORK").postLevelId(networkId).build();
        networkNewService.createNetworkPost(networkId, msg);
        return ResponseEntity.status(201).build();
    }

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

    @PostMapping("/api/{networkId}/members/{userId}")
    @ResponseBody
    public ResponseEntity<String> addMember(@PathVariable Long networkId, @PathVariable String userId) {
        String currentUserId = (String) authenticationManager.get("sub");
        networkNewService.addMember(networkId, userId, currentUserId);
        return ResponseEntity.ok("Member added");
    }

    @GetMapping("/api/{networkId}/available-members")
    @ResponseBody
    public ResponseEntity<List<UserDTO>> getAvailableMembers(@PathVariable Long networkId,
                                                             HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        NetworkDTO network = networkNewService.getNetworkById(networkId, currentUserId);
        Long communityId = network.getCommunityId();
        if (communityId == null) communityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        if (communityId == null) return ResponseEntity.badRequest().build();

        List<UserDTO> communityMembers = communityService.getCommunityMembersExcludingUser(communityId, currentUserId);
        List<UserDTO> networkMembers = networkNewService.getNetworkMembers(networkId);
        Set<String> existingIds = networkMembers.stream().map(UserDTO::getUserId).collect(Collectors.toSet());
        return ResponseEntity.ok(communityMembers.stream().filter(u -> !existingIds.contains(u.getUserId())).toList());
    }
}

