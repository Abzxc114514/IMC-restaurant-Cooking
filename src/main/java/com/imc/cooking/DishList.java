package com.imc.cooking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜系列表（见 NewModTraeLookMe.md 第"菜系与厨具"节）。
 *
 * 15 道基础完成品。每个菜品对应一段烹饪流程定义。
 * 当前版本完整实现"中式汉堡"流程，其余菜品预留流程入口。
 */
public final class DishList {

    /** 所有菜品（按文档顺序）。 */
    public static final List<String> DISHES = List.of(
            "猪肉大葱饺子",
            "猪肉白菜饺子",
            "牛肉饺子",
            "羊肉饺子",
            "鳕鱼饺子",
            "韭菜鸡蛋饺子",
            "葱花饼",
            "韭菜炒鸡蛋",
            "葱爆羊肉",
            "炸猪排",
            "炸鸡排",
            "炸鳕鱼",
            "薯条",
            "炸鱼薯条",
            "中式汉堡"
    );

    /** 菜品 -> 是否已实现完整流程。 */
    public static final Map<String, Boolean> IMPLEMENTED;

    static {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String d : DISHES) {
            m.put(d, false);
        }
        m.put("中式汉堡", true); // 当前版本完整实现
        IMPLEMENTED = Collections.unmodifiableMap(m);
    }

    private DishList() {
    }

    public static boolean isDish(String name) {
        if (name == null) return false;
        return DISHES.contains(name);
    }

    public static boolean isImplemented(String dish) {
        return Boolean.TRUE.equals(IMPLEMENTED.get(dish));
    }
}
