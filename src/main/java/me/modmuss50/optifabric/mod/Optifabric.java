package me.modmuss50.optifabric.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.Sys;

@Environment(EnvType.CLIENT)
public class Optifabric implements ModInitializer {

	public static void checkForErrors() {
		if (OptifabricError.hasError()) {
			System.out.println("An OptiFabric error has occurred");
			System.out.println(-1);
		}
	}

	@Override
	public void onInitialize() {

	}
}
