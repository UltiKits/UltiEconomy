package com.ultikits.plugins.economy;

import com.ultikits.plugins.economy.config.EconomyConfig;
import com.ultikits.plugins.economy.service.EconomyService;
import com.ultikits.plugins.economy.vault.VaultEconomyProvider;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("UltiEconomy Module")
class UltiEconomyTest {

    /**
     * Allocates a module instance without running any constructor -- {@link UltiToolsPlugin}'s
     * constructors need a running server.
     */
    private static UltiEconomy allocateModule() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (UltiEconomy) unsafe.allocateInstance(UltiEconomy.class);
    }

    private static void setVaultProvider(UltiEconomy module, VaultEconomyProvider provider) throws Exception {
        Field field = UltiEconomy.class.getDeclaredField("vaultProvider");
        field.setAccessible(true);
        field.set(module, provider);
    }

    @Nested
    @DisplayName("Annotations")
    class AnnotationTests {

        @Test
        @DisplayName("class has @UltiToolsModule annotation")
        void hasUltiToolsModuleAnnotation() {
            UltiToolsModule annotation = UltiEconomy.class.getAnnotation(UltiToolsModule.class);
            assertThat(annotation).isNotNull();
        }

        @Test
        @DisplayName("extends UltiToolsPlugin")
        void extendsUltiToolsPlugin() {
            assertThat(UltiToolsPlugin.class).isAssignableFrom(UltiEconomy.class);
        }
    }

    @Nested
    @DisplayName("Methods")
    class MethodTests {

        @Test
        @DisplayName("has registerSelf method")
        void hasRegisterSelf() throws NoSuchMethodException {
            Method method = UltiEconomy.class.getMethod("registerSelf");
            assertThat(method).isNotNull();
            assertThat(method.getReturnType()).isEqualTo(boolean.class);
        }

        @Test
        @DisplayName("declares protected onUnregister() and overrides neither framework template method (UltiKits/UltiEconomy#22)")
        void declaresOnUnregisterHookOnly() throws NoSuchMethodException {
            Method hook = UltiEconomy.class.getDeclaredMethod("onUnregister");
            assertThat(Modifier.isProtected(hook.getModifiers())).isTrue();
            assertThat(hook.getReturnType()).isEqualTo(void.class);

            for (Method declared : UltiEconomy.class.getDeclaredMethods()) {
                assertThat(declared.getName())
                        .as("UltiEconomy must not override a final framework template method")
                        .isNotIn("unregisterSelf", "reloadSelf");
            }
        }

        @Test
        @DisplayName("has supported method returning list")
        void hasSupported() throws NoSuchMethodException {
            Method method = UltiEconomy.class.getMethod("supported");
            assertThat(method).isNotNull();
            assertThat(method.getReturnType()).isEqualTo(java.util.List.class);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("supported() returns zh and en")
        void supportedLanguages() throws Exception {
            UltiEconomy instance = allocateModule();

            List<String> languages = instance.supported();
            assertThat(languages).containsExactly("zh", "en");
        }

        @Test
        @DisplayName("onUnregister() unregisters exactly this module's Vault provider for Economy.class, once (UltiKits/UltiEconomy#22)")
        void onUnregisterUnregistersVaultProvider() throws Exception {
            UltiEconomy module = allocateModule();
            VaultEconomyProvider provider = new VaultEconomyProvider(
                    mock(EconomyService.class), mock(EconomyConfig.class), mock(UltiToolsPlugin.class));
            setVaultProvider(module, provider);
            ServicesManager servicesManager = mock(ServicesManager.class);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getServicesManager).thenReturn(servicesManager);

                module.onUnregister();
            }

            // Assert on what the ServicesManager mock actually received -- the call under test is
            // not stubbed away, it is the observed interaction.
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Class<Economy>> serviceCaptor = ArgumentCaptor.forClass(Class.class);
            ArgumentCaptor<Object> providerCaptor = ArgumentCaptor.forClass(Object.class);
            verify(servicesManager, times(1)).unregister(serviceCaptor.capture(), providerCaptor.capture());
            assertThat(serviceCaptor.getValue()).isSameAs(Economy.class);
            assertThat(providerCaptor.getValue()).isSameAs(provider);
            verifyNoMoreInteractions(servicesManager);
        }

        @Test
        @DisplayName("onUnregister() with no Vault provider makes no services-manager call (UltiKits/UltiEconomy#22)")
        void onUnregisterWithNullProviderMakesNoCall() throws Exception {
            UltiEconomy module = allocateModule();
            setVaultProvider(module, null);
            ServicesManager servicesManager = mock(ServicesManager.class);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getServicesManager).thenReturn(servicesManager);

                module.onUnregister();

                bukkit.verify(Bukkit::getServicesManager, never());
            }

            verifyNoInteractions(servicesManager);
        }
    }
}
