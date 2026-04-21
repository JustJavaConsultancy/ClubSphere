package com.justjava.mycommunity.community;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Donation {

    @Id
    @GeneratedValue
    private Long id;

    private String userId;
    private Long communityId;
    private Long eventId;

    private BigDecimal amount;

    private String message;

    private LocalDateTime donatedAt;
}