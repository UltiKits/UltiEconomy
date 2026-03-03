package com.ultikits.plugins.economy.commands;

import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NoteCommand")
@ExtendWith(MockitoExtension.class)
class NoteCommandTest {

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private MoneyNoteFactory noteFactory;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private ItemStack noteItem;
    @Mock private ItemStack heldItem;

    private NoteCommand command;
    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(player.getInventory()).thenReturn(inventory);
        lenient().when(economyService.getPrimaryCurrencyId()).thenReturn("coins");
        command = new NoteCommand(plugin, economyService, noteFactory);
    }

    @Nested
    @DisplayName("Create note")
    class CreateTests {

        @Test
        @DisplayName("creates note and deducts cash for primary currency")
        void createsNoteForPrimaryCurrency() {
            when(economyService.takeCash(PLAYER_UUID, 500.0)).thenReturn(true);
            when(noteFactory.createNote("coins", 500.0, PLAYER_UUID, "TestPlayer"))
                    .thenReturn(noteItem);

            command.onCreateNote(player, "500");

            verify(economyService).takeCash(PLAYER_UUID, 500.0);
            verify(inventory).addItem(noteItem);
        }

        @Test
        @DisplayName("creates note with specific currency")
        void createsNoteForSpecificCurrency() {
            when(economyService.takeCash(PLAYER_UUID, 1000.0, "gems")).thenReturn(true);
            when(noteFactory.createNote("gems", 1000.0, PLAYER_UUID, "TestPlayer"))
                    .thenReturn(noteItem);

            command.onCreateCurrencyNote(player, "1000", "gems");

            verify(economyService).takeCash(PLAYER_UUID, 1000.0, "gems");
            verify(inventory).addItem(noteItem);
        }

        @Test
        @DisplayName("rejects invalid amount")
        void rejectsInvalidAmount() {
            command.onCreateNote(player, "abc");

            verify(economyService, never()).takeCash(any(), anyDouble());
            verify(player).sendMessage(contains("无效的金额"));
        }

        @Test
        @DisplayName("rejects zero amount")
        void rejectsZeroAmount() {
            command.onCreateNote(player, "0");

            verify(economyService, never()).takeCash(any(), anyDouble());
            verify(player).sendMessage(contains("金额必须大于零"));
        }

        @Test
        @DisplayName("rejects insufficient balance")
        void rejectsInsufficientBalance() {
            when(economyService.takeCash(PLAYER_UUID, 500.0)).thenReturn(false);

            command.onCreateNote(player, "500");

            verify(inventory, never()).addItem(any(ItemStack.class));
            verify(player).sendMessage(contains("余额不足"));
        }
    }

    @Nested
    @DisplayName("Redeem note")
    class RedeemTests {

        @Test
        @DisplayName("redeems held note and adds cash")
        void redeemsNote() {
            when(inventory.getItemInMainHand()).thenReturn(heldItem);
            when(noteFactory.isMoneyNote(heldItem)).thenReturn(true);
            when(noteFactory.getNoteValue(heldItem)).thenReturn(750.0);
            when(noteFactory.getNoteCurrency(heldItem)).thenReturn("coins");
            when(economyService.addCash(PLAYER_UUID, 750.0)).thenReturn(true);
            when(heldItem.getAmount()).thenReturn(1);

            command.onRedeem(player);

            verify(economyService).addCash(PLAYER_UUID, 750.0);
            verify(inventory).setItemInMainHand(null);
        }

        @Test
        @DisplayName("redeems currency-specific note")
        void redeemsCurrencyNote() {
            when(inventory.getItemInMainHand()).thenReturn(heldItem);
            when(noteFactory.isMoneyNote(heldItem)).thenReturn(true);
            when(noteFactory.getNoteValue(heldItem)).thenReturn(500.0);
            when(noteFactory.getNoteCurrency(heldItem)).thenReturn("gems");
            when(economyService.addCash(PLAYER_UUID, 500.0, "gems")).thenReturn(true);
            when(heldItem.getAmount()).thenReturn(1);

            command.onRedeem(player);

            verify(economyService).addCash(PLAYER_UUID, 500.0, "gems");
        }

        @Test
        @DisplayName("rejects non-note item")
        void rejectsNonNote() {
            when(inventory.getItemInMainHand()).thenReturn(heldItem);
            when(noteFactory.isMoneyNote(heldItem)).thenReturn(false);

            command.onRedeem(player);

            verify(economyService, never()).addCash(any(), anyDouble());
            verify(player).sendMessage(contains("手中没有纸币"));
        }

        @Test
        @DisplayName("decrements stack when holding multiple notes")
        void decrementsStack() {
            when(inventory.getItemInMainHand()).thenReturn(heldItem);
            when(noteFactory.isMoneyNote(heldItem)).thenReturn(true);
            when(noteFactory.getNoteValue(heldItem)).thenReturn(100.0);
            when(noteFactory.getNoteCurrency(heldItem)).thenReturn("coins");
            when(economyService.addCash(PLAYER_UUID, 100.0)).thenReturn(true);
            when(heldItem.getAmount()).thenReturn(3);

            command.onRedeem(player);

            verify(heldItem).setAmount(2);
            verify(inventory, never()).setItemInMainHand(null);
        }
    }
}
