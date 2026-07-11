package org.gtlcore.gtlcore.mixin.wildcard;

import net.minecraft.world.item.ItemStack;

import org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic;
import org.leodreamer.wildcard_pattern.wildcard.feature.IWildcardIOComponent;
import org.leodreamer.wildcard_pattern.wildcard.gui.WildcardComponentListGroup;
import org.leodreamer.wildcard_pattern.wildcard.gui.WildcardIOFancyConfigurator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = WildcardIOFancyConfigurator.class, remap = false)
public abstract class WildcardIOFancyConfiguratorMixin {

    @Shadow
    private WildcardPatternLogic logic;
    @Shadow
    private WildcardPatternLogic.IO io;
    @Shadow
    private Consumer<ItemStack> onSave;
    @Shadow
    private WildcardComponentListGroup<IWildcardIOComponent> componentList;

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void gTLCore$saveCurrentComponentStateFirst(CallbackInfo ci) {
        var components = this.componentList.getComponents();
        WildcardConfiguratorSaveHelper.saveCurrentStateFirst(
                components,
                () -> this.logic.setIOComponents(this.io, components),
                this.onSave);
        ci.cancel();
    }
}
