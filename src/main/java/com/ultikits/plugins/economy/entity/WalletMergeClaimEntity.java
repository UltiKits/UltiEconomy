package com.ultikits.plugins.economy.entity;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The claim a server takes before it runs the one-time primary-wallet merge, so that of several
 * servers sharing one database only one runs it at a time (UltiKits/UltiEconomy#25). The table holds
 * at most one row, whose {@code id} is fixed: the table's primary key is what refuses a second
 * server's claim. The row exists only while a merge is running, or after a server stopped in the
 * middle of one.
 */
@Table("economy_wallet_merge_claim")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class WalletMergeClaimEntity extends BaseDataEntity<String> {

    /** A random token the claiming server made for this start; no two starts share one. */
    @Column("claim_owner")
    private String claimOwner;

    /** Changed by the claiming server while it merges; a value that stops changing means it stopped. */
    @Column("heartbeat")
    private String heartbeat;

    /** When the claim was taken, by the claiming server's clock (for the log only). */
    @Column("claimed_at")
    private String claimedAt;
}
