package com.justjava.mycommunity.posts;

import com.justjava.mycommunity.chat.entity.AuditableEntity;
import com.justjava.mycommunity.chat.entity.User;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class Post extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content;

    private  byte[] picture;

    @Column(columnDefinition = "BOOLEAN DEFAULT false" )
    private boolean privacy = false;

    @ManyToOne(optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private PostLevel postLevel;

    private Long postLevelId;

    @OneToMany( fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Comment> comments = new ArrayList<>();
}
