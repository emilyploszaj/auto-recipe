package dev.emi.autorecipe;

import com.google.gson.JsonElement;

import net.minecraft.network.PacketByteBuf;

public interface RecipeVarSerializer<T> {
	T readJson(JsonElement element);
	T readPacket(PacketByteBuf buf);
	void writePacket(PacketByteBuf buf, T value);
}
