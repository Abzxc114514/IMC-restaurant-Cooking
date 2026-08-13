package com.imc.cooking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IMC Restaurant Cooking 客户端入口。
 *
 * 按键（见 NewModTraeLookMe.md 第1.3节）：
 *   I - 绑定厨具（对准厨具方块按 I）
 *   B - 开始绑定流程
 *   J - 开始工作（执行烹饪流程）
 *   P - 打开 GUI（左侧菜品 / 右侧转头速度+移动速度）
 *   O - 绑定菜品存储地
 *   U - 跳过当前厨具绑定
 */
public class IMCCookingMod implements ClientModInitializer {
    public static final String MOD_ID = "imccooking";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static IMCCookingMod instance;

    private CookingConfig config;
    private UtensilBindingManager bindingManager;
    private CookingController controller;

    @Override
    public void onInitializeClient() {
        instance = this;
        config = CookingConfig.load();
        bindingManager = new UtensilBindingManager();
        controller = new CookingController(bindingManager, config);

        KeyBindings.register(
                this::onPressI,
                this::onPressB,
                this::onPressJ,
                this::onPressP,
                this::onPressO,
                this::onPressU
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        LOGGER.info("[IMCCooking] 已加载。B=开始绑定 I=绑定厨具 J=开始工作 P=打开GUI O=绑定存储 U=跳过");
    }

    public static IMCCookingMod getInstance() {
        return instance;
    }

    private void onPressI() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        bindingManager.bindUtensil(player);
    }

    private void onPressB() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        bindingManager.startBinding(player);
    }

    private void onPressJ() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (controller.isRunning()) {
            controller.stop(player);
        } else {
            controller.start(player);
        }
    }

    private void onPressP() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new CookingConfigScreen(config));
    }

    private void onPressO() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        bindingManager.bindStorage(player);
    }

    private void onPressU() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        bindingManager.skipCurrent(player);
    }

    private void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        controller.tick(player);
    }

    public CookingConfig getConfig() {
        return config;
    }

    public UtensilBindingManager getBindingManager() {
        return bindingManager;
    }

    public CookingController getController() {
        return controller;
    }

    /** 向玩家发送聊天框消息。 */
    public static void send(LocalPlayer player, Component component) {
        if (player != null) {
            player.displayClientMessage(component, false);
        }
    }

    /** 向玩家发送字符串消息。 */
    public static void send(LocalPlayer player, String text) {
        if (player != null) {
            player.displayClientMessage(Component.literal(text), false);
        }
    }
}
