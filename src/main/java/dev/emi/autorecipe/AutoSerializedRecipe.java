package dev.emi.autorecipe;

import net.minecraft.inventory.Inventory;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;

public abstract class AutoSerializedRecipe<T extends Inventory> implements Recipe<T> {
	public Identifier id;

	@Override
	public boolean fits(int width, int height) {
		return true;
	}

	@Override
	public Identifier getId() {
		return id;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return AutoRecipeRegistry.getRecipeSerializer(getClass());
	}

	@Override
	public RecipeType<?> getType() {
		return AutoRecipeRegistry.getRecipeType(getClass());
	}	
}
