package com.stockai.portfolio;

import com.stockai.stock.Stock;
import com.stockai.user.UserEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "stock_id"}))
public class PortfolioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // EAGER: ver comentário equivalente em ScoreHistoryEntity — Stock é pequeno
    // e o ticker é lido em quase todo método de PortfolioService.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "average_price", nullable = false)
    private Double averagePrice;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PortfolioItem() {}

    public PortfolioItem(UserEntity user, Stock stock, Double quantity,
                         Double averagePrice, LocalDate purchaseDate) {
        this.user = user;
        this.stock = stock;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.purchaseDate = purchaseDate;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public Stock getStock() { return stock; }
    public String getTicker() { return stock.getTicker(); }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Double getAveragePrice() { return averagePrice; }
    public void setAveragePrice(Double averagePrice) { this.averagePrice = averagePrice; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
