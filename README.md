# Auto Recipe
Auto Recipe is a JIJ library intended to make the creation of data-driven recipes painless, by automatically generating serializers based on annotations, with support out of the box for a majority of common types, and the ability to be register your own serializers for your own recipe types, or override existing ones, without conflicting with others.

## Maven

```java
repositories {
	maven {
		name = "TerraformersMC"
		url = "https://maven.terraformersmc.com/"
	}
}
dependencies {
	modImplementation "dev.emi:autorecipe:${auto_recipe_version}"
	include "dev.emi:autorecipe:${auto_recipe_version}"
}
```

## Usage
The main thing you need to use this library is an annotated recipe type, for example:

```java
public class TestRecipe extends AutoSerializedRecipe<Inventory> {
	@RecipeVar("input")
	ItemStack input;
	@RecipeVar("output")
	ItemStack output;
	@RecipeVar("time")
	int processingTime;

	/* Your recipe functions */
}
```

Then you need to let Auto Recipe know you'd like to have this be a recipe, and where, with an identifier for your serializer and recipe type, and a supplier for your type, intended to be a constructor with zero arguments.

```java
RecipeType test = AutoRecipeRegistry.registerRecipeSerializer(new Identifier("mymod", "test"), TestRecipe::new);
```

And you're done, if json recipes are added, they will be parsed and accessible like any other recipe type, as an example

```json
{
  "input": "minecraft:stone",
  "output": {
	  "item": "minecraft:cobblestone",
	  "count": 2
  },
  "time": 44
}
```

Auto Recipe can handle many common types, such as all java primitives, strings, `ItemStack`s,`Block`s, `Ingredient`s, as well as the ability to parse and understand `List`s, and can parse `Map`s (where the key is a string, or a type that has a constructor that takes 1 string, such as `Identifier`s). This list will never be complete, however, and you can add (and override) parsers with your own in your namespace, with `AutoRecipeRegistry.registerVariableSerializer(String namespace, Class<T> clazz, RecipeVarSerializer<T> serializer);`.

### Notes
This guide used a child class of `AutoSerializedRecipe`, but this is not always desired. If you'd like to use your own base class, you need to pass a function that takes an `Identifier` (representing the recipe's identifier) and returning anything extending `Recipe`. This can be a constructor that takes a single `Identifier`.

In addition to `registerVariableSerializer` for namespaced serializers, `AutoRecipeRegistry.registerGlobalVariableSerializer(Class<T> clazz, RecipeVarSerializer<T> serializer);` is available, which apples to every namespace after its own local serializers. It is not recommended to use this, as it can cause incompatibilities, but is documented in case it could be of use.