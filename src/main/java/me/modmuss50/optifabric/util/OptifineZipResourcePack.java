package me.modmuss50.optifabric.util;

import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ZipResourcePack;

import java.io.File;
import java.util.function.Supplier;

public class OptifineZipResourcePack extends ZipResourcePack {
	public OptifineZipResourcePack(File file) {
		super(file);
	}

	@Override
	public String getName() {
		return "Optifine Internal Resources";
	}

	public static Supplier<ResourcePack> getSupplier(File file) {
		return () -> new OptifineZipResourcePack(file);
	}
}
