package org.gtlcore.gtlcore.mixin.wildcard;

import org.gtlcore.gtlcore.integration.wildcard.WildcardConfiguratorSaveHelper;

import net.minecraft.world.item.ItemStack;

import org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic;
import org.leodreamer.wildcard_pattern.wildcard.feature.IWildcardFilterComponent;
import org.leodreamer.wildcard_pattern.wildcard.gui.WildcardComponentListGroup;
import org.leodreamer.wildcard_pattern.wildcard.gui.WildcardFilterFancyConfigurator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = WildcardFilterFancyConfigurator.class, remap = false)
public abstract class WildcardFilterFancyConfiguratorMixin {

    @Shadow
    private WildcardPatternLogic logic;
    @Shadow
    private Consumer<ItemStack> onSave;
    @Shadow
    private WildcardComponentListGroup<IWildcardFilterComponent> componentList;

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void gTLCore$saveCurrentComponentStateFirst(CallbackInfo ci) {
        var components = this.componentList.getComponents();
        WildcardConfiguratorSaveHelper.saveCurrentStateFirst(
                components,
                () -> this.logic.setFilterComponents(components),
                this.onSave);
        ci.cancel();
    }
}
