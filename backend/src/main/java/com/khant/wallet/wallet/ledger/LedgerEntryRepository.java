package com.khant.wallet.wallet.ledger;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

  long countByMovementGroupId(UUID movementGroupId);

  @Query("""
      select coalesce(sum(case when e.direction = com.khant.wallet.wallet.ledger.LedgerDirection.DEBIT
                               then e.amount else 0L end), 0L)
      from LedgerEntry e
      join e.transaction t
      where t.status = com.khant.wallet.domain.TransactionStatus.COMPLETED
      """)
  Long sumCompletedDebits();

  @Query("""
      select coalesce(sum(case when e.direction = com.khant.wallet.wallet.ledger.LedgerDirection.CREDIT
                               then e.amount else 0L end), 0L)
      from LedgerEntry e
      join e.transaction t
      where t.status = com.khant.wallet.domain.TransactionStatus.COMPLETED
      """)
  Long sumCompletedCredits();

  @Query("""
      select coalesce(sum(
               case when e.direction = com.khant.wallet.wallet.ledger.LedgerDirection.CREDIT then e.amount
                    when e.direction = com.khant.wallet.wallet.ledger.LedgerDirection.DEBIT then -e.amount
                    else 0L end
             ), 0L)
      from LedgerEntry e
      join e.transaction t
      where e.accountKind = com.khant.wallet.wallet.ledger.LedgerAccountKind.WALLET
        and e.wallet.id = :walletId
        and t.status = com.khant.wallet.domain.TransactionStatus.COMPLETED
      """)
  Long sumCompletedWalletSignedAmount(@Param("walletId") Long walletId);
}
