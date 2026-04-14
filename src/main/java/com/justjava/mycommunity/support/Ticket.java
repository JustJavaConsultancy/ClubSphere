package com.justjava.mycommunity.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjava.mycommunity.chat.entity.AuditableEntity;
import com.justjava.mycommunity.chat.entity.Conversation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "ticket")
public class Ticket extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(nullable = false)
    private Long id;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String priority = "low";

    private String status = "Pending";

    private String userId;

    private String agentUserId;

    private Long communityId;

    private String attachmentUrl;

    @CreatedDate
    @Column(updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    private OffsetDateTime lastUpdated;

    @JsonIgnore
    @OneToOne(cascade = CascadeType.ALL)
    private Conversation conversation;
}