package com.justjava.mycommunity.community.controller;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.Donation;
import com.justjava.mycommunity.community.MembershipSubscription;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.community.dto.SubscriptionStatus;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.community.repository.DonationRepository;
import com.justjava.mycommunity.community.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/my-community/dashboard")
@RequiredArgsConstructor
public class CommunityDashboardController {

    private final CommunityService communityService;
    private final AuthenticationManager authenticationManager;
    private final DonationRepository donationRepository;
    private final MembershipSubscriptionRepository membershipSubscriptionRepository;
    private final CommunityMembershipRepository communityMembershipRepository;

    @GetMapping
    public String dashboard(@RequestParam("communityId") Long communityId, Model model,
                            HttpServletRequest request) {
        try {
            boolean isAdmin = authenticationManager.isAdmin();

            if (!isAdmin) {
                return "redirect:/my-community?communityId=" + communityId;
            }

            CommunityDTO community = communityService.getCommunityById(communityId);
            if (community == null) {
                return "redirect:/my-community/select";
            }

            // Keep session in sync
            request.getSession().setAttribute("selectedCommunityId", communityId);
            request.getSession().setAttribute("selectedCommunityName", community.getName());

            // Summary stats
            int totalMembers = communityMembershipRepository
                    .findByCommunityIdAndStatus(communityId, MembershipStatus.APPROVED).size();

            List<Donation> donations = donationRepository.findByCommunityId(communityId);
            BigDecimal totalDonations = donations.stream()
                    .map(Donation::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByCommunityId(communityId);
            long activeSubscriptions = subscriptions.stream()
                    .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).count();
            BigDecimal totalSubscriptionRevenue = subscriptions.stream()
                    .map(MembershipSubscription::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalRevenue = totalDonations.add(totalSubscriptionRevenue);

            model.addAttribute("community", community);
            model.addAttribute("communityId", communityId);
            model.addAttribute("isAdmin", isAdmin);
            model.addAttribute("totalMembers", totalMembers);
            model.addAttribute("totalDonations", totalDonations);
            model.addAttribute("donationCount", donations.size());
            model.addAttribute("activeSubscriptions", activeSubscriptions);
            model.addAttribute("totalSubscriptions", subscriptions.size());
            model.addAttribute("totalSubscriptionRevenue", totalSubscriptionRevenue);
            model.addAttribute("totalRevenue", totalRevenue);
            model.addAttribute("currentPath", "/my-community/dashboard");

            return "community-dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/my-community?communityId=" + communityId;
        }
    }

    @GetMapping("/api/chart-data")
    @ResponseBody
    public Map<String, Object> getChartData(@RequestParam("communityId") Long communityId) {
        Map<String, Object> data = new HashMap<>();

        // Monthly donations for last 12 months
        List<Donation> donations = donationRepository.findByCommunityId(communityId);
        List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByCommunityId(communityId);

        LocalDateTime now = LocalDateTime.now();
        List<String> months = new ArrayList<>();
        List<BigDecimal> donationsByMonth = new ArrayList<>();
        List<BigDecimal> subscriptionsByMonth = new ArrayList<>();

        for (int i = 11; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1);

            String label = monthStart.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    + " " + monthStart.getYear();
            months.add(label);

            BigDecimal monthDonations = donations.stream()
                    .filter(d -> d.getDonatedAt() != null
                            && !d.getDonatedAt().isBefore(monthStart)
                            && d.getDonatedAt().isBefore(monthEnd))
                    .map(Donation::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            donationsByMonth.add(monthDonations);

            BigDecimal monthSubs = subscriptions.stream()
                    .filter(s -> s.getStartDate() != null
                            && !s.getStartDate().isBefore(monthStart)
                            && s.getStartDate().isBefore(monthEnd))
                    .map(MembershipSubscription::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            subscriptionsByMonth.add(monthSubs);
        }

        data.put("months", months);
        data.put("donations", donationsByMonth);
        data.put("subscriptions", subscriptionsByMonth);

        // Donation vs Subscription split
        BigDecimal totalDon = donations.stream().map(Donation::getAmount).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSub = subscriptions.stream().map(MembershipSubscription::getAmount).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("revenueSplit", Map.of("donations", totalDon, "subscriptions", totalSub));

        // Subscription status breakdown
        long active = subscriptions.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).count();
        long cancelled = subscriptions.stream().filter(s -> s.getStatus() == SubscriptionStatus.CANCELLED).count();
        long expired = subscriptions.stream().filter(s -> s.getStatus() == SubscriptionStatus.EXPIRED).count();
        data.put("subscriptionStatus", Map.of("active", active, "cancelled", cancelled, "expired", expired));

        // Recent donations (top 5)
        List<Map<String, Object>> recentDonations = communityService.getCommunityDonations(communityId);
        data.put("recentDonations", recentDonations.stream().limit(5).collect(Collectors.toList()));

        return data;
    }
}







