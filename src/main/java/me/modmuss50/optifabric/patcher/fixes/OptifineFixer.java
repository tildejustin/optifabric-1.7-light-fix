package me.modmuss50.optifabric.patcher.fixes;

import me.modmuss50.optifabric.util.RemappingUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class OptifineFixer {

	public static final OptifineFixer INSTANCE = new OptifineFixer();

	private final HashMap<String, List<ClassFixer>> classFixes = new HashMap<>();
	private final List<String> skippedClass = new ArrayList<>();

	private OptifineFixer() {
	}

	private void registerFix(String className, ClassFixer classFixer) {
		classFixes.computeIfAbsent(RemappingUtils.fromIntermediary(className), s -> new ArrayList<>()).add(classFixer);
	}

	private void skipClass(String className) {
		skippedClass.add(RemappingUtils.fromIntermediary(className));
	}

	public boolean shouldSkip(String className) {
		return skippedClass.contains(className);
	}

	public List<ClassFixer> getFixers(String className) {
		return classFixes.getOrDefault(className, Collections.emptyList());
	}
}
