package com.justjava.mycommunity.invoice;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class DesignDTO {

    // Request DTO for creating/updating designs
    public static class Request {
        @NotBlank(message = "Design name is required")
        private String designName;

        @NotBlank(message = "HTML content is required")
        private String designHtml;

        // Getter and Setter methods
        public String getDesignName() {
            return designName;
        }

        public void setDesignName(String designName) {
            this.designName = designName;
        }

        public String getDesignHtml() {
            return designHtml;
        }

        public void setDesignHtml(String designHtml) {
            this.designHtml = designHtml;
        }
    }

    // Response DTO for returning designs
    public static class Response {
        private Long id;
        private String designName;
        private String designHtml;
        private LocalDateTime createdAt;

        // Getter and Setter methods
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDesignName() {
            return designName;
        }

        public void setDesignName(String designName) {
            this.designName = designName;
        }

        public String getDesignHtml() {
            return designHtml;
        }

        public void setDesignHtml(String designHtml) {
            this.designHtml = designHtml;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        // Static factory method
        public static Response fromEntity(InvoiceDesign design) {
            Response response = new Response();
            response.setId(design.getId());
            response.setDesignName(design.getDesignName());
            response.setDesignHtml(design.getDesignHtml());
            response.setCreatedAt(design.getCreatedAt());
            return response;
        }
    }
}