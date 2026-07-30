package dev.huskuraft.effortless.screen.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.TestPlatformSupport;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.text.Text;

class EffortlessScreenFocusTest {

    @BeforeAll
    static void installContentFactory() throws ReflectiveOperationException {
        TestPlatformSupport.installPlainContentFactory();
    }

    @Test
    void clickedChildReceivesTypedCharacters() {
        var screen = new TestScreen();
        screen.init(100, 100);
        var input = screen.addWidget(new CharacterInput());

        assertTrue(screen.onMouseClicked(5, 5, 0));
        assertTrue(screen.onCharTyped('x', 0));
        assertTrue(input.typed);
    }

    private static final class TestScreen extends EffortlessScreen {

        private TestScreen() {
            super(null, Text.empty());
        }
    }

    private static final class CharacterInput extends AbstractWidget {

        private boolean typed;

        private CharacterInput() {
            super(null, 0, 0, 20, 20, Text.empty());
            setFocusable(true);
        }

        @Override
        public boolean onCharTyped(char character, int modifiers) {
            typed = true;
            return true;
        }
    }
}
