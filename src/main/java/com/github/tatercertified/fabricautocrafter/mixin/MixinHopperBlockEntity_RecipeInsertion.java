package com.github.tatercertified.fabricautocrafter.mixin;

import com.github.tatercertified.fabricautocrafter.CraftingTableBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;

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
        if (to instanceof CraftingTableBlockEntity) {
            return transferAutoCrafter(from, (CraftingTableBlockEntity) to, stack, side);
        } else {
            return HopperBlockEntity.transfer(from, to, stack, side);
        }
    }

    private static ItemStack transferAutoCrafter(@Nullable Inventory from, CraftingTableBlockEntity to, ItemStack stack, @Nullable Direction side) {
        Recipe<?> recipe = to.getLastRecipe();
        if (recipe == null) {
            return stack;
        }
        DefaultedList<Ingredient> ingredients = recipe.getIngredients();

        // given each item in this inventory, find the first stack which is valid for the recipe, and move it
        for (int ingIdx = 0; ingIdx < ingredients.size(); ingIdx++) {
            Ingredient ingredient = ingredients.get(ingIdx);
            int dstIdx = ingIdx + 1; // CraftingTableBlockEntity input starts at slot 1
            ItemStack dstStack = to.getStack(dstIdx);
            ItemStack finalStack = stack; // java is dumb
            if (Arrays.stream(ingredient.getMatchingStacks()).anyMatch(ingStack -> ingStack.getItem().equals(finalStack.getItem()))
                    && dstStack.getItem().equals(Items.AIR)) {
                if (stack.isEmpty()) return stack;
                stack = transfer(from, to, stack, dstIdx, side);
            }
        }
        return stack;
    }
}