package com.marketlens.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "risk_factors")
public class RiskFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private RiskEvaluation evaluation;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "score_contribution", nullable = false)
    private Integer scoreContribution;

    public RiskFactor() {
    }

    public Long getId() {
        return id;
    }

    public RiskEvaluation getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(RiskEvaluation evaluation) {
        this.evaluation = evaluation;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getScoreContribution() {
        return scoreContribution;
    }

    public void setScoreContribution(Integer scoreContribution) {
        this.scoreContribution = scoreContribution;
    }
}