package dev.huskuraft.effortless.screen.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.TestPlatformSupport;
import dev.huskuraft.universal.api.gui.Typeface;
import dev.huskuraft.universal.api.platform.Client;
import dev.huskuraft.universal.api.platform.ClientEntrance;
import dev.huskuraft.universal.api.text.Text;

class ReliableEditBoxTest {

    @BeforeAll
    static void installContentFactory() throws ReflectiveOperationException {
        TestPlatformSupport.installPlainContentFactory();
    }

    @Test
    void typedCharactersReachTheWorkbenchDraftListener() {
        var input = new ReliableEditBox(
                entrance(), 0, 0, 100, 20, Text.empty()
        );
        input.setValue("Name");
        var observed = new AtomicReference<String>();
        input.setChangeListener(observed::set);
        input.setFocused(true);

        assertTrue(input.onCharTyped('X', 0));

        assertEquals(input.getValue(), observed.get());
        assertEquals("NameX", observed.get());
    }

    @Test
    void explicitCommitCapturesTextBeforeAButtonClosesTheScreen() {
        var input = new ReliableEditBox(
                entrance(), 0, 0, 100, 20, Text.empty()
        );
        input.setValue("Old");
        var observed = new AtomicReference<String>();
        input.setChangeListener(observed::set);

        input.setValue("Visible");
        input.commitVisibleValue();

        assertEquals("Visible", observed.get());
    }

    private static ClientEntrance entrance() {
        var typeface = new Typeface() {
            @Override
            public int measureHeight(Text text) {
                return 9;
            }

            @Override
            public int measureWidth(Text text) {
                return measureWidth(text.getString());
            }

            @Override
            public int measureHeight(String text) {
                return 9;
            }

            @Override
            public int measureWidth(String text) {
                return text.length();
            }

            @Override
            public int getLineHeight() {
                return 9;
            }

            @Override
            public String subtractByWidth(
                    String text,
                    int width,
                    boolean tail
            ) {
                int length = Math.min(text.length(), Math.max(0, width));
                return tail
                        ? text.substring(text.length() - length)
                        : text.substring(0, length);
            }

            @Override
            public Object refs() {
                return this;
            }
        };
        var client = (Client) Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class<?>[] {Client.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getTypeface")) {
                        return typeface;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        return (ClientEntrance) Proxy.newProxyInstance(
                ClientEntrance.class.getClassLoader(),
                new Class<?>[] {ClientEntrance.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getClient")) {
                        return client;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }
}
