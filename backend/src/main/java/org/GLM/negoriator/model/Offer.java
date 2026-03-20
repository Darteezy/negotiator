package org.GLM.negoriator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(indexes = {@Index(name = "idx_offer_negotiation_id", columnList = "negotiation_id"), @Index(name = "idx_offer_round_number", columnList = "round_number")}
)
@Getter
@Setter
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negotiation_id", nullable = false)
    @NotNull
    private Negotiation negotiation;

    @Min(1)
    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Min(30)
    @Column(nullable = false)
    private int paymentDays;

    @Min(3)
    @Column(nullable = false)
    private int deliveryDays;

    @Min(3)
    @Column(nullable = false)
    private int contractMonths;
}
