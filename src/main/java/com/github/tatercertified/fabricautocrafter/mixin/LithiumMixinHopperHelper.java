package com.github.tatercertified.fabricautocrafter.mixin;

import com.github.tatercertified.fabricautocrafter.AutoCraftingTableBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.jellysquid.mods.lithium.common.hopper.HopperHelper;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HopperHelper.class)
public class LithiumMixinHopperHelper {
    @WrapOperation(method = "tryMoveSingleItem(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/SidedInventory;getAvailableSlots(Lnet/minecraft/util/math/Direction;)[I"))
    private static int[] foo(SidedInventory instance, Direction direction, Operation<int[]> original, Inventory to, ItemStack stack, @Nullable Direction fromDirection) {
        if (instance instanceof AutoCraftingTableBlockEntity autoCrafter) {
            return autoCrafter.getValidInsertSlotsFor(stack);
        } else {
            return original.call(instance, direction);
        }
    }
}
