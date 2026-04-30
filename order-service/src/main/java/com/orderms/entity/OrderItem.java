package com.orderms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore @ToString.Exclude @EqualsAndHashCode.Exclude
    private Order order;

    @Column(name = "product_id",   nullable = false, length = 100) private String    productId;
    @Column(name = "product_name", nullable = false, length = 500) private String    productName;
    @Column(name = "quantity",     nullable = false)               private Integer   quantity;
    @Column(name = "unit_price",   nullable = false, precision = 10, scale = 2) private BigDecimal unitPrice;
    @Column(name = "line_total",   nullable = false, precision = 12, scale = 2) private BigDecimal lineTotal;
}
