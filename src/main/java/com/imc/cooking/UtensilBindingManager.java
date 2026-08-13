package com.imc.cooking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.EnumMap;
import java.util.Map;

/**
 * 厨具绑定管理（1.21.4 Yarn 映射）。
 */
public class UtensilBindingManager {

    private boolean binding = false;
    private int currentIndex = 0;
    private final Map<UtensilType, BlockPos> utensilPos = new EnumMap<>(UtensilType.class);
    private BlockPos storagePos = null;

    public boolean isBinding() { return binding; }
    public int boundCount() { return utensilPos.size(); }
    public Map<UtensilType, BlockPos> getBindings() { return utensilPos; }
    public BlockPos getStoragePos() { return storagePos; }

    public void startBinding(ClientPlayerEntity player) {
        if (binding) {
            IMCCookingMod.send(player, Text.literal("§e[IMC] 绑定流程已在进行中，当前厨具：§f" + currentUtensilName()));
            return;
        }
        if (utensilPos.size() >= UtensilType.bindOrder().length) {
            IMCCookingMod.send(player, Text.literal("§a[IMC] 6 种厨具已全部绑定。按 J 开始工作。"));
            return;
        }
        binding = true;
        currentIndex = nextUnboundIndex(0);
        announceUtensil(player);
    }

    public void bindUtensil(ClientPlayerEntity player) {
        if (!binding) {
            IMCCookingMod.send(player, Text.literal("§c[IMC] 当前未处于绑定流程，请先按 B 开始。"));
            return;
        }
        BlockPos pos = getTargetedBlockPos();
        if (pos == null) {
            IMCCookingMod.send(player, Text.literal("§c[IMC] 请对准一个方块再按 I 绑定。"));
            return;
        }
        UtensilType type = currentUtensil();
        if (type == null) {
            IMCCookingMod.send(player, Text.literal("§c[IMC] 没有更多厨具需要绑定。"));
            return;
        }
        // 不强制校验方块类型，玩家对准任意方块即可绑定
        utensilPos.put(type, pos);
        IMCCookingMod.send(player, Text.literal(
                String.format("§a[IMC] 绑定成功 §f[%d/6]§a：§e%s §a-> §7%s",
                        utensilPos.size(), type.getDisplayName(), pos.toShortString())));

        if (utensilPos.size() >= UtensilType.bindOrder().length) {
            binding = false;
            IMCCookingMod.send(player, Text.literal(
                    "§b[IMC] 6 种厨具全部绑定完成！按 O 绑定存储地，按 J 开始工作。"));
            return;
        }
        currentIndex = nextUnboundIndex(currentIndex + 1);
        announceUtensil(player);
    }

    public void skipCurrent(ClientPlayerEntity player) {
        if (!binding) {
            IMCCookingMod.send(player, Text.literal("§c[IMC] 当前未处于绑定流程。"));
            return;
        }
        UtensilType type = currentUtensil();
        if (type == null) {
            binding = false;
            return;
        }
        IMCCookingMod.send(player, Text.literal("§e[IMC] 已跳过 §f" + type.getDisplayName() + "§e。"));
        currentIndex = nextUnboundIndex(currentIndex + 1);
        if (currentIndex < 0) {
            binding = false;
            IMCCookingMod.send(player, Text.literal(
                    "§b[IMC] 绑定流程结束（部分厨具已跳过）。按 O 绑定存储地，按 J 开始工作。"));
            return;
        }
        announceUtensil(player);
    }

    public void bindStorage(ClientPlayerEntity player) {
        BlockPos pos = getTargetedBlockPos();
        if (pos == null) {
            IMCCookingMod.send(player, Text.literal("§c[IMC] 请对准存储方块再按 O。"));
            return;
        }
        storagePos = pos;
        IMCCookingMod.send(player, Text.literal("§a[IMC] 菜品存储地已绑定：§7" + pos.toShortString()));
    }

    private void announceUtensil(ClientPlayerEntity player) {
        UtensilType type = currentUtensil();
        if (type == null) return;
        IMCCookingMod.send(player, Text.literal(
                "§d[IMC] 请对准你要作为 §e" + type.getDisplayName()
                        + " §d的方块（推荐 " + type.getBlockId() + "），按 §fI §d绑定。"));
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

    private int nextUnboundIndex(int from) {
        UtensilType[] order = UtensilType.bindOrder();
        for (int i = from; i < order.length; i++) {
            if (!utensilPos.containsKey(order[i])) return i;
        }
        return -1;
    }

    private BlockPos getTargetedBlockPos() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) mc.crosshairTarget).getBlockPos();
    }

    public void reset() {
        binding = false;
        currentIndex = 0;
        utensilPos.clear();
        storagePos = null;
    }
}
