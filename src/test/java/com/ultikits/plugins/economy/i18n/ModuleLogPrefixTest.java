package com.ultikits.plugins.economy.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The framework's module logger already writes {@code [UltiTools-Economy] } in front of every line this module logs,
 * so the module's own warning texts must not start with a second, self-written {@code UltiEconomy: } prefix
 * (Phase 19 defect slot 2, D19-094..099). Each warning text starts with the setting it is about.
 */
@DisplayName("Module warnings carry no self-written module prefix")
class ModuleLogPrefixTest {

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "en, economy.warn.interest_enabled_one_wallet", "zh, economy.warn.interest_enabled_one_wallet",
            "en, economy.warn.value_out_of_range", "zh, economy.warn.value_out_of_range",
            "en, economy.warn.currency_value_out_of_range", "zh, economy.warn.currency_value_out_of_range",
            "en, economy.warn.tax_disabled", "zh, economy.warn.tax_disabled",
            "en, economy.warn.removed_key", "zh, economy.warn.removed_key",
            "en, economy.warn.primary_currency_setting", "zh, economy.warn.primary_currency_setting"})
    void warningTextHasNoModulePrefix(String code, String key) {
        String text = CatalogueText.text(code, key);
        assertThat(text).doesNotStartWith("%s:").doesNotStartWith("%s：").doesNotStartWith("UltiEconomy");
    }
}
