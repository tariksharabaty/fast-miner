package com.oidioomstudio.fastminer.config;

import com.oidioomstudio.olib.config.OConfigHolder;

public class FastMinerConfig {
    public static class Data {
        // Limits & Behavior
        public int maxBlocks = 64;
        public boolean requireTool = false;
        public boolean requireValidToolForBlock = false;
        public boolean preventToolBreak = true; // Protect tool when durability reaches 1
        public double exhaustionPerBlock = 0.40; // Balanced hunger exhaustion multiplier (default: 0.40)
        public int cooldownTicks = 0;
        public boolean teleportItemsToInventory = false; // Auto-pickup items directly to inventory (default: false)
        public boolean teleportXpToPlayer = false; // Auto-pickup XP directly to player (default: false)

        // Right-Click Features
        public boolean rightClickAxe = true;
        public boolean rightClickShovel = true;
        public boolean rightClickHoe = true;
        public boolean rightClickHarvesting = true;

        // Visual / Client settings
        public int maxRenderOutline = 128;
        public int outlineColor = 0xFFFFFFFF;
        public boolean showHud = true;
        public int hudX = 5;
        public int hudY = 5;
        public boolean toggleMode = false;
        public boolean shapeFeedbackMessage = true;
        public int hudBackgroundColor = 0xB41E1E1E; // O Lib color with alpha
        public int hudTextColor = 0xFFFFFFFF;
        public int hudBlockCountColor = 0xFFA3E02C;
    }

    private static final OConfigHolder<Data> HOLDER = new OConfigHolder<>(
            "fastminer",
            Data.class,
            Data::new
    );

    private static Data current = new Data();

    public static void load() {
        current = HOLDER.load();
    }

    public static void save() {
        HOLDER.save();
    }

    public static Data get() {
        if (current == null) {
            load();
        }
        return current;
    }
}
