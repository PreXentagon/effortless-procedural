package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.Effortless;
import dev.huskuraft.effortless.EffortlessClientNetworkChannel;
import dev.huskuraft.effortless.EffortlessNetworkChannel;
import dev.huskuraft.effortless.networking.packets.AllPacketListener;
import dev.huskuraft.effortless.networking.packets.player.PlayerBuildPacket;
import dev.huskuraft.effortless.networking.serializer.ContextSerializer;
import dev.huskuraft.effortless.networking.serializer.TransformerSerializer;

class ServerCompatibilityBoundaryTest {

    @Test
    void protocolVersionRemainsThirteen() {
        assertEquals(13, Effortless.PROTOCOL_VERSION);
    }

    @Test
    void serverBoundPacketAndSerializersDoNotReferenceProceduralTypes() throws IOException {
        for (var type : List.of(
                EffortlessClientNetworkChannel.class,
                EffortlessNetworkChannel.class,
                AllPacketListener.class,
                PlayerBuildPacket.class,
                ContextSerializer.class,
                TransformerSerializer.class
        )) {
            var bytecode = readClass(type);
            assertFalse(
                    bytecode.contains("client/pattern/procedural"),
                    type.getName() + " must not reference client procedural classes"
            );
            assertFalse(
                    bytecode.contains("ProceduralRule"),
                    type.getName() + " must not serialize procedural rules"
            );
        }
    }

    private static String readClass(Class<?> type) throws IOException {
        var path = "/" + type.getName().replace('.', '/') + ".class";
        try (var stream = type.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing class resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
