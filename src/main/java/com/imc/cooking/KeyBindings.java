package com.imc.cooking;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定（1.21.4 Yarn 映射）。
 *   I - 绑定厨具  B - 开始绑定  J - 开始工作
 *   P - 打开 GUI  O - 绑定存储  U - 跳过绑定
 */
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.imccooking.main";

    public static KeyBinding bindUtensil;   // I
    public static KeyBinding startBind;     // B
    public static KeyBinding startWork;     // J
    public static KeyBinding openGui;       // P
    public static KeyBinding bindStorage;   // O
    public static KeyBinding skipBind;      // U

    private KeyBindings() {
    }

    public static void register(Runnable onI, Runnable onB, Runnable onJ,
                                Runnable onP, Runnable onO, Runnable onU) {
        bindUtensil = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imccooking.bind_utensil", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY));
        startBind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imccooking.start_bind", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
        startWork = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imccooking.start_work", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imccooking.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY));
        bindStorage = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imccooking.bind_storage", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));
        skipBind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imccooking.skip_bind", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (bindUtensil.wasPressed()) onI.run();
            while (startBind.wasPressed()) onB.run();
            while (startWork.wasPressed()) onJ.run();
            while (openGui.wasPressed()) onP.run();
            while (bindStorage.wasPressed()) onO.run();
            while (skipBind.wasPressed()) onU.run();
        });
    }
}
