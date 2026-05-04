package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.invoice.DesignDTO;
import com.justjava.mycommunity.invoice.InvoiceDesignService;
import com.justjava.mycommunity.invoice.InvoiceService;
import com.justjava.mycommunity.invoice.PaystackService;
import com.justjava.mycommunity.processes.CustomProcessService;
import com.justjava.mycommunity.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mobile/invoice")
public class MobileInvoiceController {

    @Autowired
    private InvoiceDesignService designService;

    private final AuthenticationManager authenticationManager;
    private final CustomProcessService processService;
    private final InvoiceService invoiceService;
    private final TaskService taskService;
    private final PaystackService paystackService;

    @Value("${app.base.url}")
    private String baseUrl;

    @jakarta.annotation.PostConstruct
    public void normalizeBaseUrl() {
        if (baseUrl != null && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
    }

    public MobileInvoiceController(AuthenticationManager authenticationManager,
                                   CustomProcessService processService,
                                   InvoiceService invoiceService,
                                   TaskService taskService,
                                   PaystackService paystackService) {
        this.authenticationManager = authenticationManager;
        this.processService = processService;
        this.invoiceService = invoiceService;
        this.taskService = taskService;
        this.paystackService = paystackService;
    }

    @GetMapping
    public String getInvoices(Model model) {
        String loginUser = (String) authenticationManager.get("sub");
        List<ProcessInstance> allProcessInstances = processService.getAllProcessInstance("invoicing", loginUser);
        List<HistoricProcessInstance> allCompletedInstances = processService.getAllHistoricInstances("invoicing", loginUser);

        List<Map<String, Object>> allProcessVar = allProcessInstances.stream()
                .map(pi -> { Map<String, Object> vars = pi.getProcessVariables(); vars.put("processId", pi.getProcessInstanceId()); return vars; })
                .collect(Collectors.toList());
        List<Map<String, Object>> allCompletedVar = allCompletedInstances.stream()
                .map(HistoricProcessInstance::getProcessVariables).toList();
        allProcessVar.addAll(allCompletedVar);

        Long pendingCount = allProcessVar.stream().filter(inv -> "approved".equalsIgnoreCase((String) inv.get("status"))).count();
        Long paidCount = allCompletedVar.stream().filter(inv -> "paid".equalsIgnoreCase((String) inv.get("status"))).count();
        BigDecimal totalAmount = allCompletedVar.stream()
                .filter(inv -> "paid".equalsIgnoreCase((String) inv.get("status")))
                .map(inv -> new BigDecimal((String) inv.getOrDefault("amount", "0")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("allInvoices", allProcessVar);
        model.addAttribute("pendingInvoiceCount", pendingCount);
        model.addAttribute("paidInvoiceCount", paidCount);
        model.addAttribute("totalAmount", totalAmount);
        return "invoice/mobile-invoice";
    }

    @GetMapping("/create")
    public String createInvoicePage(Model model) {
        List<DesignDTO.Response> designs = designService.getAllDesigns();
        model.addAttribute("templates", designs);
        model.addAttribute("status", "new");
        return "invoice/mobile-create-invoice";
    }

    @PostMapping("/create-invoice")
    public String submitInvoice(@RequestParam Map<String, Object> formData) {
        String loginUser = (String) authenticationManager.get("sub");
        formData.put("status", "new");
        formData.put("baseUrl", baseUrl);
        processService.startProcess("invoicing", loginUser, formData);
        return "redirect:/mobile/invoice";
    }

    @GetMapping("/view/{processId}")
    public String viewInvoice(@PathVariable String processId, Model model) {
        try {
            ProcessInstance pi = processService.getSingleProcessInstance(processId, "invoicing");
            Map<String, Object> vars = pi.getProcessVariables();
            model.addAttribute("singleInvoice", vars);
            return "invoice/mobile-invoice-detail";
        } catch (Exception e) {
            return "redirect:/mobile/invoice";
        }
    }

    @GetMapping("/edit-invoice/{id}")
    public String editInvoicePage(@PathVariable String id, Model model) {
        try {
            Task task = taskService.getTaskByInstanceAndDefinitionKey(id, "FormTask_EditInvoice");
            Map<String, Object> vars = task.getProcessVariables();
            vars.put("taskId", task.getId());
            model.addAttribute("reviewData", vars);
            List<DesignDTO.Response> designs = designService.getAllDesigns();
            model.addAttribute("templates", designs);
            return "invoice/mobile-create-invoice";
        } catch (Exception e) {
            return "redirect:/mobile/invoice";
        }
    }

    @PostMapping("/submit-editInvoice")
    public String submitEditInvoice(@RequestParam Map<String, Object> editData) {
        try {
            String taskId = (String) editData.get("taskId");
            editData.put("status", "pending");
            invoiceService.editInvoiceTask(taskId, editData);
        } catch (Exception e) {
            // log silently
        }
        return "redirect:/mobile/invoice";
    }

    @GetMapping("/invoice-review")
    public String invoiceReviewHome(Model model) {
        try {
            List<Task> pending = taskService.getTaskByAssigneeAndProcessDefinitionKey("manager", "invoicing");
            List<HistoricTaskInstance> approved = taskService.getCompletedTaskByAssigneeAndVariable("manager", "invoicing", "status", "approved");
            List<HistoricTaskInstance> declined = taskService.getCompletedTaskByAssigneeAndVariable("manager", "invoicing", "status", "declined");

            List<Map<String, Object>> pendingVars = pending.stream().map(t -> {
                Map<String, Object> vars = t.getProcessVariables();
                vars.put("taskId", t.getId());
                return vars;
            }).toList();

            model.addAttribute("allApprovedCount", approved.size());
            model.addAttribute("allDeclinedCount", declined.size());
            model.addAttribute("allPendingCount", pending.size());
            model.addAttribute("allPendingInvoice", pendingVars);
        } catch (Exception e) {
            model.addAttribute("allPendingInvoice", List.of());
        }
        return "invoice/mobile-invoice-review-home";
    }

    @GetMapping("/invoice-review/{taskId}")
    public String invoiceReviewDetail(@PathVariable String taskId, Model model) {
        try {
            Task task = taskService.findTaskById(taskId);
            Map<String, Object> vars = task.getProcessVariables();
            vars.put("taskId", taskId);
            model.addAttribute("invoiceData", vars);
            return "invoice/mobile-invoice-review";
        } catch (Exception e) {
            return "redirect:/mobile/invoice/invoice-review";
        }
    }

    @PostMapping("/submit-review")
    public ResponseEntity<Void> submitReview(@RequestParam Map<String, Object> reviewData) {
        try {
            String taskId = (String) reviewData.get("taskId");
            invoiceService.seniorReviewTask(taskId, reviewData);
            HttpHeaders headers = new HttpHeaders();
            headers.add("HX-Redirect", "/mobile/invoice/invoice-review");
            return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/pay/{procInstId}")
    public String payInvoice(@PathVariable String procInstId) {
        try {
            Task task = taskService.getTaskByInstanceAndDefinitionKey(procInstId, "FormTask_ClientPayment");
            Map<String, Object> vars = task.getProcessVariables();
            if (paystackService.isConfigured()) {
                String email = (String) vars.get("email");
                String amountStr = (String) vars.get("amount");
                BigDecimal amount = new BigDecimal(amountStr != null ? amountStr : "0");
                String reference = paystackService.generatePaymentReference(procInstId);
                String callbackUrl = baseUrl + "/mobile/invoice/payment-callback/" + procInstId;
                Map<String, Object> metadata = paystackService.createPaymentMetadata(
                        procInstId, (String) vars.get("businessName"), (String) vars.get("clientName"));
                Map<String, Object> paymentData = paystackService.initializePayment(email, amount, reference, callbackUrl, metadata);
                String authUrl = (String) paymentData.get("authorization_url");
                return "redirect:" + authUrl;
            }
        } catch (Exception e) {
            // fall through to invoice list
        }
        return "redirect:/mobile/invoice";
    }

    @GetMapping("/payment-callback/{processId}")
    public String paymentCallback(@RequestParam(required = false) String reference,
                                  @PathVariable String processId, Model model) {
        try {
            if (reference != null && !reference.isEmpty()) {
                Map<String, Object> paymentData = paystackService.verifyPayment(reference);
                String status = (String) paymentData.get("status");
                Task task = taskService.getTaskByInstanceAndDefinitionKey(processId, "FormTask_ClientPayment");
                Map<String, Object> taskVars = task.getProcessVariables();
                taskVars.put("payStackStatus", status);
                taskVars.put("reference", reference);
                taskService.completeTask(task.getId(), taskVars);
            }
        } catch (Exception e) {
            // log silently
        }
        return "redirect:/mobile/invoice";
    }

    @GetMapping("/design")
    public String designPage(Model model) {
        return "invoice/mobile-invoice-design";
    }

    @PostMapping("/design")
    public String saveDesign(@RequestParam("invoiceHtml") String invoiceHtml,
                             @RequestParam("designName") String designName) {
        DesignDTO.Request req = new DesignDTO.Request();
        req.setDesignName(designName);
        req.setDesignHtml(invoiceHtml);
        designService.saveDesign(req);
        return "redirect:/mobile/invoice/create";
    }
}



