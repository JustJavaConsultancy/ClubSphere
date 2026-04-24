package com.justjava.mycommunity.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjava.mycommunity.chat.entity.AuditableEntity;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.module.Module;
import com.justjava.mycommunity.network.ChatGroup;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.posts.Post;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Event extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private Long duration;

    private String sessionType;

    @Column(columnDefinition = "TEXT")
    private String videoLink;

    @Column(nullable = false)
    private boolean hasCertificate;

    @Column(columnDefinition = "TEXT")
    private String certificateHtml;

    private Boolean approved = true;

    /** "EVENT" = standalone event for donations/community purposes; "SESSION" = training/coaching session */
    private String eventType = "SESSION";

    @OneToOne(fetch = FetchType.LAZY)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MODULE_ID")
    private Module module;

    @JsonIgnore
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Participant> participants = new ArrayList<>();

    @JsonIgnore
    @ManyToOne(optional = false)
    private Organization organization;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COMMUNITY_ID")
    private Community community;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    private ChatGroup chatGroup;
}
