package com.imc.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * 厨具类型（见 NewModTraeLookMe.md 第"绑定厨具"节）。
 *
 * 每种厨具对应一个具体的 Minecraft 方块：
 *   FRYING_PAN  煎锅      = smoker (烟熏炉)
 *   CUTTING_BOARD 砧板    = beehive (蜂巢/蜂箱)
 *   MIXER       搅拌机    = cauldron (炼药锅)
 *   DUMPLING_MACHINE 饺子机 = blast_furnace (高炉)
 *   BOILING_POT 煮锅      = campfire (营火)
 *   MIXING_TANK 搅拌桶    = composter (堆肥桶)
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

    public String getDisplayName() {
        return displayName;
    }

    public String getBlockId() {
        return blockId;
    }

    /** 检查给定方块是否匹配本厨具类型。 */
    public boolean matchesBlock(Block block) {
        String regName = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));
        return blockId.equals(regName);
    }

    /** 检查给定位置的方块状态是否匹配本厨具类型。 */
    public boolean matchesBlockState(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return matchesBlock(state.getBlock());
    }

    /** 按显示名查找厨具类型。 */
    public static UtensilType byDisplayName(String name) {
        if (name == null) return null;
        for (UtensilType t : values()) {
            if (t.displayName.equals(name) || t.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return t;
            }
        }
        return null;
    }

    /** 绑定流程的推荐顺序。 */
    public static UtensilType[] bindOrder() {
        return new UtensilType[]{
                FRYING_PAN,
                CUTTING_BOARD,
                MIXER,
                DUMPLING_MACHINE,
                BOILING_POT,
                MIXING_TANK
        };
    }
}
