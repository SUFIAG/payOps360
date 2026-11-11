package com.payops.payment.application.port.in;

import com.payops.payment.domain.PaymentEvent;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.payment.domain.PaymentStatus;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.util.List;
import java.util.Set;

public interface PaymentQueryUseCase {

    PaymentDetails get(long organizationId, String reference);

    PageResult<PaymentView> search(long organizationId, PaymentSearchCriteria criteria, PageQuery page);

    /** Resolves a provider code to its internal id within the tenant, if it exists. */
    java.util.Optional<Long> providerIdFor(long organizationId, String providerCode);

    record PaymentDetails(PaymentView payment, List<PaymentEvent> timeline, Set<PaymentStatus> allowedTransitions,
                          boolean retryable) {
    }
}
