package org.gtlcore.gtlcore.mixin.wildcard;

import net.minecraftforge.fml.loading.LoadingModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class WildcardMixinPlugin implements IMixinConfigPlugin {

    private static final String WILDCARD_PATTERN_MOD_ID = "wildcard_pattern";
    private static final String WILDCARD_PATTERN_MARKER_CLASS = "org.leodreamer.wildcard_pattern.WildcardPattern";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return gTLCore$isWildcardPatternLoaded();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    private static boolean gTLCore$isWildcardPatternLoaded() {
        try {
            var loadingModList = LoadingModList.get();
            if (loadingModList != null) {
                return loadingModList.getModFileById(WILDCARD_PATTERN_MOD_ID) != null;
            }
        } catch (Throwable ignored) {
            // Fallback to classpath probing below; mixin selection should stay recoverable.
        }
        return gTLCore$isClassPresent(WILDCARD_PATTERN_MARKER_CLASS);
    }

    private static boolean gTLCore$isClassPresent(String className) {
        try {
            Class.forName(className, false, WildcardMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
