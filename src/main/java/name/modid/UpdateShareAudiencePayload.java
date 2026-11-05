package name.modid;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Le client indique qui peut voir sa position : tout le monde, ou seulement les joueurs de la liste
public record UpdateShareAudiencePayload(boolean tout, List<UUID> joueurs) {

    // shortcut: au-dela, le paquet est refuse et le joueur deconnecte ; a relever si un serveur depasse 1024 joueurs retenus
    public static final int MAX_JOUEURS = 1024;

    public static void encode(UpdateShareAudiencePayload p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.tout());
        buf.writeVarInt(p.joueurs().size());
        p.joueurs().forEach(buf::writeUUID);
    }

    public static UpdateShareAudiencePayload decode(FriendlyByteBuf buf) {
        boolean tout = buf.readBoolean();
        int taille = buf.readVarInt();
        // Le serveur ne fait pas confiance a la taille annoncee par le client
        if (taille < 0 || taille > MAX_JOUEURS) {
            throw new IllegalArgumentException("Liste de partage trop grande : " + taille);
        }
        List<UUID> joueurs = new ArrayList<>(taille);
        for (int i = 0; i < taille; i++) {
            joueurs.add(buf.readUUID());
        }
        return new UpdateShareAudiencePayload(tout, joueurs);
    }
}
