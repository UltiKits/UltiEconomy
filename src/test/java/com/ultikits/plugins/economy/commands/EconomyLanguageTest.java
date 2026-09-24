package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.service.CurrencyManager;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEconomy#14: the command messages whose keys were missing from both language files now
 * follow the framework's {@code language} setting.
 * <p>
 * {@code i18n} answers from the real shipped catalogue ({@link CatalogueText}). Before the fix the keys
 * these commands passed had no entry in either file, so every one of these lines rendered the raw
 * Chinese source text whatever the language; each {@code en} test then fails by showing that Chinese
 * line.
 */
@DisplayName("UltiEconomy#14: legacy command messages follow the language setting")
class EconomyLanguageTest {

    private static final UUID PLAYER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private UltiToolsPlugin plugin;
    private EconomyService economy;

    @BeforeEach
    void setUp() {
        plugin = mock(UltiToolsPlugin.class);
        economy = mock(EconomyService.class);
        when(economy.formatAmount(1500.0)).thenReturn("$1,500.00");
        when(economy.formatAmount(3000.0)).thenReturn("$3,000.00");
        when(economy.formatAmount(4500.0)).thenReturn("$4,500.00");
        when(economy.formatAmount(500.0)).thenReturn("$500.00");
    }

    private void speak(String code) {
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(code));
    }

    private static List<String> said(CommandSender to) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(to, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    private List<String> money(String code) {
        speak(code);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER);
        when(economy.getCash(PLAYER)).thenReturn(1500.0);
        when(economy.getBank(PLAYER)).thenReturn(3000.0);
        when(economy.getTotalWealth(PLAYER)).thenReturn(4500.0);
        MoneyCommand.createForTest(plugin, economy, mock(CurrencyManager.class)).onBalance(player);
        return said(player);
    }

    @Test
    @DisplayName("/money under language: en is English")
    void moneyInEnglish() {
        assertThat(money("en")).containsExactly(
                ChatColor.GOLD + "=== Economy System ===",
                ChatColor.YELLOW + "Your balance: $1,500.00",
                ChatColor.YELLOW + "Your bank balance: $3,000.00",
                ChatColor.GREEN + "Total wealth: $4,500.00");
    }

    @Test
    @DisplayName("/money under language: zh is Chinese, and differs from en line by line")
    void moneyInChineseDiffersFromEnglish() {
        List<String> zh = money("zh");
        assertThat(zh).containsExactly(
                ChatColor.GOLD + "=== 经济系统 ===",
                ChatColor.YELLOW + "你的余额: $1,500.00",
                ChatColor.YELLOW + "你的银行存款: $3,000.00",
                ChatColor.GREEN + "总资产: $4,500.00");
        List<String> en = money("en");
        for (int i = 0; i < zh.size(); i++) {
            assertThat(en.get(i)).as("line " + i).isNotEqualTo(zh.get(i));
        }
    }

    @Test
    @DisplayName("/pay under language: en tells both players in English")
    void payInEnglish() {
        speak("en");
        Player alice = mock(Player.class);
        Player bob = mock(Player.class);
        UUID bobId = UUID.randomUUID();
        when(alice.getUniqueId()).thenReturn(PLAYER);
        when(alice.getName()).thenReturn("Alice");
        when(bob.getUniqueId()).thenReturn(bobId);
        when(bob.getName()).thenReturn("Bob");
        when(economy.transfer(PLAYER, bobId, 500.0)).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Bob")).thenReturn(bob);
            new PayCommand(plugin, economy).onPay(alice, "Bob", "500");
        }
        assertThat(said(alice)).containsExactly(ChatColor.GREEN + "Successfully transferred $500.00 to Bob");
        assertThat(said(bob)).containsExactly(ChatColor.GREEN + "Alice transferred $500.00 to you");
    }

    @Test
    @DisplayName("/eco check and /eco take under language: en are English, with the English word order")
    @SuppressWarnings("deprecation")
    void ecoAdminInEnglish() {
        speak("en");
        CommandSender admin = mock(CommandSender.class);
        OfflinePlayer steve = mock(OfflinePlayer.class);
        when(steve.getUniqueId()).thenReturn(PLAYER);
        when(steve.getName()).thenReturn("Steve");
        when(steve.hasPlayedBefore()).thenReturn(true);
        when(economy.getCash(PLAYER)).thenReturn(1500.0);
        when(economy.getBank(PLAYER)).thenReturn(3000.0);
        when(economy.getTotalWealth(PLAYER)).thenReturn(4500.0);
        when(economy.takeCash(PLAYER, 500.0)).thenReturn(true);
        EcoAdminCommand eco = EcoAdminCommand.createForTest(plugin, economy);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(steve);
            eco.onCheck(admin, "Steve");
            eco.onTake(admin, "Steve", "500");
        }
        assertThat(said(admin)).containsExactly(
                ChatColor.GOLD + "=== Steve ===",
                ChatColor.YELLOW + "Steve's balance: $1,500.00",
                ChatColor.YELLOW + "Steve's bank balance: $3,000.00",
                ChatColor.GREEN + "Total wealth: $4,500.00",
                ChatColor.GREEN + "Took $500.00 from Steve");
    }

    @Test
    @DisplayName("/bank, /deposit, /withdraw, /pay, /money and /note help under language: en are English")
    void helpInEnglish() throws Exception {
        speak("en");
        CommandSender sender = mock(CommandSender.class);
        help(BankCommand.createForTest(plugin, economy, mock(EconomyConfig.class), mock(CurrencyManager.class)), sender);
        assertThat(said(sender)).containsExactly(
                ChatColor.GOLD + "=== UltiEconomy Bank ===",
                ChatColor.YELLOW + "/bank" + ChatColor.GRAY + " - View bank balance",
                ChatColor.YELLOW + "/bank <currency>" + ChatColor.GRAY + " - View bank balance (specific currency)");
    }

    @Test
    @DisplayName("help headers under language: zh are Chinese")
    void helpHeadersInChinese() throws Exception {
        speak("zh");
        List<Object> commands = Arrays.<Object>asList(
                BankCommand.createForTest(plugin, economy, mock(EconomyConfig.class), mock(CurrencyManager.class)),
                new DepositCommand(plugin, economy, mock(EconomyConfig.class)),
                new WithdrawCommand(plugin, economy, mock(EconomyConfig.class)),
                new PayCommand(plugin, economy),
                NoteCommand.createForTest(plugin, economy, null),
                EcoAdminCommand.createForTest(plugin, economy));
        for (Object command : commands) {
            CommandSender sender = mock(CommandSender.class);
            help(command, sender);
            assertThat(said(sender).get(0)).as(command.getClass().getSimpleName() + " header")
                    .matches(".*\\p{IsHan}.*");
        }
    }

    private static void help(Object command, CommandSender sender) throws Exception {
        Method m = command.getClass().getDeclaredMethod("handleHelp", CommandSender.class);
        m.setAccessible(true); // NOPMD - handleHelp is the framework's protected help hook
        m.invoke(command, sender);
    }
}
