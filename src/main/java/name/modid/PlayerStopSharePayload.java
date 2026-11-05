package name.modid;

import net.minecraft.network.FriendlyByteBuf;

// Un joueur ne diffuse plus sa position : partage coupé (retirer = true) ou déconnexion (retirer = false)
public record PlayerStopSharePayload(String pseudo, boolean retirer) {

    // Pseudo vide, impossible pour un vrai joueur : concerne toutes les têtes, y compris celles des joueurs déconnectés
    public static final String TOUS = "";

    public static void encode(PlayerStopSharePayload p, FriendlyByteBuf buf) {
        buf.writeUtf(p.pseudo());
        buf.writeBoolean(p.retirer());
    }

    public static PlayerStopSharePayload decode(FriendlyByteBuf buf) {
        return new PlayerStopSharePayload(buf.readUtf(), buf.readBoolean());
    }
}
