package name.modid;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

// 1. On ajoute "String dimension" à la fin de la déclaration
public record PlayerPositionPayload(String pseudo, UUID uuid, double x, double y, double z, String dimension) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerPositionPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("xaeroheadtracker", "player_position"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPositionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlayerPositionPayload::pseudo,
            UUIDUtil.STREAM_CODEC, PlayerPositionPayload::uuid,
            ByteBufCodecs.DOUBLE, PlayerPositionPayload::x,
            ByteBufCodecs.DOUBLE, PlayerPositionPayload::y,
            ByteBufCodecs.DOUBLE, PlayerPositionPayload::z,
            ByteBufCodecs.STRING_UTF8, PlayerPositionPayload::dimension, // 2. On ajoute la dimension dans le Codec !
            PlayerPositionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}