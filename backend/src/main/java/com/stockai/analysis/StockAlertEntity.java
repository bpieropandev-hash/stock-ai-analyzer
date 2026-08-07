package com.stockai.analysis;

import com.stockai.stock.Stock;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_alerts")
public class StockAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // EAGER: ver comentário equivalente em ScoreHistoryEntity.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "alert_date", nullable = false)
    private LocalDate alertDate;

    @Column(name = "score_before", nullable = false)
    private double scoreBefore;

    @Column(name = "score_after", nullable = false)
    private double scoreAfter;

    @Column(nullable = false, length = 4)
    private String direction;

    @Column(nullable = false)
    private double magnitude;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StockAlertEntity() {}

    public StockAlertEntity(Stock stock, LocalDate alertDate, double scoreBefore,
                            double scoreAfter, String direction, double magnitude,
                            LocalDateTime createdAt) {
        this.stock = stock;
        this.alertDate = alertDate;
        this.scoreBefore = scoreBefore;
        this.scoreAfter = scoreAfter;
        this.direction = direction;
        this.magnitude = magnitude;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Stock getStock() { return stock; }
    public String getTicker() { return stock.getTicker(); }
    public LocalDate getAlertDate() { return alertDate; }
    public double getScoreBefore() { return scoreBefore; }
    public double getScoreAfter() { return scoreAfter; }
    public String getDirection() { return direction; }
    public double getMagnitude() { return magnitude; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
