package dev.lynxplay.paper.beb;

import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class BlockEntityBinRegionUpgrader implements Runnable {

    private static final Logger LOGGER = LogManager.getLogger();

    private final @NotNull ServerLevel level;
    private final @NotNull IntIntPair regionPosition;
    private final @NotNull Runnable chunkCompletionCallback;
    private final @NotNull DataFixer dataFixer;
    private final @NotNull ChunkStorage chunkStorage;

    public BlockEntityBinRegionUpgrader(@NotNull ServerLevel level,
                                        @NotNull IntIntPair regionPosition,
                                        @NotNull Runnable chunkCompletionCallback,
                                        @NotNull DataFixer dataFixer,
                                        @NotNull ChunkStorage chunkStorage) {
        this.level = level;
        this.regionPosition = regionPosition;
        this.chunkCompletionCallback = chunkCompletionCallback;
        this.dataFixer = dataFixer;
        this.chunkStorage = chunkStorage;
    }

    @Override
    public void run() {
        final int lowChunkX = this.regionPosition.firstInt() << 5;
        final int lowChunkZ = this.regionPosition.secondInt() << 5;

        for (int chunkX = lowChunkX; chunkX < lowChunkX + 32; chunkX++) {
            for (int chunkZ = lowChunkZ; chunkZ < lowChunkZ + 32; chunkZ++) {
                final ChunkPos position = new ChunkPos(chunkX, chunkZ);

                try {
                    final CompoundTag chunkData = this.chunkStorage.read(position).join().orElse(null);
                    if (chunkData == null) continue;

                    if (!processChunk(chunkData, position)) continue;

                    this.chunkStorage.write(position, chunkData);
                } catch (final Exception exception) {
                    LOGGER.error("Failed to process chunk {}: {}", position.x + "/" + position.z, exception);
                } finally {
                    this.chunkCompletionCallback.run();
                }
            }
        }
    }

    private boolean processChunk(@NotNull CompoundTag chunkData,
                                 @NotNull final ChunkPos position) {
        final ListTag sectionsListTag = chunkData.getList("sections", 10);
        final LevelChunkSection[] sections = BlockEntityBinSectionDeserialiser.parseSections(
            this.level, position, sectionsListTag
        );

        if (!chunkData.contains("block_entities", ListTag.TAG_LIST)) return false;

        final ListTag blockEntities = chunkData.getList("block_entities", CompoundTag.TAG_COMPOUND);

        for (int blockEntityIdx = 0; blockEntityIdx < blockEntities.size(); blockEntityIdx++) {
            final CompoundTag blockEntityNBT = blockEntities.getCompound(blockEntityIdx);
            final BlockPos blockPos = BlockEntity.getPosFromTag(blockEntityNBT);

            final BlockState blockState = sections[this.level.getSectionIndex(blockPos.getY())].getBlockState(
                blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15
            );

            final ResourceLocation blockEntityId = ResourceLocation.tryParse(blockEntityNBT.getString("id"));
            if (blockEntityId == null) continue; // Presumably the DUMMY block entity

            BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, blockState, blockEntityNBT);
            if (blockEntity == null || !blockEntity.getType().isValid(blockEntity.getBlockState())) {
                LOGGER.info("Found invalid block entity {}, removing", blockEntityNBT.toString());
                blockEntityNBT.put("__beb_remove", IntTag.valueOf(0));
                continue;
            }
        }

        return blockEntities.removeIf(t -> t instanceof CompoundTag compoundTag && compoundTag.contains("__beb_remove"));
    }

}
