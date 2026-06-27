package name.modid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class XaeroHeadTracker implements ModInitializer {
	public static final String MOD_ID = "xaeroheadtracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int compteurTemps = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("Le XaeroHeadTracker s'allume avec succès !");

		// 1. LA DÉCLARATION : On prévient le serveur que notre "TYPE" de paquet existe sur le réseau
		PayloadTypeRegistry.clientboundPlay().register(PlayerPositionPayload.TYPE, PlayerPositionPayload.CODEC);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			compteurTemps++;

			if (compteurTemps >= 20) {
				compteurTemps = 0;

				for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {

					String pseudo = joueur.getName().getString();
					UUID uuid = joueur.getUUID();
					double x = joueur.getX();
					double y = joueur.getY();
					double z = joueur.getZ();

					// 2. L'EMBALLAGE : On met nos variables dans notre paquet
					PlayerPositionPayload paquet = new PlayerPositionPayload(pseudo, uuid, x, y, z);

					// 3. L'ENVOI : On balance la trame sur le réseau à tous les joueurs connectés
					for (ServerPlayer destinataire : server.getPlayerList().getPlayers()) {
						ServerPlayNetworking.send(destinataire, paquet);
					}
				}
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}