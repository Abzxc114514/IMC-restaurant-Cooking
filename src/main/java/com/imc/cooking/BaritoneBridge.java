package com.imc.cooking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Baritone 软依赖桥接。
 *
 * 通过反射调用 baritone-api，避免编译期硬依赖。
 * 如果玩家未安装 Baritone，则降级为发送 #goto 聊天指令（Baritone 监听公共聊天）。
 *
 * 用法：
 *   BaritoneBridge.goto(x, y, z)    寻路到坐标
 *   BaritoneBridge.isPathing()      是否正在寻路
 *   BaritoneBridge.stop()           停止寻路
 */
public final class BaritoneBridge {

    private static Boolean apiAvailable = null;
    private static Class<?> baritoneClass;
    private static Class<?> pathingBehaviorClass;
    private static Object baritoneInstance;
    private static java.lang.reflect.Method isPathingMethod;
    private static java.lang.reflect.Method cancelMethod;

    private static boolean fallbackActive = false; // 是否用聊天指令降级

    private BaritoneBridge() {
    }

    /** 初始化反射句柄。返回 true 表示可用（API 方式）。 */
    private static boolean init() {
        if (apiAvailable != null) return apiAvailable;
        try {
            Class<?> providerClass = Class.forName("baritone.api.BaritoneAPI");
            java.lang.reflect.Method getProvider = providerClass.getMethod("getProvider");
            Object provider = getProvider.invoke(null);
            java.lang.reflect.Method getBaritone = provider.getClass().getMethod("getPrimaryBaritone");
            baritoneInstance = getBaritone.invoke(provider);
            java.lang.reflect.Method getBehavior = baritoneInstance.getClass().getMethod("getPathingBehavior");
            pathingBehaviorClass = Class.forName("baritone.api.behavior.IPathingBehavior");
            Object behavior = getBehavior.invoke(baritoneInstance);
            baritoneInstance = behavior;
            isPathingMethod = pathingBehaviorClass.getMethod("isPathing");
            cancelMethod = pathingBehaviorClass.getMethod("cancelEverything");
            apiAvailable = true;
            return true;
        } catch (Throwable t) {
            apiAvailable = false;
            return false;
        }
    }

    /**
     * 寻路到指定坐标。
     * 优先调用 Baritone API；失败则发送 #goto 指令到聊天。
     */
    public static void gotoPos(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (init()) {
            try {
                Class<?> goalClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
                java.lang.reflect.Constructor<?> ctor = goalClass.getConstructor(int.class, int.class, int.class);
                Object goal = ctor.newInstance((int) x, (int) y, (int) z);
                Class<?> behaviorClass = baritoneInstance.getClass();
                java.lang.reflect.Method setGoal = behaviorClass.getMethod("setGoal", Class.forName("baritone.api.pathing.goals.Goal"));
                setGoal.invoke(baritoneInstance, goal);
                java.lang.reflect.Method path = behaviorClass.getMethod("path");
                path.invoke(baritoneInstance);
                fallbackActive = false;
                return;
            } catch (Throwable t) {
                IMCCookingMod.LOGGER.warn("[IMCCooking] Baritone API 调用失败，降级为聊天指令", t);
            }
        }
        // 降级：发送 #goto 指令
        String cmd = String.format("#goto %d %d %d", (int) x, (int) y, (int) z);
        if (mc.player.connection != null) {
            mc.player.connection.sendChat(cmd);
        }
        fallbackActive = true;
    }

    /** 是否正在寻路。 */
    public static boolean isPathing() {
        if (init() && !fallbackActive) {
            try {
                Object result = isPathingMethod.invoke(baritoneInstance);
                return Boolean.TRUE.equals(result);
            } catch (Throwable t) {
                return false;
            }
        }
        // 降级模式下无法准确判断，由调用方用距离判断
        return fallbackActive;
    }

    /** 停止寻路。 */
    public static void stop() {
        if (init() && !fallbackActive) {
            try {
                cancelMethod.invoke(baritoneInstance);
            } catch (Throwable t) {
                // ignore
            }
        } else if (fallbackActive) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendChat("#stop");
            }
        }
    }

    /**
     * 等待到达目标：返回 true 表示已到达（距离小于阈值）。
     * 用于状态机中轮询。
     */
    public static boolean hasReached(LocalPlayer player, double x, double y, double z, double threshold) {
        if (player == null) return true;
        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double dz = player.getZ() - z;
        return dx * dx + dy * dy + dz * dz < threshold * threshold;
    }
}
