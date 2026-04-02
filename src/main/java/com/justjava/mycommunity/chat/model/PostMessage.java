package com.justjava.mycommunity.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostMessage {

    private String content;
    private String userId;
    private MultipartFile file;
    private boolean privacy;
    private String timestamp;
    private String postLevel;
    private Long postLevelId;

    public PostMessage(String content, String userId, MultipartFile file){
        this.content = content;
        this.userId = userId;
        this.file = file;
    }
}
