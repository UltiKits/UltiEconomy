package com.ultikits.plugins.economy.entity;

import com.ultikits.ultitools.abstracts.AbstractDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("economy_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerAccountEntity extends AbstractDataEntity {

    @Column("uuid")
    private String uuid;

    @Column("player_name")
    private String playerName;

    @Column(value = "cash", type = "DOUBLE")
    private double cash;

    @Column(value = "bank", type = "DOUBLE")
    private double bank;

    public double getTotalWealth() {
        return cash + bank;
    }
}
