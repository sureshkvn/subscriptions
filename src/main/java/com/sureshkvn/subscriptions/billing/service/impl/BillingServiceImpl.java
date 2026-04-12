package com.sureshkvn.subscriptions.billing.service.impl;

import com.sureshkvn.subscriptions.billing.dto.BillingCycleResponse;
import com.sureshkvn.subscriptions.billing.mapper.BillingMapper;
import com.sureshkvn.subscriptions.billing.model.BillingCycle;
import com.sureshkvn.subscriptions.billing.repository.BillingCycleRepository;
import com.sureshkvn.subscriptions.billing.service.BillingService;
import com.sureshkvn.subscriptions.common.exception.BusinessRuleException;
import com.sureshkvn.subscriptions.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Default implementation of {@link BillingService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingServiceImpl implements BillingService {

    private final BillingCycleRepository billingCycleRepository;
    private final BillingMapper billingMapper;

    @Override
    public List<BillingCycleResponse> getBillingCyclesBySubscription(Long subscriptionId) {
        return billingCycleRepository
                .findAllBySubscriptionIdOrderByCreatedAtDesc(subscriptionId)
                .stream()
                .map(billingMapper::toResponse)
                .toList();
    }

    @Override
    public BillingCycleResponse getBillingCycleById(Long id) {
        return billingMapper.toResponse(findOrThrow(id));
    }

    @Override
    public List<BillingCycleResponse> getBillingCyclesByStatus(BillingCycle.BillingStatus status) {
        return billingCycleRepository.findAllByStatus(status).stream()
                .map(billingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BillingCycleResponse markAsPaid(Long id, String paymentReference) {
        log.info("Marking billing cycle {} as paid with reference: {}", id, paymentReference);
        BillingCycle cycle = findOrThrow(id);

        if (cycle.getStatus() != BillingCycle.BillingStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING billing cycles can be marked as paid (current: "
                            + cycle.getStatus() + ")");
        }

        cycle.setStatus(BillingCycle.BillingStatus.PAID);
        cycle.setPaidAt(Instant.now());
        cycle.setPaymentReference(paymentReference);
        return billingMapper.toResponse(billingCycleRepository.save(cycle));
    }

    @Override
    @Transactional
    public BillingCycleResponse voidBillingCycle(Long id) {
        log.info("Voiding billing cycle id: {}", id);
        BillingCycle cycle = findOrThrow(id);

        if (cycle.getStatus() == BillingCycle.BillingStatus.PAID) {
            throw new BusinessRuleException("Cannot void a PAID billing cycle. Use refund instead.");
        }
        if (cycle.getStatus() == BillingCycle.BillingStatus.VOID) {
            throw new BusinessRuleException("Billing cycle is already voided");
        }

        cycle.setStatus(BillingCycle.BillingStatus.VOID);
        return billingMapper.toResponse(billingCycleRepository.save(cycle));
    }

    private BillingCycle findOrThrow(Long id) {
        return billingCycleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BillingCycle", "id", id));
    }
}
