package dev.emi.autorecipe;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.registry.Registry;

@SuppressWarnings("unchecked")
public class AutoRecipeRegistry {
	private static final Logger LOG = LogManager.getLogger("autorecipe");
	private static final Identifier AUTO_CLASS = new Identifier("auto", "class");
	private static final Map<Class<? extends Recipe<?>>, AutoRecipeSerializer<?>> RECIPE_SERIALIZERS = new HashMap<>();
	private static final Map<Class<? extends Recipe<?>>, RecipeType<?>> RECIPE_TYPES = new HashMap<>();
	private static final Map<Class<?>, RecipeVarSerializer<?>> VAR_SERIALIZERS = new HashMap<>();
	private static final Map<String, Map<Class<?>, RecipeVarSerializer<?>>> SCOPED_VAR_SERIALIZERS = new HashMap<>();

	/**
	 * Creates and registers a recipe type and serializer based on the provided supplier of
	 * a child of {@link AutoSerializedRecipe}, such as a blank constructor
	 * 
	 * @return The recipe type
	 */
	public static <V extends Inventory, T extends AutoSerializedRecipe<V>> AutoSerializedRecipe<V> registerRecipeSerializer(Identifier id,
			Supplier<T> supplier) {
		return (AutoSerializedRecipe<V>) registerRecipeSerializer(id, i -> supplier.get());
	}

	/**
	 * Creates and registers a recipe type and serializer based on the provided function of
	 * an {@link Identifier} to a {@link Recipe}, such as a constructor
	 * 
	 * @return The recipe type
	 */
	public static <V extends Inventory, T extends Recipe<V>> RecipeType<T> registerRecipeSerializer(Identifier id,
			Function<Identifier, T> function) {
		Class<T> clazz = (Class<T>) function.apply(AUTO_CLASS).getClass();
		AutoRecipeSerializer<T> serializer = new AutoRecipeSerializer<>(function, clazz, id);
		RECIPE_SERIALIZERS.put(clazz, serializer);
		RecipeType<T> type = new RecipeType<T>() {
			public String toString() {
				return id.toString();
			}
		};
		RECIPE_TYPES.put(clazz, type);
		Registry.register(Registry.RECIPE_TYPE, id, type);
		Registry.register(Registry.RECIPE_SERIALIZER, id, serializer);
		return type;
	}

	/**
	 * Registers a recipe variable serializer for a given class in a given namespace
	 */
	public static <T> void registerVariableSerializer(String namespace, Class<T> clazz, RecipeVarSerializer<T> serializer) {
		Map<Class<?>, RecipeVarSerializer<?>> map = SCOPED_VAR_SERIALIZERS.putIfAbsent(namespace, new HashMap<>());
		if (map.containsKey(clazz)) {
			LOG.warn("Variable serializer registered over existing serializer for class " + clazz.getTypeName());
		}
		map.put(clazz, serializer);
	}

	public static <T> void registerGlobalVariableSerializer(Class<T> clazz, RecipeVarSerializer<T> serializer) {
		if (VAR_SERIALIZERS.containsKey(clazz)) {
			LOG.warn("Global variable serializer registered over existing serializer for class " + clazz.getTypeName());
		}
		VAR_SERIALIZERS.put(clazz, serializer);
	}

	public static AutoRecipeSerializer<? extends Recipe<?>> getRecipeSerializer(Class<?> clazz) {
		return RECIPE_SERIALIZERS.get(clazz);
	}

	public static RecipeVarSerializer<?> getVariableSerializer(String namespace, Class<?> clazz) {
		if (SCOPED_VAR_SERIALIZERS.containsKey(namespace)) {
			Map<Class<?>, RecipeVarSerializer<?>> map = SCOPED_VAR_SERIALIZERS.get(namespace);
			if (map.containsKey(clazz)) {
				return map.get(clazz);
			}
		}
		return VAR_SERIALIZERS.get(clazz);
	}

	public static RecipeType<?> getRecipeType(Class<?> clazz) {
		return RECIPE_TYPES.get(clazz);
	}
	
	private static <T> RecipeVarSerializer<T> newSerializer(Function<JsonElement, T> readJsonFunction,
			Function<PacketByteBuf, T> readPacketFunction, BiConsumer<PacketByteBuf, T> writePacketConsumer) {
		return new RecipeVarSerializer<T>() {
			public T readJson(JsonElement element) {
				return readJsonFunction.apply(element);
			}
			public T readPacket(PacketByteBuf buf) {
				return readPacketFunction.apply(buf);
			}
			public void writePacket(PacketByteBuf buf, T value) {
				writePacketConsumer.accept(buf, value);
			}

		};
	}

	static {
		AutoRecipeRegistry.registerGlobalVariableSerializer(boolean.class, newSerializer(
			element -> element.getAsBoolean(),
			buf -> buf.readBoolean(),
			(buf, value) -> buf.writeBoolean(value)
		));
		VAR_SERIALIZERS.put(Boolean.class, VAR_SERIALIZERS.get(boolean.class));
		AutoRecipeRegistry.registerGlobalVariableSerializer(byte.class, newSerializer(
			element -> element.getAsByte(),
			buf -> buf.readByte(),
			(buf, value) -> buf.writeByte(value)
		));
		VAR_SERIALIZERS.put(Byte.class, VAR_SERIALIZERS.get(byte.class));
		AutoRecipeRegistry.registerGlobalVariableSerializer(short.class, newSerializer(
			element -> element.getAsShort(),
			buf -> buf.readShort(),
			(buf, value) -> buf.writeShort(value)
		));
		VAR_SERIALIZERS.put(Short.class, VAR_SERIALIZERS.get(short.class));
		AutoRecipeRegistry.registerGlobalVariableSerializer(int.class, newSerializer(
			element -> element.getAsInt(),
			buf -> buf.readInt(),
			(buf, value) -> buf.writeInt(value)
		));
		VAR_SERIALIZERS.put(Integer.class, VAR_SERIALIZERS.get(int.class));
		AutoRecipeRegistry.registerGlobalVariableSerializer(long.class, newSerializer(
			element -> element.getAsLong(),
			buf -> buf.readLong(),
			(buf, value) -> buf.writeLong(value)
		));
		VAR_SERIALIZERS.put(Long.class, VAR_SERIALIZERS.get(long.class));
		AutoRecipeRegistry.registerGlobalVariableSerializer(float.class, newSerializer(
			element -> element.getAsFloat(),
			buf -> buf.readFloat(),
			(buf, value) -> buf.writeFloat(value)
		));
		VAR_SERIALIZERS.put(Float.class, VAR_SERIALIZERS.get(float.class));
		AutoRecipeRegistry.registerGlobalVariableSerializer(double.class, newSerializer(
			element -> element.getAsDouble(),
			buf -> buf.readDouble(),
			(buf, value) -> buf.writeDouble(value)
		));
		VAR_SERIALIZERS.put(Double.class, VAR_SERIALIZERS.get(double.class));
		AutoRecipeRegistry.registerGlobalVariableSerializer(String.class, newSerializer(
			element -> element.getAsString(),
			buf -> buf.readString(),
			(buf, value) -> buf.writeString(value)
		));
		AutoRecipeRegistry.registerGlobalVariableSerializer(Identifier.class, newSerializer(
			element -> new Identifier(element.getAsString()),
			buf -> buf.readIdentifier(),
			(buf, value) -> buf.writeIdentifier(value)
		));
		AutoRecipeRegistry.registerGlobalVariableSerializer(ItemStack.class, newSerializer(
			element -> {
				if (JsonHelper.isString(element)) {
					Identifier id = new Identifier(element.getAsString());
					return new ItemStack(Registry.ITEM.get(id));
				} else {
					JsonObject json = element.getAsJsonObject();
					Identifier id = new Identifier(JsonHelper.getString(json, "item"));
					int count = JsonHelper.getInt(json, "count", 1);
					return new ItemStack(Registry.ITEM.get(id), count);
				}
			},
			buf -> buf.readItemStack(),
			(buf, value) -> buf.writeItemStack(value)
		));
		AutoRecipeRegistry.registerGlobalVariableSerializer(Ingredient.class, newSerializer(
			element -> Ingredient.fromJson(element),
			buf -> Ingredient.fromPacket(buf),
			(buf, value) -> value.write(buf)
		));
		AutoRecipeRegistry.registerGlobalVariableSerializer(Block.class, newSerializer(
			element -> Registry.BLOCK.get(new Identifier(element.getAsString())),
			buf -> Registry.BLOCK.get(buf.readIdentifier()),
			(buf, value) -> buf.writeIdentifier(Registry.BLOCK.getId(value))
		));
	}
}
