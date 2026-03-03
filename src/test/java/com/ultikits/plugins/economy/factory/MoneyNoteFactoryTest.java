package com.ultikits.plugins.economy.factory;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("MoneyNoteFactory")
@ExtendWith(MockitoExtension.class)
class MoneyNoteFactoryTest {

    @Mock private Plugin plugin;
    @Mock private ItemStack itemStack;
    @Mock private ItemMeta itemMeta;
    @Mock private PersistentDataContainer pdc;

    private MoneyNoteFactory factory;
    private Map<NamespacedKey, Object> pdcStore;

    private static final UUID CREATOR_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getName()).thenReturn("UltiTools");
        factory = new MoneyNoteFactory(plugin);

        pdcStore = new HashMap<>();
        lenient().doAnswer(inv -> {
            pdcStore.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(pdc).set(any(NamespacedKey.class), any(), any());

        lenient().when(pdc.get(any(NamespacedKey.class), any()))
                .thenAnswer(inv -> pdcStore.get(inv.getArgument(0)));
        lenient().when(pdc.has(any(NamespacedKey.class), any()))
                .thenAnswer(inv -> pdcStore.containsKey(inv.getArgument(0)));

        lenient().when(itemMeta.getPersistentDataContainer()).thenReturn(pdc);
        lenient().when(itemStack.getItemMeta()).thenReturn(itemMeta);
    }

    @Nested
    @DisplayName("applyNoteData")
    class ApplyNoteDataTests {

        @Test
        @DisplayName("stores currency, value, creator, and timestamp in PDC")
        void storesDataInPdc() {
            factory.applyNoteData(itemMeta, "coins", 500.0, CREATOR_UUID, "TestPlayer");

            NamespacedKey currencyKey = new NamespacedKey(plugin, "note_currency");
            NamespacedKey valueKey = new NamespacedKey(plugin, "note_value");
            NamespacedKey creatorKey = new NamespacedKey(plugin, "note_creator");
            NamespacedKey createdAtKey = new NamespacedKey(plugin, "note_created_at");

            assertThat(pdcStore.get(currencyKey)).isEqualTo("coins");
            assertThat(pdcStore.get(valueKey)).isEqualTo(500.0);
            assertThat(pdcStore.get(creatorKey)).isEqualTo(CREATOR_UUID.toString());
            assertThat(pdcStore.get(createdAtKey)).isInstanceOf(Long.class);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("sets display name with amount")
        void setsDisplayName() {
            factory.applyNoteData(itemMeta, "coins", 500.0, CREATOR_UUID, "TestPlayer");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(itemMeta).setDisplayName(captor.capture());
            assertThat(captor.getValue()).contains("500");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("sets lore with currency and value info")
        void setsLore() {
            factory.applyNoteData(itemMeta, "gems", 1000.0, CREATOR_UUID, "TestPlayer");

            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(itemMeta).setLore(captor.capture());
            List<String> lore = captor.getValue();
            assertThat(lore).anyMatch(s -> s.contains("1000"));
            assertThat(lore).anyMatch(s -> s.contains("TestPlayer"));
        }
    }

    @Nested
    @DisplayName("isMoneyNote")
    class IsMoneyNoteTests {

        @Test
        @DisplayName("returns true for valid money note")
        void validNote() {
            when(itemStack.getType()).thenReturn(Material.PAPER);
            pdcStore.put(new NamespacedKey(plugin, "note_value"), 500.0);

            assertThat(factory.isMoneyNote(itemStack)).isTrue();
        }

        @Test
        @DisplayName("returns false for null item")
        void nullItem() {
            assertThat(factory.isMoneyNote(null)).isFalse();
        }

        @Test
        @DisplayName("returns false for non-paper item")
        void nonPaper() {
            when(itemStack.getType()).thenReturn(Material.DIAMOND);
            assertThat(factory.isMoneyNote(itemStack)).isFalse();
        }

        @Test
        @DisplayName("returns false for paper without PDC data")
        void plainPaper() {
            when(itemStack.getType()).thenReturn(Material.PAPER);
            assertThat(factory.isMoneyNote(itemStack)).isFalse();
        }
    }

    @Nested
    @DisplayName("Value and currency extraction")
    class ExtractionTests {

        @Test
        @DisplayName("getNoteValue returns stored value")
        void getsValue() {
            pdcStore.put(new NamespacedKey(plugin, "note_value"), 750.50);
            assertThat(factory.getNoteValue(itemStack)).isEqualTo(750.50);
        }

        @Test
        @DisplayName("getNoteCurrency returns stored currency")
        void getsCurrency() {
            pdcStore.put(new NamespacedKey(plugin, "note_currency"), "gems");
            assertThat(factory.getNoteCurrency(itemStack)).isEqualTo("gems");
        }

        @Test
        @DisplayName("getNoteValue returns 0 when no value stored")
        void noValue() {
            assertThat(factory.getNoteValue(itemStack)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getNoteCurrency returns empty string when no currency stored")
        void noCurrency() {
            assertThat(factory.getNoteCurrency(itemStack)).isEmpty();
        }
    }
}
