package com.ultikits.plugins.economy.listener;

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

    @Mock private UltiToolsPlugin plugin;
    @Mock private EconomyService economyService;
    @Mock private MoneyNoteFactory noteFactory;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private ItemStack heldItem;

    private NoteRedeemListener listener;
    private static final UUID PLAYER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        lenient().when(player.getInventory()).thenReturn(inventory);
        lenient().when(economyService.getPrimaryCurrencyId()).thenReturn("coins");
        listener = new NoteRedeemListener(plugin, economyService, noteFactory);
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
