package com.justjava.mycommunity.chat.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostDTO {

    private String content;
    private String userFullName;
    private String userEmail;
    private byte[] picture;
    private String dateCreated;
    private Boolean privacy;
    private Long postID;
    private Long postLevelId;
    private String communityName;
    private Boolean isGeneralPost;
    private List<CommentDTO> comments;

}
