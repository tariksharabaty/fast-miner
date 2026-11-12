package com.oidioomstudio.fastminer.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.oidioomstudio.fastminer.client.FastMinerClientMod;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(
            method = "onScroll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z",
                    ordinal = 0
            ),
            cancellable = true
    )
    public void onRawMouseScrolled(
            long handle,
            double xOffset,
            double yOffset,
            CallbackInfo ci,
            @Local(name = "scaledXOffset") double scaledXOffset,
            @Local(name = "scaledYOffset") double scaledYOffset
    ) {
        if (!ci.isCancelled()) {
            FastMinerClientMod client = FastMinerClientMod.getInstance();
            if (client != null && client.onMouseScrolled(scaledXOffset, scaledYOffset)) {
                ci.cancel();
            }
        }
    }
}
