package name.modid;

import net.minecraft.network.FriendlyByteBuf;

// Derniere position d'un joueur parti avant notre connexion : age en ms, skin signe par Mojang (chaines vides si inconnu)
public record PlayerLastPositionPayload(PlayerPositionPayload position, long age, String skin, String signature) {

    public static void encode(PlayerLastPositionPayload p, FriendlyByteBuf buf) {
        PlayerPositionPayload.encode(p.position(), buf);
        buf.writeVarLong(p.age());
        buf.writeUtf(p.skin());
        buf.writeUtf(p.signature());
    }

    public static PlayerLastPositionPayload decode(FriendlyByteBuf buf) {
        return new PlayerLastPositionPayload(PlayerPositionPayload.decode(buf), buf.readVarLong(), buf.readUtf(), buf.readUtf());
    }
}
