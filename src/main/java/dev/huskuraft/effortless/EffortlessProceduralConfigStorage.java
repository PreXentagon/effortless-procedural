package dev.huskuraft.effortless;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternConfigSerializer;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.universal.api.file.ConfigFileStorage;
import dev.huskuraft.universal.api.file.FileType;

/**
 * Separate client-local file. It is never constructed by the common/server
 * entrance and is never referenced by a network serializer.
 */
public final class EffortlessProceduralConfigStorage
        extends ConfigFileStorage<ProceduralPatternLibrary> {

    public static final String CONFIG_NAME = "effortless-procedural-client.toml";
    public static final String EXPORT_NAME = "effortless-procedural-export.toml";
    public static final long MAX_IMPORT_BYTES = 8L * 1024L * 1024L;

    public EffortlessProceduralConfigStorage() {
        super(CONFIG_NAME, FileType.TOML, new ProceduralPatternConfigSerializer());
    }

    public Path exportLibrary(ProceduralPatternLibrary library) throws IOException {
        var path = getDir().toPath().resolve(EXPORT_NAME);
        write(path.toFile(), library);
        return path;
    }

    public ImportedLibrary importLibrary() throws IOException {
        var path = getDir().toPath().resolve(EXPORT_NAME);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Import file does not exist: " + path);
        }
        long size = Files.size(path);
        if (size > MAX_IMPORT_BYTES) {
            throw new IOException(
                    "Import file is " + size + " bytes; maximum is "
                            + MAX_IMPORT_BYTES
            );
        }
        return new ImportedLibrary(path, read(path.toFile()));
    }

    public record ImportedLibrary(
            Path path,
            ProceduralPatternLibrary library
    ) {
    }
}
