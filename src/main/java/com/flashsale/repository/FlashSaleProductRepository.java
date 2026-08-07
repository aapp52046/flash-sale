package com.flashsale.repository;

import com.flashsale.entity.FlashSaleProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FlashSaleProductRepository extends JpaRepository<FlashSaleProduct, Long> {

    List<FlashSaleProduct> findByStatus(Integer status);

    List<FlashSaleProduct> findByStartTimeBeforeAndEndTimeAfterAndStatus(
            LocalDateTime now1, LocalDateTime now2, Integer status);

    Optional<FlashSaleProduct> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FlashSaleProduct f WHERE f.id = :id")
    Optional<FlashSaleProduct> findByIdWithPessimisticLock(@Param("id") Long id);
}
