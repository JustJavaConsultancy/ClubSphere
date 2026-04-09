package com.justjava.mycommunity.community;

import com.justjava.mycommunity.community.dto.PaymentStatus;
import com.justjava.mycommunity.community.dto.PaymentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class PaymentTransaction {

    @Id
    @GeneratedValue
    private Long id;

    private String userId;
    private Long communityId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentType type; // SUBSCRIPTION, DONATION

    @Enumerated(EnumType.STRING)
    private PaymentStatus status; // PENDING, SUCCESS, FAILED

    private String providerRef;

    private LocalDateTime createdAt;
}