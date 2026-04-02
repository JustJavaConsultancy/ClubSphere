package com.justjava.mycommunity.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {

    private String comment;
    private Long postId;
    private String userId;
    private String senderFullName;
    private String dateCreated;

}
