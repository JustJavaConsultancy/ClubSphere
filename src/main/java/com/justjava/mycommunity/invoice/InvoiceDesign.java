package com.justjava.mycommunity.invoice;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "invoice_designs")
public class InvoiceDesign {

    // Getter and Setter methods
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "design_name", nullable = false)
    private String designName;

    @Column(name = "design_html", columnDefinition = "TEXT", nullable = false)
    private String designHtml;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Constructors
    public InvoiceDesign() {
        this.createdAt = LocalDateTime.now();
    }

    public InvoiceDesign(String designName, String designHtml) {
        this();
        this.designName = designName;
        this.designHtml = designHtml;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDesignName(String designName) {
        this.designName = designName;
    }

    public void setDesignHtml(String designHtml) {
        this.designHtml = designHtml;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}