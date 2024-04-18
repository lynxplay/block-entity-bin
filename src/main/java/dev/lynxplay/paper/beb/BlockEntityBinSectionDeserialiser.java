package dev.lynxplay.paper.beb;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class BlockEntityBinSectionDeserialiser {

    private static final Logger LOGGER = LogManager.getLogger();

    // See ChunkSerialiser#readInProgressChunkHolder
    public static LevelChunkSection[] parseSections(@NotNull ServerLevel serverLevel,
                                                    @NotNull ChunkPos chunkPos,
                                                    @NotNull ListTag sections) {
        final LevelChunkSection[] output = new LevelChunkSection[serverLevel.getSectionsCount()];
        for (int i = 0; i < sections.size(); i++) {
            final CompoundTag section = sections.getCompound(i);
            byte sectionYLevel = section.getByte("Y");
            int sectionIndex = serverLevel.getSectionIndexFromSectionY(sectionYLevel);

            if (sectionIndex < 0 || sectionIndex >= output.length) continue;
            final BlockState[] presetBlockStates = serverLevel.chunkPacketBlockController.getPresetBlockStates(serverLevel, chunkPos, sectionYLevel);

            if (!section.contains("block_states", 10)) {
                output[sectionIndex] = new LevelChunkSection(
                    new PalettedContainer<>(
                        Block.BLOCK_STATE_REGISTRY,
                        Blocks.AIR.defaultBlockState(),
                        PalettedContainer.Strategy.SECTION_STATES,
                        presetBlockStates
                    ),
                    null
                );
                continue;
            }

            final Codec<PalettedContainer<BlockState>> blockStateCodec = presetBlockStates == null ? ChunkSerializer.BLOCK_STATE_CODEC :
                PalettedContainer.codecRW(
                    Block.BLOCK_STATE_REGISTRY,
                    BlockState.CODEC,
                    PalettedContainer.Strategy.SECTION_STATES,
                    Blocks.AIR.defaultBlockState(),
                    presetBlockStates
                );

            output[sectionIndex] = new LevelChunkSection(
                blockStateCodec.parse(NbtOps.INSTANCE, section.getCompound("block_states")).getOrThrow(false, LOGGER::error),
                null
            );
        }

        return output;
    }

}
