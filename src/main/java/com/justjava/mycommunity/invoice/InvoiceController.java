package com.justjava.mycommunity.invoice;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.processes.CustomProcessService;
import com.justjava.mycommunity.task.TaskService;
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
@RequestMapping("/invoice")
public class InvoiceController {

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

    @Value("${account.bank}")
    private String recipientBank;

    @Value("${account.account-number}")
    private String recipientAccountNumber;

    @Value("${account.account-name}")
    private String recipientAccountName;

    public InvoiceController(final AuthenticationManager authenticationManager,
                             final CustomProcessService processService,
                             final InvoiceService invoiceService,
                             final TaskService taskService,
                             final PaystackService paystackService){
        this.authenticationManager = authenticationManager;
        this.processService = processService;
        this.invoiceService = invoiceService;
        this.taskService = taskService;
        this.paystackService = paystackService;
    }

    @GetMapping
    public String getInvoices(Model model){
        String loginUser = (String) authenticationManager.get("sub");
        List<ProcessInstance> allProcessInstance = processService.getAllProcessInstance("invoicing", loginUser);
        List<HistoricProcessInstance> allCompletedInstancesByUser = processService.
                getAllHistoricInstances("invoicing", loginUser);

        List<Map<String, Object>> allProcessVar = allProcessInstance.stream()
                .map(processInstance -> {
                    String processId = processInstance.getProcessInstanceId();
                    Map<String, Object> allSingleProcessVar = processInstance.getProcessVariables();
                    allSingleProcessVar.put("processId", processId);

                    return allSingleProcessVar;
                }).collect(Collectors.toList());

        List<Map<String, Object>> allCompletedInstancesVar = allCompletedInstancesByUser.stream()
                        .map(HistoricProcessInstance::getProcessVariables).toList();

        allProcessVar.addAll(allCompletedInstancesVar);
//        System.out.println("This is the all process var:::" + allProcessVar);

        // Calculate statistics
        Long allPendingInvoiceCount = allProcessVar.stream()
                .filter(invoice -> "approved".equalsIgnoreCase((String) invoice.get("status")))
                .count();

        Long allPaidInvoiceCount = allCompletedInstancesVar.stream()
                .filter(invoice -> "paid".equalsIgnoreCase((String) invoice.get("status")))
                .count();

        BigDecimal totalInvoiceAmount = allCompletedInstancesVar.stream()
                .filter(invoice -> "paid".equalsIgnoreCase((String) invoice.get("status")))
                .map(invoice -> {
                    String amount = (String) invoice.get("amount");
                    return new BigDecimal(amount != null ? amount : "0");
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("recipientBank", recipientBank);
        model.addAttribute("recipientAccountNumber", recipientAccountNumber);
        model.addAttribute("recipientAccountName", recipientAccountName);
        model.addAttribute("paidInvoiceCount", allPaidInvoiceCount);
        model.addAttribute("pendingInvoiceCount", allPendingInvoiceCount);
        model.addAttribute("totalAmount", totalInvoiceAmount);
        model.addAttribute("allInvoices", allProcessVar);
        return "invoice/invoice";
    }

    @GetMapping("/view-invoice/{processId}")
    public String getInvoiceDetail(@PathVariable String processId, Model model){
        try {
            ProcessInstance singleProcessInstance = processService.getSingleProcessInstance(processId, "invoicing");
            Map<String, Object> processVar = singleProcessInstance.getProcessVariables();

//            System.out.println("This is the single process" + processVar);
            model.addAttribute("singleInvoice", processVar);
            return "invoice/invoiceDetail";
        } catch (Exception e) {
//            System.err.println("Error retrieving invoice detail: " + e.getMessage());
            model.addAttribute("error", "Invoice not found");
            return "redirect:/invoice";
        }
    }

    @GetMapping("/create")
    public String addInvoice(Model model){
        List<DesignDTO.Response> designs = designService.getAllDesigns();
        designs.forEach(
                design -> System.out.println("Design ID: " + design.getId() + ", Name: " + design.getDesignName())
        );
        model.addAttribute("templates", designs);
        model.addAttribute("status", "new");
        return "invoice/createInvoice";
    }
    @GetMapping("/design")
    public String designInvoice(Model model){
        return "invoice/invoiceDesign";
    }

    @PostMapping("/create-invoice")
    public String getInvoice(@RequestParam Map<String, Object> formData, Model model) {
        try {
            String loginUser = (String) authenticationManager.get("sub");
            formData.put("status", "new");
            formData.put("baseUrl", baseUrl);

            // Start the process
            processService.startProcess("invoicing", loginUser, formData);

            model.addAttribute("invoiceData", formData);
            return "redirect:/invoice";

        } catch (Exception e) {
//            System.err.println("Error creating invoice: " + e.getMessage());
            model.addAttribute("error", "Failed to create invoice");
            return "invoice/createInvoice";
        }
    }

    @GetMapping("/edit-invoice/{id}")
    public String getEditInvoice(@PathVariable String id, Model model){
        try {
            Task singleTask = taskService.getTaskByInstanceAndDefinitionKey(id, "FormTask_EditInvoice");
            Map<String, Object> singleTaskVar = singleTask.getProcessVariables();
            singleTaskVar.put("taskId", singleTask.getId());

            model.addAttribute("reviewData", singleTaskVar);
            return "invoice/createInvoice";
        } catch (Exception e) {
//            System.err.println("Error retrieving invoice for edit: " + e.getMessage());
            model.addAttribute("error", "Invoice not found for editing");
            return "redirect:/invoice";
        }
    }

    @PostMapping("/submit-editInvoice")
    public String submitEditInvoice(@RequestParam Map<String, Object> editData, Model model){
        try {
            String taskId = (String) editData.get("taskId");
            editData.put("status", "pending");
            invoiceService.editInvoiceTask(taskId, editData);

            model.addAttribute("invoiceData", editData);
            return "redirect:/invoice";
        } catch (Exception e) {
//            System.err.println("Error submitting invoice edit: " + e.getMessage());
            model.addAttribute("error", "Failed to update invoice");
            return "invoice/createInvoice";
        }
    }

    @GetMapping("/invoice-review")
    public String getInvoiceReview(Model model){
        try {
            List<Task> allPendingReview = taskService.getTaskByAssigneeAndProcessDefinitionKey("manager", "invoicing");
            List<HistoricTaskInstance> allApprovedInvoices = taskService.getCompletedTaskByAssigneeAndVariable("manager", "invoicing", "status", "approved");
            List<HistoricTaskInstance> allDeclinedInvoices = taskService.getCompletedTaskByAssigneeAndVariable("manager", "invoicing", "status", "declined");

            List<Map<String, Object>> allPendingInvoiceVariables = allPendingReview.stream()
                    .map(invoice -> {
                        Map<String, Object> invoiceVariables = invoice.getProcessVariables();
                        String taskId = invoice.getId();
                        invoiceVariables.put("taskId", taskId);
                        return invoiceVariables;
                    }).toList();

            model.addAttribute("allApprovedCount", allApprovedInvoices.size());
            model.addAttribute("allDeclinedCount", allDeclinedInvoices.size());
            model.addAttribute("allPendingCount", allPendingReview.size());
            model.addAttribute("allPendingInvoice", allPendingInvoiceVariables);
            return "invoice/invoiceReviewHome";
        } catch (Exception e) {
            System.err.println("Error retrieving invoice reviews: " + e.getMessage());
            model.addAttribute("error", "Failed to load invoice reviews");
            return "invoice/invoiceReviewHome";
        }
    }

    @GetMapping("/invoice-review/{taskId}")
    public String getInvoiceReviewDetails(@PathVariable String taskId, Model model){
        try {
            Task singleTask = taskService.findTaskById(taskId);
            Map<String, Object> singleTaskVar = singleTask.getProcessVariables();
            singleTaskVar.put("taskId", taskId);

            model.addAttribute("invoiceData", singleTaskVar);
            return "invoice/invoiceReview";
        } catch (Exception e) {
            System.err.println("Error retrieving invoice review details: " + e.getMessage());
            model.addAttribute("error", "Invoice review not found");
            return "redirect:/invoice/invoice-review";
        }
    }

    @PostMapping("/submit-review")
    public ResponseEntity<Void> submitReview(@RequestParam Map<String, Object> reviewData, Model model){
        try {
            System.out.println("This is the review Data" + reviewData);
            String taskId = (String) reviewData.get("taskId");
            invoiceService.seniorReviewTask(taskId, reviewData);

            HttpHeaders headers = new HttpHeaders();
            headers.add("HX-Redirect", "/invoice/invoice-review");
            return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
        } catch (Exception e) {
            System.err.println("Error submitting review: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/pay/{procInstId}")
    public String payInvoice(@PathVariable String procInstId){
//        String loginUser = (String) authenticationManager.get("sub");
        Task singleTask = taskService.getTaskByInstanceAndDefinitionKey(procInstId, "FormTask_ClientPayment");
        Map<String, Object> singleTaskVar = singleTask.getProcessVariables();

        // Handle payment link generation for new invoice
        String paymentLinkUrl = newInvoicePaymentLink(procInstId, singleTaskVar);
        return "redirect:"+ paymentLinkUrl;
    }

    /**
     * Handle payment callback from Paystack
     */

    @PostMapping("/design")
    public String saveDesign(@RequestParam("invoiceHtml") String invoiceHtml,
                             @RequestParam("designName") String designName) {

        System.out.println("\n\n=== RECEIVED INVOICE DESIGN ===");
        System.out.println("Design Name: " + designName);
        System.out.println("HTML Content Received!");
        System.out.println("Total characters: " + invoiceHtml.length());
        System.out.println("\n=== HTML PREVIEW (first 300 chars) ===");

        // Show a preview of the HTML
        if (invoiceHtml.length() > 300) {
            System.out.println(invoiceHtml.substring(0, 300) + "...");
        } else {
            System.out.println(invoiceHtml);
        }

        System.out.println("\n=== FULL HTML ===");
        System.out.println(invoiceHtml);

        // Create request DTO
        DesignDTO.Request request = new DesignDTO.Request();
        request.setDesignName(designName);
        request.setDesignHtml(invoiceHtml);

        // Save to database
        DesignDTO.Response savedDesign = designService.saveDesign(request);

        System.out.println("\n=== DESIGN SAVED SUCCESSFULLY ===");
        System.out.println("Saved Design ID: " + savedDesign.getId());
        System.out.println("Saved Design Name: " + savedDesign.getDesignName());
        System.out.println("Created At: " + savedDesign.getCreatedAt());

        return "redirect:/invoice/create";
    }

    @GetMapping("/payment-callback/{processId}")
    public String handlePaymentCallback(@RequestParam(required = false) String reference,@PathVariable String processId
            , Model model) {
//        System.out.println("This is the reference::" + reference);
//        System.out.println("This is the process id:: " + processId);
        try {
            if (reference != null && !reference.isEmpty()) {
                Map<String, Object> paymentData = paystackService.verifyPayment(reference);
//                System.out.println("This is the payment Data::" + paymentData);

                String status = (String) paymentData.get("status");
                String channel = (String) paymentData.get("channel");
                String paidDate = (String) paymentData.get("paid_at");

                Map<String, Object> authorizationObject = (Map<String, Object>) paymentData.get("authorization");
                Map<String, Object> metadataObject = (Map<String, Object>) paymentData.get("metadata");
//                System.out.println("This is the current status::" + status);

                Task singleTask = taskService.getTaskByInstanceAndDefinitionKey(processId, "FormTask_ClientPayment");
                Map<String, Object> singleTaskVar = singleTask.getProcessVariables();

                if ("success".equals(status)) {
                    String accountNumber = "";
                    String senderBank = "";
                    String senderName = (String) metadataObject.get("client_name");
                    String remark= "Payment for Service";
                    if("bank_transfer".equalsIgnoreCase(channel)){
                        String bin = (String) authorizationObject.get("bin");
                        String last4 = (String) authorizationObject.get("last4");
                        accountNumber = bin + last4;
                        senderBank = (String) authorizationObject.get("sender_bank");
                        String[] senderBankList = senderBank.split(" ");
                        senderBank = senderBankList[0];
                        senderName = (String) authorizationObject.get("sender_name");
                        remark = (String) authorizationObject.get("narration");
                    }

                    singleTaskVar.put("paidDate", paidDate);
                    singleTaskVar.put("accountNumber", accountNumber);
                    singleTaskVar.put("senderBank", senderBank);
                    singleTaskVar.put("senderName", senderName);
                    singleTaskVar.put("remark", remark);
                    singleTaskVar.put("payStackStatus", status);
                    singleTaskVar.put("reference", reference);

                    taskService.completeTask(singleTask.getId(), singleTaskVar);
                    model.addAttribute("message", "Payment successful! Your invoice has been paid.");
                    model.addAttribute("type", "success");
                } else {
                    singleTaskVar.put("payStackStatus", status);
                    singleTaskVar.put("reference", reference);
                    taskService.completeTask(singleTask.getId(), singleTaskVar);
                    model.addAttribute("message", "Payment was not successful. Please try again.");
                    model.addAttribute("type", "error");
                }
            }
        } catch (Exception e) {
            System.err.println("Error verifying payment: " + e.getMessage());
            model.addAttribute("message", "Error verifying payment. Please contact support.");
            model.addAttribute("type", "error");
        }

        return "redirect:/invoice";
    }

    /**
     * Handle payment link generation for new invoices
     */
    public String newInvoicePaymentLink(String processInstanceId, Map<String, Object> formData){
//        System.out.println("This is the new invoice payment link::" + formData);
        String paymentLink = "";
        if(paystackService.isConfigured()){
            try{
//                System.out.println("The paystack service is configured");
                paymentLink = generatePaymentLink(formData, processInstanceId);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return paymentLink;
    }

    /**
     * Generate Paystack payment link for an invoice
     */
    private String generatePaymentLink(Map<String, Object> invoiceData, String processId) {
        try {
            String email = (String) invoiceData.get("email");
            String amountStr = (String) invoiceData.get("amount");

            // Validate required fields
            if (email == null || email.isEmpty()) {
                throw new RuntimeException("Email is required for payment link generation");
            }

            if (amountStr == null || amountStr.isEmpty()) {
                throw new RuntimeException("Amount is required for payment link generation");
            }

            BigDecimal amount = new BigDecimal(amountStr);
            String businessName = (String) invoiceData.get("businessName");
            String clientName = (String) invoiceData.get("clientName");

            String reference = paystackService.generatePaymentReference(processId);
            String callbackUrl = baseUrl + "/invoice/payment-callback/"+processId;
            Map<String, Object> metadata = paystackService.createPaymentMetadata(processId, businessName, clientName);

//            System.out.println("Generating payment link for invoice: " + processId + ", email: " + email + ", amount: " + amount);

            Map<String, Object> paymentData = paystackService.initializePayment(
                    email, amount, reference, callbackUrl, metadata
            );

//            System.out.println("This is the payment data::" + paymentData);
            String authorizationUrl = (String) paymentData.get("authorization_url");

//            System.out.println("Successfully generated payment link for invoice: " + authorizationUrl);

            return authorizationUrl;

        } catch (Exception e) {
            System.err.println("Failed to generate payment link for invoice: " + processId + ". Error: " + e.getMessage());
            throw new RuntimeException("Error generating payment link: " + e.getMessage(), e);
        }
    }
}
