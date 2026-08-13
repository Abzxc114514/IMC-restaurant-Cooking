package com.imc.cooking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Baritone 软依赖桥接（1.21.4 Yarn 映射）。
 * 通过反射调用 baritone-api，失败则降级发送 #goto 聊天指令。
 */
public final class BaritoneBridge {

    private static Boolean apiAvailable = null;
    private static Object baritoneInstance;
    private static java.lang.reflect.Method isPathingMethod;
    private static java.lang.reflect.Method cancelMethod;
    private static boolean fallbackActive = false;

    private BaritoneBridge() {
    }

    private static boolean init() {
        if (apiAvailable != null) return apiAvailable;
        try {
            Class<?> providerClass = Class.forName("baritone.api.BaritoneAPI");
            java.lang.reflect.Method getProvider = providerClass.getMethod("getProvider");
            Object provider = getProvider.invoke(null);
            java.lang.reflect.Method getBaritone = provider.getClass().getMethod("getPrimaryBaritone");
            Object baritone = getBaritone.invoke(provider);
            java.lang.reflect.Method getBehavior = baritone.getClass().getMethod("getPathingBehavior");
            baritoneInstance = getBehavior.invoke(baritone);
            isPathingMethod = baritoneInstance.getClass().getMethod("isPathing");
            cancelMethod = baritoneInstance.getClass().getMethod("cancelEverything");
            apiAvailable = true;
            return true;
        } catch (Throwable t) {
            apiAvailable = false;
            return false;
        }
    }

    public static void gotoPos(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (init()) {
            try {
                Class<?> goalClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
                java.lang.reflect.Constructor<?> ctor = goalClass.getConstructor(int.class, int.class, int.class);
                Object goal = ctor.newInstance((int) x, (int) y, (int) z);
                java.lang.reflect.Method setGoal = baritoneInstance.getClass().getMethod("setGoal",
                        Class.forName("baritone.api.pathing.goals.Goal"));
                setGoal.invoke(baritoneInstance, goal);
                java.lang.reflect.Method path = baritoneInstance.getClass().getMethod("path");
                path.invoke(baritoneInstance);
                fallbackActive = false;
                return;
            } catch (Throwable t) {
                IMCCookingMod.LOGGER.warn("[IMCCooking] Baritone API 调用失败，降级为聊天指令", t);
            }
        }
        String cmd = String.format("#goto %d %d %d", (int) x, (int) y, (int) z);
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatMessage(cmd);
        }
        fallbackActive = true;
    }

    public static boolean isPathing() {
        if (init() && !fallbackActive) {
            try {
                return Boolean.TRUE.equals(isPathingMethod.invoke(baritoneInstance));
            } catch (Throwable t) {
                return false;
            }
        }
        return fallbackActive;
    }

    public static void stop() {
        if (init() && !fallbackActive) {
            try {
                cancelMethod.invoke(baritoneInstance);
            } catch (Throwable t) {
                // ignore
            }
        } else if (fallbackActive) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendChatMessage("#stop");
            }
        }
    }

    public static boolean hasReached(ClientPlayerEntity player, double x, double y, double z, double threshold) {
        if (player == null) return true;
        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double dz = player.getZ() - z;
        return dx * dx + dy * dy + dz * dz < threshold * threshold;
    }
}
