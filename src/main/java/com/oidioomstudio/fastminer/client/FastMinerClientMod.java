package com.oidioomstudio.fastminer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oidioomstudio.fastminer.config.FastMinerConfig;
import com.oidioomstudio.fastminer.net.KeyPressedPayload;
import com.oidioomstudio.fastminer.net.ModeChangedPayload;
import com.oidioomstudio.fastminer.net.SyncShapePayload;
import com.oidioomstudio.fastminer.shape.FastMinerShape;
import com.oidioomstudio.fastminer.shape.ShapeRegistry;
import com.oidioomstudio.olib.api.OConfigCategory;
import com.oidioomstudio.olib.api.OConfigRegistry;
import com.oidioomstudio.olib.api.OModConfig;
import com.oidioomstudio.olib.api.hud.OHudElement;
import com.oidioomstudio.olib.api.hud.OHudRegistry;
import com.oidioomstudio.olib.gui.hud.OHudEditorScreen;
import com.oidioomstudio.olib.api.options.BooleanOption;
import com.oidioomstudio.olib.api.options.ColorOption;
import com.oidioomstudio.olib.api.options.FloatSliderOption;
import com.oidioomstudio.olib.api.options.IntegerSliderOption;
import com.oidioomstudio.olib.util.render.CachedEdge;
import com.oidioomstudio.olib.util.render.OBlockOutlineRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FastMinerClientMod implements ClientModInitializer {
    public static final Identifier GUI_OVERLAY_ID = Identifier.fromNamespaceAndPath("fastminer", "overlay");

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("fastminer", "main")
    );

    public static final KeyMapping KEY_ACTIVATE = new KeyMapping(
            "key.fastminer.activate",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_GRAVE, // `~` key (Identical to FTB Ultimine)
            CATEGORY
    );

    public static final KeyMapping KEY_CYCLE_NEXT = new KeyMapping(
            "key.fastminer.change_shape_next",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_UP,
            CATEGORY
    );

    public static final KeyMapping KEY_CYCLE_PREV = new KeyMapping(
            "key.fastminer.change_shape_prev",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_DOWN,
            CATEGORY
    );

    public static final KeyMapping KEY_MOVE_HUD = new KeyMapping(
            "key.fastminer.move_hud",
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    private static FastMinerClientMod instance;
    private static OHudElement syHudElement;

    public record IndentedLine(int indent, Component text) {}

    private boolean ultimineKeyPressed = false;
    private boolean wasKeyPressed = false;
    private boolean toggledActive = false;
    private int clientShapeIndex = 0;
    private long lastToggle = 0L;
    private float panelAlphaMult = 0.0f;
    private int maxPanelWidth = 0;
    private List<IndentedLine> infoPanelList = Collections.emptyList();

    private List<BlockPos> shapeBlocks = Collections.emptyList();
    private int actualBlocks = 0;
    private BlockPos cachedPos = null;
    private List<CachedEdge> cachedEdges = null;

    public static FastMinerClientMod getInstance() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        KeyMappingHelper.registerKeyMapping(KEY_ACTIVATE);
        KeyMappingHelper.registerKeyMapping(KEY_CYCLE_NEXT);
        KeyMappingHelper.registerKeyMapping(KEY_CYCLE_PREV);
        KeyMappingHelper.registerKeyMapping(KEY_MOVE_HUD);

        registerConfig();

        // Shape sync from server
        ClientPlayNetworking.registerGlobalReceiver(SyncShapePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                clientShapeIndex = payload.shapeIdx();
                FastMinerShape shape = ShapeRegistry.getShape(clientShapeIndex);
                if (shape != null && FastMinerConfig.get().shapeFeedbackMessage && !ultimineKeyPressed) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(
                                Component.translatable("fastminer.shape_changed", shape.getDisplayName())
                        );
                    }
                }
            });
        });

        // Client Tick: handle keypress & mode cycling
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);

        // Level Render: 3D Block Outline Rendering
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            renderInGame(context.poseStack(), context.submitNodeCollector(), context.levelState());
        });

        // Register to O Lib HUD System
        FastMinerConfig.Data cfg = FastMinerConfig.get();
        syHudElement = OHudElement.builder("fastminer", Component.literal("Fast Miner"))
                .defaultPos(cfg.hudX, cfg.hudY)
                .size(80, 24)
                .defaultVisible(cfg.showHud)
                .accentColor(0xFFA3E02C)
                .renderer((graphics, deltaTracker, x, y, width, height) -> {
                    renderGameOverlay(graphics, deltaTracker.getGameTimeDeltaPartialTick(false), x, y);
                })
                .build();
        OHudRegistry.register(syHudElement);
    }

    public boolean isUltimineKeyPressed() {
        FastMinerConfig.Data config = FastMinerConfig.get();
        return config.toggleMode ? toggledActive : KEY_ACTIVATE.isDown();
    }

    public boolean isMenuSneaking() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isShiftKeyDown();
    }

    public boolean onMouseScrolled(double xOffset, double yOffset) {
        if (isUltimineKeyPressed()) {
            if (yOffset != 0.0 || xOffset != 0.0) {
                if (isMenuSneaking()) {
                    boolean next = (xOffset < 0.0 || yOffset < 0.0);
                    ClientPlayNetworking.send(new ModeChangedPayload(next));
                    lastToggle = System.currentTimeMillis();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean onKeyPress() {
        if (System.currentTimeMillis() - lastToggle < 125L) {
            return false;
        }
        if (KEY_CYCLE_PREV.isDown()) {
            cycleCurrentShape(false);
            return true;
        } else if (KEY_CYCLE_NEXT.isDown()) {
            cycleCurrentShape(true);
            return true;
        }
        return false;
    }

    private void cycleCurrentShape(boolean next) {
        if (isUltimineKeyPressed() && isMenuSneaking()) {
            ClientPlayNetworking.send(new ModeChangedPayload(next));
            lastToggle = System.currentTimeMillis();
        }
    }

    private void clientTick(Minecraft client) {
        if (client.player == null) return;

        boolean isDown = KEY_ACTIVATE.isDown();
        FastMinerConfig.Data config = FastMinerConfig.get();

        if (config.toggleMode) {
            if (isDown && !wasKeyPressed) {
                toggledActive = !toggledActive;
                ClientPlayNetworking.send(new KeyPressedPayload(toggledActive));
            }
            ultimineKeyPressed = toggledActive;
        } else {
            if (isDown != wasKeyPressed) {
                ClientPlayNetworking.send(new KeyPressedPayload(isDown));
            }
            ultimineKeyPressed = isDown;
        }
        wasKeyPressed = isDown;

        // Shape key cycling
        while (KEY_CYCLE_NEXT.consumeClick()) {
            cycleCurrentShape(true);
        }
        while (KEY_CYCLE_PREV.consumeClick()) {
            cycleCurrentShape(false);
        }

        // Open O Lib HUD Editor Screen
        while (KEY_MOVE_HUD.consumeClick()) {
            client.gui.setScreen(new OHudEditorScreen(client.gui.screen()));
        }

        // Update block preview
        updateClientPreview(client);

        // Update info panel list
        if (ultimineKeyPressed) {
            infoPanelList = buildInfoPanelList();
        }
    }

    private void updateClientPreview(Minecraft client) {
        if (!ultimineKeyPressed || client.level == null || client.player == null) {
            shapeBlocks = Collections.emptyList();
            actualBlocks = 0;
            cachedEdges = null;
            cachedPos = null;
            return;
        }

        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos originPos = blockHit.getBlockPos();
            BlockState originState = client.level.getBlockState(originPos);
            Direction face = blockHit.getDirection();

            FastMinerShape shape = ShapeRegistry.getShape(clientShapeIndex);
            if (shape != null) {
                List<BlockPos> blocks = shape.getBlocks(
                        client.level,
                        client.player,
                        originPos,
                        face,
                        originState,
                        FastMinerConfig.get().maxBlocks
                );

                if (!blocks.isEmpty()) {
                    actualBlocks = blocks.size();
                    int maxRender = Math.min(actualBlocks, FastMinerConfig.get().maxRenderOutline);
                    List<BlockPos> sub = blocks.subList(0, maxRender);
                    if (cachedPos == null || !cachedPos.equals(originPos) || !sub.equals(shapeBlocks)) {
                        shapeBlocks = sub;
                        cachedPos = originPos;
                        updateEdges();
                    }
                    return;
                }
            }
        }

        shapeBlocks = Collections.emptyList();
        actualBlocks = 0;
        cachedEdges = null;
        cachedPos = null;
    }

    private void updateEdges() {
        if (shapeBlocks.isEmpty() || cachedPos == null) {
            cachedEdges = Collections.emptyList();
            return;
        }
        cachedEdges = OBlockOutlineRenderer.buildEdges(shapeBlocks, cachedPos);
    }

    public void renderInGame(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.LevelRenderState levelState) {
        if (!ultimineKeyPressed) {
            return;
        }

        OBlockOutlineRenderer.render(
                poseStack,
                collector,
                levelState,
                this.cachedPos,
                this.cachedEdges,
                FastMinerConfig.get().outlineColor,
                2.0f
        );
    }

    private List<IndentedLine> buildInfoPanelList() {
        List<IndentedLine> list = new ArrayList<>();
        boolean active = actualBlocks > 0;

        // Line 1: "Fast Miner" (No "is active" / "not active", customizable text color)
        int titleCol = FastMinerConfig.get().hudTextColor;
        list.add(new IndentedLine(0, Component.literal("Fast Miner").withStyle(style -> style.withColor(TextColor.fromRgb(titleCol & 0x00FFFFFF)))));

        int totalShapes = ShapeRegistry.getCount();
        int contextLines = Math.min((totalShapes - 1) / 2, 2);
        boolean sneaking = isMenuSneaking();

        if (sneaking) {
            // Shift menu: Context shape lines above current
            for (int i = -contextLines; i < 0; i++) {
                FastMinerShape shape = ShapeRegistry.getShape(clientShapeIndex + i);
                if (shape != null) {
                    list.add(new IndentedLine(16, shape.getDisplayName().copy().withStyle(ChatFormatting.GRAY)));
                }
            }
        }

        // Current selected shape:
        FastMinerShape currentShape = ShapeRegistry.getShape(clientShapeIndex);
        if (currentShape != null) {
            int indent = sneaking ? 16 : 0;
            MutableComponent shapeComp;
            int countCol = FastMinerConfig.get().hudBlockCountColor;
            Component countComp = Component.literal(" / " + actualBlocks).withStyle(style -> style.withColor(TextColor.fromRgb(countCol & 0x00FFFFFF)));

            if (sneaking) {
                shapeComp = currentShape.getDisplayName().copy().withStyle(ChatFormatting.YELLOW);
                shapeComp.append(countComp);
            } else {
                shapeComp = Component.literal("- ").append(currentShape.getDisplayName().copy());
                shapeComp.append(countComp);
            }
            list.add(new IndentedLine(indent, shapeComp));
        }

        if (sneaking) {
            // Context shape lines below current
            for (int i = 1; i <= contextLines; i++) {
                FastMinerShape shape = ShapeRegistry.getShape(clientShapeIndex + i);
                if (shape != null) {
                    list.add(new IndentedLine(16, shape.getDisplayName().copy().withStyle(ChatFormatting.GRAY)));
                }
            }
        }

        return list;
    }

    public int getMaxPanelWidth() {
        return maxPanelWidth;
    }

    public int getPanelHeight() {
        return 9 * Math.max(1, infoPanelList.size());
    }

    public void renderCustomHudAt(GuiGraphicsExtractor graphics, int x, int y, float alphaMult) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<IndentedLine> lines = infoPanelList;
        if (lines.isEmpty()) {
            lines = new ArrayList<>();
            lines.add(new IndentedLine(0, Component.literal("Fast Miner").withStyle(ChatFormatting.WHITE)));
            FastMinerShape shape = ShapeRegistry.getShape(clientShapeIndex);
            lines.add(new IndentedLine(0, Component.literal("- " + (shape != null ? shape.getDisplayName().getString() : "Şekilsiz") + " / 64").withStyle(ChatFormatting.WHITE)));
        }

        int maxLineWidth = 0;
        for (IndentedLine line : lines) {
            maxLineWidth = Math.max(maxLineWidth, font.width(line.text()) + line.indent());
        }

        int lineHeight = 9;
        int panelHeight = lineHeight * lines.size();

        int alpha = (int) (alphaMult * 180.0f);
        int bgColor = (alpha << 24) | 0x1E1E1E;

        // Dark translucent background behind HUD dynamically sized to text
        graphics.fill(x - 3, y - 2, x + maxLineWidth + 4, y + panelHeight + 2, bgColor);

        // Render each line with exact indentation and color
        int currentY = y;
        for (IndentedLine line : lines) {
            int lineAlpha = (int) (alphaMult * 255.0f);
            int textColor = (lineAlpha << 24) | 0xECEDE8;
            graphics.text(font, line.text().getVisualOrderText(), x + line.indent(), currentY, textColor, true);
            currentY += lineHeight;
        }
    }

    public void renderGameOverlay(GuiGraphicsExtractor graphics, float partialTick, int x, int y) {
        if (syHudElement != null && !syHudElement.isVisible()) return;
        if (!FastMinerConfig.get().showHud) return;

        if (ultimineKeyPressed) {
            panelAlphaMult = Math.min(1.0f, panelAlphaMult + 0.1f * partialTick);
        } else {
            panelAlphaMult = Math.max(0.0f, panelAlphaMult - 0.1f * partialTick);
        }

        if (panelAlphaMult <= 0.0f || infoPanelList.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int maxLineWidth = 0;
        for (IndentedLine line : infoPanelList) {
            maxLineWidth = Math.max(maxLineWidth, font.width(line.text()) + line.indent());
        }
        maxPanelWidth = maxLineWidth + 7;

        int lineHeight = 9;
        int panelHeight = lineHeight * infoPanelList.size();

        if (syHudElement != null) {
            syHudElement.setWidth(maxPanelWidth + 2);
            syHudElement.setHeight(panelHeight + 4);
            FastMinerConfig.get().hudX = syHudElement.getX();
            FastMinerConfig.get().hudY = syHudElement.getY();
        }

        int userBg = FastMinerConfig.get().hudBackgroundColor;
        int baseA = (userBg >> 24) & 0xFF;
        if (baseA == 0) baseA = 180;
        int alpha = (int) (panelAlphaMult * baseA);
        int bgColor = (alpha << 24) | (userBg & 0x00FFFFFF);

        // Translucent background behind HUD dynamically sized to text
        graphics.fill(x - 3, y - 2, x + maxLineWidth + 4, y + panelHeight + 2, bgColor);

        // Render each line with exact indentation and style color
        int currentY = y;
        for (IndentedLine line : infoPanelList) {
            graphics.text(font, line.text().getVisualOrderText(), x + line.indent(), currentY, 0xFFFFFFFF, true);
            currentY += lineHeight;
        }
    }

    private static void registerConfig() {
        FastMinerConfig.Data cfg = FastMinerConfig.get();

        OConfigRegistry.register(
                OModConfig.builder("fastminer", Component.translatable("fastminer.name"), "1.1.2")
                        .category(OConfigCategory.builder("general", Component.translatable("fastminer.config.general"))
                                .group("core", Component.translatable("fastminer.config.limits"), g -> g
                                        .add(IntegerSliderOption.builder("max_blocks", Component.translatable("fastminer.config.option.max_blocks"))
                                                .description(Component.translatable("fastminer.config.option.max_blocks.desc"))
                                                .range(1, 1024, 1)
                                                .defaultValue(64)
                                                .getter(() -> cfg.maxBlocks)
                                                .setter(v -> cfg.maxBlocks = v)
                                                .build())
                                        .add(FloatSliderOption.builder("exhaustion", Component.translatable("fastminer.config.option.exhaustion"))
                                                .description(Component.translatable("fastminer.config.option.exhaustion.desc"))
                                                .range(0.0f, 5.0f, 0.05f)
                                                .defaultValue(0.40f)
                                                .getter(() -> (float) cfg.exhaustionPerBlock)
                                                .setter(v -> cfg.exhaustionPerBlock = v)
                                                .build())
                                        .add(BooleanOption.builder("prevent_tool_break", Component.translatable("fastminer.config.option.prevent_tool_break"))
                                                .description(Component.translatable("fastminer.config.option.prevent_tool_break.desc"))
                                                .defaultValue(cfg.preventToolBreak)
                                                .getter(() -> cfg.preventToolBreak)
                                                .setter(v -> cfg.preventToolBreak = v)
                                                .build())
                                        .add(BooleanOption.builder("require_valid_tool", Component.translatable("fastminer.config.option.require_valid_tool"))
                                                .description(Component.translatable("fastminer.config.option.require_valid_tool.desc"))
                                                .defaultValue(cfg.requireValidToolForBlock)
                                                .getter(() -> cfg.requireValidToolForBlock)
                                                .setter(v -> cfg.requireValidToolForBlock = v)
                                                .build())
                                )
                                .group("features", Component.translatable("fastminer.config.features"), g -> g
                                        .add(BooleanOption.builder("teleport_items", Component.translatable("fastminer.config.option.teleport_items"))
                                                .description(Component.translatable("fastminer.config.option.teleport_items.desc"))
                                                .defaultValue(cfg.teleportItemsToInventory)
                                                .getter(() -> cfg.teleportItemsToInventory)
                                                .setter(v -> cfg.teleportItemsToInventory = v)
                                                .build())
                                        .add(BooleanOption.builder("teleport_xp", Component.translatable("fastminer.config.option.teleport_xp"))
                                                .description(Component.translatable("fastminer.config.option.teleport_xp.desc"))
                                                .defaultValue(cfg.teleportXpToPlayer)
                                                .getter(() -> cfg.teleportXpToPlayer)
                                                .setter(v -> cfg.teleportXpToPlayer = v)
                                                .build())
                                        .add(BooleanOption.builder("right_click_axe", Component.translatable("fastminer.config.option.right_click_axe"))
                                                .description(Component.translatable("fastminer.config.option.right_click_axe.desc"))
                                                .defaultValue(cfg.rightClickAxe)
                                                .getter(() -> cfg.rightClickAxe)
                                                .setter(v -> cfg.rightClickAxe = v)
                                                .build())
                                        .add(BooleanOption.builder("right_click_shovel", Component.translatable("fastminer.config.option.right_click_shovel"))
                                                .description(Component.translatable("fastminer.config.option.right_click_shovel.desc"))
                                                .defaultValue(cfg.rightClickShovel)
                                                .getter(() -> cfg.rightClickShovel)
                                                .setter(v -> cfg.rightClickShovel = v)
                                                .build())
                                        .add(BooleanOption.builder("right_click_hoe", Component.translatable("fastminer.config.option.right_click_hoe"))
                                                .description(Component.translatable("fastminer.config.option.right_click_hoe.desc"))
                                                .defaultValue(cfg.rightClickHoe)
                                                .getter(() -> cfg.rightClickHoe)
                                                .setter(v -> cfg.rightClickHoe = v)
                                                .build())
                                )
                                .group("visual", Component.translatable("fastminer.config.rendering"), g -> g
                                        .add(BooleanOption.builder("show_hud", Component.translatable("fastminer.config.option.show_hud"))
                                                .description(Component.translatable("fastminer.config.option.show_hud.desc"))
                                                .defaultValue(cfg.showHud)
                                                .getter(() -> cfg.showHud)
                                                .setter(v -> {
                                                     cfg.showHud = v;
                                                     if (syHudElement != null) syHudElement.setVisible(v);
                                                })
                                                .build())
                                        .add(BooleanOption.builder("drag_hud_screen", Component.translatable("fastminer.config.option.drag_hud_screen"))
                                                .description(Component.translatable("fastminer.config.option.drag_hud_screen.desc"))
                                                .labels(Component.translatable("olib.config.edit"), Component.translatable("olib.config.edit"))
                                                .actionButton(true)
                                                .defaultValue(false)
                                                .getter(() -> false)
                                                .setter(v -> {
                                                     Minecraft client = Minecraft.getInstance();
                                                     client.gui.setScreen(new OHudEditorScreen(client.gui.screen()));
                                                })
                                                .build())
                                        .add(ColorOption.builder("hud_bg_color", Component.translatable("fastminer.config.option.hud_bg_color"))
                                                .description(Component.translatable("fastminer.config.option.hud_bg_color.desc"))
                                                .hasAlpha(true)
                                                .defaultValue(cfg.hudBackgroundColor)
                                                .getter(() -> cfg.hudBackgroundColor)
                                                .setter(v -> cfg.hudBackgroundColor = v)
                                                .build())
                                        .add(ColorOption.builder("hud_text_color", Component.translatable("fastminer.config.option.hud_text_color"))
                                                .description(Component.translatable("fastminer.config.option.hud_text_color.desc"))
                                                .hasAlpha(false)
                                                .defaultValue(cfg.hudTextColor)
                                                .getter(() -> cfg.hudTextColor)
                                                .setter(v -> cfg.hudTextColor = v)
                                                .build())
                                        .add(ColorOption.builder("hud_block_count_color", Component.translatable("fastminer.config.option.hud_block_count_color"))
                                                .description(Component.translatable("fastminer.config.option.hud_block_count_color.desc"))
                                                .hasAlpha(false)
                                                .defaultValue(cfg.hudBlockCountColor)
                                                .getter(() -> cfg.hudBlockCountColor)
                                                .setter(v -> cfg.hudBlockCountColor = v)
                                                .build())
                                        .add(ColorOption.builder("outline_color", Component.translatable("fastminer.config.option.outline_color"))
                                                .description(Component.translatable("fastminer.config.option.outline_color.desc"))
                                                .hasAlpha(true)
                                                .defaultValue(cfg.outlineColor)
                                                .getter(() -> cfg.outlineColor)
                                                .setter(v -> cfg.outlineColor = v)
                                                .build())
                                        .add(BooleanOption.builder("shape_feedback", Component.translatable("fastminer.config.option.shape_feedback"))
                                                .description(Component.translatable("fastminer.config.option.shape_feedback.desc"))
                                                .defaultValue(cfg.shapeFeedbackMessage)
                                                .getter(() -> cfg.shapeFeedbackMessage)
                                                .setter(v -> cfg.shapeFeedbackMessage = v)
                                                .build())
                                )
                                .build()
                        )
                        .onSave(FastMinerConfig::save)
                        .build()
        );
    }
}
