package fr.theorozier.rmcp;

import fr.theorozier.rmcp.chain.ActionChain;
import fr.theorozier.rmcp.chain.AssertAction;
import fr.theorozier.rmcp.chain.StringInputAction;
import fr.theorozier.rmcp.chain.custom.DecompilerInputAction;
import fr.theorozier.rmcp.chain.PrintAction;
import fr.theorozier.rmcp.chain.custom.GameSideInputAction;
import fr.theorozier.rmcp.chain.custom.VersionInputAction;
import fr.theorozier.rmcp.decompiler.CfrDecompiler;
import fr.theorozier.rmcp.decompiler.FernFlowerDecompiler;
import fr.theorozier.rmcp.mcapi.GameSide;
import fr.theorozier.rmcp.mcapi.VersionListManifest;
import fr.theorozier.rmcp.mcapi.VersionManifest;
import fr.theorozier.rmcp.util.ImprovedMap;
import fr.theorozier.rmcp.util.JsonException;
import fr.theorozier.rmcp.util.Utils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class Main {
	
	private static final int MINIMUM_JAVA_VERSION = 8;
	
	public static void main(String[] args) {
		
		ImprovedMap<Setting, Object> settings = ImprovedMap.improve(new HashMap<>());
		
		ActionChain main = new ActionChain()
				.append(new PrintAction("Reliable Minecraft Coder Pack [RMCP]"))
				.append(new PrintAction("Using official Mojang mappings to decompile client or server jars."))
				.append(new PrintAction("Default options are in uppercase, just enter."))
				.append(new PrintAction())
				.append(new AssertAction(Main::validateJavaVersion, "Java 8 minimum required."))
				.append(new DecompilerInputAction("Choose a decompiler, CFR or FernFlower", settings.putRef(Setting.DECOMPILER))
						.decompiler("cfr", CfrDecompiler::new)
						.decompiler("ff", FernFlowerDecompiler::new))
				.append(() -> loadManifest(settings))
				.append(new PrintAction("Please select a valid version (starting from 19w36a / 1.14.4).\nUse 'snapshot' or 'release' for latest snapshot or release."))
				.append(new VersionInputAction("Version" , settings.putRef(Setting.VERSION), settings.getCastRef(Setting.VERSION_LIST_MANIFEST)))
				.append(() -> validateVersion(settings))
				.append(new GameSideInputAction("Select either client or server side", settings.putRef(Setting.SIDE)))
				.append(() -> validateSide(settings))
				.append(new StringInputAction("Enter a suffix for your project directory", settings.putRef(Setting.PROJECT), false))
				.append(() -> validateProject(settings));
	
		main.run();
		
	}
	
	private static boolean validateJavaVersion() {
		return Utils.getJavaVersion() >= MINIMUM_JAVA_VERSION;
	}
	
	private static boolean loadManifest(ImprovedMap<Setting, Object> settings) {
		
		try {
			
			System.out.println("Reading versions list manifest...");
			
			VersionListManifest manifest = VersionListManifest.loadManifest();
			
			if (manifest.getLatestRelease() == null || manifest.getLatestSnapshot() == null) {
				System.out.println("Can't get latest release or latest snapshot.");
				return false;
			}
			
			settings.put(Setting.VERSION_LIST_MANIFEST, manifest);
			System.out.println("Manifest loaded.");
			return true;
			
		} catch (JsonException e) {
			
			System.out.println("Failed to load manifest, aborting :");
			e.printStackTrace();
			return false;
			
		}
		
	}
	
	private static boolean validateVersion(ImprovedMap<Setting, Object> settings) {
		
		String version = settings.getCast(Setting.VERSION);
		Path versionPath = Paths.get("versions", version);
		Path versionManifestPath = versionPath.resolve("manifest.json");
		URL versionManifestUrl;
		boolean mustCache = false;
		
		if (Files.isRegularFile(versionManifestPath)) {
			System.out.println("Version manifest '" + versionManifestPath + "' already exists, delete this file if you want to download again.");
			versionManifestUrl = Utils.pathToUrl(versionManifestPath);
		} else {
			VersionListManifest versionListManifest = settings.getCast(Setting.VERSION_LIST_MANIFEST);
			versionManifestUrl = versionListManifest.getVersionManifestUrl(version);
			mustCache = true;
		}
		
		if (versionManifestUrl == null) {
			System.out.println("Invalid version '" + version + "'.");
			return false;
		} else {
			
			try {
				
				VersionManifest manifest = VersionManifest.loadManifest(versionManifestUrl);
				
				if (manifest.doSupportMappings()) {
					System.out.println("This version is too old it do not support mappings, you can use MCP for that instead.");
					return false;
				}
				
				if (mustCache) {
					VersionManifest.writeManifest(manifest, versionManifestPath);
				}
				
				settings.put(Setting.VERSION_MANIFEST, manifest);
				settings.put(Setting.VERSION_PATH, versionPath);
				return true;
				
			} catch (JsonException e) {
				System.out.println("Failed to load version manifest :");
				e.printStackTrace();
				return false;
			}
			
		}
		
	}
	
	private static boolean validateSide(ImprovedMap<Setting, Object> settings) {
		
		VersionManifest manifest = settings.getCast(Setting.VERSION_MANIFEST);
		Path versionPath = settings.getCast(Setting.VERSION_PATH);
		GameSide side = settings.getCast(Setting.SIDE);
		
		URL jarUrl = manifest.getJarUrl(side);
		URL mappingsUrl = manifest.getMappingsUrl(side);
		
		Path jarPath = versionPath.resolve(side.id() + ".jar");
		Path mappingsPath = versionPath.resolve(side.id() + ".map");
		
		if (!downloadIfNotExists(jarPath, jarUrl, "jar")) return false;
		if (!downloadIfNotExists(mappingsPath, mappingsUrl, "mappings")) return false;
		
		settings.put(Setting.SIDE_JAR_PATH, jarPath);
		settings.put(Setting.SIDE_MAPPINGS_PATH, mappingsPath);
		
		return true;
		
	}
	
	private static boolean downloadIfNotExists(Path path, URL url, String element) {
		if (Files.isRegularFile(path)) {
			System.out.println("The " + element + " file already exists, delete this file if you want to download again.");
			return true;
		} else {
			try {
				System.out.println("Downloading " + element + " file...");
				Files.copy(url.openStream(), path);
				return true;
			} catch (IOException e) {
				System.out.println("Failed to download " + element + " file.");
				e.printStackTrace();
				return false;
			}
		}
	}
	
	private static boolean validateProject(ImprovedMap<Setting, Object> settings) {
	
		String project = settings.getCast(Setting.PROJECT);
		Path projectPath = Paths.get("projects", project);
		
		if (Files.isDirectory(projectPath)) {
			System.out.println("This project '" + project + "' already exists, can't continue.");
			return false;
		} else {
			
			String version = settings.getCast(Setting.VERSION);
			GameSide side = settings.getCast(Setting.SIDE);
			
			settings.put(Setting.PROJECT_PATH, projectPath);
			
			try {
				Files.createFile(projectPath.resolve(version + ".mcversion"));
				Files.createFile(projectPath.resolve(side.id() + ".mcside"));
			} catch (IOException e) {
				System.out.println("Failed to create .mcversion/.mcside files.");
				e.printStackTrace();
				return false;
			}
			
			return true;
			
		}
	
	}
	
	private enum Setting {
		DECOMPILER,
		VERSION_LIST_MANIFEST,
		VERSION,
		VERSION_MANIFEST,
		VERSION_PATH,
		SIDE,
		SIDE_JAR_PATH,
		SIDE_MAPPINGS_PATH,
		SIDE_TSRG_MAPPINGS_PATH,
		PROJECT,
		PROJECT_PATH
	}
	
}
