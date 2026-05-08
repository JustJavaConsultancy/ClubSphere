package com.justjava.mycommunity.community.controller;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.SubscriptionPlan;
import com.justjava.mycommunity.community.dto.BillingCycle;
import com.justjava.mycommunity.invoice.Invoice;
import com.justjava.mycommunity.invoice.Status;
import com.justjava.mycommunity.invoice.PaystackService;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CommunityPaymentController {

    private final RuntimeService runtimeService;
    private final CommunityService communityService;
    private final AuthenticationManager authenticationManager;
    private final PaystackService paystackService;
    private final UserRepository userRepository;

    @Value("${app.base.url}")
    private String baseUrl;

    @PostConstruct
    public void normalizeBaseUrl() {
        if (baseUrl != null && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
    }

    @PostMapping("/subscription/webhook/payment")
    public void handleWebhook(@RequestBody Map<String, Object> payload) {

        String paymentRef = (String) payload.get("reference");
        String status = (String) payload.get("status");
        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceBusinessKey(paymentRef)
                .messageEventSubscriptionName("payment-confirmation")
                .singleResult();

        runtimeService.messageEventReceived(
                "payment-confirmation",
                execution.getId()
        );
    }

    // 🔹 Subscribe (HTMX — initializes Paystack payment)
    @PostMapping("/subscription/subscribe")
    public ResponseEntity<String> subscribe(@RequestParam("communityId") Long communityId,
                                            @RequestParam("amount") BigDecimal amount,
                                            @RequestParam(value = "source", defaultValue = "web") String source) {
        try {
            String userId = (String) authenticationManager.get("sub");
            User user = userRepository.findByUserId(userId);
            String email = (user != null && user.getEmail() != null)
                    ? user.getEmail()
                    : userId + "@clubsphere.app";

            String reference = "SUB-" + communityId + "-" + System.currentTimeMillis();
            String callbackUrl = baseUrl + "/subscription/payment-callback?communityId=" + communityId
                    + "&amount=" + amount + "&source=" + source;

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("type", "SUBSCRIPTION");
            metadata.put("userId", userId);
            metadata.put("communityId", communityId);

            Map<String, Object> paymentData = paystackService.initializePayment(email, amount, reference, callbackUrl, metadata);
            String authUrl = (String) paymentData.get("authorization_url");

            return ResponseEntity.ok()
                    .header("HX-Redirect", authUrl)
                    .body("");
        } catch (IllegalStateException e) {
            return ResponseEntity.ok("<div class='text-amber-600 font-medium'>⚠️ " + e.getMessage() + "</div>");
        } catch (SecurityException e) {
            return ResponseEntity.ok("<div class='text-red-600 font-medium'>❌ " + e.getMessage() + "</div>");
        } catch (Exception e) {
            return ResponseEntity.ok("<div class='text-red-600 font-medium'>❌ Error: " + e.getMessage() + "</div>");
        }
    }

    // 🔹 Subscription payment callback (Paystack redirects here after payment)
    @GetMapping("/subscription/payment-callback")
    public String subscriptionPaymentCallback(@RequestParam(required = false) String reference,
                                              @RequestParam Long communityId,
                                              @RequestParam BigDecimal amount,
                                              @RequestParam(defaultValue = "web") String source,
                                              RedirectAttributes redirectAttributes) {
        try {
            if (reference == null || reference.isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Payment reference missing.");
            } else {
                Map<String, Object> paymentData = paystackService.verifyPayment(reference);
                String status = (String) paymentData.get("status");
                if ("success".equals(status)) {
                    String userId = (String) authenticationManager.get("sub");
                    communityService.startSubscription(userId, communityId, amount, reference);
                    redirectAttributes.addFlashAttribute("successMessage", "✅ Subscription activated successfully!");
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", "Payment was not successful. Please try again.");
                }
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Subscription could not be completed: " + e.getMessage());
        }
        if ("mobile".equals(source)) {
            return "redirect:/subscription/mobile/my-subscriptions";
        }
        return "redirect:/subscription/my-subscriptions";
    }

    // 🔹 Cancel subscription
    @PostMapping("/subscription/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelSubscription(@RequestParam("subscriptionId") Long subscriptionId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String userId = (String) authenticationManager.get("sub");
            communityService.cancelSubscription(userId, subscriptionId);
            response.put("success", true);
            response.put("message", "Subscription cancelled successfully");
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(403).body(response);
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to cancel subscription");
            return ResponseEntity.status(500).body(response);
        }
    }

    // 🔹 Admin: community subscriptions JSON (for HTMX)
    @GetMapping("/subscription/subscriptions/list")
    @ResponseBody
    public List<Map<String, Object>> communitySubscriptionsList(@RequestParam("communityId") Long communityId) {
        return communityService.getCommunitySubscriptions(communityId);
    }

    @PostMapping("/subscription/plan/configure")
    public ResponseEntity<String> configureSubscriptionPlan(@RequestParam("communityId") Long communityId,
                                                            @RequestParam("billingCycle") BillingCycle billingCycle,
                                                            @RequestParam("amount") BigDecimal amount) {
        try {
            if (!authenticationManager.isAdmin()) {
                return ResponseEntity.ok("<div class='text-red-600 font-medium'>Only admins can configure subscription plans.</div>");
            }
            SubscriptionPlan plan = communityService.upsertSubscriptionPlan(communityId, billingCycle, amount);
            return ResponseEntity.ok("<div class='text-green-600 font-medium'>Plan updated: " + plan.getBillingCycle() + " / ₦" + plan.getAmount() + "</div>");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok("<div class='text-amber-600 font-medium'>" + e.getMessage() + "</div>");
        } catch (Exception e) {
            return ResponseEntity.ok("<div class='text-red-600 font-medium'>Failed to update plan: " + e.getMessage() + "</div>");
        }
    }

    // 🔹 User: My Subscriptions page (all communities)
    @GetMapping("/subscription/my-subscriptions")
    public String mySubscriptionsPage(Model model) {

        String userId = (String) authenticationManager.get("sub");

        List<Map<String, Object>> subscriptions =
                communityService.getUserSubscriptions(userId);

        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("currentPath", "/subscription/my-subscriptions");
        model.addAttribute("userId", userId);
        model.addAttribute("usersName", authenticationManager.get("name"));

        return "my-subscriptions";
    }

    @GetMapping("/subscription/my-invoices")
    public String mySubscriptionInvoicesPage(Model model) {
        String userId = (String) authenticationManager.get("sub");
        List<Invoice> invoices = communityService.getUserSubscriptionInvoices(userId);
        long paidCount = invoices.stream().filter(i -> i.getStatus() == Status.PAID).count();
        long pendingCount = invoices.stream().filter(i -> i.getStatus() == Status.NEW).count();
        BigDecimal paidTotal = invoices.stream()
                .filter(i -> i.getStatus() == Status.PAID)
                .map(Invoice::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("subscriptionInvoices", invoices);
        model.addAttribute("paidInvoiceCount", paidCount);
        model.addAttribute("pendingInvoiceCount", pendingCount);
        model.addAttribute("totalAmountPaid", paidTotal);
        model.addAttribute("currentPath", "/subscription/my-invoices");
        model.addAttribute("userId", userId);
        model.addAttribute("usersName", authenticationManager.get("name"));
        return "my-subscription-invoices";
    }

    // 🔹 User: My Subscriptions JSON (for HTMX)
    @GetMapping("/subscription/my-subscriptions/data")
    @ResponseBody
    public List<Map<String, Object>> mySubscriptionsData() {
        String userId = (String) authenticationManager.get("sub");
        return communityService.getUserSubscriptions(userId);
    }

    // 🔹 Mobile: My Subscriptions page
    @GetMapping("/subscription/mobile/my-subscriptions")
    public String mobileMySubscriptionsPage(Model model) {

        String userId = (String) authenticationManager.get("sub");

        List<Map<String, Object>> subscriptions =
                communityService.getUserSubscriptions(userId);

        long activeCount = subscriptions.stream().filter(s -> "ACTIVE".equals(s.get("status"))).count();
        long cancelledCount = subscriptions.stream().filter(s -> "CANCELLED".equals(s.get("status"))).count();
        long expiredCount = subscriptions.stream().filter(s -> "EXPIRED".equals(s.get("status"))).count();

        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("cancelledCount", cancelledCount);
        model.addAttribute("expiredCount", expiredCount);
        model.addAttribute("currentPath", "/subscription/mobile/my-subscriptions");
        model.addAttribute("userId", userId);
        model.addAttribute("usersName", authenticationManager.get("name"));

        return "mobile-my-subscriptions";
    }

    @GetMapping("/subscription/mobile/my-invoices")
    public String mobileMySubscriptionInvoicesPage(Model model) {
        String userId = (String) authenticationManager.get("sub");
        List<Invoice> invoices = communityService.getUserSubscriptionInvoices(userId);
        long paidCount = invoices.stream().filter(i -> i.getStatus() == Status.PAID).count();
        long pendingCount = invoices.stream().filter(i -> i.getStatus() == Status.NEW).count();
        BigDecimal paidTotal = invoices.stream()
                .filter(i -> i.getStatus() == Status.PAID)
                .map(Invoice::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("subscriptionInvoices", invoices);
        model.addAttribute("paidInvoiceCount", paidCount);
        model.addAttribute("pendingInvoiceCount", pendingCount);
        model.addAttribute("totalAmountPaid", paidTotal);
        model.addAttribute("currentPath", "/subscription/mobile/my-invoices");
        model.addAttribute("userId", userId);
        model.addAttribute("usersName", authenticationManager.get("name"));
        return "mobile-my-subscription-invoices";
    }

    // ══════════════════ DONATIONS ══════════════════

    // 🔹 Make a donation (HTMX — initializes Paystack payment)
    @PostMapping("/donation/donate")
    public ResponseEntity<String> donate(@RequestParam("communityId") Long communityId,
                                         @RequestParam("eventId") Long eventId,
                                         @RequestParam("amount") BigDecimal amount,
                                         @RequestParam(value = "message", required = false) String message,
                                         @RequestParam(value = "source", defaultValue = "web") String source) {
        try {
            String userId = (String) authenticationManager.get("sub");
            User user = userRepository.findByUserId(userId);
            String email = (user != null && user.getEmail() != null)
                    ? user.getEmail()
                    : userId + "@clubsphere.app";

            String reference = "DON-" + communityId + "-" + eventId + "-" + System.currentTimeMillis();
            String callbackUrl = baseUrl + "/donation/payment-callback?communityId=" + communityId
                    + "&eventId=" + eventId + "&amount=" + amount + "&source=" + source;
            if (message != null && !message.isBlank()) {
                callbackUrl += "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("type", "DONATION");
            metadata.put("userId", userId);
            metadata.put("communityId", communityId);
            metadata.put("eventId", eventId);

            Map<String, Object> paymentData = paystackService.initializePayment(email, amount, reference, callbackUrl, metadata);
            String authUrl = (String) paymentData.get("authorization_url");

            return ResponseEntity.ok()
                    .header("HX-Redirect", authUrl)
                    .body("");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok("<div class='text-amber-600 font-medium'>⚠️ " + e.getMessage() + "</div>");
        } catch (SecurityException e) {
            return ResponseEntity.ok("<div class='text-red-600 font-medium'>❌ " + e.getMessage() + "</div>");
        } catch (Exception e) {
            return ResponseEntity.ok("<div class='text-red-600 font-medium'>❌ Error: " + e.getMessage() + "</div>");
        }
    }

    // 🔹 Donation payment callback (Paystack redirects here after payment)
    @GetMapping("/donation/payment-callback")
    public String donationPaymentCallback(@RequestParam(required = false) String reference,
                                          @RequestParam Long communityId,
                                          @RequestParam Long eventId,
                                          @RequestParam BigDecimal amount,
                                          @RequestParam(required = false) String message,
                                          @RequestParam(defaultValue = "web") String source,
                                          RedirectAttributes redirectAttributes) {
        try {
            if (reference == null || reference.isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Payment reference missing.");
            } else {
                Map<String, Object> paymentData = paystackService.verifyPayment(reference);
                String status = (String) paymentData.get("status");
                if ("success".equals(status)) {
                    String userId = (String) authenticationManager.get("sub");
                    communityService.makeDonation(userId, communityId, eventId, amount, message, reference);
                    redirectAttributes.addFlashAttribute("successMessage", "✅ Donation successful! Thank you for your generosity.");
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", "Payment was not successful. Please try again.");
                }
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Donation could not be completed: " + e.getMessage());
        }
        if ("mobile".equals(source)) {
            return "redirect:/donation/mobile/my-donations";
        }
        return "redirect:/donation/my-donations";
    }

    // 🔹 Admin: community donations JSON (for HTMX / JS)
    @GetMapping("/donation/donations/list")
    @ResponseBody
    public List<Map<String, Object>> communityDonationsList(@RequestParam("communityId") Long communityId) {
        return communityService.getCommunityDonations(communityId);
    }

    // 🔹 User: My Donations page (all communities)
    @GetMapping("/donation/my-donations")
    public String myDonationsPage(Model model) {
        String userId = (String) authenticationManager.get("sub");
        List<Map<String, Object>> donations = communityService.getUserDonations(userId);
        model.addAttribute("donations", donations);
        model.addAttribute("currentPath", "/donation/my-donations");
        model.addAttribute("userId", userId);
        model.addAttribute("usersName", authenticationManager.get("name"));
        return "my-donations";
    }

    // 🔹 User: My Donations JSON (for HTMX)
    @GetMapping("/donation/my-donations/data")
    @ResponseBody
    public List<Map<String, Object>> myDonationsData() {
        String userId = (String) authenticationManager.get("sub");
        return communityService.getUserDonations(userId);
    }

    // 🔹 Mobile: My Donations page
    @GetMapping("/donation/mobile/my-donations")
    public String mobileMyDonationsPage(Model model) {
        String userId = (String) authenticationManager.get("sub");
        List<Map<String, Object>> donations = communityService.getUserDonations(userId);

        java.math.BigDecimal totalAmount = donations.stream()
                .map(d -> d.get("amount") instanceof java.math.BigDecimal b ? b : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        model.addAttribute("donations", donations);
        model.addAttribute("totalDonationAmount", totalAmount);
        model.addAttribute("currentPath", "/donation/mobile/my-donations");
        model.addAttribute("userId", userId);
        model.addAttribute("usersName", authenticationManager.get("name"));
        return "mobile-my-donations";
    }

    // 🔹 Promise Donation
    @PostMapping("/donation/promise")
    public String promiseDonation(@RequestParam Long communityId,
                                  @RequestParam Long eventId,
                                  @RequestParam BigDecimal amount,
                                  @RequestParam(required = false) String message,
                                  @RequestParam(defaultValue = "web") String source,
                                  RedirectAttributes redirectAttributes) {
        try {
            String userId = (String) authenticationManager.get("sub");
            communityService.promiseDonation(userId, communityId, eventId, amount, message);
            redirectAttributes.addFlashAttribute("successMessage", "✅ Donation promised! You can fulfill it later when ready.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Donation promise could not be made: " + e.getMessage());
        }
        if ("mobile".equals(source)) {
            return "redirect:/donation/mobile/my-donations";
        }
        return "redirect:/donation/my-donations";
    }

    // 🔹 Fulfill Donation Promise
    @PostMapping("/donation/fulfill")
    public String fulfillDonationPromise(@RequestParam Long donationId,
                                         @RequestParam String reference,
                                         @RequestParam(defaultValue = "web") String source,
                                         RedirectAttributes redirectAttributes) {
        try {
            Map<String, Object> paymentData = paystackService.verifyPayment(reference);
            String status = (String) paymentData.get("status");
            if ("success".equals(status)) {
                communityService.fulfillDonationPromise(donationId, reference);
                redirectAttributes.addFlashAttribute("successMessage", "✅ Donation fulfilled! Thank you for keeping your promise.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Payment was not successful. Please try again.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Donation fulfillment could not be completed: " + e.getMessage());
        }
        if ("mobile".equals(source)) {
            return "redirect:/donation/mobile/my-donations";
        }
        return "redirect:/donation/my-donations";
    }

    // 🔹 User: My Pending Donations JSON
    @GetMapping("/donation/my-pending-donations/data")
    @ResponseBody
    public List<Map<String, Object>> myPendingDonationsData() {
        String userId = (String) authenticationManager.get("sub");
        return communityService.getUserPendingDonations(userId);
    }
}
