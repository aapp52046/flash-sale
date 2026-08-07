package com.flashsale.repository;

import com.flashsale.entity.FlashOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlashOrderRepository extends JpaRepository<FlashOrder, Long> {

    Optional<FlashOrder> findByOrderNo(String orderNo);

    List<FlashOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndFlashProductId(Long userId, Long flashProductId);

    long countByFlashProductId(Long flashProductId);
}
