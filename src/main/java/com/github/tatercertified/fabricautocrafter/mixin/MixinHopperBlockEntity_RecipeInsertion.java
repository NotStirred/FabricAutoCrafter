package com.github.tatercertified.fabricautocrafter.mixin;

import com.github.tatercertified.fabricautocrafter.AutoCrafterMod;
import com.github.tatercertified.fabricautocrafter.AutoCraftingTableBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;

@Mixin(HopperBlockEntity.class)
public class MixinHopperBlockEntity_RecipeInsertion {
    private static final int[] WIDTH_1_LUT = new int[] { 0, 3, 6 };
    private static final int[] WIDTH_2_LUT = new int[] { 0, 1, 3, 4, 6, 7 };
    private static final int[] WIDTH_3_LUT = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
    private static final int[][] WIDTH_LUTS = new int[][] {
            new int[] {},
            WIDTH_1_LUT,
            WIDTH_2_LUT,
            WIDTH_3_LUT,
    };

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
        RecipeEntry<?> recipe = to.getLastRecipe();
        if (recipe == null) {
            return stack;
        }
        DefaultedList<Ingredient> ingredients = recipe.value().getIngredients();

        int recipeWidth = 3;
        if (recipe.value() instanceof ShapedRecipe) {
            // width must be > 0 && < 3
            recipeWidth = Math.min(3, Math.max(1, ((ShapedRecipe) recipe.value()).getWidth()));
        }
        int[] widthLUT = WIDTH_LUTS[recipeWidth];
        if (ingredients.size() > widthLUT.length) { // Something has gone very wrong, exit early
            AutoCrafterMod.LOGGER.error(String.format("Recipe has more ingredients %d than expected %d", ingredients.size(), widthLUT.length));
            return stack;
        }

        // given each item in this inventory, find the first stack which is valid for the recipe, and move it
        for (int ingIdx = 0; ingIdx < ingredients.size(); ingIdx++) {
            Ingredient ingredient = ingredients.get(ingIdx);
            int dstIdx = widthLUT[ingIdx] + 1; // CraftingTableBlockEntity input starts at slot 1
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
