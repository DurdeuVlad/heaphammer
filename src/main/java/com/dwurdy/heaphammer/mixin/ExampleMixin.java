package com.dwurdy.heaphammer.mixin;

import com.dwurdy.heaphammer.HeapHammer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class ExampleMixin {
	@Inject(at = @At("HEAD"), method = "loadLevel")
	private void onServerLoadLevel(CallbackInfo info) {
		HeapHammer.LOGGER.info("HeapHammer: MinecraftServer.loadLevel invoked.");
	}
}
