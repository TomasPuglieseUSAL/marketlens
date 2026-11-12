package com.marketlens.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "risk_evaluations")
public class RiskEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel level;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiskFactor> factors = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_status", length = 20)
    private RiskCoverageStatus coverageStatus;

    @Column(name = "available_factors")
    private Integer availableFactors;

    @Column(name = "total_factors")
    private Integer totalFactors;

    public RiskEvaluation() {
    }

    @PrePersist
    protected void onCreate() {
        if (evaluatedAt == null) {
            evaluatedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Publication getPublication() {
        return publication;
    }

    public void setPublication(Publication publication) {
        this.publication = publication;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public RiskLevel getLevel() {
        return level;
    }

    public void setLevel(RiskLevel level) {
        this.level = level;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public List<RiskFactor> getFactors() {
        return factors;
    }

    public void setFactors(List<RiskFactor> factors) {
        this.factors = factors;
    }

    public RiskCoverageStatus getCoverageStatus() {
        return coverageStatus;
    }

    public void setCoverageStatus(RiskCoverageStatus coverageStatus) {
        this.coverageStatus = coverageStatus;
    }

    public Integer getAvailableFactors() {
        return availableFactors;
    }

    public void setAvailableFactors(Integer availableFactors) {
        this.availableFactors = availableFactors;
    }

    public Integer getTotalFactors() {
        return totalFactors;
    }

    public void setTotalFactors(Integer totalFactors) {
        this.totalFactors = totalFactors;
    }
}