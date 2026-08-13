package com.imc.cooking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 配置 GUI（见 NewModTraeLookMe.md 第1.1节）。
 *
 * 布局：
 *   左侧：菜品列表（可点击选中，选中后高亮）
 *   右侧：转头速度滑条 + 移动速度滑条 + 寻路目标坐标
 *
 * 按 ESC 关闭时自动保存配置。
 */
public class CookingConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 140;
    private static final int MARGIN = 10;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 30;

    private final CookingConfig config;

    private int leftX;
    private int rightX;
    private int topY;
    private int listY;
    private int listHeight;

    private int hoveredDish = -1;
    private int selectedDishIndex = 0;

    public CookingConfigScreen(CookingConfig config) {
        super(Component.translatable("gui.imccooking.title"));
        this.config = config;
    }

    @Override
    protected void init() {
        leftX = MARGIN;
        rightX = this.width - PANEL_WIDTH - MARGIN;
        topY = HEADER_HEIGHT;
        listY = topY + 16;
        listHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 16;

        // 初始化选中菜品下标
        selectedDishIndex = Math.max(0, DishList.DISHES.indexOf(config.selectedDish));

        // 右侧：转头速度滑条
        int sliderY = listY;
        addRenderableWidget(new DoubleSlider(rightX, sliderY, PANEL_WIDTH, 20,
                Component.translatable("gui.imccooking.turn_speed"),
                config.turnSpeed / 180.0,
                v -> config.turnSpeed = v * 180.0,
                "°/tick"));

        // 右侧：移动速度滑条
        sliderY += 30;
        addRenderableWidget(new DoubleSlider(rightX, sliderY, PANEL_WIDTH, 20,
                Component.translatable("gui.imccooking.move_speed"),
                (config.moveSpeed - 0.1) / 1.9,
                v -> config.moveSpeed = 0.1 + v * 1.9,
                "x"));

        // 右侧：保存并关闭按钮
        addRenderableWidget(Button.builder(Component.literal("§a保存并关闭"), b -> onClose())
                .bounds(rightX, this.height - FOOTER_HEIGHT + 4, PANEL_WIDTH, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 super 的 renderBackground，避免高斯模糊导致看不清
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);

        // 标题
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        // 左侧菜品面板背景
        graphics.fill(leftX, topY, leftX + PANEL_WIDTH, this.height - FOOTER_HEIGHT, 0x80000000);
        graphics.drawCenteredString(this.font, Component.translatable("gui.imccooking.dishes"),
                leftX + PANEL_WIDTH / 2, topY + 4, 0xFFFFA0);

        // 右侧设置面板背景
        graphics.fill(rightX, topY, rightX + PANEL_WIDTH, this.height - FOOTER_HEIGHT, 0x80000000);
        graphics.drawCenteredString(this.font, Component.translatable("gui.imccooking.settings"),
                rightX + PANEL_WIDTH / 2, topY + 4, 0xFFFFA0);

        // 渲染菜品列表
        hoveredDish = -1;
        for (int i = 0; i < DishList.DISHES.size(); i++) {
            int y = listY + i * 12;
            if (y + 12 > this.height - FOOTER_HEIGHT) break;
            String dish = DishList.DISHES.get(i);
            boolean sel = (i == selectedDishIndex);
            boolean impl = DishList.isImplemented(dish);

            // 鼠标悬停高亮
            if (mouseX >= leftX + 2 && mouseX < leftX + PANEL_WIDTH - 2
                    && mouseY >= y && mouseY < y + 12) {
                hoveredDish = i;
                graphics.fill(leftX + 2, y, leftX + PANEL_WIDTH - 2, y + 12, 0x60FFFFFF);
            }
            // 选中高亮
            if (sel) {
                graphics.fill(leftX + 2, y, leftX + PANEL_WIDTH - 2, y + 12, 0x8040A0FF);
            }
            int color = sel ? 0xFFFFD0 : (impl ? 0xA0FFA0 : 0xAAAAAA);
            String label = (impl ? "✓ " : "  ") + dish;
            graphics.drawString(this.font, label, leftX + 6, y + 2, color, false);
        }

        // 渲染坐标提示
        int infoY = listY + 70;
        graphics.drawString(this.font, "§7寻路目标:", rightX + 6, infoY, 0xFFFFFF, false);
        graphics.drawString(this.font,
                String.format("§f%.1f %.1f %.1f", config.gotoX, config.gotoY, config.gotoZ),
                rightX + 6, infoY + 12, 0xA0FFA0, false);

        // 渲染控件
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean inside) {
        // 点击菜品列表：根据事件坐标计算点击的菜品下标
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            if (mx >= leftX + 2 && mx < leftX + PANEL_WIDTH - 2) {
                for (int i = 0; i < DishList.DISHES.size(); i++) {
                    int y = listY + i * 12;
                    if (y + 12 > this.height - FOOTER_HEIGHT) break;
                    if (my >= y && my < y + 12) {
                        selectedDishIndex = i;
                        config.selectedDish = DishList.DISHES.get(selectedDishIndex);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, inside);
    }

    @Override
    public void onClose() {
        config.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 双精度滑条按钮（0.0~1.0 归一化，回调返回真实值）。 */
    private static class DoubleSlider extends AbstractSliderButton {
        private final Component label;
        private final java.util.function.DoubleConsumer onChange;
        private final String unit;

        DoubleSlider(int x, int y, int w, int h, Component label,
                     double initialValue, java.util.function.DoubleConsumer onChange, String unit) {
            super(x, y, w, h, label, initialValue);
            this.label = label;
            this.onChange = onChange;
            this.unit = unit;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            // 在滑条上显示标签 + 当前数值
            // 注：onChange 此时未触发，需从 value 反推
        }

        @Override
        protected void applyValue() {
            onChange.accept(value);
        }
    }
}
