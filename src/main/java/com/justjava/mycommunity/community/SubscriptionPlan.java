package com.justjava.mycommunity.community;

import com.justjava.mycommunity.community.dto.BillingCycle;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"communityId", "name"})
})
public class SubscriptionPlan {

    @Id
    @GeneratedValue
    private Long id;

    private Long communityId;

    private String name; // e.g. "Premium Access"

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle; // WEEKLY, MONTHLY

    private Boolean active = true;
}
