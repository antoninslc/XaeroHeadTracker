package name.modid;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PlayerPositionPayload(String pseudo, UUID uuid, double x, double y, double z) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerPositionPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("xaeroheadtracker", "player_position"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPositionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlayerPositionPayload::pseudo,
            UUIDUtil.STREAM_CODEC, PlayerPositionPayload::uuid,
            ByteBufCodecs.DOUBLE, PlayerPositionPayload::x,
            ByteBufCodecs.DOUBLE, PlayerPositionPayload::y,
            ByteBufCodecs.DOUBLE, PlayerPositionPayload::z,
            PlayerPositionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}