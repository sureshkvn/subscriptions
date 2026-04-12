package com.sureshkvn.subscriptions.coupon.repository;

import com.sureshkvn.subscriptions.coupon.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Coupon} entities.
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    List<Coupon> findAllByStatus(Coupon.CouponStatus status);
}
