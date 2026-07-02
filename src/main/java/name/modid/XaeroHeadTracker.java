package name.modid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class XaeroHeadTracker implements ModInitializer {
	public static final String MOD_ID = "xaeroheadtracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int compteurTemps = 0;

	// La "Blacklist" des UUID des joueurs qui refusent de partager leur position
	public static final Set<UUID> joueursCaches = new HashSet<>();

	@Override
	public void onInitialize() {
		// 1. Déclarations S2C et C2S
		PayloadTypeRegistry.clientboundPlay().register(PlayerPositionPayload.TYPE, PlayerPositionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateShareStatusPayload.TYPE, UpdateShareStatusPayload.CODEC);

		// 2. Écouteur C2S : Quand un client change ses paramètres
		ServerPlayNetworking.registerGlobalReceiver(UpdateShareStatusPayload.TYPE, (payload, context) -> {
			UUID uuidJoueur = context.player().getUUID();
			if (payload.isSharing()) {
				joueursCaches.remove(uuidJoueur); // Il veut partager, on le retire de la blacklist
			} else {
				joueursCaches.add(uuidJoueur); // Il se cache, on l'ajoute
			}
		});

		// 3. Boucle de diffusion
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			compteurTemps++;
			if (compteurTemps >= 20) {
				compteurTemps = 0;

				for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
					// L'ASTUCE EST ICI : Si le joueur est dans la liste des cachés, on saute son tour !
					if (joueursCaches.contains(joueur.getUUID())) {
						continue;
					}

					String pseudo = joueur.getName().getString();
					UUID uuid = joueur.getUUID();
					double x = joueur.getX();
					double y = joueur.getY();
					double z = joueur.getZ();

					PlayerPositionPayload paquet = new PlayerPositionPayload(pseudo, uuid, x, y, z);

					for (ServerPlayer destinataire : server.getPlayerList().getPlayers()) {
						ServerPlayNetworking.send(destinataire, paquet);
					}
				}
			}
		});
	}
}