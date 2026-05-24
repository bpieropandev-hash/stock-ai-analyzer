package com.stockai.analysis;

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

    @Column(nullable = false)
    private String ticker;

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

    public StockAlertEntity(String ticker, LocalDate alertDate, double scoreBefore,
                            double scoreAfter, String direction, double magnitude,
                            LocalDateTime createdAt) {
        this.ticker = ticker;
        this.alertDate = alertDate;
        this.scoreBefore = scoreBefore;
        this.scoreAfter = scoreAfter;
        this.direction = direction;
        this.magnitude = magnitude;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getTicker() { return ticker; }
    public LocalDate getAlertDate() { return alertDate; }
    public double getScoreBefore() { return scoreBefore; }
    public double getScoreAfter() { return scoreAfter; }
    public String getDirection() { return direction; }
    public double getMagnitude() { return magnitude; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
