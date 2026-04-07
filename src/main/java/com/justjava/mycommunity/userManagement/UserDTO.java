package com.justjava.mycommunity.userManagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.justjava.mycommunity.community.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private String userId;
    private String firstName;
    private String lastName;
    private String email;
    private String status;
    private String group;
    private String level;
    private Boolean privacy;
    private Boolean online;
    private String avatar;
    private Role role;
    public String getName() {
        return firstName+" "+lastName;
    }
}
