package com.justjava.mycommunity.util;

import org.flowable.engine.delegate.DelegateExecution;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component("emailService")
public class EmailService {

    @Autowired
    ResendService resendService;

    @Value("${sendgrid.template-id}")
    private String TEMPLATE_ID;

    @Value("${sendgrid.template-id-2}")
    private String TEMPLATE_ID_2;

    @Value("${resend.template-id-invoice}")
    private String templateInvoiceId;

    @Value("${resend.template-id-reminder-invoice}")
    private String templateInvoiceReminder;

    public void sendMail(DelegateExecution execution) throws Exception {
        String businessName = (String) execution.getVariable("businessName");
        String invoiceLink = (String) execution.getVariable("invoiceLink");
        String toEmail = (String) execution.getVariable("email");
        String dueDate = (String) execution.getVariable("dueDate");

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("invoiceLink", invoiceLink);
        templateData.put("businessName", businessName);

        resendService.sendMailWithTemplate(toEmail,templateInvoiceId,templateData);

//        System.out.println("\n\n" + " Sending mail..execution variable=="
//                +execution.getVariables() +" task "+execution.getCurrentActivityName());
    }

    public void sendReminder(DelegateExecution execution) throws Exception{
        String businessName = (String) execution.getVariable("businessName");
        String invoiceLink = (String) execution.getVariable("invoiceLink");
        String toEmail = (String) execution.getVariable("email");
        String dueDate = (String) execution.getVariable("dueDate");

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("invoiceLink", invoiceLink);
        templateData.put("dueDate", dueDate);
        templateData.put("businessName", businessName);

        resendService.sendMailWithTemplate(toEmail, templateInvoiceReminder, templateData);
//        System.out.println("\n\n" + " Send reminder" + execution.getVariables());
    }


    public void sendApproval(DelegateExecution execution) throws Exception{
        String htmlReceipt = (String) execution.getVariable("templateHtml");
        String clientName = (String) execution.getVariable("clientName");
        String dueDate = (String) execution.getVariable("dueDate");
        String amount = (String) execution.getVariable("amount");
        String clientAddress = (String) execution.getVariable("address");
        String clientEmail = (String) execution.getVariable("email");
        String clientPhoneNumber = (String) execution.getVariable("phoneNumber");
        String reference = (String) execution.getVariable("reference");
        String remark = (String) execution.getVariable("remark");
        String description = (String) execution.getVariable("description");
        String paidDate = (String) execution.getVariable("paidDate");
        paidDate = paidDate.split("T")[0];

        // Parse the HTML
        Document doc = Jsoup.parse(htmlReceipt);

        // Update invoice number on receipt
        Element invoiceNumberElement = doc.getElementById("receiptInvoiceNumber");
        if (invoiceNumberElement != null) {
            invoiceNumberElement.html("<strong>ID:" + reference + "</strong>");
        }

        // Update paid date on receipt
        Element paidDateElement = doc.getElementById("receiptCreatedDate");
        if (paidDateElement != null) {
            paidDateElement.html("<strong>Paid Date:" + paidDate + "</strong>");
        }

        // Update due date on receipt
        Element dueDateElement = doc.getElementById("receiptDueDate");
        if (dueDateElement != null) {
            dueDateElement.html("<strong>Due Date:" + dueDate + "</strong>");
        }

        // Update client name (assuming an element with id "clientName")
        Element clientNameElement = doc.getElementById("receiptClientName");
        if (clientNameElement != null) {
            clientNameElement.text(clientName);
        }

        // Update client address
        Element clientAddressElement = doc.getElementById("receiptClientAddress");
        if (clientAddressElement != null) {
            clientAddressElement.text(clientAddress);
        }

        //Update client email
        Element clientEmailElement = doc.getElementById("receiptClientEmail");
        if (clientEmailElement != null) {
            clientEmailElement.text(clientEmail);
        }

        // Update client phone number
        Element clientPhoneElement = doc.getElementById("receiptClientPhone");
        if (clientPhoneElement != null) {
            clientPhoneElement.text(clientPhoneNumber);
        }

        // update description
        Element descriptionElement = doc.getElementById("receiptDescription");
        if (descriptionElement != null) {
            descriptionElement.text(description);
        }

        // update receipt amount
        Element receiptAmountElement = doc.getElementById("receiptAmount");
        if (receiptAmountElement != null) {
            receiptAmountElement.text("₦" + amount);
        }

        // Update amount paid
        Element amountSubTotalElement = doc.getElementById("receiptSubTotal");
        if (amountSubTotalElement != null) {
            amountSubTotalElement.text("₦" + amount);
        }

        // Update amount paid
        Element amountPaidElement = doc.getElementById("receiptTotal");
        if (amountPaidElement != null) {
            amountPaidElement.text("₦" + amount);
        }

        // Get the updated HTML string
        String updatedHtmlReceipt = doc.html();

//        System.out.println("I am reaching here ............");
        //send mail receipt
        resendService.sendHtmlEmail(clientEmail, "Your Invoice Receipt", updatedHtmlReceipt);

    }
}
