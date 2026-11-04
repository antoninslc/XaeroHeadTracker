package name.modid;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Un joueur ne diffuse plus sa position : partage coupé (retirer = true) ou déconnexion (retirer = false)
public record PlayerStopSharePayload(String pseudo, boolean retirer) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerStopSharePayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("xaeroheadtracker", "player_stop_share"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerStopSharePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlayerStopSharePayload::pseudo,
            ByteBufCodecs.BOOL, PlayerStopSharePayload::retirer,
            PlayerStopSharePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
