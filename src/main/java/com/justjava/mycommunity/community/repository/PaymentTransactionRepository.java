package com.justjava.mycommunity.community.repository;

import com.justjava.mycommunity.community.PaymentTransaction;
import com.justjava.mycommunity.community.dto.PaymentStatus;
import com.justjava.mycommunity.community.dto.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByProviderRef(String providerRef);

    List<PaymentTransaction> findByUserId(String userId);

    List<PaymentTransaction> findByCommunityId(Long communityId);

    List<PaymentTransaction> findByStatus(PaymentStatus status);

    List<PaymentTransaction> findByType(PaymentType type);
    List<PaymentTransaction> findByStatusAndType(PaymentStatus status, PaymentType type);

    List<PaymentTransaction> findByCommunityIdAndStatus(Long communityId, PaymentStatus paymentStatus);
}