package com.ultikits.plugins.economy;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UltiEconomy Module")
class UltiEconomyTest {

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
        @DisplayName("has unregisterSelf method")
        void hasUnregisterSelf() throws NoSuchMethodException {
            Method method = UltiEconomy.class.getMethod("unregisterSelf");
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("has supported method returning list")
        void hasSupported() throws NoSuchMethodException {
            Method method = UltiEconomy.class.getMethod("supported");
            assertThat(method).isNotNull();
            assertThat(method.getReturnType()).isEqualTo(java.util.List.class);
        }
    }
}
