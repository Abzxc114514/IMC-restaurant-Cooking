package com.imc.cooking;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import net.minecraft.block.BlockState;

import java.util.Locale;

/**
 * 厨具类型（1.21.4 Yarn 映射）。
 *   煎锅=smoker  砧板=beehive  搅拌机=cauldron
 *   饺子机=blast_furnace  煮锅=campfire  搅拌桶=composter
 */
public enum UtensilType {
    FRYING_PAN("煎锅", "smoker"),
    CUTTING_BOARD("砧板", "beehive"),
    MIXER("搅拌机", "cauldron"),
    DUMPLING_MACHINE("饺子机", "blast_furnace"),
    BOILING_POT("煮锅", "campfire"),
    MIXING_TANK("搅拌桶", "composter");

    private final String displayName;
    private final String blockId;

    UtensilType(String displayName, String blockId) {
        this.displayName = displayName;
        this.blockId = blockId;
    }

    public String getDisplayName() { return displayName; }
    public String getBlockId() { return blockId; }

    public boolean matchesBlock(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        return blockId.equals(id.toString());
    }

    public boolean matchesBlockState(WorldAccess level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return matchesBlock(state.getBlock());
    }

    public static UtensilType byDisplayName(String name) {
        if (name == null) return null;
        for (UtensilType t : values()) {
            if (t.displayName.equals(name) || t.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return t;
            }
        }
        return null;
    }

    public static UtensilType[] bindOrder() {
        return new UtensilType[]{FRYING_PAN, CUTTING_BOARD, MIXER, DUMPLING_MACHINE, BOILING_POT, MIXING_TANK};
    }
}
