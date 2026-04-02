package com.justjava.mycommunity.keycloak;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name="KeycloakFeignClient",url="${keycloak.base-url}")
public interface KeycloakFeignClient {

    @PostMapping(path = "/realms/{realmName}/protocol/openid-connect/token",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    Map<String, Object> getAccessToken(@PathVariable("realmName") String realmName, Map<String,?> paramMap);

    @GetMapping("/admin/realms/{realmName}/users")
    List<Map<String, Object>> getUsers(@RequestHeader(value = "Authorization") String authorizationHeader,
                                       @PathVariable("realmName") String realmName);

    @GetMapping("/admin/realms/{realmName}/users/{userId}")
    Map<String, Object> getUser(@RequestHeader(value = "Authorization") String authorizationHeader,
                                @PathVariable("realmName") String realmName,
                                       @PathVariable("userId") String userId);

    @GetMapping("/admin/realms/{realmName}/groups")
    List<Map<String, Object>> getRealmGroups(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName);

    @GetMapping("/admin/realms/{realmName}/groups/{groupId}/members")
    List<Map<String, Object>> getAllUserInGroup(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName,
            @PathVariable("groupId") String groupId
    );

    @GetMapping("/admin/realms/{realmName}/users")
    ResponseEntity<List<Map<String, Object>>> getUserByEmail(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName,
            @RequestParam(name = "email") String email
    );

    @PostMapping("/admin/realms/{realmName}/users")
    ResponseEntity<Void> createUser(@RequestHeader(value = "Authorization")
                                    String authorizationHeader,
                                    @PathVariable("realmName") String realmName,
                                    Map<String,Object> user);

    @PutMapping("/admin/realms/{realmName}/users/{userId}/groups/{groupId}")
    ResponseEntity<Void> addUserToGroup(@RequestHeader(value = "Authorization") String authorizationHeader,
                                        @PathVariable("realmName") String realmName,
                             @PathVariable String userId,
                             @PathVariable String groupId,
                             @RequestBody Map<String, Object> groupBody
    );

    @GetMapping("/admin/realms/{realmName}/users/{id}/groups")
    List<Map<String, Object>> getUserGroups(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName,
            @PathVariable("id") String userId
    );

    @PutMapping("/admin/realms/{realmName}/groups/{groupId}")
    ResponseEntity<Void> updateGroup(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName,
            @PathVariable String groupId,
            @RequestBody Map<String, Object> groupBody);

    @PutMapping("/admin/realms/{realmName}/users/{userId}")
    ResponseEntity<Void> updateUser(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName,
            @PathVariable String userId,
            @RequestBody Map<String, Object> body);

    @PutMapping("/admin/realms/{realmName}/users/{userId}/reset-password")
    Object updatePassword(@RequestHeader(value = "Authorization", required = true)
                          String authorizationHeader,
                          @PathVariable("realmName") String realmName,
                          @PathVariable String userId ,Map credentials);

    @PostMapping("/admin/realms/{realmName}/groups")
    void createGroup(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName,
            Map<String,?> paramMap
            );

    @DeleteMapping("/admin/realms/{realmName}/users/{userId}")
    void deleteUser(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName,
            @PathVariable String userId
    );

    @DeleteMapping("/admin/realms/{realmName}/groups/{groupId}")
    ResponseEntity<Void> deleteGroup(
            @RequestHeader(value = "Authorization") String authorizationHeader,
            @PathVariable("realmName") String realmName,
            @PathVariable String groupId
    );

    @PostMapping(value = "/realms/{realmName}/protocol/openid-connect/token",
    consumes =  {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    ResponseEntity<Map<String, Object>> authenticate(
            @PathVariable("realmName") String realmName,
            Map<String,?> params
    );

    @PostMapping("/admin/realms/{realmName}/users/{userId}/logout")
    ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = true) String authorizationHeader,
                             @PathVariable("realmName") String realmName,
                                     @PathVariable String userId);

}
