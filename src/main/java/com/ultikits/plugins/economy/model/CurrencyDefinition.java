package com.ultikits.plugins.economy.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CurrencyDefinition {
    private final String id;
    private final String displayName;
    private final String symbol;
    @Builder.Default private final double initialCash = 0.0;
    @Builder.Default private final boolean bankEnabled = false;
    @Builder.Default private final double minDeposit = 0.0;
    @Builder.Default private final double maxBankBalance = -1;
    @Builder.Default private final boolean primary = false;
}
