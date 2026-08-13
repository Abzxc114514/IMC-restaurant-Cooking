package com.imc.cooking;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * 配置 GUI（1.21.4 Yarn 映射）。
 * 左侧：菜品列表（可点击选中）
 * 右侧：转头速度滑条 + 移动速度滑条 + 寻路目标坐标
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
        super(Text.translatable("gui.imccooking.title"));
        this.config = config;
    }

    @Override
    protected void init() {
        leftX = MARGIN;
        rightX = this.width - PANEL_WIDTH - MARGIN;
        topY = HEADER_HEIGHT;
        listY = topY + 16;
        listHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 16;

        selectedDishIndex = Math.max(0, DishList.DISHES.indexOf(config.selectedDish));

        int sliderY = listY;
        addDrawableChild(new DoubleSlider(rightX, sliderY, PANEL_WIDTH, 20,
                Text.translatable("gui.imccooking.turn_speed"),
                config.turnSpeed / 180.0,
                v -> config.turnSpeed = v * 180.0));

        sliderY += 30;
        addDrawableChild(new DoubleSlider(rightX, sliderY, PANEL_WIDTH, 20,
                Text.translatable("gui.imccooking.move_speed"),
                (config.moveSpeed - 0.1) / 1.9,
                v -> config.moveSpeed = 0.1 + v * 1.9));

        addDrawableChild(ButtonWidget.builder(Text.literal("§a保存并关闭"), b -> close())
                .dimensions(rightX, this.height - FOOTER_HEIGHT + 4, PANEL_WIDTH, 20)
                .build());
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        graphics.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        graphics.fill(leftX, topY, leftX + PANEL_WIDTH, this.height - FOOTER_HEIGHT, 0x80000000);
        graphics.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.imccooking.dishes"),
                leftX + PANEL_WIDTH / 2, topY + 4, 0xFFFFA0);

        graphics.fill(rightX, topY, rightX + PANEL_WIDTH, this.height - FOOTER_HEIGHT, 0x80000000);
        graphics.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.imccooking.settings"),
                rightX + PANEL_WIDTH / 2, topY + 4, 0xFFFFA0);

        hoveredDish = -1;
        for (int i = 0; i < DishList.DISHES.size(); i++) {
            int y = listY + i * 12;
            if (y + 12 > this.height - FOOTER_HEIGHT) break;
            String dish = DishList.DISHES.get(i);
            boolean sel = (i == selectedDishIndex);
            boolean impl = DishList.isImplemented(dish);

            if (mouseX >= leftX + 2 && mouseX < leftX + PANEL_WIDTH - 2
                    && mouseY >= y && mouseY < y + 12) {
                hoveredDish = i;
                graphics.fill(leftX + 2, y, leftX + PANEL_WIDTH - 2, y + 12, 0x60FFFFFF);
            }
            if (sel) {
                graphics.fill(leftX + 2, y, leftX + PANEL_WIDTH - 2, y + 12, 0x8040A0FF);
            }
            int color = sel ? 0xFFFFD0 : (impl ? 0xA0FFA0 : 0xAAAAAA);
            String label = (impl ? "✓ " : "  ") + dish;
            graphics.drawText(this.textRenderer, label, leftX + 6, y + 2, color, false);
        }

        int infoY = listY + 70;
        graphics.drawText(this.textRenderer, "§7寻路目标:", rightX + 6, infoY, 0xFFFFFF, false);
        graphics.drawText(this.textRenderer,
                String.format("§f%.1f %.1f %.1f", config.gotoX, config.gotoY, config.gotoZ),
                rightX + 6, infoY + 12, 0xA0FFA0, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    // 重写背景渲染：仅用纯色覆盖，不调用 super 以避免高斯模糊导致看不清
    @Override
    public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= leftX + 2 && mouseX < leftX + PANEL_WIDTH - 2) {
                for (int i = 0; i < DishList.DISHES.size(); i++) {
                    int y = listY + i * 12;
                    if (y + 12 > this.height - FOOTER_HEIGHT) break;
                    if (mouseY >= y && mouseY < y + 12) {
                        selectedDishIndex = i;
                        config.selectedDish = DishList.DISHES.get(selectedDishIndex);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static class DoubleSlider extends SliderWidget {
        private final java.util.function.DoubleConsumer onChange;

        DoubleSlider(int x, int y, int w, int h, Text label, double initialValue,
                     java.util.function.DoubleConsumer onChange) {
            super(x, y, w, h, label, initialValue);
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
        }

        @Override
        protected void applyValue() {
            onChange.accept(this.value);
        }
    }
}
