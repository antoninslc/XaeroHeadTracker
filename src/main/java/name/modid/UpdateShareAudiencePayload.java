package name.modid;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

// Qui peut voir ma position : le mode et la liste partent ensemble pour que le serveur n'applique jamais l'un sans l'autre
public record UpdateShareAudiencePayload(boolean tout, List<UUID> joueurs) implements CustomPacketPayload {

    // Le paquet vient du client : la taille est bornee. shortcut: au-dela, l'envoi echoue, a revoir si un joueur coche autant de monde
    public static final int MAX_JOUEURS = 1024;

    public static final CustomPacketPayload.Type<UpdateShareAudiencePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("xaeroheadtracker", "update_share_audience"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateShareAudiencePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, UpdateShareAudiencePayload::tout,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_JOUEURS)), UpdateShareAudiencePayload::joueurs,
            UpdateShareAudiencePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
