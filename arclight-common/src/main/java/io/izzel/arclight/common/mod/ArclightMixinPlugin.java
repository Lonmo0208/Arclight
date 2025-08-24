package io.izzel.arclight.common.mod;

import io.izzel.arclight.common.mod.mixins.*;
import io.izzel.arclight.mixin.MixinTools;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ArclightMixinPlugin implements IMixinConfigPlugin {

    private final List<MixinProcessor> preProcessors = List.of(
    );

    private final List<MixinProcessor> postProcessors = List.of(
        new RenameIntoProcessor(),
        new TransformAccessProcessor(),
        new CreateConstructorProcessor(),
        new InlineMethodProcessor(),
        new InlineFieldProcessor(),
        new InvokeSpecialProcessor()
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Smart handling for ComponentMixin to prevent early class loading
        if (mixinClassName.endsWith("ComponentMixin") && targetClassName.equals("net.minecraft.network.chat.Component")) {
            try {
                // Use reflection to check if the class is already loaded without triggering loading
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                java.lang.reflect.Method findLoadedClassMethod = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
                findLoadedClassMethod.setAccessible(true);
                Class<?> loadedClass = (Class<?>) findLoadedClassMethod.invoke(classLoader, targetClassName);

                if (loadedClass != null) {
                    // Class already loaded, skip this mixin to prevent exception
                    System.out.println("[Luminara] Component class already loaded, skipping ComponentMixin to prevent MixinTargetAlreadyLoadedException");
                    return false;
                }
                // Class not loaded yet, safe to apply mixin
                return ShouldApplyProcessor.shouldApply(mixinClassName);
            } catch (Exception e) {
                // If reflection fails, fall back to default behavior
                System.out.println("[Luminara] Failed to check Component class loading status, applying default logic: " + e.getMessage());
                return ShouldApplyProcessor.shouldApply(mixinClassName);
            }
        }
        return ShouldApplyProcessor.shouldApply(mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        for (var processor : this.preProcessors) {
            processor.accept(targetClassName, targetClass, mixinInfo);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        for (var processor : this.postProcessors) {
            processor.accept(targetClassName, targetClass, mixinInfo);
        }
        MixinTools.onPostMixin(targetClass);
    }
}
