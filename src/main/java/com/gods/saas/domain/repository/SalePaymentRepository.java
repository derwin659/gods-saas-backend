package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.SalePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {
    List<SalePayment> findBySale_Id(Long saleId);
}
