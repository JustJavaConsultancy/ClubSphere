package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequestMapping("/mobile")
public class MobileSettingsController {

    @Autowired
    UserService userService;

    @Autowired
    KeycloakService keycloakService;

    @Autowired
    AuthenticationManager authenticationManager;

    @GetMapping("/settings")
    public String settings(Model model) {
        // Preload any data if needed later (e.g., current level)
        String loginUser = (String) authenticationManager.get("sub");

        // Ensure we get fresh user data from database
        UserDTO currentUser = userService.getSingleUser(loginUser);

        model.addAttribute("user", currentUser);
        return "settings/mobile-index";
    }

    @PostMapping("/change-password")
    public ResponseEntity<Object> changePassword(@RequestParam Map<String, Object> formData, HttpServletRequest request){
//        System.out.println("This is the password form" + formData);

        String message = keycloakService.updatePassword(formData);
        HttpHeaders headers = new HttpHeaders();

        if (message.equalsIgnoreCase("success")) {
            keycloakService.logoutUser(request);
            headers.add("HX-Redirect", "/mobile");
        } else {
            return ResponseEntity.ok("Verification failed. Current Password Incorrect!");
        }

        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @PostMapping("/save-level")
    public ResponseEntity<Object> saveLevel(@RequestParam Map<String, String> updatedLevel){
        userService.updateUserLevel(updatedLevel.get("level"));

        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/mobile/settings");
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

    @PostMapping("/logout")
    public ResponseEntity<Object> logout(HttpServletRequest request) {
        try {
            keycloakService.logoutUser(request);

            HttpHeaders headers = new HttpHeaders();
            headers.add("HX-Redirect", "https://just-community.up.railway.app/oauth2/authorization/keycloak-mobile");
            return ResponseEntity.status(HttpStatus.OK).headers(headers).build();

        } catch (Exception e) {
            System.out.println("Logout error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Logout failed");
        }
    }
}
