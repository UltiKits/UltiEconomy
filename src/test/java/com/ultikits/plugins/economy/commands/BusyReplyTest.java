package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.entity.TreasuryEntity;
import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.service.EconomyServiceImpl;
import com.ultikits.plugins.economy.service.EconomyTestWorld;
import com.ultikits.plugins.economy.service.TaxService;
import com.ultikits.plugins.economy.testsupport.InMemoryDataOperator;
import com.ultikits.plugins.economy.vault.VaultEconomyProvider;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * UltiKits/UltiEconomy#41 follow-up (gate-1 top-up F-E1, maintainer decision 2026-10-04): when a balance
 * change gives up because another server kept changing the row on every attempt, the caller replies
 * that the balance is busy and to try again -- not "insufficient funds", which would be false (the
 * balance was enough; nothing was written). Every caller is driven through a real service whose writes
 * always meet a row another server has just changed.
 */
@DisplayName("UltiEconomy#41: a change that lost to another server on every attempt replies 'busy, try again', not 'insufficient'")
class BusyReplyTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    /** The part of the busy reply every caller shows. */
    private static final String BUSY = "please try again";

    private EconomyTestWorld world;
    private EconomyServiceImpl contended;
    private UltiEconomy plugin;
    private Player steve;
    private CommandSender console;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        world = EconomyTestWorld.relational();
        world.seedAccount(STEVE, "Steve", 1000.0, 1000.0);
        world.seedAccount(ALEX, "Alex", 0.0, 0.0);
        contended = world.contendedService();
        plugin = mock(UltiEconomy.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        lenient().when(plugin.getCurrencyManager()).thenReturn(world.currencies);
        lenient().when(plugin.getLogger()).thenReturn(world.logger);
        steve = mock(Player.class);
        lenient().when(steve.getUniqueId()).thenReturn(STEVE);
        lenient().when(steve.getName()).thenReturn("Steve");
        lenient().when(steve.getInventory()).thenReturn(mock(PlayerInventory.class));
        Player alex = mock(Player.class);
        lenient().when(alex.getUniqueId()).thenReturn(ALEX);
        lenient().when(alex.getName()).thenReturn("Alex");
        console = mock(CommandSender.class);
        OfflinePlayer offlineSteve = mock(OfflinePlayer.class);
        lenient().when(offlineSteve.getUniqueId()).thenReturn(STEVE);
        lenient().when(offlineSteve.getName()).thenReturn("Steve");
        lenient().when(offlineSteve.hasPlayedBefore()).thenReturn(true);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
        bukkit.when(() -> Bukkit.getPlayer("Alex")).thenReturn(alex);
        bukkit.when(() -> Bukkit.getPlayer(STEVE)).thenReturn(steve);
        bukkit.when(() -> Bukkit.getPlayer(ALEX)).thenReturn(alex);
        bukkit.when(() -> Bukkit.getOfflinePlayer("Steve")).thenReturn(offlineSteve);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private String lastLine(CommandSender to) {
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(to, atLeastOnce()).sendMessage(line.capture());
        List<String> all = line.getAllValues();
        return all.get(all.size() - 1);
    }

    @Test
    @DisplayName("/deposit")
    void deposit() {
        new DepositCommand(plugin, contended, world.config).onDeposit(steve, "200");
        assertThat(lastLine(steve)).contains(BUSY).doesNotContainIgnoringCase("insufficient");
    }

    @Test
    @DisplayName("/withdraw")
    void withdraw() {
        new WithdrawCommand(plugin, contended, world.config).onWithdraw(steve, "200");
        assertThat(lastLine(steve)).contains(BUSY).doesNotContainIgnoringCase("insufficient");
    }

    @Test
    @DisplayName("/pay")
    void pay() {
        new PayCommand(plugin, contended).onPay(steve, "Alex", "100");
        assertThat(lastLine(steve)).contains(BUSY).doesNotContainIgnoringCase("insufficient");
    }

    @Test
    @DisplayName("/eco take")
    void ecoTake() {
        EcoAdminCommand.createForTest(plugin, contended).onTake(console, "Steve", "100");
        assertThat(lastLine(console)).contains(BUSY).doesNotContainIgnoringCase("insufficient");
    }

    @Test
    @DisplayName("/eco give")
    void ecoGive() {
        EcoAdminCommand.createForTest(plugin, contended).onGive(console, "Steve", "100");
        assertThat(lastLine(console)).contains(BUSY);
    }

    @Test
    @DisplayName("/eco treasury withdraw")
    void treasuryWithdraw() {
        InMemoryDataOperator<TreasuryEntity> shared =
                InMemoryDataOperator.relational("economy_treasury", TreasuryEntity.class, world.crash);
        shared.seed(TreasuryEntity.builder().currencyId("coins").balance(500.0).build());
        TaxService treasury = new TaxService(world.config, EconomyTestWorld.contended("economy_treasury", shared));
        EcoAdminCommand.createForTest(plugin, contended, treasury, world.currencies).onTreasuryWithdraw(console, "100");
        assertThat(lastLine(console)).contains(BUSY).doesNotContainIgnoringCase("insufficient");
    }

    @Test
    @DisplayName("/note <amount>")
    void noteCreate() {
        NoteCommand.createForTest(plugin, contended, mock(MoneyNoteFactory.class), world.currencies).onCreateNote(steve, "100");
        assertThat(lastLine(steve)).contains(BUSY).doesNotContainIgnoringCase("insufficient");
    }

    @Test
    @DisplayName("Vault withdrawPlayer: FAILURE with the busy message")
    void vaultWithdraw() {
        OfflinePlayer offline = Bukkit.getOfflinePlayer("Steve");
        EconomyResponse response = new VaultEconomyProvider(contended, world.config, plugin).withdrawPlayer(offline, 100.0);
        assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.FAILURE);
        assertThat(response.errorMessage).contains(BUSY).doesNotContainIgnoringCase("insufficient");
    }

    @Test
    @DisplayName("Vault depositPlayer: FAILURE with the busy message")
    void vaultDeposit() {
        OfflinePlayer offline = Bukkit.getOfflinePlayer("Steve");
        EconomyResponse response = new VaultEconomyProvider(contended, world.config, plugin).depositPlayer(offline, 100.0);
        assertThat(response.type).isEqualTo(EconomyResponse.ResponseType.FAILURE);
        assertThat(response.errorMessage).contains(BUSY);
    }
}
