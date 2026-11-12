package com.oidioomstudio.fastminer.mixin;

import com.oidioomstudio.fastminer.client.FastMinerClientMod;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(
            method = "keyPress",
            at = @At("RETURN"),
            cancellable = true
    )
    public void onKeyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window == mc.getWindow().handle()) {
            FastMinerClientMod client = FastMinerClientMod.getInstance();
            if (client != null && client.onKeyPress()) {
                ci.cancel();
            }
        }
    }
}
