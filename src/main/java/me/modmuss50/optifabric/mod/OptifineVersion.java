package me.modmuss50.optifabric.mod;

import me.modmuss50.optifabric.patcher.ASMUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class OptifineVersion {


	public static String version;
	public static String minecraftVersion;
	public static JarType jarType;

	public static File findOptifineJar() throws IOException {
		File modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toFile();
		File[] mods = modsDir.listFiles();

		File optifineJar = null;

		if (mods != null) {
			for (File file : mods) {
				if (file.isDirectory()) {
					continue;
				}
				if (file.getName().endsWith(".jar")) {
					JarType type = getJarType(file);
					if (type.error) {
						if(!type.equals(JarType.INCOMPATIBLE)){
							throw new RuntimeException("An error occurred when trying to find the optifine jar: " + type.name());
						}else{
							continue;
						}
					}
					if (type == JarType.OPTIFINE_MOD || type == JarType.OPTIFINE_INSTALLER) {
						if(optifineJar != null){
							OptifabricError.setError("Found 2 or more optifine jars, please ensure you only have 1 copy of optifine in the mods folder!");
							throw new FileNotFoundException("Multiple optifine jars");
						}
						jarType = type;
						optifineJar =  file;
					}
				}
			}
		}

		if(optifineJar != null){
			return optifineJar;
		}

		OptifabricError.setError("OptiFabric could not find the Optifine jar in the mods folder.");
		throw new FileNotFoundException("Could not find optifine jar");
	}

	private static JarType getJarType(File file) throws IOException {
		ClassNode classNode;
		try (JarFile jarFile = new JarFile(file)) {
			JarEntry jarEntry = jarFile.getJarEntry("Config.class");
			if (jarEntry == null) {
				jarEntry = jarFile.getJarEntry("VersionThread.class");
			}
			System.out.println("jar entry: " + jarEntry);
			if (jarEntry == null) {
				return JarType.SOMETHING_ELSE;
			}
			classNode = ASMUtils.asClassNode(jarEntry, jarFile);
		}

		for (FieldNode fieldNode : classNode.fields) {
			if (fieldNode.name.equals("VERSION")) {
				version = (String) fieldNode.value;
			}
			if (fieldNode.name.equals("MC_VERSION")) {
				minecraftVersion = (String) fieldNode.value;
			}
		}

		if (version == null || version.isEmpty() || minecraftVersion == null || minecraftVersion.isEmpty()) {
			return JarType.INCOMPATIBLE;
		}

		String currentMcVersion = "1.7.10";

		if (!currentMcVersion.equals(minecraftVersion)) {
			OptifabricError.setError(String.format("This version of optifine is not compatible with the current minecraft version\n\n Optifine requires %s you have %s", minecraftVersion, currentMcVersion));
			return JarType.INCOMPATIBLE;
		}

		AtomicBoolean isInstaller = new AtomicBoolean(false);
		ZipUtil.iterate(file, (in, zipEntry) -> {
			if (zipEntry.getName().startsWith("patch/")) {
				isInstaller.set(true);
			}
		});

		if (isInstaller.get()) {
			return JarType.OPTIFINE_INSTALLER;
		} else {
			return JarType.OPTIFINE_MOD;
		}
	}

	public enum JarType {
		OPTIFINE_MOD(false),
		OPTIFINE_INSTALLER(false),
		INCOMPATIBLE(true),
		SOMETHING_ELSE(false);

		final boolean error;

		JarType(boolean error) {
			this.error = error;
		}

	}
}
