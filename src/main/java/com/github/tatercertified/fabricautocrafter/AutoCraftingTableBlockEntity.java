package com.github.tatercertified.fabricautocrafter;

import com.github.tatercertified.fabricautocrafter.mixin.CraftingInventoryMixin;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.recipe.*;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class AutoCraftingTableBlockEntity extends LockableContainerBlockEntity implements SidedInventory, RecipeUnlocker, RecipeInputProvider {

    private static final int[] OUTPUT_SLOTS = {0};
    private static final int[] INPUT_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 9};

    private final List<AutoCraftingTableContainer> openContainers = new ArrayList<>();
    private final CraftingInventory craftingInventory = new CraftingInventory(null, 3, 3);
    public DefaultedList<ItemStack> inventory;
    private ItemStack output = ItemStack.EMPTY;
    private final StoredRecipe lastRecipe = new StoredRecipe(); // last recipe is lazy loaded, to prevent crash on world load if loaded in readNbt

    public AutoCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(AutoCrafterMod.TYPE, pos, state);
        this.inventory = DefaultedList.ofSize(9, ItemStack.EMPTY);
        ((CraftingInventoryMixin) craftingInventory).setInventory(this.inventory);
    }

    public CraftingInventory bindInventory(ScreenHandler handler) {
        ((CraftingInventoryMixin) craftingInventory).setHandler(handler);
        return craftingInventory;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        Inventories.writeNbt(nbt, inventory, registryLookup);
        if (!output.isEmpty()) {
            nbt.put("Output", output.encode(registryLookup));
        }

        Optional<? extends RecipeEntry<?>> recipe = lastRecipe.getRecipe(this.world);
        if (recipe.isPresent()) {
            Identifier id = recipe.get().id();
            nbt.put("lockedRecipeNamespace", NbtString.of(id.getNamespace()));
            nbt.put("lockedRecipePath", NbtString.of(id.getPath()));
        }

        if (lastRecipe.inputItems != null) {
            NbtList inputItemsNbt = new NbtList();
            lastRecipe.inputItems.forEach(item -> {
                inputItemsNbt.add(NbtString.of(Registries.ITEM.getId(item).toString()));
            });
            nbt.put("inputStacks", inputItemsNbt);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        Inventories.readNbt(nbt, inventory, registryLookup);
        this.output = ItemStack.fromNbtOrEmpty(registryLookup, nbt.getCompound("Output"));

        if (nbt.contains("lockedRecipeNamespace") && nbt.contains("lockedRecipePath")) {
            NbtElement lockedRecipeNamespace = nbt.get("lockedRecipeNamespace");
            NbtElement lockedRecipePath = nbt.get("lockedRecipePath");
            if (lockedRecipeNamespace instanceof NbtString && lockedRecipePath instanceof NbtString) {
                this.lastRecipe.lockedRecipeNamespace = Optional.of(lockedRecipeNamespace.asString());
                this.lastRecipe.lockedRecipePath = Optional.of(lockedRecipePath.asString());
            }
        }

        if (nbt.contains("inputStacks")) {
            var stacks = nbt.getList("inputStacks", NbtElement.STRING_TYPE);
            List<Item> inputStacks = new ArrayList<>();

            for (NbtElement stack : stacks) {
                Identifier id = Identifier.tryParse(stack.asString());
                if (id != null) {
                    inputStacks.add(Registries.ITEM.get(id));
                } else {
                    AutoCrafterMod.LOGGER.error("Found invalid item in recipe: " + stack.asString());
                }
            }
            this.lastRecipe.inputItems = inputStacks;
        }
    }

    @Override
    protected Text getContainerName() {
        return Text.translatable("block.autocrafter.autocrafter");
    }

    @Override
    protected DefaultedList<ItemStack> getHeldStacks() {
        return this.inventory;
    }

    public ItemStack getOutput() {
        return this.output;
    }

    @Override
    protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
        this.inventory = inventory;
    }

    @Override
    protected ScreenHandler createScreenHandler(int id, PlayerInventory playerInventory) {
        final AutoCraftingTableContainer container = new AutoCraftingTableContainer(id, playerInventory, this);
        this.openContainers.add(container);
        return container;
    }

    @Override
    public int[] getAvailableSlots(Direction dir) {
        return (dir == Direction.DOWN && (!output.isEmpty() || getCurrentRecipe().isPresent())) ? OUTPUT_SLOTS : INPUT_SLOTS;
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
        return recipe.map(craftingRecipe -> craftingRecipe.craft(craftingInventory.createRecipeInput(), this.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
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
    public void setLastRecipe(@Nullable RecipeEntry<?> recipe) {
        // do nothing.
    }

    @Nullable
    @Override
    public RecipeEntry<?> getLastRecipe() {
        return lastRecipe.recipe.orElse(null);
    }

    public Optional<List<Item>> getSpecialRecipeItems() {
        return Optional.ofNullable(this.lastRecipe.inputItems);
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    private Optional<CraftingRecipe> getCurrentRecipe() {
        // Optimization Code from Crec0
        if (this.world == null || this.isEmpty()) return Optional.empty();

        RecipeManager manager = this.world.getRecipeManager();

        var getLastRecipe = getLastRecipe();

        if (getLastRecipe != null) {
             CraftingRecipe recipe = (CraftingRecipe) getLastRecipe.value();

             for (RecipeEntry<CraftingRecipe> entry : manager.getAllOfType(RecipeType.CRAFTING)) {
                 if (entry.value().equals(recipe)) {
                     CraftingRecipe mapRecipe = entry.value();
                     if (mapRecipe.matches(this.craftingInventory.createRecipeInput(), world)) {
                         return Optional.of(mapRecipe);
                     }
                 }
             }
        }

        Optional<RecipeEntry<CraftingRecipe>> recipe = manager.getFirstMatch(RecipeType.CRAFTING, craftingInventory.createRecipeInput(), world);
        recipe.ifPresent(this::setLastRecipe);

        return recipe.map(RecipeEntry::value);
    }

    private ItemStack craft() {
        if (this.world == null) return ItemStack.EMPTY;
        final Optional<CraftingRecipe> optionalRecipe = getCurrentRecipe();
        if (optionalRecipe.isEmpty()) return ItemStack.EMPTY;

        final CraftingRecipe recipe = optionalRecipe.get();
        if (recipe instanceof SpecialCraftingRecipe || recipe instanceof ShapelessRecipe) {
            lastRecipe.inputItems = craftingInventory.getHeldStacks().subList(0, craftingInventory.getHeldStacks().size()).stream().map(ItemStack::getItem).collect(Collectors.toList());
        } else {
            lastRecipe.inputItems = null;
        }
        final CraftingRecipeInput.Positioned input = craftingInventory.createPositionedRecipeInput();
        final ItemStack result = recipe.craft(input.input(), this.getWorld().getRegistryManager());
        final DefaultedList<ItemStack> remaining = world.getRecipeManager().getRemainingStacks(RecipeType.CRAFTING, input.input(), world);
        for (int i = 0; i < input.input().getSize(); i++) {
            int startIdx =  input.left() + input.top() * 3;
                int idxWithinInv = startIdx + LUTUtil.WIDTH_LUTS[input.input().getWidth()][i];
            ItemStack current = inventory.get(idxWithinInv);
            ItemStack remainingStack = remaining.get(i);
            if (!current.isEmpty()) {
                current.decrement(1);
            }
            if (!remainingStack.isEmpty()) {
                if (current.isEmpty()) {
                    inventory.set(idxWithinInv, remainingStack);
                } else if (ItemStack.areItemsAndComponentsEqual(current, remainingStack)) {
                    current.increment(remainingStack.getCount());
                } else {
                    ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), remainingStack);
                }
            }
        }
        markDirty();
        lastRecipe.recipe = Optional.of(new RecipeEntry<Recipe<?>>(Identifier.of(""), recipe));
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
        private Optional<RecipeEntry<?>> recipe = Optional.empty();
        private List<Item> inputItems;

        Optional<? extends RecipeEntry<?>> getRecipe(World world) {
            if (recipe.isPresent()) {
                return recipe;
            }

            if (lockedRecipeNamespace.isEmpty() || lockedRecipePath.isEmpty()) {
                return Optional.empty();
            }
            return world.getRecipeManager().get(Identifier.of(lockedRecipeNamespace.get(), lockedRecipePath.get()));
        }
    }
}
