package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.service.EconomyTestWorld;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEconomy#25 at the command surface: the primary currency's id typed on a command
 * reaches the account wallet (the one Vault and {@code /money} use), never a second primary-currency
 * wallet. Every server that ran 2.0.0 has such a leftover row for each player, so each test seeds one
 * and asserts it never moves.
 */
@DisplayName("UltiKits/UltiEconomy#25: commands naming the primary currency use the account wallet")
class PrimaryCurrencyCommandsTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private EconomyTestWorld world;
    private Player player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        world = EconomyTestWorld.relational();
        world.seedAccount(STEVE, "Steve", 700.0, 300.0);
        world.seedBalance(STEVE, "coins", 1000.0, 0.0);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        lenient().when(player.getUniqueId()).thenReturn(STEVE);
        lenient().when(player.getName()).thenReturn("Steve");
        lenient().when(player.getInventory()).thenReturn(inventory);
    }

    private void assertLeftoverUntouched() {
        assertThat(world.balanceRows("coins")).hasSize(1);
        assertThat(world.balanceRows("coins").get(0).getCash()).isEqualTo(1000.0);
        assertThat(world.balanceRows("coins").get(0).getBank()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("/money coins shows the account wallet, the same figures /money shows")
    void moneyByNameShowsTheAccountWallet() {
        MoneyCommand money = MoneyCommand.createForTest(world.plugin, world.service, world.currencies);

        money.onCurrencyBalance(player, "coins");

        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(lines.capture());
        List<String> shown = lines.getAllValues();
        assertThat(shown).anyMatch(l -> l.contains("Your balance: $700.00"));
        assertThat(shown).anyMatch(l -> l.contains("Your bank balance: $300.00"));
        assertThat(shown).anyMatch(l -> l.contains("Total wealth: $1,000.00"));
        assertThat(shown).noneMatch(l -> l.contains("1,000.00") && l.contains("Your balance"));
    }

    @Test
    @DisplayName("/note <n> coins then redeeming it conserves the account wallet and never touches a second wallet")
    void noteByNameRoundTripConservesMoney() {
        MoneyNoteFactory factory = mock(MoneyNoteFactory.class);
        ItemStack note = mock(ItemStack.class);
        when(factory.createNote(eq("coins"), anyDouble(), any(UUID.class), anyString())).thenReturn(note);
        NoteCommand command = NoteCommand.createForTest(world.plugin, world.service, factory, world.currencies);

        command.onCreateCurrencyNote(player, "100", "coins");

        assertThat(world.account(STEVE).getCash()).as("the note is paid from the account wallet").isEqualTo(600.0);
        assertLeftoverUntouched();

        when(inventory.getItemInMainHand()).thenReturn(note);
        when(factory.isMoneyNote(note)).thenReturn(true);
        when(factory.getNoteValue(note)).thenReturn(100.0);
        when(factory.getNoteCurrency(note)).thenReturn("coins");
        when(note.getAmount()).thenReturn(1);

        command.onRedeem(player);

        assertThat(world.account(STEVE).getCash()).as("redeeming credits the same wallet").isEqualTo(700.0);
        assertThat(world.account(STEVE).getBank()).isEqualTo(300.0);
        assertLeftoverUntouched();
    }

    @Test
    @DisplayName("/eco give|take|set <player> <n> coins and /eco check <player> coins act on the account wallet")
    void ecoByNameActsOnTheAccountWallet() {
        EcoAdminCommand eco = EcoAdminCommand.createForTest(world.plugin, world.service, world.currencies);
        CommandSender console = mock(CommandSender.class);
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(STEVE);
        when(target.getName()).thenReturn("Steve");
        lenient().when(target.hasPlayedBefore()).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(target);

            eco.onGiveCurrency(console, "Steve", "50", "coins");
            assertThat(world.account(STEVE).getCash()).isEqualTo(750.0);
            eco.onTakeCurrency(console, "Steve", "25", "coins");
            assertThat(world.account(STEVE).getCash()).isEqualTo(725.0);
            eco.onSetCurrency(console, "Steve", "400", "coins");
            assertThat(world.account(STEVE).getCash()).isEqualTo(400.0);
            eco.onCheckCurrency(console, "Steve", "coins");
        }

        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(console, atLeastOnce()).sendMessage(lines.capture());
        assertThat(lines.getAllValues()).anyMatch(l -> l.contains("Steve's balance: $400.00"));
        assertThat(world.service.getCash(STEVE)).as("what Vault reads").isEqualTo(400.0);
        assertLeftoverUntouched();
    }
}
