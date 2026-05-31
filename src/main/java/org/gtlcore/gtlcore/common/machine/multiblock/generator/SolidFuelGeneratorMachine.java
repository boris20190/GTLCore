package org.gtlcore.gtlcore.common.machine.multiblock.generator;

import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.utils.MachineIO;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.side.fluid.FluidTransferHelper;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.BlockHitResult;

import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.utils.TeamUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SolidFuelGeneratorMachine extends NoEnergyMultiblockMachine implements IMachineLife {

    private static final long EU_PER_STEAM_MB = 1;
    private static final long STEAM_MB_PER_EU = 2;
    private static final long LARGE_BOILER_DURATION_REFERENCE = 6400;
    private static final double KELVIN_OFFSET = 273.15;
    private static final int ENERGY_MODEL_VERSION = 1;
    private static final long EU_PER_LARGE_BOILER_BASE_TICK = LARGE_BOILER_DURATION_REFERENCE * EU_PER_STEAM_MB /
            STEAM_MB_PER_EU;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            SolidFuelGeneratorMachine.class, NoEnergyMultiblockMachine.MANAGED_FIELD_HOLDER);

    private final int tier;
    protected final ConditionalSubscriptionHandler generationSubs;

    @Persisted
    @Nullable
    private UUID userid;
    @Persisted
    @DescSynced
    private long remainingEU;
    @Persisted
    @DescSynced
    private long operationTotalEU;
    @Persisted
    @DescSynced
    private long targetEUt;
    @Persisted
    @DescSynced
    private long lastEUt;
    @Persisted
    private int energyModelVersion;

    public SolidFuelGeneratorMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, args);
        this.tier = tier;
        this.generationSubs = new ConditionalSubscriptionHandler(this, this::generationServerTick, this::isFormed);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && migrateEnergyModel() && getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::markDirty));
        }
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        generationSubs.initialize(getLevel());
    }

    protected boolean migrateEnergyModel() {
        if (energyModelVersion == ENERGY_MODEL_VERSION) {
            return false;
        }
        remainingEU = 0;
        operationTotalEU = 0;
        targetEUt = 0;
        lastEUt = 0;
        energyModelVersion = ENERGY_MODEL_VERSION;
        return true;
    }

    @Override
    public void onMachinePlaced(@Nullable LivingEntity player, ItemStack stack) {
        if (player != null) {
            this.userid = player.getUUID();
        }
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        if (this.userid == null) {
            this.userid = player.getUUID();
        }
        return true;
    }

    protected void generationServerTick() {
        if (isRemote() || !isFormed()) {
            return;
        }
        if (!isWorkingEnabled()) {
            lastEUt = 0;
            recipeLogic.setStatus(RecipeLogic.Status.IDLE);
            return;
        }
        if (userid == null) {
            lastEUt = 0;
            recipeLogic.setWaiting(Component.translatable("gtceu.machine.solid_fuel_generator.no_owner"));
            return;
        }
        if (remainingEU <= 0 && !startNextFuelOperation()) {
            lastEUt = 0;
            operationTotalEU = 0;
            targetEUt = getTargetEUt();
            recipeLogic.setStatus(RecipeLogic.Status.IDLE);
            return;
        }

        targetEUt = getTargetEUt();
        long euToCredit = Math.min(targetEUt, remainingEU);
        if (euToCredit <= 0) {
            lastEUt = 0;
            recipeLogic.setStatus(RecipeLogic.Status.IDLE);
            return;
        }
        if (WirelessEnergyManager.addEUToGlobalEnergyMap(userid, euToCredit, this)) {
            remainingEU -= euToCredit;
            lastEUt = euToCredit;
            recipeLogic.setStatus(RecipeLogic.Status.WORKING);
            if (remainingEU <= 0) {
                remainingEU = 0;
                operationTotalEU = 0;
            }
        } else {
            lastEUt = 0;
            recipeLogic.setWaiting(Component.translatable("gtceu.machine.solid_fuel_generator.wireless_failed"));
        }
    }

    protected boolean startNextFuelOperation() {
        FuelOperation operation = findFuelOperation();
        if (operation == null || !MachineIO.inputItem(this, operation.stack())) {
            return false;
        }
        targetEUt = getTargetEUt();
        remainingEU = Math.max(1, (long) operation.largeBoilerDuration() * EU_PER_LARGE_BOILER_BASE_TICK);
        operationTotalEU = remainingEU;
        return true;
    }

    @Nullable
    protected FuelOperation findFuelOperation() {
        List<IRecipeHandler<?>> handlers = getCapabilitiesProxy().get(IO.IN, ItemRecipeCapability.CAP);
        if (handlers == null) {
            return null;
        }
        for (IRecipeHandler<?> handler : handlers) {
            for (Object content : handler.getContents()) {
                if (content instanceof ItemStack stack && !stack.isEmpty()) {
                    int duration = getLargeBoilerFuelDuration(stack);
                    if (duration > 0) {
                        ItemStack singleFuel = stack.copy();
                        singleFuel.setCount(1);
                        return new FuelOperation(singleFuel, duration);
                    }
                }
            }
        }
        return null;
    }

    protected int getLargeBoilerFuelDuration(ItemStack stack) {
        if (stack.isEmpty() || FluidTransferHelper.getFluidContained(stack) != null) {
            return 0;
        }
        for (Recipe<?> recipe : recipeLogic.getRecipeManager().getRecipes()) {
            if (recipe instanceof GTRecipe gtRecipe &&
                    gtRecipe.getType().equals(GTRecipeTypes.LARGE_BOILER_RECIPES) &&
                    isFuelInput(gtRecipe, stack)) {
                return gtRecipe.duration;
            }
        }
        return 0;
    }

    protected boolean isFuelInput(GTRecipe recipe, ItemStack stack) {
        for (var content : recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, Collections.emptyList())) {
            Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    protected long getTargetEUt() {
        return Math.max(1, (long) getBoilerMaxTemperature() * EU_PER_STEAM_MB / STEAM_MB_PER_EU);
    }

    protected int getBoilerMaxTemperature() {
        var largeBoilers = ConfigHolder.INSTANCE.machines.largeBoilers;
        return switch (tier) {
            case GTValues.HV -> largeBoilers.steelBoilerMaxTemperature;
            case GTValues.EV -> largeBoilers.titaniumBoilerMaxTemperature;
            case GTValues.IV -> largeBoilers.tungstensteelBoilerMaxTemperature;
            default -> largeBoilers.steelBoilerMaxTemperature;
        };
    }

    protected int getDisplayedBoilerMaxTemperature() {
        return (int) (getBoilerMaxTemperature() + KELVIN_OFFSET);
    }

    protected int getProgressPercent() {
        if (operationTotalEU <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.max(0, (operationTotalEU - remainingEU) * 100 / operationTotalEU));
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        if (!isFormed()) {
            super.addDisplayText(textList);
            return;
        }
        if (!isWorkingEnabled()) {
            textList.add(Component.translatable("gtceu.multiblock.work_paused"));
        } else if (remainingEU > 0) {
            textList.add(Component.translatable("gtceu.multiblock.running"));
            textList.add(Component.translatable("gtceu.multiblock.progress", getProgressPercent()));
        } else {
            textList.add(Component.translatable("gtceu.multiblock.idling"));
        }
        if (userid != null) {
            textList.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.0",
                    TeamUtil.GetName(getLevel(), userid)));
            textList.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.1",
                    FormattingUtil.formatNumbers(WirelessEnergyManager.getUserEU(userid))));
        } else {
            textList.add(Component.translatable("gtceu.machine.solid_fuel_generator.no_owner"));
        }
        if (recipeLogic.isWaiting() && remainingEU > 0 && userid != null) {
            textList.add(Component.translatable("gtceu.machine.solid_fuel_generator.wireless_failed")
                    .withStyle(ChatFormatting.RED));
        }
        textList.add(Component.translatable("gtceu.machine.solid_fuel_generator.max_temperature",
                FormattingUtil.formatNumbers(getDisplayedBoilerMaxTemperature())));
        textList.add(Component.translatable("gtceu.recipe.eu_inverted",
                FormattingUtil.formatNumbers(targetEUt > 0 ? targetEUt : getTargetEUt())));
        if (lastEUt > 0) {
            textList.add(Component.translatable("gtceu.machine.solid_fuel_generator.last_eut",
                    FormattingUtil.formatNumbers(lastEUt)));
        }
        if (remainingEU > 0) {
            textList.add(Component.translatable("gtceu.machine.solid_fuel_generator.remaining_eu",
                    FormattingUtil.formatNumbers(remainingEU)));
        }
    }

    protected record FuelOperation(ItemStack stack, int largeBoilerDuration) {}
}
