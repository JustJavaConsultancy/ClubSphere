package com.justjava.mycommunity.util;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SendGridService {
    private SendGrid sendGridClient;

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail; // Client is set up once and reused

    public SendGridService() {
//        this.sendGridClient = new SendGrid(apiKey);
        this.fromEmail = fromEmail;
    }

    @PostConstruct
    public void init() {
        // ONE-TIME SETUP: Runs automatically after injection
        System.out.println("Initializing SendGrid with API Key: " + apiKey);
        this.sendGridClient = new SendGrid(apiKey);
        System.out.println("This is the apiKey after init " + apiKey);
    }

    @Async
    public void sendTemplateEmail(String templateId, String toEmail, String businessName,
                                  String invoiceLink, String dueDate) {
        try {
            Email from = new Email(fromEmail, "Just Java");
            Email to = new Email(toEmail);

            Mail mail = new Mail();
            mail.setFrom(from);
            mail.setTemplateId(templateId);

            Personalization personalization = new Personalization();
            personalization.addTo(to);
            personalization.addDynamicTemplateData("businessName", businessName);
            personalization.addDynamicTemplateData("invoiceLink", invoiceLink);
            personalization.addDynamicTemplateData("dueDate", dueDate);
            mail.addPersonalization(personalization);

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGridClient.api(request);
            System.out.println("\nEmail Status Code: " + response.getStatusCode());
            if (response.getStatusCode() >= 400) {
                throw new RuntimeException(response.getBody());
            }
        } catch (IOException e) {
            System.err.println("No response from SendGrid " + e.getMessage());
        }
    }

    public void sendHtmlEmail(String toEmail, String html){
        try {
            Email from = new Email(fromEmail, "Just Java");
            Email to = new Email(toEmail);

            String subject = "Your Invoice Receipt";
            Content content = new Content("text/html", html);
            Mail mail = new Mail(from, subject, to, content);

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGridClient.api(request);
            System.out.println("\nEmail Status Code: " + response.getStatusCode());
            System.out.println("\n This is the email response body" + response.getBody());

            if (response.getStatusCode() >= 400) {
                throw new RuntimeException(response.getBody());
            }
            return;
        } catch (IOException e) {
            System.err.println("No response from SendGrid " + e.getMessage());
        }
    }

}