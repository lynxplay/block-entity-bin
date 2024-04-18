package dev.lynxplay.paper.beb;

import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.dimension.LevelStem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The world upgrader is responsible for iterating over each region file / chunk and removing respective dead
 * block entities.
 */
public class BlockEntityBinWorldUpgrader {

    private static final Logger LOGGER = LogManager.getLogger();

    private final @NotNull ServerLevel level;
    private final @NotNull ResourceKey<LevelStem> dimensionType;
    private final @NotNull Path worldDirectory;
    private final @NotNull DataFixer dataFixer;
    private final ExecutorService threadPool;

    public BlockEntityBinWorldUpgrader(
        @NotNull ServerLevel level,
        @NotNull ResourceKey<LevelStem> dimensionType,
        @NotNull Path worldDirectory,
        @NotNull DataFixer dataFixer,
        int threads
    ) {
        this.level = level;
        this.dimensionType = dimensionType;
        this.worldDirectory = worldDirectory;
        this.dataFixer = dataFixer;
        this.threadPool = Executors.newFixedThreadPool(threads, r -> {
            final Thread thread = new Thread(r);
            thread.setName("BlockEntityBinWorkerThread");
            thread.setUncaughtExceptionHandler((t, ex) -> LOGGER.fatal("failed to discard faulty block entities", ex));
            return thread;
        });
    }

    public void run() {
        final Path regionFilesFolder = worldDirectory.resolve("region");
        final Predicate<String> regionFileNamePredicate = WorldUpgrader.REGEX.asPredicate();
        try (
            final ChunkStorage chunkStorage = new ChunkStorage(regionFilesFolder, this.dataFixer, false);
            final Stream<Path> regionFileWalker = Files.walk(regionFilesFolder, 1)
                .filter(p -> regionFileNamePredicate.test(p.getFileName().toString()));
        ) {
            final List<Path> regionFiles = regionFileWalker.toList();
            LOGGER.info("Inspecting {} region files for world {}", regionFiles.size(), worldDirectory.toString());

            final AtomicLong chunksProcessed = new AtomicLong();
            long expectedChunks = 0;
            for (final Path regionFile : regionFiles) {
                final ChunkPos regionChunkPosition = RegionFileStorage.getRegionFileCoordinates(regionFile);
                if (regionChunkPosition == null) {
                    continue;
                }

                expectedChunks += (long) Math.pow(32, 2);

                this.threadPool.execute(new BlockEntityBinRegionUpgrader(
                    this.level, IntIntPair.of(regionChunkPosition.x >> 5, regionChunkPosition.z >> 5),
                    chunksProcessed::incrementAndGet, this.dataFixer, chunkStorage
                ));
            }

            this.threadPool.shutdown(); // Drain thread pool

            final DecimalFormat format = new DecimalFormat("#0.00");

            while (!this.threadPool.isTerminated()) {
                final long current = chunksProcessed.get();

                LOGGER.info(
                    "{}% completed ({} / {} chunks)...",
                    format.format((double) current / (double) expectedChunks * 100.0),
                    current,
                    expectedChunks
                );

                try {
                    Thread.sleep(1000L);
                } catch (final InterruptedException ignore) {
                }
            }
        } catch (final IOException exception) {
            LOGGER.error("Failed to read region file folder", exception);
        }
    }

}
