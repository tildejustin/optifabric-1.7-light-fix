package me.modmuss50.optifabric.mixin;

import me.modmuss50.optifabric.mod.Optifabric;
import me.modmuss50.optifabric.mod.OptifabricError;
import me.modmuss50.optifabric.mod.OptifineVersion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {

	protected MixinTitleScreen() {
		super();
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void init(CallbackInfo info) {
		Optifabric.checkForErrors();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void render(int int_1, int int_2, float float_1, CallbackInfo info) {
		if (!OptifabricError.hasError()) {
			this.drawWithShadow(MinecraftClient.getInstance().textRenderer, OptifineVersion.version, 2, this.height - 20, 0xFFFFFFFF);
		}
	}
}
