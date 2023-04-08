package me.modmuss50.optifabric.mod;

import me.modmuss50.optifabric.patcher.ClassCache;
import me.modmuss50.optifabric.patcher.LambdaRebuilder;
import me.modmuss50.optifabric.patcher.PatchSplitter;
import me.modmuss50.optifabric.patcher.RemapUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.mappings.TinyRemapperMappingsHelper;
import net.fabricmc.mapping.reader.v2.TinyMetadata;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class OptifineSetup {

	private final File workingDir = FabricLoader.getInstance().getGameDir().resolve(".optifine").toFile();
	private File versionDir;
	private final MappingConfiguration mappingConfiguration = new MappingConfiguration();

	private final FabricLauncher fabricLauncher = FabricLauncherBase.getLauncher();


	public Pair<File, ClassCache> getRuntime() throws Throwable {
		if (!workingDir.exists()) {
			workingDir.mkdirs();
		}
		File optifineModJar = OptifineVersion.findOptifineJar();

		byte[] modHash = fileHash(optifineModJar);

		versionDir = new File(workingDir, OptifineVersion.version);
		if (!versionDir.exists()) {
			versionDir.mkdirs();
		}

		File remappedJar = new File(versionDir, "optifine-mapped.jar");
		File optifinePatches = new File(versionDir, "optifine.classes");

		ClassCache classCache = null;
		if(remappedJar.exists() && optifinePatches.exists()){
			classCache = ClassCache.read(optifinePatches);
			//Validate that the classCache found is for the same input jar
			if(!Arrays.equals(classCache.getHash(), modHash)){
				System.out.println("Class cache is from a different optifine jar, deleting and re-generating");
				classCache = null;
				optifinePatches.delete();
			}
		}

		if (remappedJar.exists() && classCache != null) {
			System.out.println("Found existing patched optifine jar, using that");
			return Pair.of(remappedJar, classCache);
		}

		if (OptifineVersion.jarType == OptifineVersion.JarType.OPTIFINE_INSTALLER) {
			File optifineMod = new File(versionDir, "/optifine-mod.jar");
			if (!optifineMod.exists()) {
				OptifineInstaller.extract(optifineModJar, optifineMod, getMinecraftJar().toFile());
			}
			optifineModJar = optifineMod;
		}

		System.out.println("Setting up optifine for the first time, this may take a few seconds.");

		//A jar without srgs
		File jarOfTheFree = new File(versionDir, "/optifine-jar-of-the-free.jar");
		List<String> srgs = new ArrayList<>();

		System.out.println("De-Volderfiying jar");

		//Find all the SRG named classes and remove them
		ZipUtil.iterate(optifineModJar, (in, zipEntry) -> {
			String name = zipEntry.getName();
			if(name.startsWith("com/mojang/blaze3d/platform/")){
				if(name.contains("$")){
					String[] split = name.replace(".class", "").split("\\$");
					if(split.length >= 2){
						if(split[1].length() > 2){
							srgs.add(name);
						}
					}
				}
			}

			if(name.startsWith("srg/") || name.startsWith("net/minecraft/")){
				srgs.add(name);
			}
		});

		if(jarOfTheFree.exists()){
			jarOfTheFree.delete();
		}

		ZipUtil.removeEntries(optifineModJar, srgs.toArray(new String[0]), jarOfTheFree);

		System.out.println("Building lambada fix mappings");
		LambdaRebuilder rebuilder = new LambdaRebuilder(jarOfTheFree, getMinecraftJar().toFile());
		rebuilder.buildLambadaMap();

		System.out.println("Remapping optifine with fixed lambada names");
		File lambadaFixJar = new File(versionDir, "/optifine-lambda-fix.jar");
		RemapUtils.mapJar(lambadaFixJar.toPath(), jarOfTheFree.toPath(), rebuilder, getLibs());

		remapOptifine(lambadaFixJar.toPath(), remappedJar);

		classCache = PatchSplitter.generateClassCache(remappedJar, optifinePatches, modHash);

		//We are done, lets get rid of the stuff we no longer need
		lambadaFixJar.delete();
		jarOfTheFree.delete();

		if(OptifineVersion.jarType == OptifineVersion.JarType.OPTIFINE_INSTALLER){
			optifineModJar.delete();
		}

		File extractedMappings = new File(versionDir, "mappings.tiny");
		File fieldMappings = new File(versionDir, "mappings.full.tiny");
		extractedMappings.delete();
		fieldMappings.delete();

		boolean extractClasses = Boolean.parseBoolean(System.getProperty("optifabric.extract", "false"));
		if(extractClasses){
			System.out.println("Extracting optifine classes");
			File optifineClasses = new File(versionDir, "optifine-classes");
			if(optifineClasses.exists()){
				FileUtils.deleteDirectory(optifineClasses);
			}
			ZipUtil.unpack(remappedJar, optifineClasses);
		}

		return Pair.of(remappedJar, classCache);
	}

	private void remapOptifine(Path input, File remappedJar) throws Exception {
		String namespace = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
		System.out.println("Remapping optifine to :" + namespace);

		List<Path> mcLibs = getLibs();
		mcLibs.add(getMinecraftJar());

		RemapUtils.mapJar(remappedJar.toPath(), input, createMappings("official", namespace), mcLibs);
	}

	//Optifine currently has two fields that match the same name as Yarn mappings, we'll rename Optifine's to something else
	IMappingProvider createMappings(@SuppressWarnings("SameParameterValue") String from, String to) {
		//In dev
		if (fabricLauncher.isDevelopment()) {
			try {
				File fullMappings = extractMappings();
				return (out) -> {
					RemapUtils.getTinyRemapper(fullMappings, from, to).load(out);
					//TODO use the mappings API here to stop needing to change this each version
					out.acceptField(new IMappingProvider.Member("dbq", "CLOUDS", "Ldbe;"),
							"CLOUDS_OF");
					out.acceptField(new IMappingProvider.Member("dqr", "renderDistance", "I"),
							"renderDistance_OF");
				};
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		//In prod
		TinyTree mappingsNew = new TinyTree() {
			private final TinyTree mappings = mappingConfiguration.getMappings();

			@Override
			public TinyMetadata getMetadata() {
				return mappings.getMetadata();
			}

			@Override
			public Map<String, ClassDef> getDefaultNamespaceClassMap() {
				return mappings.getDefaultNamespaceClassMap();
			}

			@Override
			public Collection<ClassDef> getClasses() {
				return mappings.getClasses();
			}
		};
		return TinyRemapperMappingsHelper.create(mappingsNew, from, to);
	}

	//Gets the minecraft librarys
	List<Path> getLibs() {
		return net.fabricmc.loader.launch.common.FabricLauncherBase.getLauncher().getLoadTimeDependencies().stream().map(UrlUtil::asPath).filter(Files::exists).collect(Collectors.toList());
	}

	//Gets the offical minecraft jar
	Path getMinecraftJar() {
		String givenJar = System.getProperty("optifabric.mc-jar");
		if (givenJar != null) {
			File givenJarFile = new File(givenJar);

			if (givenJarFile.exists()) {
				return givenJarFile.toPath();
			} else {
				System.err.println("Supplied Minecraft jar at " + givenJar + " doesn't exist, falling back");
			}
		}

		Path minecraftJar = getLaunchMinecraftJar();

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			Path officialNames = minecraftJar.resolveSibling(String.format("minecraft-%s-client.jar", OptifineVersion.minecraftVersion));

			if (Files.notExists(officialNames)) {
				Path parent = minecraftJar.getParent().resolveSibling(String.format("minecraft-%s-client.jar", OptifineVersion.minecraftVersion));

				if (Files.notExists(parent)) {
					Path alternativeParent = parent.resolveSibling("minecraft-client.jar");

					if (Files.notExists(alternativeParent)) {
						throw new AssertionError("Unable to find Minecraft dev jar! Tried " + officialNames + ", " + parent + " and " + alternativeParent
								+ "\nPlease supply it explicitly with -Doptifabric.mc-jar");
					}

					parent = alternativeParent;
				}

				officialNames = parent;
			}

			minecraftJar = officialNames;
		}

		return minecraftJar;
	}

	private static Path getLaunchMinecraftJar() {
		try {
			return (Path) FabricLoader.getInstance().getObjectShare().get("fabric-loader:inputGameJar");
		} catch (NoClassDefFoundError | NoSuchMethodError old) {
			ModContainer mod = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow(() -> new IllegalStateException("No Minecraft?"));
			URI uri = mod.getRootPaths().get(0).toUri();
			assert "jar".equals(uri.getScheme());

			String path = uri.getSchemeSpecificPart();
			int split = path.lastIndexOf("!/");

			if (path.substring(0, split).indexOf(' ') > 0 && path.startsWith("file:///")) {//This is meant to be a URI...
				Path out = Paths.get(path.substring(8, split));
				if (Files.exists(out)) return out;
			}

			try {
				return Paths.get(new URI(path.substring(0, split)));
			} catch (URISyntaxException e) {
				throw new RuntimeException("Failed to find Minecraft jar from " + uri + " (calculated " + path.substring(0, split) + ')', e);
			}
		}
	}

	//Extracts the dev-time mappings out of yarn into a file
	File extractMappings() throws IOException {
		File extractedMappings = new File(versionDir, "mappings.tiny");
		if (extractedMappings.exists()) {
			extractedMappings.delete();
		}
		InputStream mappingStream = FabricLauncherBase.class.getClassLoader().getResourceAsStream("mappings/mappings.tiny");
		if (mappingStream != null) {
			FileUtils.copyInputStreamToFile(mappingStream, extractedMappings);
			if (!extractedMappings.exists()) {
				throw new RuntimeException("failed to extract mappings!");
			}
			return extractedMappings;
		} else {
			return null;
		}
	}

	byte[] fileHash(File input) throws IOException {
		try (InputStream is = Files.newInputStream(input.toPath())) {
			return DigestUtils.md5(is);
		}
	}
}
