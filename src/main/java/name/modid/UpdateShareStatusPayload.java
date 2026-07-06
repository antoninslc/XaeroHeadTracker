package name.modid;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateShareStatusPayload(boolean isSharing) implements CustomPacketPayload {

    // Identifiant unique pour ce paquet
    public static final CustomPacketPayload.Type<UpdateShareStatusPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("xaeroheadtracker", "update_share_status"));

    // Codec pour traduire le booléen en données réseau
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateShareStatusPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, UpdateShareStatusPayload::isSharing,
            UpdateShareStatusPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}