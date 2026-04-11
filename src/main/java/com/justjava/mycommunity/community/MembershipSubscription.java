package com.justjava.mycommunity.community;

import com.justjava.mycommunity.community.dto.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class MembershipSubscription {

    @Id
    @GeneratedValue
    private Long id;

    private String userId;

    private Long communityId;

    private Long planId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status; // ACTIVE, CANCELLED, EXPIRED

    private LocalDateTime startDate;
    private LocalDateTime nextBillingDate;

    private String paymentProviderRef; // Paystack/Stripe ID
}