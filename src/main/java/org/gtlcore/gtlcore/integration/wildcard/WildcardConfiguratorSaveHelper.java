package org.gtlcore.gtlcore.integration.wildcard;

import net.minecraft.world.item.ItemStack;

import org.leodreamer.wildcard_pattern.wildcard.feature.IWildcardComponentUI;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WildcardConfiguratorSaveHelper {

    private WildcardConfiguratorSaveHelper() {}

    public static void saveCurrentStateFirst(List<? extends IWildcardComponentUI> components,
                                             Supplier<ItemStack> stackWriter,
                                             Consumer<ItemStack> onSave) {
        components.forEach(IWildcardComponentUI::onSave);
        ItemStack stack = stackWriter.get();
        onSave.accept(stack);
    }
}
