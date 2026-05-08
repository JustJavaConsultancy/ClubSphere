package com.justjava.mycommunity.community.services;

import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.community.MembershipSubscription;
import com.justjava.mycommunity.community.SubscriptionPlan;
import com.justjava.mycommunity.community.dto.BillingCycle;
import com.justjava.mycommunity.community.dto.SubscriptionStatus;
import com.justjava.mycommunity.community.repository.MembershipSubscriptionRepository;
import com.justjava.mycommunity.community.repository.SubscriptionPlanRepository;
import com.justjava.mycommunity.invoice.Invoice;
import com.justjava.mycommunity.invoice.InvoiceRepository;
import com.justjava.mycommunity.invoice.Status;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionBillingService {

    private final MembershipSubscriptionRepository membershipSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional
    public Optional<Invoice> generateInitialInvoice(MembershipSubscription subscription) {
        return generateInvoiceForCycle(subscription, subscription.getNextBillingDate());
    }

    @Transactional
    public int generateInvoicesForDueSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<MembershipSubscription> dueSubscriptions =
                membershipSubscriptionRepository.findByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus.ACTIVE, now);

        int generatedCount = 0;
        for (MembershipSubscription subscription : dueSubscriptions) {
            if (generateInvoiceForCycle(subscription, subscription.getNextBillingDate()).isPresent()) {
                generatedCount++;
            }

            subscription.setNextBillingDate(calculateNextBillingDate(subscription.getNextBillingDate(), resolveBillingCycle(subscription)));
            membershipSubscriptionRepository.save(subscription);
        }
        return generatedCount;
    }

    @Transactional
    public Optional<Invoice> generateInvoiceForCycle(MembershipSubscription subscription, LocalDateTime cycleDateTime) {
        if (cycleDateTime == null) {
            cycleDateTime = LocalDateTime.now();
        }

        String merchantId = buildCycleMerchantId(subscription, cycleDateTime.toLocalDate());
        if (invoiceRepository.findByMerchantId(merchantId).isPresent()) {
            return Optional.empty();
        }

        User user = Optional.ofNullable(userRepository.findByUserId(subscription.getUserId()))
                .orElseThrow(() -> new EntityNotFoundException("User not found for subscription billing"));

        Invoice invoice = new Invoice();
        invoice.setMerchantId(merchantId);
        invoice.setCustomerEmail(user.getEmail());
        invoice.setCustomerPhoneNumber(null);
        invoice.setCustomerName(buildCustomerName(user));
        invoice.setDescription(buildInvoiceDescription(subscription, cycleDateTime.toLocalDate()));
        invoice.setQuantity("1");
        invoice.setIssueDate(cycleDateTime.toLocalDate());
        invoice.setDueDate(cycleDateTime.toLocalDate());
        invoice.setAmount(subscription.getAmount());
        invoice.setStatus(Status.NEW);
        return Optional.of(invoiceRepository.save(invoice));
    }

    @Transactional(readOnly = true)
    public BillingCycle resolveBillingCycle(MembershipSubscription subscription) {
        if (subscription.getPlanId() == null) {
            return BillingCycle.MONTHLY;
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(subscription.getPlanId())
                .orElse(null);
        if (plan == null || plan.getBillingCycle() == null) {
            return BillingCycle.MONTHLY;
        }
        return plan.getBillingCycle();
    }

    @Transactional(readOnly = true)
    public LocalDateTime calculateNextBillingDate(LocalDateTime from, BillingCycle cycle) {
        LocalDateTime effectiveFrom = from == null ? LocalDateTime.now() : from;
        return switch (cycle) {
            case WEEKLY -> effectiveFrom.plusWeeks(1);
            case QUARTERLY -> effectiveFrom.plusMonths(3);
            case YEARLY, ANNUALLY -> effectiveFrom.plusYears(1);
            case MONTHLY -> effectiveFrom.plusMonths(1);
        };
    }

    private String buildCycleMerchantId(MembershipSubscription subscription, LocalDate cycleDate) {
        return "SUB-" + subscription.getId() + "-" + cycleDate;
    }

    private String buildCustomerName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? user.getUserId() : fullName;
    }

    private String buildInvoiceDescription(MembershipSubscription subscription, LocalDate cycleDate) {
        BillingCycle cycle = resolveBillingCycle(subscription);
        return switch (cycle) {
            case MONTHLY -> "Subscription for Month " + cycleDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + cycleDate.getYear();
            case QUARTERLY -> "Subscription for " + getQuarterLabel(cycleDate.getMonth()) + " Quarter";
            case YEARLY, ANNUALLY -> "Subscription for Year " + cycleDate.getYear();
            case WEEKLY -> "Subscription for Week of " + cycleDate;
        };
    }

    private String getQuarterLabel(Month month) {
        int monthValue = month.getValue();
        if (monthValue <= 3) return "1st";
        if (monthValue <= 6) return "2nd";
        if (monthValue <= 9) return "3rd";
        return "4th";
    }
}
