package dev.wxdao.ccsmartcard.card;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.WritableMount;
import dev.wxdao.ccsmartcard.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public final class CardStorage {
    private static final String ID_DIR = "cc_smartcard/cards";

    private CardStorage() {
    }

    public static int allocateCardId(MinecraftServer server) {
        return ComputerCraftAPI.createUniqueNumberedSaveDir(server, ID_DIR);
    }

    public static WritableMount mount(MinecraftServer server, int cardId) {
        return ComputerCraftAPI.createSaveDirMount(server, ID_DIR + "/" + cardId, ModConfig.smartCardSpaceLimit());
    }

    public static void clear(MinecraftServer server, int cardId) throws IOException {
        Path dir = saveRoot(server).resolve(Integer.toString(cardId));
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public static void writeFiles(MinecraftServer server, int cardId, Map<String, String> files) throws IOException {
        WritableMount mount = mount(server, cardId);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = toMountPath(entry.getKey());
            createParents(mount, path);
            try (SeekableByteChannel channel = mount.openFile(path, Set.of(
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE));
                 var writer = Channels.newWriter(channel, StandardCharsets.UTF_8)) {
                writer.write(entry.getValue());
            }
        }
    }

    public static String readFile(MinecraftServer server, int cardId, String path) throws IOException {
        WritableMount mount = mount(server, cardId);
        try (SeekableByteChannel channel = mount.openForRead(toMountPath(path));
             var reader = Channels.newReader(channel, StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }

    public static boolean exists(MinecraftServer server, int cardId, String path) throws IOException {
        return mount(server, cardId).exists(toMountPath(path));
    }

    private static void createParents(WritableMount mount, String path) throws IOException {
        int slash = path.lastIndexOf('/');
        if (slash <= 0) {
            return;
        }
        String[] parts = path.substring(0, slash).split("/");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!current.isEmpty()) {
                current.append('/');
            }
            current.append(part);
            String dir = current.toString();
            if (!mount.exists(dir)) {
                mount.makeDirectory(dir);
            }
        }
    }

    private static String toMountPath(String absolutePath) {
        String path = absolutePath.startsWith("/") ? absolutePath.substring(1) : absolutePath;
        if (path.contains("..")) {
            throw new IllegalArgumentException("path cannot contain ..");
        }
        return path;
    }

    private static Path saveRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("computercraft").resolve(ID_DIR);
    }
}
