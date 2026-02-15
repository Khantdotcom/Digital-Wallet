package com.khant.wallet.repository;

import com.khant.wallet.domain.RiskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskEventRepository extends JpaRepository<RiskEvent, Long> {
}
