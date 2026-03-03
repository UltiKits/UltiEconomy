package com.ultikits.plugins.economy.entity;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;
import lombok.*;

@Table("economy_treasury")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryEntity extends BaseDataEntity<String> {

    @Column("currency_id")
    private String currencyId;

    @Column(value = "balance", type = "DOUBLE")
    @Builder.Default
    private double balance = 0.0;
}
