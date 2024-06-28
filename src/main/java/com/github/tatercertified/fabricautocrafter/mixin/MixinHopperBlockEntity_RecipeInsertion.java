package com.github.tatercertified.fabricautocrafter.mixin;

import com.github.tatercertified.fabricautocrafter.AutoCraftingTableBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;
import java.util.OptionalInt;

@Mixin(HopperBlockEntity.class)
public class MixinHopperBlockEntity_RecipeInsertion {
    @Shadow
    private static ItemStack transfer(@Nullable Inventory from, Inventory to, ItemStack stack, int slot, @Nullable Direction side) {
        throw new IllegalStateException("Mixin failed to apply");
    }

    /**
     * @author NotStirred
     * @reason Replace recipe insertion logic for auto-crafters
     */
    @Redirect(method = "insert", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack insert(Inventory from, Inventory to, ItemStack stack, Direction side) {
        if (to instanceof AutoCraftingTableBlockEntity toAutoCrafter) {
            return transferAutoCrafter(from, toAutoCrafter, stack, side);
        } else {
            return HopperBlockEntity.transfer(from, to, stack, side);
        }
    }

    @Unique
    private static ItemStack transferAutoCrafter(@Nullable Inventory from, AutoCraftingTableBlockEntity to, ItemStack stack, @Nullable Direction side) {
        if (stack.isEmpty()) return stack;

        int[] validInsertSlots = to.getValidInsertSlotsFor(stack);
        OptionalInt firstValidSlot = Arrays.stream(validInsertSlots).findFirst();
        if (firstValidSlot.isPresent()) {
            stack = transfer(from, to, stack, firstValidSlot.getAsInt(), side);
        }
        return stack;
    }
}
