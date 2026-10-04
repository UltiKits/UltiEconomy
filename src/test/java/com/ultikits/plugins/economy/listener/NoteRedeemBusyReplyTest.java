package com.ultikits.plugins.economy.listener;

import com.ultikits.plugins.economy.UltiEconomy;
import com.ultikits.plugins.economy.factory.MoneyNoteFactory;
import com.ultikits.plugins.economy.i18n.CatalogueText;
import com.ultikits.plugins.economy.service.EconomyService;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEconomy#41 follow-up: redeeming a money note by right-click when the credit gave up
 * because another server kept changing the balance tells the player to try again and keeps the note.
 * Before, the redeem failed silently.
 */
@DisplayName("UltiEconomy#41: a right-click redeem that lost to another server says busy and keeps the note")
class NoteRedeemBusyReplyTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    @DisplayName("right-click redeem under contention: busy reply, note kept, event cancelled")
    void busyRedeemKeepsTheNote() {
        UltiEconomy plugin = mock(UltiEconomy.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        EconomyService service = mock(EconomyService.class);
        when(service.getPrimaryCurrencyId()).thenReturn("coins");
        when(service.addCash(STEVE, 100.0)).thenReturn(false);
        when(service.isLastChangeBusy()).thenReturn(true);
        MoneyNoteFactory notes = mock(MoneyNoteFactory.class);
        ItemStack held = mock(ItemStack.class);
        when(notes.isMoneyNote(held)).thenReturn(true);
        when(notes.getNoteValue(held)).thenReturn(100.0);
        when(notes.getNoteCurrency(held)).thenReturn("coins");
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(STEVE);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(held);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, held, null, null);
        NoteRedeemListener.createForTest(plugin, service, notes).onInteract(event);

        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("please try again"));
        verify(inventory, never()).setItemInMainHand(any());
        verify(held, never()).setAmount(anyInt());
        assertThat(event.isCancelled()).isTrue();
    }
}
