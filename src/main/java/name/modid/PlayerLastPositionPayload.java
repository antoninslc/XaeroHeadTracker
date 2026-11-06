package name.modid;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Derniere position d'un joueur parti avant notre connexion : age en ms, skin signe par Mojang (chaines vides si inconnu)
public record PlayerLastPositionPayload(PlayerPositionPayload position, long age, String skin, String signature) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerLastPositionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("xaeroheadtracker", "player_last_position"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerLastPositionPayload> CODEC = StreamCodec.composite(
            PlayerPositionPayload.CODEC, PlayerLastPositionPayload::position,
            ByteBufCodecs.VAR_LONG, PlayerLastPositionPayload::age,
            ByteBufCodecs.STRING_UTF8, PlayerLastPositionPayload::skin,
            ByteBufCodecs.STRING_UTF8, PlayerLastPositionPayload::signature,
            PlayerLastPositionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
