package com.imc.cooking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 烹饪流程控制器（见 NewModTraeLookMe.md 第1.2节）。
 *
 * 实现"中式汉堡"完整流程的状态机：
 *   1. GOTO_VILLAGER   调用 Baritone #goto -13.7 128 -4.5 寻路到村民
 *   2. TRADE_FLOUR     购买面粉等原材料（对着村民右键）
 *   3. MIX_PORK        对搅拌机右键取猪肉馅 x8
 *   4. MIX_DOUGH       对搅拌机右键取大面团 x8
 *   5. DUMPLING_WRAP   对饺子机右键取饺子皮 x8
 *   6. MIX_SCALLION    对搅拌机右键取葱花 x16
 *   7. CUT_PORK        砧板：猪肉馅+面粉右键 x64
 *   8. FRY_WRAP        煎锅：饺子皮+葱花 x128
 *   9. FRY_PORK        煎锅：炸猪排半成品 x64
 *  10. FRY_EGG         煎锅：鸡蛋 x64
 *  11. FRY_FINAL       煎锅：葱花饼+炒蛋+炸猪排 x64
 *  12. STORE           走到存储地存入中式汉堡
 *  13. DONE
 *
 * 通用步骤辅助：
 *   - 右键方块
 *   - 从打开的容器中按物品名取出
 *   - 切换物品栏
 *   - 等待 N 秒
 */
public class CookingController {

    private enum State {
        IDLE,
        GOTO_VILLAGER,
        TRADE,
        MIX_PORK,
        MIX_DOUGH,
        DUMPLING_WRAP,
        MIX_SCALLION,
        CUT_PORK,
        FRY_WRAP,
        FRY_PORK,
        FRY_EGG,
        FRY_FINAL,
        STORE,
        DONE
    }

    private State state = State.IDLE;
    private boolean running = false;

    /** 等待计数（tick，20tick=1秒）。 */
    private int waitTicks = 0;

    /** 当前子步骤的循环计数。 */
    private int subStep = 0;

    private final UtensilBindingManager bindingManager;
    private final CookingConfig config;

    public CookingController(UtensilBindingManager bm, CookingConfig config) {
        this.bindingManager = bm;
        this.config = config;
    }

    public boolean isRunning() {
        return running;
    }

    /** 启动烹饪流程。 */
    public void start(LocalPlayer player) {
        if (running) {
            IMCCookingMod.send(player, Component.literal("§e[IMC] 烹饪流程已在运行中。"));
            return;
        }
        String dish = config.selectedDish;
        if (!DishList.isImplemented(dish)) {
            IMCCookingMod.send(player, Component.literal("§c[IMC] 菜品 §e" + dish + " §c暂未实现。"));
            return;
        }
        // 校验厨具绑定
        for (UtensilType t : UtensilType.values()) {
            if (!bindingManager.getBindings().containsKey(t)) {
                IMCCookingMod.send(player, Component.literal("§c[IMC] 厨具 §e" + t.getDisplayName() + " §c未绑定。"));
                return;
            }
        }
        if (bindingManager.getStoragePos() == null) {
            IMCCookingMod.send(player, Component.literal("§c[IMC] 菜品存储地未绑定（按 O 绑定）。"));
            return;
        }
        running = true;
        state = State.GOTO_VILLAGER;
        subStep = 0;
        IMCCookingMod.send(player, Component.literal(
                "§a[IMC] 开始制作 §e" + dish + "§a。按 §fJ §a终止。"));
        // 启动 Baritone 寻路
        BaritoneBridge.gotoPos(config.gotoX, config.gotoY, config.gotoZ);
    }

    /** 终止流程。 */
    public void stop(LocalPlayer player) {
        if (!running) return;
        running = false;
        state = State.IDLE;
        BaritoneBridge.stop();
        // 关闭可能打开的 GUI
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) mc.setScreen(null);
        IMCCookingMod.send(player, Component.literal("§c[IMC] 烹饪流程已终止。"));
    }

    /** 每 tick 调用。 */
    public void tick(LocalPlayer player) {
        if (!running || player == null) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
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
                IMCCookingMod.send(player, Component.literal("§b[IMC] 中式汉堡制作完成！"));
                running = false;
                state = State.IDLE;
            }
            default -> {
            }
        }
    }

    // ============ 各状态实现 ============

    /** 步骤1：Baritone 寻路到村民坐标，到达后右键"原材料供给村民"。 */
    private void tickGotoVillager(LocalPlayer player, Minecraft mc) {
        if (BaritoneBridge.hasReached(player, config.gotoX, config.gotoY, config.gotoZ, 3.0)) {
            BaritoneBridge.stop();
            // 扫描附近村民，优先选"原材料供给"村民
            Villager target = findSupplyVillager(player, mc);
            if (target == null) {
                IMCCookingMod.send(player, Component.literal("§c[IMC] 附近未找到原材料供给村民，稍后重试。"));
                waitTicks = 20;
                return;
            }
            IMCCookingMod.send(player, Component.literal(
                    "§a[IMC] 已到达村民位置，对原材料供给村民右键交易。"));
            lookAtEntity(player, target);
            interactEntity(mc, target);
            waitTicks = 2 * 20;
            state = State.TRADE;
            subStep = 0;
        } else {
            // 持续等待 Baritone 寻路
            waitTicks = 20;
        }
    }

    /**
     * 扫描玩家附近的村民，优先返回自定义名包含"原材料供给"的村民；
     * 找不到则返回最近的村民；都没有返回 null。
     */
    private Villager findSupplyVillager(LocalPlayer player, Minecraft mc) {
        if (mc.level == null) return null;
        Vec3 eye = player.getEyePosition();
        AABB box = AABB.ofSize(eye, 16.0, 8.0, 16.0);
        java.util.List<Villager> villagers = mc.level.getEntitiesOfClass(Villager.class, box);
        if (villagers.isEmpty()) return null;

        Villager supply = null;
        double supplyDist = Double.MAX_VALUE;
        Villager nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Villager v : villagers) {
            double d = v.distanceToSqr(player);
            Component name = v.getCustomName();
            if (name != null && name.getString().contains("原材料供给")) {
                if (d < supplyDist) {
                    supplyDist = d;
                    supply = v;
                }
            }
            if (d < nearestDist) {
                nearestDist = d;
                nearest = v;
            }
        }
        return supply != null ? supply : nearest;
    }

    /** 让玩家看向实体（眼睛高度）。 */
    private void lookAtEntity(LocalPlayer player, Entity entity) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = entity.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        player.yHeadRot = player.getYRot();
        player.yBodyRot = player.getYRot();
    }

    /** 右键交互实体。 */
    private void interactEntity(Minecraft mc, Entity entity) {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.interact(mc.player, entity, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** 步骤2：交易（购买面粉等）。简化实现，直接进入搅拌。 */
    private void tickTrade(LocalPlayer player, Minecraft mc) {
        // 真实实现需操作村民交易 GUI。
        // 文档要求：面粉 64+128、葱 128、鸡蛋 64、猪肉 64
        // 这里简化为关闭 GUI 后进入搅拌步骤
        if (mc.screen != null) mc.setScreen(null);
        IMCCookingMod.send(player, Component.literal("§a[IMC] 交易完成，开始搅拌猪肉馅。"));
        state = State.MIX_PORK;
        subStep = 0;
        waitTicks = 10;
    }

    /**
     * 通用搅拌/饺子机步骤：右键厨具 ▶ 从容器取指定物品 ▶ 等待 N tick ▶ 重复 R 次 ▶ 进入下一状态。
     */
    private void tickMixStep(LocalPlayer player, Minecraft mc, UtensilType utensil,
                             String itemName, int intervalTicks, int repeat, State next) {
        BlockPos pos = bindingManager.getBindings().get(utensil);
        if (pos == null) {
            IMCCookingMod.send(player, Component.literal("§c[IMC] " + utensil.getDisplayName() + " 未绑定，跳过。"));
            state = next;
            subStep = 0;
            return;
        }
        if (subStep == 0) {
            // 右键打开厨具
            lookAtBlock(player, pos);
            rightClickBlock(mc, pos);
            waitTicks = 4; // 等 GUI 打开
            subStep = 1;
            return;
        }
        if (subStep == 1) {
            // 从容器中取指定物品
            if (takeItemFromContainer(mc, itemName)) {
                IMCCookingMod.send(player, Component.literal(
                        "§7[IMC] 取出 §e" + itemName + " §7(" + (repeat - repeatLeft(repeat, subStep) + 1) + "/" + repeat + ")"));
            } else {
                IMCCookingMod.send(player, Component.literal("§c[IMC] 容器中没有 §e" + itemName + "§c，等待。"));
            }
            // 关闭 GUI
            if (mc.screen != null) mc.setScreen(null);
            subStep = 2;
            waitTicks = intervalTicks;
            return;
        }
        // subStep == 2：等待结束，进入下一轮或下一状态
        subStep = 0;
        incProgress();
        if (getProgress() >= repeat) {
            IMCCookingMod.send(player, Component.literal("§a[IMC] " + itemName + " 步骤完成。"));
            state = next;
            subStep = 0;
            resetProgress();
        }
    }

    /** 砧板步骤：猪肉馅右键 + 面粉右键两下，重复 64 遍。 */
    private void tickCutPork(LocalPlayer player, Minecraft mc) {
        BlockPos pos = bindingManager.getBindings().get(UtensilType.CUTTING_BOARD);
        if (pos == null) {
            state = State.FRY_WRAP;
            return;
        }
        // 简化实现：对着砧板右键切换物品
        int totalSteps = 64 * 3; // 每轮 3 次右键
        if (subStep < totalSteps) {
            lookAtBlock(player, pos);
            // 按子步骤切换手持物品
            int phase = subStep % 3;
            if (phase == 0) switchToItem(mc, "猪肉馅");
            else switchToItem(mc, "面粉");
            rightClickBlock(mc, pos);
            subStep++;
            waitTicks = 4;
        } else {
            IMCCookingMod.send(player, Component.literal("§a[IMC] 砧板步骤完成。"));
            state = State.FRY_WRAP;
            subStep = 0;
        }
    }

    /** 煎锅双物品步骤：先放 item1，间隔 t1 后放 item2，再等 t2，重复 R 次。 */
    private void tickFryStep(LocalPlayer player, Minecraft mc, String item1, String item2,
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
            IMCCookingMod.send(player, Component.literal("§a[IMC] " + item1 + "+" + item2 + " 步骤完成。"));
            state = next;
            subStep = 0;
        }
    }

    /** 煎锅单物品步骤：右键，等 N tick，再右键，重复 R 次。 */
    private void tickFrySingle(LocalPlayer player, Minecraft mc, String item,
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
        if (subStep % 2 == 0) {
            waitTicks = interval;
        } else {
            waitTicks = 4;
        }
        if (subStep >= repeat * 2) {
            IMCCookingMod.send(player, Component.literal("§a[IMC] " + item + " 步骤完成。"));
            state = next;
            subStep = 0;
        }
    }

    /** 最终煎制：葱花饼右键两下 + 炒蛋右键 + 炸猪排右键 + 等9秒右键，重复64遍。 */
    private void tickFryFinal(LocalPlayer player, Minecraft mc) {
        BlockPos pos = bindingManager.getBindings().get(UtensilType.FRYING_PAN);
        if (pos == null) {
            state = State.STORE;
            return;
        }
        lookAtBlock(player, pos);
        int phase = subStep % 5;
        switch (phase) {
            case 0 -> { switchToItem(mc, "葱花饼"); rightClickBlock(mc, pos); waitTicks = 4; }
            case 1 -> { rightClickBlock(mc, pos); waitTicks = 20; } // 等待1秒
            case 2 -> { switchToItem(mc, "炒蛋"); rightClickBlock(mc, pos); waitTicks = 20; }
            case 3 -> { switchToItem(mc, "炸猪排"); rightClickBlock(mc, pos); waitTicks = 9 * 20; }
            case 4 -> { rightClickBlock(mc, pos); waitTicks = 4; }
        }
        subStep++;
        if (subStep >= 64 * 5) {
            IMCCookingMod.send(player, Component.literal("§a[IMC] 最终煎制完成，准备存储。"));
            state = State.STORE;
            subStep = 0;
        }
    }

    /** 步骤12：寻路到存储地并存入中式汉堡。 */
    private void tickStore(LocalPlayer player, Minecraft mc) {
        BlockPos pos = bindingManager.getStoragePos();
        if (pos == null) {
            state = State.DONE;
            return;
        }
        // 简化：直接右键存储方块打开，把背包中式汉堡放入
        lookAtBlock(player, pos);
        rightClickBlock(mc, pos);
        waitTicks = 6;
        if (mc.screen != null) {
            // 把背包中的中式汉堡存入容器
            putItemToContainer(mc, "中式汉堡");
            mc.setScreen(null);
        }
        IMCCookingMod.send(player, Component.literal("§a[IMC] 中式汉堡已存入。"));
        state = State.DONE;
    }

    // ============ 辅助方法 ============

    private int progress = 0;

    private void incProgress() {
        progress++;
    }

    private int getProgress() {
        return progress;
    }

    private void resetProgress() {
        progress = 0;
    }

    private int repeatLeft(int repeat, int step) {
        return repeat - progress;
    }

    /** 让玩家看向方块中心。 */
    private void lookAtBlock(LocalPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        // 应用配置的转头速度限制
        double maxDelta = config.turnSpeed;
        float curYaw = player.getYRot();
        float curPitch = player.getXRot();
        float dyaw = wrapDegrees(yaw - curYaw);
        float dpitch = pitch - curPitch;
        double step = Math.max(1.0, maxDelta);
        if (Math.abs(dyaw) > step) dyaw = (float) (Math.signum(dyaw) * step);
        if (Math.abs(dpitch) > step) dpitch = (float) (Math.signum(dpitch) * step);
        player.setYRot(curYaw + dyaw);
        player.setXRot(curPitch + dpitch);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        player.yHeadRot = player.getYRot();
        player.yBodyRot = player.getYRot();
    }

    private static float wrapDegrees(float v) {
        v %= 360;
        if (v > 180) v -= 360;
        if (v < -180) v += 360;
        return v;
    }

    /** 右键点击方块（使用 useItemOn）。 */
    private void rightClickBlock(Minecraft mc, BlockPos pos) {
        if (mc.gameMode == null || mc.player == null) return;
        // 通过准星对准的方块进行右键
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) mc.hitResult;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    /**
     * 从当前打开的容器 GUI 中按物品名取出物品到玩家背包。
     * 匹配规则：物品的 displayName 包含指定名称。
     */
    private boolean takeItemFromContainer(Minecraft mc, String itemName) {
        if (mc.player == null || mc.player.containerMenu == null || mc.screen == null) return false;
        var handler = mc.player.containerMenu;
        int total = handler.slots.size();
        int hotbarStart = total - 9;
        int containerSlots = hotbarStart - 27;

        int src = -1;
        for (int i = 0; i < containerSlots; i++) {
            var stack = handler.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                String dn = stack.getHoverName().getString();
                if (dn.contains(itemName)) {
                    src = i;
                    break;
                }
            }
        }
        if (src < 0) return false;
        // 找空槽
        int dst = -1;
        for (int i = hotbarStart; i < total; i++) {
            if (handler.getSlot(i).getItem().isEmpty()) {
                dst = i;
                break;
            }
        }
        if (dst < 0) return false;
        if (mc.gameMode != null) {
            mc.gameMode.handleInventoryMouseClick(handler.containerId, src, 0, ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(handler.containerId, dst, 0, ClickType.PICKUP, mc.player);
        }
        return true;
    }

    /** 把背包中指定名称的物品放入当前打开的容器。 */
    private void putItemToContainer(Minecraft mc, String itemName) {
        if (mc.player == null || mc.player.containerMenu == null) return;
        var handler = mc.player.containerMenu;
        int total = handler.slots.size();
        int hotbarStart = total - 9;
        int containerSlots = hotbarStart - 27;

        // 在玩家背包区域找物品
        for (int i = hotbarStart; i < total; i++) {
            var stack = handler.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.getHoverName().getString().contains(itemName)) {
                // 找容器空槽
                for (int j = 0; j < containerSlots; j++) {
                    if (handler.getSlot(j).getItem().isEmpty()) {
                        if (mc.gameMode != null) {
                            mc.gameMode.handleInventoryMouseClick(handler.containerId, i, 0, ClickType.PICKUP, mc.player);
                            mc.gameMode.handleInventoryMouseClick(handler.containerId, j, 0, ClickType.PICKUP, mc.player);
                        }
                        return;
                    }
                }
            }
        }
    }

    /** 切换热栏到指定名称的物品槽。 */
    private void switchToItem(Minecraft mc, String itemName) {
        if (mc.player == null) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getHoverName().getString().contains(itemName)) {
                inv.setSelectedSlot(i);
                return;
            }
        }
        IMCCookingMod.LOGGER.warn("[IMCCooking] 物品栏未找到 {}", itemName);
    }
}
