package com.github.tatercertified.fabricautocrafter;

import com.github.tatercertified.fabricautocrafter.mixin.CraftingInventoryMixin;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.recipe.*;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.tatercertified.fabricautocrafter.AutoCrafterMod.TYPE;
import static net.minecraft.util.math.Direction.DOWN;


public class CraftingTableBlockEntity extends LockableContainerBlockEntity implements SidedInventory, RecipeUnlocker, RecipeInputProvider {

    private static final int[] OUTPUT_SLOTS = {0};
    private static final int[] INPUT_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 9};

    private final List<AutoCraftingTableContainer> openContainers = new ArrayList<>();
    private final CraftingInventory craftingInventory = new CraftingInventory(null, 3, 3);
    public DefaultedList<ItemStack> inventory;
    public ItemStack output = ItemStack.EMPTY;
    private final StoredRecipe lastRecipe = new StoredRecipe(); // last recipe is lazy loaded, to prevent crash on world load if loaded in readNbt

    public CraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
        this.inventory = DefaultedList.ofSize(9, ItemStack.EMPTY);

        ((CraftingInventoryMixin) craftingInventory).setInventory(this.inventory);
    }

    public CraftingInventory boundCraftingInventory(ScreenHandler handler) {
        ((CraftingInventoryMixin) craftingInventory).setHandler(handler);
        return craftingInventory;
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        Inventories.writeNbt(tag, inventory);
        tag.put("Output", output.writeNbt(new NbtCompound()));

        if (lastRecipe.recipe.isPresent()) {
            Identifier id = lastRecipe.recipe.get().getId();
            tag.put("lockedRecipeNamespace", NbtString.of(id.getNamespace()));
            tag.put("lockedRecipePath", NbtString.of(id.getPath()));
        }
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        Inventories.readNbt(tag, inventory);
        this.output = ItemStack.fromNbt(tag.getCompound("Output"));

        if (tag.contains("lockedRecipeNamespace") && tag.contains("lockedRecipePath")) {
            NbtElement lockedRecipeNamespace = tag.get("lockedRecipeNamespace");
            NbtElement lockedRecipePath = tag.get("lockedRecipePath");
            if (lockedRecipeNamespace instanceof NbtString && lockedRecipePath instanceof NbtString) {
                this.lastRecipe.lockedRecipeNamespace = Optional.of(lockedRecipeNamespace.asString());
                this.lastRecipe.lockedRecipePath = Optional.of(lockedRecipePath.asString());
            }
        }
    }

    @Override
    protected Text getContainerName() {
        return Text.translatable("container.crafting");
    }

    @Override
    protected ScreenHandler createScreenHandler(int id, PlayerInventory playerInventory) {
        final AutoCraftingTableContainer container = new AutoCraftingTableContainer(id, playerInventory, this);
        this.openContainers.add(container);
        return container;
    }

    @Override
    public int[] getAvailableSlots(Direction dir) {
        return dir == DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, Direction dir) {
        return slot > 0 && getStack(slot).isEmpty();
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot != 0 || !output.isEmpty() || getCurrentRecipe().isPresent();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot != 0 && slot <= size();
    }

    @Override
    public int size() {
        return 10;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.inventory) {
            if (!stack.isEmpty()) return false;
        }
        return output.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot > 0) return this.inventory.get(slot - 1);
        if (!output.isEmpty()) return output;
        Optional<CraftingRecipe> recipe = getCurrentRecipe();
        return recipe.map(craftingRecipe -> craftingRecipe.craft(craftingInventory, this.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == 0) {
            if (output.isEmpty()) output = craft();
            return output.split(amount);
        }
        return Inventories.splitStack(this.inventory, slot - 1, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot == 0) {
            ItemStack output = this.output;
            this.output = ItemStack.EMPTY;
            return output;
        }
        return Inventories.removeStack(this.inventory, slot - 1);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            output = stack;
            return;
        }
        inventory.set(slot - 1, stack);
        markDirty();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        for (AutoCraftingTableContainer c : openContainers) c.onContentChanged(this);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return player.getBlockPos().getSquaredDistance(this.pos) <= 64.0D;
    }

    @Override
    public void provideRecipeInputs(RecipeMatcher finder) {
        for (ItemStack stack : this.inventory) finder.addInput(stack);
    }

    @Override
    public void setLastRecipe(Recipe<?> recipe) {
    }

    @Override @Nullable
    public Recipe<?> getLastRecipe() {
        return lastRecipe.getRecipe(this.world).orElse(null);
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    private Optional<CraftingRecipe> getCurrentRecipe() {
        //Optimization Code from Crec0
        if (this.world == null || this.isEmpty()) return Optional.empty();

        CraftingRecipe lastRecipe = (CraftingRecipe) getLastRecipe();
        RecipeManager manager = this.world.getRecipeManager();

        if (lastRecipe != null) {
            CraftingRecipe mapRecipe = manager.getAllOfType(RecipeType.CRAFTING).get(lastRecipe);
            if (mapRecipe != null && mapRecipe.matches(craftingInventory, world)) {
                return Optional.of(lastRecipe);
            }
        }
        Optional<CraftingRecipe> recipe = manager.getFirstMatch(RecipeType.CRAFTING, craftingInventory, world);
        return recipe;
    }

    private ItemStack craft() {
        if (this.world == null) return ItemStack.EMPTY;
        final Optional<CraftingRecipe> optionalRecipe = getCurrentRecipe();
        if (optionalRecipe.isEmpty()) return ItemStack.EMPTY;

        final CraftingRecipe recipe = optionalRecipe.get();
        final ItemStack result = recipe.craft(craftingInventory, this.getWorld().getRegistryManager());
        final DefaultedList<ItemStack> remaining = world.getRecipeManager().getRemainingStacks(RecipeType.CRAFTING, craftingInventory, world);
        for (int i = 0; i < 9; i++) {
            ItemStack current = inventory.get(i);
            ItemStack remainingStack = remaining.get(i);
            if (!current.isEmpty()) {
                current.decrement(1);
            }
            if (!remainingStack.isEmpty()) {
                if (current.isEmpty()) {
                    inventory.set(i, remainingStack);
                } else if (ItemStack.canCombine(current, remainingStack)) {
                    current.increment(remainingStack.getCount());
                } else {
                    ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), remainingStack);
                }
            }
        }
        markDirty();
        lastRecipe.recipe = Optional.of(recipe);
        return result;
    }

    public CraftingInventory unsetHandler() {
        ((CraftingInventoryMixin) craftingInventory).setHandler(null);
        return craftingInventory;
    }

    public void onContainerClose(AutoCraftingTableContainer container) {
        this.openContainers.remove(container);
    }

    static final class StoredRecipe {
        private Optional<String> lockedRecipeNamespace = Optional.empty();
        private Optional<String> lockedRecipePath = Optional.empty();
        private Optional<Recipe<?>> recipe = Optional.empty();

        Optional<? extends Recipe<?>> getRecipe(World world) {
            if (recipe.isPresent()) {
                return recipe;
            }

            if (lockedRecipeNamespace.isEmpty() || lockedRecipePath.isEmpty()) {
                return Optional.empty();
            }
            return world.getRecipeManager().get(new Identifier(lockedRecipeNamespace.get(), lockedRecipePath.get()));
        }
    }
}
