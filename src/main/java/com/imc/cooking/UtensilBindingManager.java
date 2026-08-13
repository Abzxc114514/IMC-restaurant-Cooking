package com.imc.cooking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.EnumMap;
import java.util.Map;

/**
 * 厨具绑定管理（见 NewModTraeLookMe.md 第1.3节）。
 *
 * 绑定流程：
 *   玩家按 B 开始绑定 ▶ 系统报出第一个厨具名（如"煎锅"）
 *   ▶ 玩家对准对应方块按 I 绑定 ▶ 报出下一个厨具名
 *   ▶ ... 直到 6 种厨具全部绑定
 *   按 U 可跳过当前厨具
 *   按 O 绑定菜品存储地（独立于厨具绑定流程）
 *
 * 绑定结果：UtensilType -> BlockPos 映射 + 存储地坐标
 */
public class UtensilBindingManager {

    private boolean binding = false;
    private int currentIndex = 0; // 当前等待绑定的厨具在 bindOrder() 中的下标

    private final Map<UtensilType, BlockPos> utensilPos = new EnumMap<>(UtensilType.class);
    private BlockPos storagePos = null;

    public boolean isBinding() {
        return binding;
    }

    public int boundCount() {
        return utensilPos.size();
    }

    public Map<UtensilType, BlockPos> getBindings() {
        return utensilPos;
    }

    public BlockPos getStoragePos() {
        return storagePos;
    }

    /** 玩家按 B：开始绑定流程。 */
    public void startBinding(LocalPlayer player) {
        if (binding) {
            IMCCookingMod.send(player, Component.literal("§e[IMC] 绑定流程已在进行中，当前厨具：§f" + currentUtensilName()));
            return;
        }
        if (utensilPos.size() >= UtensilType.bindOrder().length) {
            IMCCookingMod.send(player, Component.literal("§a[IMC] 6 种厨具已全部绑定。按 J 开始工作。"));
            return;
        }
        binding = true;
        currentIndex = nextUnboundIndex(0);
        announceUtensil(player);
    }

    /** 玩家按 I：将当前看向的方块绑定到当前厨具。 */
    public void bindUtensil(LocalPlayer player) {
        if (!binding) {
            IMCCookingMod.send(player, Component.literal("§c[IMC] 当前未处于绑定流程，请先按 B 开始。"));
            return;
        }
        BlockPos pos = getTargetedBlockPos();
        if (pos == null) {
            IMCCookingMod.send(player, Component.literal("§c[IMC] 请对准一个方块再按 I 绑定。"));
            return;
        }
        UtensilType type = currentUtensil();
        if (type == null) {
            IMCCookingMod.send(player, Component.literal("§c[IMC] 没有更多厨具需要绑定。"));
            return;
        }
        // 校验方块是否匹配
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !type.matchesBlockState(mc.level, pos)) {
            IMCCookingMod.send(player, Component.literal(
                    "§c[IMC] 该方块不是 §e" + type.getDisplayName()
                            + " §c（应为 " + type.getBlockId() + "），请对准正确的方块。"));
            return;
        }
        utensilPos.put(type, pos);
        IMCCookingMod.send(player, Component.literal(
                String.format("§a[IMC] 绑定成功 §f[%d/6]§a：§e%s §a-> §7%s",
                        utensilPos.size(), type.getDisplayName(), pos.toShortString())));

        if (utensilPos.size() >= UtensilType.bindOrder().length) {
            binding = false;
            IMCCookingMod.send(player, Component.literal(
                    "§b[IMC] 6 种厨具全部绑定完成！按 O 绑定存储地，按 J 开始工作。"));
            return;
        }
        currentIndex = nextUnboundIndex(currentIndex + 1);
        announceUtensil(player);
    }

    /** 玩家按 U：跳过当前厨具。 */
    public void skipCurrent(LocalPlayer player) {
        if (!binding) {
            IMCCookingMod.send(player, Component.literal("§c[IMC] 当前未处于绑定流程。"));
            return;
        }
        UtensilType type = currentUtensil();
        if (type == null) {
            binding = false;
            return;
        }
        IMCCookingMod.send(player, Component.literal("§e[IMC] 已跳过 §f" + type.getDisplayName() + "§e。"));
        currentIndex = nextUnboundIndex(currentIndex + 1);
        if (currentIndex < 0) {
            binding = false;
            IMCCookingMod.send(player, Component.literal(
                    "§b[IMC] 绑定流程结束（部分厨具已跳过）。按 O 绑定存储地，按 J 开始工作。"));
            return;
        }
        announceUtensil(player);
    }

    /** 玩家按 O：绑定菜品存储地（看向存储方块，如木桶/箱子）。 */
    public void bindStorage(LocalPlayer player) {
        BlockPos pos = getTargetedBlockPos();
        if (pos == null) {
            IMCCookingMod.send(player, Component.literal("§c[IMC] 请对准存储方块再按 O。"));
            return;
        }
        storagePos = pos;
        IMCCookingMod.send(player, Component.literal(
                "§a[IMC] 菜品存储地已绑定：§7" + pos.toShortString()));
    }

    private void announceUtensil(LocalPlayer player) {
        UtensilType type = currentUtensil();
        if (type == null) return;
        IMCCookingMod.send(player, Component.literal(
                "§d[IMC] 请对准 §e" + type.getDisplayName()
                        + " §d(" + type.getBlockId() + ") §d方块，按 §fI §d绑定。"));
    }

    private UtensilType currentUtensil() {
        UtensilType[] order = UtensilType.bindOrder();
        if (currentIndex < 0 || currentIndex >= order.length) return null;
        return order[currentIndex];
    }

    private String currentUtensilName() {
        UtensilType t = currentUtensil();
        return t == null ? "无" : t.getDisplayName();
    }

    /** 返回下一个未绑定厨具的下标，全部已绑定返回 -1。 */
    private int nextUnboundIndex(int from) {
        UtensilType[] order = UtensilType.bindOrder();
        for (int i = from; i < order.length; i++) {
            if (!utensilPos.containsKey(order[i])) {
                return i;
            }
        }
        return -1;
    }

    /** 取得玩家视线准星对准的方块坐标。 */
    private BlockPos getTargetedBlockPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return ((BlockHitResult) mc.hitResult).getBlockPos();
    }

    public void reset() {
        binding = false;
        currentIndex = 0;
        utensilPos.clear();
        storagePos = null;
    }
}
