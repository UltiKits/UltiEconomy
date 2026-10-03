package com.ultikits.plugins.economy.listener;

import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NoteRedeemListener")
@ExtendWith(MockitoExtension.class)
class NoteRedeemListenerTest {

    // The module itself, so a redeem can ask it which currencies currencies.yml defines (UltiKits/UltiEconomy#37).
    @Mock private com.ultikits.plugins.economy.UltiEconomy plugin;
    @Mock private EconomyService economyService;
    @Mock private MoneyNoteFactory noteFactory;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private ItemStack heldItem;

    private NoteRedeemListener listener;
    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        lenient().when(player.getInventory()).thenReturn(inventory);
        lenient().when(economyService.getPrimaryCurrencyId()).thenReturn("coins");
        lenient().when(plugin.getCurrencyManager()).thenReturn(new com.ultikits.plugins.economy.service.CurrencyManager(
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.StringReader(
                        "currencies:\n  coins:\n    primary: true\n  gems:\n    bank-enabled: true\n"))));
        listener = NoteRedeemListener.createForTest(plugin, economyService, noteFactory);
    }

    @Test
    @DisplayName("redeems note on right-click air")
    void redeemsOnRightClickAir() {
        when(inventory.getItemInMainHand()).thenReturn(heldItem);
        when(noteFactory.isMoneyNote(heldItem)).thenReturn(true);
        when(noteFactory.getNoteValue(heldItem)).thenReturn(500.0);
        when(noteFactory.getNoteCurrency(heldItem)).thenReturn("coins");
        when(economyService.addCash(PLAYER_UUID, 500.0)).thenReturn(true);
        when(heldItem.getAmount()).thenReturn(1);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, heldItem, null, null);
        listener.onInteract(event);

        verify(economyService).addCash(PLAYER_UUID, 500.0);
        verify(inventory).setItemInMainHand(null);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("redeems note on right-click block")
    void redeemsOnRightClickBlock() {
        when(inventory.getItemInMainHand()).thenReturn(heldItem);
        when(noteFactory.isMoneyNote(heldItem)).thenReturn(true);
        when(noteFactory.getNoteValue(heldItem)).thenReturn(250.0);
        when(noteFactory.getNoteCurrency(heldItem)).thenReturn("gems");
        when(economyService.addCash(PLAYER_UUID, 250.0, "gems")).thenReturn(true);
        when(heldItem.getAmount()).thenReturn(1);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, heldItem, null, null);
        listener.onInteract(event);

        verify(economyService).addCash(PLAYER_UUID, 250.0, "gems");
        assertThat(event.isCancelled()).isTrue();
    }

    /**
     * UltiKits/UltiEconomy#37, maintainer decision 2026-10-04: a note whose currency an operator removed
     * from currencies.yml is refused -- the note is kept, the player is told the currency no longer
     * exists, and an operator-facing line names the currency. Before, a player who still had a wallet
     * row for it redeemed the note into that hidden wallet.
     */
    @Test
    @DisplayName("a note of a currency no longer in currencies.yml is refused: kept, the player told, the currency logged (UltiEconomy#37)")
    void refusesANoteOfARemovedCurrency() {
        com.ultikits.ultitools.interfaces.impl.logger.PluginLogger logger =
                mock(com.ultikits.ultitools.interfaces.impl.logger.PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        lenient().when(player.getName()).thenReturn("Steve");
        when(inventory.getItemInMainHand()).thenReturn(heldItem);
        when(noteFactory.isMoneyNote(heldItem)).thenReturn(true);
        lenient().when(noteFactory.getNoteValue(heldItem)).thenReturn(250.0);
        when(noteFactory.getNoteCurrency(heldItem)).thenReturn("rubies");
        lenient().when(economyService.addCash(any(), anyDouble(), anyString())).thenReturn(true);
        lenient().when(heldItem.getAmount()).thenReturn(1);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, heldItem, null, null);
        listener.onInteract(event);

        verify(economyService, never()).addCash(any(), anyDouble(), anyString());
        verify(economyService, never()).addCash(any(), anyDouble());
        verify(inventory, never()).setItemInMainHand(any());
        verify(heldItem, never()).setAmount(anyInt());
        verify(player).sendMessage(contains("rubies"));
        verify(logger).warn(contains("rubies"));
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("ignores left-click actions")
    void ignoresLeftClick() {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, heldItem, null, null);
        listener.onInteract(event);

        verify(economyService, never()).addCash(any(), anyDouble());
        verify(noteFactory, never()).isMoneyNote(any());
    }

    @Test
    @DisplayName("ignores non-note items")
    void ignoresNonNote() {
        when(inventory.getItemInMainHand()).thenReturn(heldItem);
        when(noteFactory.isMoneyNote(heldItem)).thenReturn(false);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, heldItem, null, null);
        listener.onInteract(event);

        verify(economyService, never()).addCash(any(), anyDouble());
        verify(inventory, never()).setItemInMainHand(any());
    }

    @Test
    @DisplayName("decrements stack when holding multiple notes")
    void decrementsStack() {
        when(inventory.getItemInMainHand()).thenReturn(heldItem);
        when(noteFactory.isMoneyNote(heldItem)).thenReturn(true);
        when(noteFactory.getNoteValue(heldItem)).thenReturn(100.0);
        when(noteFactory.getNoteCurrency(heldItem)).thenReturn("coins");
        when(economyService.addCash(PLAYER_UUID, 100.0)).thenReturn(true);
        when(heldItem.getAmount()).thenReturn(5);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, heldItem, null, null);
        listener.onInteract(event);

        verify(heldItem).setAmount(4);
        verify(inventory, never()).setItemInMainHand(null);
    }
}
