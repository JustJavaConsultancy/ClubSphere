package com.justjava.mycommunity.config;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.keycloak.KeycloakService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class SettingsController {

    @Autowired
    UserService userService;

    @Autowired
    KeycloakService keycloakService;

    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    CommunityService communityService;

    @GetMapping("/settings")
    public String settings(Model model) {
        // Preload any data if needed later (e.g., current level)
        String loginUser = (String) authenticationManager.get("sub");
        UserDTO currentUser = userService.getSingleUser(loginUser);
        CommunityDTO myCommunity = communityService.getCommunity();

        System.out.println("This is the current mycommunity " + myCommunity.getCommunityPrivacy());
        model.addAttribute("isAdmin", authenticationManager.isAdmin());
        model.addAttribute("community", myCommunity);
        model.addAttribute("user", currentUser);
        return "settings/index";
    }

    @PostMapping("/change-password")
    public ResponseEntity<Object> changePassword(@RequestParam Map<String, Object> formData, HttpServletRequest request){
//        System.out.println("This is the password form" + formData);

        String message = keycloakService.updatePassword(formData);
        HttpHeaders headers = new HttpHeaders();

        if (message.equalsIgnoreCase("success")) {
            keycloakService.logoutUser(request);
            headers.add("HX-Redirect", "/");
        } else {
            return ResponseEntity.ok("Verification failed. Current Password Incorrect!");
        }

        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @PostMapping("/save-level")
    public ResponseEntity<Object> saveLevel(@RequestParam Map<String, String> updatedLevel){
        userService.updateUserLevel(updatedLevel.get("level"));

        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/settings");
        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @PostMapping("/update-status")
    public ResponseEntity<String> updateStatus(@RequestParam("status") String status) {
        System.out.println("Received status: " + status);

        userService.updateUserStatus(status);

        if ("public".equalsIgnoreCase(status)) {
            return ResponseEntity.ok("✅ Status updated to Public");
        } else if ("private".equalsIgnoreCase(status)) {
            return ResponseEntity.ok("🔒 Status updated to Private");
        } else {
            return ResponseEntity.badRequest().body("❌ Invalid status value");
        }
    }

    @PostMapping("/update-communityStatus")
    public ResponseEntity<String> updateCommunityStatus(@RequestParam("status") String status){

        System.out.println("This is the community status: " + status);
        System.out.println("This is the mycommunity status" + status);

        if ("public".equalsIgnoreCase(status)) {
            return ResponseEntity.ok("✅Community Status updated to Public");
        } else if ("private".equalsIgnoreCase(status)) {
            return ResponseEntity.ok("🔒 Community Status updated to Private");
        } else {
            return ResponseEntity.badRequest().body("❌ Invalid status value");
        }
    }
}


