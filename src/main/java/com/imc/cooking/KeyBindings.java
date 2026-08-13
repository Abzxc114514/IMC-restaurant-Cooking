package com.imc.cooking;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定（见 NewModTraeLookMe.md 第1.3节）。
 *
 *   I - 绑定厨具
 *   B - 开始绑定
 *   J - 开始工作
 *   P - 打开 GUI
 *   O - 绑定菜品存储地
 *   U - 跳过此厨具绑定
 */
public final class KeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(IMCCookingMod.MOD_ID, "main"));

    public static KeyMapping bindUtensil;   // I
    public static KeyMapping startBind;     // B
    public static KeyMapping startWork;     // J
    public static KeyMapping openGui;       // P
    public static KeyMapping bindStorage;   // O
    public static KeyMapping skipBind;      // U

    private KeyBindings() {
    }

    public static void register(Runnable onI, Runnable onB, Runnable onJ,
                                Runnable onP, Runnable onO, Runnable onU) {
        bindUtensil = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imccooking.bind_utensil",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                CATEGORY
        ));
        startBind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imccooking.start_bind",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        ));
        startWork = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imccooking.start_work",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));
        openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imccooking.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY
        ));
        bindStorage = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imccooking.bind_storage",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));
        skipBind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imccooking.skip_bind",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (bindUtensil.consumeClick()) onI.run();
            while (startBind.consumeClick()) onB.run();
            while (startWork.consumeClick()) onJ.run();
            while (openGui.consumeClick()) onP.run();
            while (bindStorage.consumeClick()) onO.run();
            while (skipBind.consumeClick()) onU.run();
        });
    }
}
