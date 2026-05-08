package com.justjava.mycommunity.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByMerchantIdOrderByDueDateDesc(String merchantId);
    Optional<Invoice> findByMerchantId(String merchantId);
    List<Invoice> findByMerchantIdStartingWith(String merchantIdPrefix);
    List<Invoice> findByMerchantIdStartingWithAndStatus(String merchantIdPrefix, Status status);

    //Invoice findFirstByCusomer(Customer customer);

}
