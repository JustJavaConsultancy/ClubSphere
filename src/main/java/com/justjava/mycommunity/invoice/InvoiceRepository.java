package com.justjava.mycommunity.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByMerchantIdOrderByDueDateDesc(String merchantId);

    //Invoice findFirstByCusomer(Customer customer);

}
