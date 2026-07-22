package name.modid;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod(XaeroHeadTracker.MOD_ID)
public class XaeroHeadTracker {
	public static final String MOD_ID = "xaeroheadtracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int compteurTemps = 0;
	public static final Set<UUID> joueursCaches = new HashSet<>();

	public XaeroHeadTracker(IEventBus modEventBus) {
		// Enregistrement des événements réseau spécifiques au mod
		modEventBus.addListener(this::registerNetworking);

		// Enregistrement de la boucle (tick) sur le bus global du jeu
		NeoForge.EVENT_BUS.addListener(this::onServerTick);
	}

	private void registerNetworking(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("1");

		// Réception du paquet client -> serveur
		registrar.playToServer(
				UpdateShareStatusPayload.TYPE,
				UpdateShareStatusPayload.CODEC,
				(payload, context) -> {
					// On exécute le changement de manière synchronisée
					context.enqueueWork(() -> {
						UUID uuidJoueur = context.player().getUUID();
						if (payload.isSharing()) {
							joueursCaches.remove(uuidJoueur);
						} else {
							joueursCaches.add(uuidJoueur);
						}
					});
				}
		);

		// Déclaration du paquet serveur -> client
		registrar.playToClient(
				PlayerPositionPayload.TYPE,
				PlayerPositionPayload.CODEC,
				name.modid.client.XaeroHeadTrackerClient::recevoirPositions
		);
	}

	private void onServerTick(ServerTickEvent.Post event) {
		compteurTemps++;
		if (compteurTemps >= 20) {
			compteurTemps = 0;

			for (ServerPlayer joueur : event.getServer().getPlayerList().getPlayers()) {
				if (joueursCaches.contains(joueur.getUUID())) {
					continue;
				}

				String pseudo = joueur.getName().getString();
				UUID uuid = joueur.getUUID();
				double x = joueur.getX();
				double y = joueur.getY();
				double z = joueur.getZ();

				PlayerPositionPayload paquet = new PlayerPositionPayload(pseudo, uuid, x, y, z);

				// NeoForge gère l'envoi global plus facilement que Fabric
				PacketDistributor.sendToAllPlayers(paquet);
			}
		}
	}
}