package com.payops.payment.adapter.output.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PaymentEventJpaRepository extends JpaRepository<PaymentEventEntity, Long> {

    List<PaymentEventEntity> findByPaymentIdOrderByOccurredAtAscIdAsc(Long paymentId);
}
