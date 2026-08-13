package com.imc.cooking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 烹饪配置（GUI 编辑）。
 *
 * 包含：
 *   - turnSpeed：转头速度（tick 数，值越小越快）
 *   - moveSpeed：移动速度倍率（传给 Baritone）
 *   - selectedDish：GUI 中当前选中的菜品
 *   - baritoneGotoTarget：寻路目标坐标（默认文档中的 -13.7 128 -4.5）
 */
public class CookingConfig {

    private static final Path CONFIG_DIR = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("imc_cooking.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 转头速度：每 tick 旋转的角度。值越大转得越快。 */
    public double turnSpeed = 30.0;

    /** 移动速度倍率（传给 Baritone 的 movementSpeed，范围 0.1~2.0）。 */
    public double moveSpeed = 1.0;

    /** GUI 中当前选中的菜品名。 */
    public String selectedDish = "中式汉堡";

    /** Baritone 寻路目标（文档中的村民坐标）。 */
    public double gotoX = -13.7;
    public double gotoY = 128.0;
    public double gotoZ = -4.5;

    public static CookingConfig load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                CookingConfig cfg = GSON.fromJson(json, CookingConfig.class);
                return cfg != null ? cfg : new CookingConfig();
            }
        } catch (IOException e) {
            IMCCookingMod.LOGGER.warn("[IMCCooking] 配置加载失败，使用默认值", e);
        }
        return new CookingConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(this));
        } catch (IOException e) {
            IMCCookingMod.LOGGER.error("[IMCCooking] 配置保存失败", e);
        }
    }
}
