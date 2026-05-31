package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeLogic.class)
public interface RecipeLogicAccessor {

    @Accessor(value = "duration", remap = false)
    void setDuration(int duration);

    @Accessor(value = "isActive", remap = false)
    void setIsActive(boolean isActive);
}
