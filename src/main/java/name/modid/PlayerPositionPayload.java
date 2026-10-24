package name.modid;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record PlayerPositionPayload(String pseudo, UUID uuid, double x, double y, double z) {

    public static void encode(PlayerPositionPayload p, FriendlyByteBuf buf) {
        buf.writeUtf(p.pseudo());
        buf.writeUUID(p.uuid());
        buf.writeDouble(p.x());
        buf.writeDouble(p.y());
        buf.writeDouble(p.z());
    }

    public static PlayerPositionPayload decode(FriendlyByteBuf buf) {
        return new PlayerPositionPayload(buf.readUtf(), buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
