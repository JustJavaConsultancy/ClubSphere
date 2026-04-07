package com.justjava.mycommunity.community.controller;

import com.justjava.mycommunity.community.dto.ApprovalTaskDTO;
import com.justjava.mycommunity.community.services.CommunityApprovalService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/approvals")
@RequiredArgsConstructor
public class CommunityApprovalController {

    @Autowired
    CommunityApprovalService approvalService;

    @GetMapping
    public String getPendingApprovals(Model model, HttpSession session) {
        String adminUserId = (String) session.getAttribute("userId");
        System.out.println("This are all the pending tasks: " + approvalService.getPendingTasks(adminUserId));

        if (adminUserId == null) {
            return "redirect:/login";
        }

        List<ApprovalTaskDTO> pendingTasks = approvalService.getPendingTasks(adminUserId);

        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("adminUserId", adminUserId);
        model.addAttribute("pendingCount", pendingTasks.size());

        return "approvals"; // resolves to templates/approvals.html
    }

    @PostMapping("/{taskId}")
    public String completeApprovalTask(
            @PathVariable("taskId")     String taskId,
            @RequestParam("decision")   String decision,   // "approve" | "reject"
            @RequestParam(value = "adminNote", required = false, defaultValue = "") String adminNote,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            boolean approved = "approve".equalsIgnoreCase(decision);
            approvalService.completeTask(taskId, approved);

            String msg = approved
                    ? "Membership request approved successfully."
                    : "Membership request rejected.";
            redirectAttributes.addFlashAttribute("successMessage", msg);

        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not process task: " + ex.getMessage());
        }

        return "redirect:/admin/approvals";
    }
}