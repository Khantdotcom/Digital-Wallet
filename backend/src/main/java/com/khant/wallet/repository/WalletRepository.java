package com.khant.wallet.repository;

import com.khant.wallet.domain.Wallet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

  List<Wallet> findByUserId(Long userId);

  Optional<Wallet> findByIdAndUserId(Long id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Wallet w where w.id = :walletId and w.user.id = :userId")
  Optional<Wallet> findByIdAndUserIdForUpdate(@Param("walletId") Long walletId, @Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Wallet w where w.id in :walletIds order by w.id asc")
  List<Wallet> findAllByIdInForUpdateOrdered(@Param("walletIds") List<Long> walletIds);
}
