package com.imc.cooking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 烹饪流程控制器（1.21.4 Yarn 映射）。
 * 实现"中式汉堡"完整流程的状态机（见 NewModTraeLookMe.md 第1.2节）。
 */
public class CookingController {

    private enum State {
        IDLE, GOTO_VILLAGER, TRADE,
        MIX_PORK, MIX_DOUGH, DUMPLING_WRAP, MIX_SCALLION,
        CUT_PORK, FRY_WRAP, FRY_PORK, FRY_EGG, FRY_FINAL,
        STORE, DONE
    }

    private State state = State.IDLE;
    private boolean running = false;
    private int waitTicks = 0;
    private int subStep = 0;
    private int progress = 0;

    private final UtensilBindingManager bindingManager;
    private final CookingConfig config;

    public CookingController(UtensilBindingManager bm, CookingConfig config) {
        this.bindingManager = bm;
        this.config = config;
    }

    public boolean isRunning() { return running; }

    public void start(ClientPlayerEntity player) {
        if (running) {
            IMCCookingMod.send(player, Text.literal("§e[IMC] 烹饪流程已在运行中。"));
            return;
        }
        String dish = config.selectedDish;
        if (!DishList.isImplemented(dish)) {
            IMCCookingMod.send(player, Text.literal("§c[IMC] 菜品 §e" + dish + " §c暂未实现。"));
            return;
        }
        for (UtensilType t : UtensilType.values()) {
            if (!bindingManager.getBindings().containsKey(t)) {
                IMCCookingMod.send(player, Text.literal("§c[IMC] 厨具 §e" + t.getDisplayName() + " §c未绑定。"));
                return;
            }
        }
        if (bindingManager.getStoragePos() == null) {
            IMCCookingMod.send(player, Text.literal("§c[IMC] 菜品存储地未绑定（按 O 绑定）。"));
            return;
        }
        running = true;
        state = State.GOTO_VILLAGER;
        subStep = 0;
        progress = 0;
        IMCCookingMod.send(player, Text.literal("§a[IMC] 开始制作 §e" + dish + "§a。按 §fJ §a终止。"));
        BaritoneBridge.gotoPos(config.gotoX, config.gotoY, config.gotoZ);
    }

    public void stop(ClientPlayerEntity player) {
        if (!running) return;
        running = false;
        state = State.IDLE;
        BaritoneBridge.stop();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) mc.setScreen(null);
        IMCCookingMod.send(player, Text.literal("§c[IMC] 烹饪流程已终止。"));
    }

    public void tick(ClientPlayerEntity player) {
        if (!running || player == null) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (state) {
            case GOTO_VILLAGER -> tickGotoVillager(player, mc);
            case TRADE -> tickTrade(player, mc);
            case MIX_PORK -> tickMixStep(player, mc, UtensilType.MIXER, "猪肉馅", 4 * 20, 8, State.MIX_DOUGH);
            case MIX_DOUGH -> tickMixStep(player, mc, UtensilType.MIXER, "大面团", 6 * 20, 8, State.DUMPLING_WRAP);
            case DUMPLING_WRAP -> tickMixStep(player, mc, UtensilType.DUMPLING_MACHINE, "饺子皮", 4 * 20, 8, State.MIX_SCALLION);
            case MIX_SCALLION -> tickMixStep(player, mc, UtensilType.MIXER, "葱花", 4 * 20, 16, State.CUT_PORK);
            case CUT_PORK -> tickCutPork(player, mc);
            case FRY_WRAP -> tickFryStep(player, mc, "饺子皮", "葱花", 2 * 20, 9 * 20, 128, State.FRY_PORK);
            case FRY_PORK -> tickFrySingle(player, mc, "炸猪排半成品", 10 * 20, 64, State.FRY_EGG);
            case FRY_EGG -> tickFrySingle(player, mc, "鸡蛋", 10 * 20, 64, State.FRY_FINAL);
            case FRY_FINAL -> tickFryFinal(player, mc);
            case STORE -> tickStore(player, mc);
            case DONE -> {
                IMCCookingMod.send(player, Text.literal("§b[IMC] 中式汉堡制作完成！3秒后自动开始下一单。"));
                // 自动循环：等待 3 秒后重新回到村民交易步骤，无需手按 J
                state = State.GOTO_VILLAGER;
                subStep = 0;
                progress = 0;
                waitTicks = 3 * 20;
                BaritoneBridge.gotoPos(config.gotoX, config.gotoY, config.gotoZ);
            }
            default -> {
            }
        }
    }

    private void tickGotoVillager(ClientPlayerEntity player, MinecraftClient mc) {
        if (BaritoneBridge.hasReached(player, config.gotoX, config.gotoY, config.gotoZ, 3.0)) {
            BaritoneBridge.stop();
            // gotoX/Y/Z 即"原材料供给"村民所在坐标，直接对最近村民右键交易
            VillagerEntity target = findNearestVillager(player, mc);
            if (target == null) {
                IMCCookingMod.send(player, Text.literal("§c[IMC] 附近未找到村民，稍后重试。"));
                waitTicks = 20;
                return;
            }
            IMCCookingMod.send(player, Text.literal(
                    "§a[IMC] 已到达村民位置，右键交易。"));
            lookAtEntity(player, target);
            interactEntity(mc, target);
            waitTicks = 2 * 20;
            state = State.TRADE;
            subStep = 0;
        } else {
            waitTicks = 20;
        }
    }

    /** 返回玩家附近最近的村民；没有返回 null。 */
    private VillagerEntity findNearestVillager(ClientPlayerEntity player, MinecraftClient mc) {
        if (mc.world == null) return null;
        Vec3d eye = player.getEyePos();
        Box box = Box.of(eye, 16.0, 8.0, 16.0);
        java.util.List<VillagerEntity> villagers = mc.world.getEntitiesByClass(VillagerEntity.class, box, e -> true);
        if (villagers.isEmpty()) return null;

        VillagerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (VillagerEntity v : villagers) {
            double d = v.squaredDistanceTo(player);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = v;
            }
        }
        return nearest;
    }

    /** 让玩家看向实体（眼睛高度）。 */
    private void lookAtEntity(ClientPlayerEntity player, Entity entity) {
        Vec3d eye = player.getEyePos();
        Vec3d target = entity.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.prevYaw = player.getYaw();
        player.prevPitch = player.getPitch();
        player.headYaw = player.getYaw();
        player.bodyYaw = player.getYaw();
    }

    /** 右键交互实体。 */
    private void interactEntity(MinecraftClient mc, Entity entity) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void tickTrade(ClientPlayerEntity player, MinecraftClient mc) {
        if (mc.currentScreen != null) mc.setScreen(null);
        IMCCookingMod.send(player, Text.literal("§a[IMC] 交易完成，开始搅拌猪肉馅。"));
        state = State.MIX_PORK;
        subStep = 0;
        waitTicks = 10;
    }

    private void tickMixStep(ClientPlayerEntity player, MinecraftClient mc, UtensilType utensil,
                             String itemName, int intervalTicks, int repeat, State next) {
        BlockPos pos = bindingManager.getBindings().get(utensil);
        if (pos == null) {
            state = next;
            subStep = 0;
            return;
        }
        if (subStep == 0) {
            lookAtBlock(player, pos);
            rightClickBlock(mc, pos);
            waitTicks = 4;
            subStep = 1;
            return;
        }
        if (subStep == 1) {
            if (takeItemFromContainer(mc, itemName)) {
                IMCCookingMod.send(player, Text.literal(
                        "§7[IMC] 取出 §e" + itemName + " §7(" + (progress + 1) + "/" + repeat + ")"));
            } else {
                IMCCookingMod.send(player, Text.literal("§c[IMC] 容器中没有 §e" + itemName + "§c，等待。"));
            }
            if (mc.currentScreen != null) mc.setScreen(null);
            subStep = 2;
            waitTicks = intervalTicks;
            return;
        }
        subStep = 0;
        progress++;
        if (progress >= repeat) {
            IMCCookingMod.send(player, Text.literal("§a[IMC] " + itemName + " 步骤完成。"));
            state = next;
            subStep = 0;
            progress = 0;
        }
    }

    private void tickCutPork(ClientPlayerEntity player, MinecraftClient mc) {
        BlockPos pos = bindingManager.getBindings().get(UtensilType.CUTTING_BOARD);
        if (pos == null) {
            state = State.FRY_WRAP;
            return;
        }
        int totalSteps = 64 * 3;
        if (subStep < totalSteps) {
            lookAtBlock(player, pos);
            int phase = subStep % 3;
            if (phase == 0) switchToItem(mc, "猪肉馅");
            else switchToItem(mc, "面粉");
            rightClickBlock(mc, pos);
            subStep++;
            waitTicks = 4;
        } else {
            IMCCookingMod.send(player, Text.literal("§a[IMC] 砧板步骤完成。"));
            state = State.FRY_WRAP;
            subStep = 0;
        }
    }

    private void tickFryStep(ClientPlayerEntity player, MinecraftClient mc, String item1, String item2,
                             int t1, int t2, int repeat, State next) {
        BlockPos pos = bindingManager.getBindings().get(UtensilType.FRYING_PAN);
        if (pos == null) {
            state = next;
            return;
        }
        lookAtBlock(player, pos);
        int phase = subStep % 3;
        if (phase == 0) {
            switchToItem(mc, item1);
            rightClickBlock(mc, pos);
            waitTicks = t1;
        } else if (phase == 1) {
            switchToItem(mc, item2);
            rightClickBlock(mc, pos);
            waitTicks = t2;
        } else {
            rightClickBlock(mc, pos);
            waitTicks = 4;
        }
        subStep++;
        if (subStep >= repeat * 3) {
            IMCCookingMod.send(player, Text.literal("§a[IMC] " + item1 + "+" + item2 + " 步骤完成。"));
            state = next;
            subStep = 0;
        }
    }

    private void tickFrySingle(ClientPlayerEntity player, MinecraftClient mc, String item,
                               int interval, int repeat, State next) {
        BlockPos pos = bindingManager.getBindings().get(UtensilType.FRYING_PAN);
        if (pos == null) {
            state = next;
            return;
        }
        lookAtBlock(player, pos);
        switchToItem(mc, item);
        rightClickBlock(mc, pos);
        subStep++;
        if (subStep % 2 == 0) waitTicks = interval;
        else waitTicks = 4;
        if (subStep >= repeat * 2) {
            IMCCookingMod.send(player, Text.literal("§a[IMC] " + item + " 步骤完成。"));
            state = next;
            subStep = 0;
        }
    }

    private void tickFryFinal(ClientPlayerEntity player, MinecraftClient mc) {
        BlockPos pos = bindingManager.getBindings().get(UtensilType.FRYING_PAN);
        if (pos == null) {
            state = State.STORE;
            return;
        }
        lookAtBlock(player, pos);
        int phase = subStep % 5;
        switch (phase) {
            case 0 -> { switchToItem(mc, "葱花饼"); rightClickBlock(mc, pos); waitTicks = 4; }
            case 1 -> { rightClickBlock(mc, pos); waitTicks = 20; }
            case 2 -> { switchToItem(mc, "炒蛋"); rightClickBlock(mc, pos); waitTicks = 20; }
            case 3 -> { switchToItem(mc, "炸猪排"); rightClickBlock(mc, pos); waitTicks = 9 * 20; }
            case 4 -> { rightClickBlock(mc, pos); waitTicks = 4; }
        }
        subStep++;
        if (subStep >= 64 * 5) {
            IMCCookingMod.send(player, Text.literal("§a[IMC] 最终煎制完成，准备存储。"));
            state = State.STORE;
            subStep = 0;
        }
    }

    private void tickStore(ClientPlayerEntity player, MinecraftClient mc) {
        BlockPos pos = bindingManager.getStoragePos();
        if (pos == null) {
            state = State.DONE;
            return;
        }
        lookAtBlock(player, pos);
        rightClickBlock(mc, pos);
        waitTicks = 6;
        if (mc.currentScreen != null) {
            putItemToContainer(mc, "中式汉堡");
            mc.setScreen(null);
        }
        IMCCookingMod.send(player, Text.literal("§a[IMC] 中式汉堡已存入。"));
        state = State.DONE;
    }

    // ============ 辅助方法 ============

    private void lookAtBlock(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eye = player.getEyePos();
        Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        double maxDelta = config.turnSpeed;
        float curYaw = player.getYaw();
        float curPitch = player.getPitch();
        float dyaw = wrapDegrees(yaw - curYaw);
        float dpitch = pitch - curPitch;
        double step = Math.max(1.0, maxDelta);
        if (Math.abs(dyaw) > step) dyaw = (float) (Math.signum(dyaw) * step);
        if (Math.abs(dpitch) > step) dpitch = (float) (Math.signum(dpitch) * step);
        player.setYaw(curYaw + dyaw);
        player.setPitch(curPitch + dpitch);
        player.prevYaw = player.getYaw();
        player.prevPitch = player.getPitch();
        player.headYaw = player.getYaw();
        player.bodyYaw = player.getYaw();
    }

    private static float wrapDegrees(float v) {
        v %= 360;
        if (v > 180) v -= 360;
        if (v < -180) v += 360;
        return v;
    }

    private void rightClickBlock(MinecraftClient mc, BlockPos pos) {
        if (mc.interactionManager == null || mc.player == null) return;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) mc.crosshairTarget;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void useItemOnTarget(ClientPlayerEntity player, MinecraftClient mc) {
        if (mc.interactionManager != null) {
            mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        }
        player.swingHand(Hand.MAIN_HAND);
    }

    private boolean takeItemFromContainer(MinecraftClient mc, String itemName) {
        if (mc.player == null || mc.player.currentScreenHandler == null || mc.currentScreen == null) return false;
        var handler = mc.player.currentScreenHandler;
        int total = handler.slots.size();
        int hotbarStart = total - 9;
        int containerSlots = hotbarStart - 27;

        int src = -1;
        for (int i = 0; i < containerSlots; i++) {
            var stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                String dn = stack.getName().getString();
                if (dn.contains(itemName)) {
                    src = i;
                    break;
                }
            }
        }
        if (src < 0) return false;
        int dst = -1;
        for (int i = hotbarStart; i < total; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) {
                dst = i;
                break;
            }
        }
        if (dst < 0) return false;
        if (mc.interactionManager != null) {
            mc.interactionManager.clickSlot(handler.syncId, src, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(handler.syncId, dst, 0, SlotActionType.PICKUP, mc.player);
        }
        return true;
    }

    private void putItemToContainer(MinecraftClient mc, String itemName) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return;
        var handler = mc.player.currentScreenHandler;
        int total = handler.slots.size();
        int hotbarStart = total - 9;
        int containerSlots = hotbarStart - 27;

        for (int i = hotbarStart; i < total; i++) {
            var stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getName().getString().contains(itemName)) {
                for (int j = 0; j < containerSlots; j++) {
                    if (handler.getSlot(j).getStack().isEmpty()) {
                        if (mc.interactionManager != null) {
                            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                            mc.interactionManager.clickSlot(handler.syncId, j, 0, SlotActionType.PICKUP, mc.player);
                        }
                        return;
                    }
                }
            }
        }
    }

    private void switchToItem(MinecraftClient mc, String itemName) {
        if (mc.player == null) return;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.getName().getString().contains(itemName)) {
                inv.selectedSlot = i;
                return;
            }
        }
        IMCCookingMod.LOGGER.warn("[IMCCooking] 物品栏未找到 {}", itemName);
    }
}
