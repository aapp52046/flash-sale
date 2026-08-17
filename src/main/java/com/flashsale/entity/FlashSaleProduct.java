package com.flashsale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "flash_sale_product", indexes = {
    @Index(name = "idx_time_status", columnList = "start_time, end_time, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "flash_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal flashPrice;

    @Column(name = "flash_stock", nullable = false)
    private Integer flashStock;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    @Builder.Default
    private Integer status = 0;

    @Transient
    private String productName;

    @Transient
    private String displayLabel;

    @Transient
    private String displayBadgeClass;
}
