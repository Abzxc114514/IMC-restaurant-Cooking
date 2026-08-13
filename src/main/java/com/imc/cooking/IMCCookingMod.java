package com.imc.cooking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IMC Restaurant Cooking 客户端入口（1.21.4 Yarn 映射）。
 *
 * 按键（见 NewModTraeLookMe.md 第1.3节）：
 *   I - 绑定厨具  B - 开始绑定  J - 开始工作
 *   P - 打开 GUI  O - 绑定存储  U - 跳过绑定
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
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        bindingManager.bindUtensil(player);
    }

    private void onPressB() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        bindingManager.startBinding(player);
    }

    private void onPressJ() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (controller.isRunning()) {
            controller.stop(player);
        } else {
            controller.start(player);
        }
    }

    private void onPressP() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new CookingConfigScreen(config));
    }

    private void onPressO() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        bindingManager.bindStorage(player);
    }

    private void onPressU() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        bindingManager.skipCurrent(player);
    }

    private void onClientTick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        controller.tick(player);
    }

    public CookingConfig getConfig() { return config; }
    public UtensilBindingManager getBindingManager() { return bindingManager; }
    public CookingController getController() { return controller; }

    public static void send(ClientPlayerEntity player, Text text) {
        if (player != null) player.sendMessage(text, false);
    }

    public static void send(ClientPlayerEntity player, String text) {
        if (player != null) player.sendMessage(Text.literal(text), false);
    }
}
