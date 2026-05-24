package com.stockai.portfolio;

import com.stockai.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<PortfolioItem, UUID> {
    List<PortfolioItem> findByUser(UserEntity user);
    Optional<PortfolioItem> findByUserAndTicker(UserEntity user, String ticker);
}
