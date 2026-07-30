package dev.huskuraft.effortless;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.core.ItemStack;
import dev.huskuraft.universal.api.core.ResourceLocation;
import dev.huskuraft.universal.api.core.StatType;
import dev.huskuraft.universal.api.core.StatTypes;
import dev.huskuraft.universal.api.core.fluid.Fluid;
import dev.huskuraft.universal.api.core.fluid.Fluids;
import dev.huskuraft.universal.api.platform.ContentFactory;
import dev.huskuraft.universal.api.platform.OperatingSystem;
import dev.huskuraft.universal.api.platform.PlatformLoader;
import dev.huskuraft.universal.api.sound.Sound;
import dev.huskuraft.universal.api.sound.Sounds;
import dev.huskuraft.universal.api.tag.InputStreamTagReader;
import dev.huskuraft.universal.api.tag.OutputStreamTagWriter;
import dev.huskuraft.universal.api.text.Style;
import dev.huskuraft.universal.api.text.Text;

public final class TestPlatformSupport {

    private TestPlatformSupport() {
    }

    public static void installPlainContentFactory()
            throws ReflectiveOperationException {
        Field instancesField = PlatformLoader.class.getDeclaredField("INSTANCES");
        instancesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var instances = (Map<Class<?>, Object>) instancesField.get(null);
        instances.put(ContentFactory.class, new PlainJvmContentFactory());
    }

    private static final class PlainJvmContentFactory implements ContentFactory {

        @Override
        public ResourceLocation newResourceLocation(
                String namespace,
                String path
        ) {
            return new PlainResourceLocation(namespace, path);
        }

        @Override
        public Optional<Item> newOptionalItem(ResourceLocation location) {
            return Optional.empty();
        }

        @Override
        public ItemStack newItemStack() {
            return null;
        }

        @Override
        public ItemStack newItemStack(Item item, int count) {
            return null;
        }

        @Override
        public Text newText() {
            return new PlainText("", Style.EMPTY, List.of());
        }

        @Override
        public Text newText(String text) {
            return new PlainText(text, Style.EMPTY, List.of());
        }

        @Override
        public Text newTranslatableText(String text) {
            return newText(text);
        }

        @Override
        public Text newTranslatableText(String text, Object... args) {
            return newText(text);
        }

        @Override
        public InputStreamTagReader getInputStreamTagReader() {
            return null;
        }

        @Override
        public OutputStreamTagWriter getOutputStreamTagWriter() {
            return null;
        }

        @Override
        public OperatingSystem getOperatingSystem() {
            return OperatingSystem.WINDOWS;
        }

        @Override
        public Sound getSound(Sounds sounds) {
            return null;
        }

        @Override
        public Fluid getFluid(Fluids fluids) {
            return null;
        }

        @Override
        public <T extends dev.huskuraft.universal.api.platform.PlatformReference>
        StatType<T> getStatType(StatTypes statTypes) {
            return null;
        }
    }

    private record PlainResourceLocation(String namespace, String path)
            implements ResourceLocation {

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Object refs() {
            return getString();
        }
    }

    private record PlainText(
            String string,
            Style style,
            Collection<Text> siblings
    ) implements Text {

        private PlainText {
            siblings = List.copyOf(siblings);
        }

        @Override
        public Style getStyle() {
            return style;
        }

        @Override
        public Text withStyle(Style value) {
            return new PlainText(string, value, siblings);
        }

        @Override
        public String getString() {
            return string;
        }

        @Override
        public Collection<Text> getSiblings() {
            return siblings;
        }

        @Override
        public Text withSiblings(Collection<Text> value) {
            return new PlainText(string, style, value);
        }

        @Override
        public Text copy() {
            return new PlainText(string, style, siblings);
        }

        @Override
        public void decompose(Sink sink) {
            sink.accept(0, string, style);
        }

        @Override
        public Object refs() {
            return string;
        }
    }
}
