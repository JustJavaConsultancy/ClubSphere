package com.justjava.mycommunity.invoice;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceDesignRepository extends JpaRepository<InvoiceDesign, Long> {
    List<InvoiceDesign> findAllByOrderByCreatedAtDesc();
}
