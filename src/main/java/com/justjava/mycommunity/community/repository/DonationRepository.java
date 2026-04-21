package com.justjava.mycommunity.community.repository;

import com.justjava.mycommunity.community.Donation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByCommunityId(Long communityId);

    List<Donation> findByUserId(String userId);

    List<Donation> findByEventId(Long eventId);
}
