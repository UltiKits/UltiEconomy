package com.ultikits.plugins.economy.service;

import com.ultikits.plugins.economy.config.EconomyConfig;

/**
 * Test access to the package-private service factory for tests in other packages.
 */
public final class EconomyServiceImplAccess {

    private EconomyServiceImplAccess() {
    }

    /** A service over the world's storage that reads the given configuration and currency registry. */
    public static EconomyServiceImpl withCurrencies(EconomyTestWorld world, EconomyConfig config, CurrencyManager currencies) {
        return EconomyServiceImpl.createForTest(world.plugin, world.accounts, config, world.balances, currencies);
    }
}
