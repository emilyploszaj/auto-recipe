package dev.emi.autorecipe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AutoRecipeSerializer<T extends Recipe<?>> implements RecipeSerializer<T> {
	private static final Logger LOG = LogManager.getLogger("autorecipe");
	private final Function<Identifier, T> constructor;
	private final Map<RecipeVar, Field> variables = new LinkedHashMap<>();
	private final String namespace;

	public AutoRecipeSerializer(Function<Identifier, T> constructor, Class<T> clazz, Identifier id) {
		this.constructor = constructor;
		namespace = id.getNamespace();
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			RecipeVar annot = field.getAnnotation(RecipeVar.class);
			if (annot != null) {
				variables.put(annot, field);
				RecipeVarSerializer<?> serializer = AutoRecipeRegistry.getVariableSerializer(namespace, field.getType());
				if (field.getType() == Map.class) {

				} else if (serializer == null && field.getType() != List.class && field.getType() != Set.class
						&& field.getType() != DefaultedList.class) {
					LOG.warn("No serializer found for type " + field.getType().getTypeName() + " at "
						+ clazz.getTypeName() + "#" + field.getName() + ", has it not been registered yet?");
				}
			}
		}
	}

	@Override
	public T read(Identifier id, JsonObject json) {
		T t = constructor.apply(id);
		//if (t instanceof AutoSerializedRecipe asr && asr.id == null) {
		//	asr.id = id;
		//}
		if (t instanceof AutoSerializedRecipe) {
			AutoSerializedRecipe asr = (AutoSerializedRecipe) t;
			if (asr.id == null) {
				asr.id = id;
			}
		}
		for (Map.Entry<RecipeVar, Field> entry : variables.entrySet()) {
			RecipeVar var = entry.getKey();
			Field field = entry.getValue();
			try {
				String[] parts = var.value().split("/");
				JsonObject obj = json;
				int i = 0;
				while (i + 1 < parts.length) {
					obj = obj.getAsJsonObject(parts[i]);
					i++;
				}
				JsonElement el = obj.get(parts[i]);
				Class<?> fieldType = field.getType();
				if (fieldType == List.class || fieldType == Set.class || fieldType == DefaultedList.class) {
					Class<?> genericType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, genericType);
					Collection collection;
					if (fieldType == List.class) {
						collection = Lists.newArrayList();
					} else if (fieldType == Set.class) {
						collection = Sets.newHashSet();
					} else {
						collection = DefaultedList.of();
					}
					if (el.isJsonArray()) {
						for (JsonElement e : el.getAsJsonArray()) {
							collection.add(varSerializer.readJson(e));
						}
					} else {
						collection.add(varSerializer.readJson(el));
					}
					field.set(t, collection);
				} else if (fieldType ==  Map.class) {
					Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
					Class<?> stringType = (Class<?>) types[0];
					Class<?> genericType = (Class<?>) types[1];
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, genericType);
					Map map = Maps.newHashMap();
					Function<String, Object> keySupplier;
					if (stringType == String.class) {
						keySupplier = s -> s;
					} else {
						Constructor constructor = stringType.getConstructor(String.class);
						keySupplier = s -> {
							try {
								return constructor.newInstance(s);
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						};
					}
					for (Map.Entry<String, JsonElement> me : el.getAsJsonObject().entrySet()) {
						map.put(keySupplier.apply(me.getKey()), varSerializer.readJson(me.getValue()));
					}
					field.set(t, map);
				} else {
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, fieldType);
					field.set(t, varSerializer.readJson(el));
				}
			} catch (Exception e) {
				if (var.required()) {
					throw new RuntimeException("Error parsing recipe " + id + ", missing or malformed field " + field.getName(), e);
				}
			}
		}
		System.out.println("Read recipe: " + t);
		return t;
	}

	@Override
	public T read(Identifier id, PacketByteBuf buf) {
		T t = constructor.apply(id);
		//if (t instanceof AutoSerializedRecipe asr && asr.id == null) {
		//	asr.id = id;
		//}
		if (t instanceof AutoSerializedRecipe) {
			AutoSerializedRecipe asr = (AutoSerializedRecipe) t;
			if (asr.id == null) {
				asr.id = id;
			}
		}
		for (Map.Entry<RecipeVar, Field> entry : variables.entrySet()) {
			try {
				Field field = entry.getValue();
				Class<?> fieldType = field.getType();
				if (fieldType == List.class) {
					Class<?> genericType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, genericType);
					//field.set(t, buf.readList(varSerializer::readPacket));
					field.set(t, readCollection(buf, i -> new ArrayList(i), varSerializer::readPacket));
				} else if (fieldType == Set.class) {
					Class<?> genericType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, genericType);
					//field.set(t, buf.readCollection(i -> new HashSet(), varSerializer::readPacket));
					field.set(t, readCollection(buf, i -> new HashSet(), varSerializer::readPacket));
				}  else if (fieldType == DefaultedList.class) {
					Class<?> genericType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, genericType);
					//field.set(t, buf.readCollection(i -> DefaultedList.of(), varSerializer::readPacket));
					field.set(t, readCollection(buf, i -> DefaultedList.of(), varSerializer::readPacket));
				} else if (fieldType ==  Map.class) {
					Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
					Class<?> stringType = (Class<?>) types[0];
					Class<?> genericType = (Class<?>) types[1];
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, genericType);
					Function<String, Object> keySupplier;
					if (stringType == String.class) {
						keySupplier = s -> s;
					} else {
						Constructor constructor = stringType.getConstructor(String.class);
						keySupplier = s -> {
							try {
								return constructor.newInstance(s);
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						};
					}
					//field.set(t, buf.readMap(b -> keySupplier.apply(b.readString()), varSerializer::readPacket));
					field.set(t, readMap(buf, Maps::newHashMapWithExpectedSize, b -> keySupplier.apply(b.readString()),
						varSerializer::readPacket));
				} else {
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, fieldType);
					field.set(t, varSerializer.readPacket(buf));
				}
			} catch (Exception e) {
				throw new RuntimeException("Error parsing packet", e);
			}
		}
		System.out.println("Read [(remote)] recipe: " + t);
		return t;
	}

	@Override
	public void write(PacketByteBuf buf, T recipe) {
		for (Map.Entry<RecipeVar, Field> entry : variables.entrySet()) {
			try {
				Field field = entry.getValue();
				Class<?> fieldType = field.getType();
				if (fieldType == List.class || fieldType == Set.class || fieldType == DefaultedList.class) {
					Class<?> genericType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
					RecipeVarSerializer varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, genericType);
					Collection list = (Collection) field.get(recipe);
					//buf.writeCollection(list, varSerializer::writePacket);
					writeCollection(buf, list, varSerializer::writePacket);
				} else if (fieldType ==  Map.class) {
					Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
					Class<?> genericType = (Class<?>) types[1];
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, genericType);
					//buf.writeMap((Map) field.get(recipe), (b, k) -> b.writeString(k.toString()), varSerializer::writePacket);
					writeMap(buf, (Map) field.get(recipe), (b, k) -> b.writeString(k.toString()), varSerializer::writePacket);
				} else {
					RecipeVarSerializer<?> varSerializer = AutoRecipeRegistry.getVariableSerializer(namespace, fieldType);
					// Generic hack
					varSerializer.getClass().getMethod("writePacket", PacketByteBuf.class, Object.class)
						.invoke(varSerializer, buf, field.get(recipe));
				}
			} catch (Exception e) {
				throw new RuntimeException("Error writing packet", e);
			}
		}
	}

	private <G> void writeCollection(PacketByteBuf buf, Collection<G> collection, BiConsumer<PacketByteBuf, G> entrySerializer) {
		buf.writeVarInt(collection.size());
		for (G val : collection) {
			entrySerializer.accept(buf, val);
		}
	}

	private <G, C extends Collection<G>> C readCollection(PacketByteBuf buf, IntFunction<C> collectionFactory,
			Function<PacketByteBuf, G> entryParser) {
		int i = buf.readVarInt();
		C collection = collectionFactory.apply(i);
		for(int j = 0; j < i; j++) {
			collection.add(entryParser.apply(buf));
		}
		return collection;
	}

	private <K, V> void writeMap(PacketByteBuf buf, Map<K, V> map, BiConsumer<PacketByteBuf, K> keySerializer,
			BiConsumer<PacketByteBuf, V> valueSerializer) {
		buf.writeVarInt(map.size());
		map.forEach((key, value) -> {
			keySerializer.accept(buf, key);
			valueSerializer.accept(buf, value);
		});
	}

	public <K, V, M extends Map<K, V>> M readMap(PacketByteBuf buf, IntFunction<M> mapFactory,
			Function<PacketByteBuf, K> keyParser, Function<PacketByteBuf, V> valueParser) {
		int i = buf.readVarInt();
		M map = mapFactory.apply(i);
		for(int j = 0; j < i; j++) {
			K key = keyParser.apply(buf);
			V value = valueParser.apply(buf);
			map.put(key, value);
		}
		return map;
	}
}
