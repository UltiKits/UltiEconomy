package com.ultikits.plugins.economy.entity;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;
import lombok.*;

@Table("currency_balances")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyBalanceEntity extends BaseDataEntity<String> {

    @Column("uuid")
    private String uuid;

    @Column("currency_id")
    private String currencyId;

    @Column(value = "cash", type = "DOUBLE")
    @Builder.Default
    private double cash = 0.0;

    @Column(value = "bank", type = "DOUBLE")
    @Builder.Default
    private double bank = 0.0;

    public double getTotalWealth() {
        return cash + bank;
    }
}
