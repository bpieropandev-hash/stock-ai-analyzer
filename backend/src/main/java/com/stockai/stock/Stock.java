package com.stockai.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Identidade canônica de um ativo (hoje só ações B3). Substitui o padrão
 * anterior de ticker como string solta duplicada em portfolio_items,
 * score_history e stock_alerts. Criada sob demanda (get-or-create) na
 * primeira vez que um ticker é referenciado — não existe cadastro manual.
 */
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String ticker;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Stock() {
        // exigido pelo JPA
    }

    Stock(String ticker) {
        this.ticker = ticker;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
