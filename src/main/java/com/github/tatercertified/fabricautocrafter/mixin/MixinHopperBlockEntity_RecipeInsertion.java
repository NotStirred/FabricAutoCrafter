package com.github.tatercertified.fabricautocrafter.mixin;

import com.github.tatercertified.fabricautocrafter.CraftingTableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Arrays;

@Mixin(HopperBlockEntity.class)
public abstract class MixinHopperBlockEntity_RecipeInsertion {
    @Shadow
    private static ItemStack transfer(@Nullable Inventory from, Inventory to, ItemStack stack, int slot, @Nullable Direction side) {
        throw new IllegalStateException("Mixin failed to apply");
    }

    @Shadow protected static boolean isInventoryFull(Inventory inventory, Direction direction) {
        throw new IllegalStateException("Mixin failed to apply");
    }

    /**
     * This is an @Inject because it cancels the Lithium logic, making it compatible with this mod
     *
     * @author NotStirred
     * @reason Replace recipe insertion logic for auto-crafters
     */
    @SuppressWarnings("InvalidInjectorMethodSignature")
    @Inject(method = "insert", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/util/math/Direction;getOpposite()Lnet/minecraft/util/math/Direction;"), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void insert(World world, BlockPos pos, BlockState state, Inventory from, CallbackInfoReturnable<Boolean> cir, Inventory to, Direction side) {
        if (isInventoryFull(to, side)) {
            cir.setReturnValue(false);
            return;
        }
        for (int i = 0; i < from.size(); ++i) {
            if (from.getStack(i).isEmpty()) continue;
            ItemStack stack = from.getStack(i).copy();
            ItemStack outStack;
            if (to instanceof CraftingTableBlockEntity) {
                outStack = transferAutoCrafter(from, (CraftingTableBlockEntity) to, from.removeStack(i, 1), side);
            } else {
                outStack = HopperBlockEntity.transfer(from, to, from.removeStack(i, 1), side);
            }
            if (outStack.isEmpty()) {
                to.markDirty();
                cir.setReturnValue(true);
                return;
            }
            from.setStack(i, stack);
        }
        cir.setReturnValue(false);
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