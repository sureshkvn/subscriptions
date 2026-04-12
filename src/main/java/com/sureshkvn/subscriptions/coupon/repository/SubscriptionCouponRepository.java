package com.sureshkvn.subscriptions.coupon.repository;

import com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SubscriptionCoupon} join entities.
 */
@Repository
public interface SubscriptionCouponRepository extends JpaRepository<SubscriptionCoupon, Long> {

    List<SubscriptionCoupon> findAllBySubscriptionIdAndActiveTrue(Long subscriptionId);

    Optional<SubscriptionCoupon> findBySubscriptionIdAndCouponCodeAndActiveTrue(
            Long subscriptionId, String couponCode);

    boolean existsBySubscriptionIdAndCouponIdAndActiveTrue(Long subscriptionId, Long couponId);
}
