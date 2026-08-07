package com.stockai.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisAuditRepository extends JpaRepository<AnalysisAudit, Long> {
}
