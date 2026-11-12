package com.oidioomstudio.fastminer.shape;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BlockMatcher {
    public static boolean matches(BlockState origin, BlockState target, boolean shapeless) {
        if (origin.is(target.getBlock())) {
            return true;
        }

        // Crops equivalence
        if (origin.getBlock() instanceof CropBlock && target.getBlock() instanceof CropBlock) {
            return origin.getBlock() == target.getBlock();
        }

        // Ore matching
        if (origin.is(BlockTags.ORES) && target.is(BlockTags.ORES)) {
            // Check specific ore groups
            if (origin.is(BlockTags.IRON_ORES) && target.is(BlockTags.IRON_ORES)) return true;
            if (origin.is(BlockTags.GOLD_ORES) && target.is(BlockTags.GOLD_ORES)) return true;
            if (origin.is(BlockTags.COPPER_ORES) && target.is(BlockTags.COPPER_ORES)) return true;

            // Name-based match for diamond, coal, redstone, lapis, emerald
            Identifier id1 = BuiltInRegistries.BLOCK.getKey(origin.getBlock());
            Identifier id2 = BuiltInRegistries.BLOCK.getKey(target.getBlock());
            if (id1 != null && id2 != null) {
                String p1 = id1.getPath();
                String p2 = id2.getPath();
                if (isSameOreType(p1, p2, "diamond")) return true;
                if (isSameOreType(p1, p2, "coal")) return true;
                if (isSameOreType(p1, p2, "redstone")) return true;
                if (isSameOreType(p1, p2, "lapis")) return true;
                if (isSameOreType(p1, p2, "emerald")) return true;
                if (isSameOreType(p1, p2, "quartz")) return true;
            }
        }

        // Logs matching: must be same wood family AND both stripped or both unstripped
        if (origin.is(BlockTags.LOGS) && target.is(BlockTags.LOGS)) {
            Identifier id1 = BuiltInRegistries.BLOCK.getKey(origin.getBlock());
            Identifier id2 = BuiltInRegistries.BLOCK.getKey(target.getBlock());
            if (id1 != null && id2 != null) {
                String p1 = id1.getPath();
                String p2 = id2.getPath();
                boolean s1 = p1.contains("stripped_");
                boolean s2 = p2.contains("stripped_");
                // Stripped logs and normal logs never match
                if (s1 != s2) {
                    return false;
                }
                return getWoodType(p1).equals(getWoodType(p2));
            }
        }

        // Leaves matching: same species only
        if (origin.is(BlockTags.LEAVES) && target.is(BlockTags.LEAVES)) {
            Identifier id1 = BuiltInRegistries.BLOCK.getKey(origin.getBlock());
            Identifier id2 = BuiltInRegistries.BLOCK.getKey(target.getBlock());
            if (id1 != null && id2 != null) {
                return id1.equals(id2);
            }
        }

        // Note: Dirt & Soil variants (dirt, coarse dirt, grass block, podzol, rooted dirt, mud)
        // are intentionally NOT grouped together so each variant remains separate.

        // Overworld Base Stone in tunnels only (when not in shapeless vein mode)
        if (!shapeless && origin.is(BlockTags.BASE_STONE_OVERWORLD) && target.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return true;
        }

        // Nether Base Stone in tunnels only
        if (!shapeless && origin.is(BlockTags.BASE_STONE_NETHER) && target.is(BlockTags.BASE_STONE_NETHER)) {
            return true;
        }

        return false;
    }

    private static String getWoodType(String path) {
        return path.replace("stripped_", "")
                .replace("_log", "")
                .replace("_wood", "")
                .replace("_stem", "")
                .replace("_hyphae", "");
    }

    private static boolean isSameOreType(String path1, String path2, String oreName) {
        return path1.contains(oreName) && path2.contains(oreName);
    }
}
