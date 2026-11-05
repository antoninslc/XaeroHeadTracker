package name.modid;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod(XaeroHeadTracker.MOD_ID)
public class XaeroHeadTracker {
	public static final String MOD_ID = "xaeroheadtracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int compteurTemps = 0;

	// UUID des joueurs qui ont demandé à partager : sans le mod ou juste après la connexion, un joueur n'y figure pas
	public static final Set<UUID> joueursPartageant = new HashSet<>();

	// Joueurs autorisés à voir chaque partageur. Pas d'entrée = tout le monde : c'est ce qu'a choisi un client d'une version sans sélection
	private static final Map<UUID, Set<UUID>> audiences = new HashMap<>();

	// Permet à l'admin de couper tout le partage du serveur ; le choix de chaque joueur reste en mémoire
	public static final GameRules.Key<GameRules.BooleanValue> PARTAGE_AUTORISE = GameRules.register(MOD_ID + ":allow_sharing",
			GameRules.Category.MISC, GameRules.BooleanValue.create(true, (server, regle) -> partageAutoriseChange(server, regle.get())));

	public XaeroHeadTracker(IEventBus modEventBus) {
		// Enregistrement des événements réseau spécifiques au mod
		modEventBus.addListener(this::registerNetworking);

		// Enregistrement de la boucle (tick) sur le bus global du jeu
		NeoForge.EVENT_BUS.addListener(this::onServerTick);
		NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
	}

	private void registerNetworking(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("1");

		// Réception du paquet client -> serveur
		registrar.playToServer(
				UpdateShareStatusPayload.TYPE,
				UpdateShareStatusPayload.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					ServerPlayer joueur = (ServerPlayer) context.player();
					definirPartage(joueur, payload.sharePosition());

					// Le client annonce son choix à chaque connexion : il a pu être absent au moment de la coupure
					if (!joueur.level().getServer().getGameRules().getBoolean(PARTAGE_AUTORISE)
							&& joueur.connection.hasChannel(PlayerStopSharePayload.TYPE)) {
						PacketDistributor.sendToPlayer(joueur, new PlayerStopSharePayload(PlayerStopSharePayload.TOUS, true));
					}
				})
		);

		// Déclaration du paquet serveur -> client
		registrar.playToClient(
				PlayerPositionPayload.TYPE,
				PlayerPositionPayload.CODEC,
				name.modid.client.XaeroHeadTrackerClient::recevoirPositions
		);

		// Optionnels : un client ou un serveur en version précédente peut toujours se connecter
		final PayloadRegistrar optionnel = registrar.optional();
		optionnel.playToClient(
				PlayerStopSharePayload.TYPE,
				PlayerStopSharePayload.CODEC,
				name.modid.client.XaeroHeadTrackerClient::recevoirArretPartage
		);
		optionnel.playToServer(
				UpdateShareAudiencePayload.TYPE,
				UpdateShareAudiencePayload.CODEC,
				(payload, context) -> context.enqueueWork(() ->
						definirAudience((ServerPlayer) context.player(), payload.tout(), payload.joueurs()))
		);
	}

	private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		ServerPlayer joueur = (ServerPlayer) event.getEntity();
		if (joueursPartageant.remove(joueur.getUUID())) {
			diffuser(joueur.level().getServer(), new PlayerStopSharePayload(joueur.getName().getString(), false));
		}
		audiences.remove(joueur.getUUID());
	}

	private void onServerTick(ServerTickEvent.Post event) {
		compteurTemps++;
		if (compteurTemps >= 20) {
			compteurTemps = 0;

			MinecraftServer server = event.getServer();
			if (!server.getGameRules().getBoolean(PARTAGE_AUTORISE)) {
				return;
			}

			for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
				if (!joueursPartageant.contains(joueur.getUUID())) {
					continue;
				}

				String pseudo = joueur.getName().getString();
				UUID uuid = joueur.getUUID();
				double x = joueur.getX();
				double y = joueur.getY();
				double z = joueur.getZ();

				PlayerPositionPayload paquet = new PlayerPositionPayload(pseudo, uuid, x, y, z);

				Set<UUID> audience = audiences.get(uuid);
				for (ServerPlayer destinataire : server.getPlayerList().getPlayers()) {
					if ((audience == null || audience.contains(destinataire.getUUID()))
							&& destinataire.connection.hasChannel(paquet.type())) {
						PacketDistributor.sendToPlayer(destinataire, paquet);
					}
				}
			}
		}
	}

	private static void partageAutoriseChange(MinecraftServer server, boolean autorise) {
		if (!autorise) {
			diffuser(server, new PlayerStopSharePayload(PlayerStopSharePayload.TOUS, true));
		}
		if (!FMLEnvironment.production) {
			LOGGER.info("[XHT Dev] Partage autorise sur le serveur : {}", autorise);
		}
	}

	private static void diffuser(MinecraftServer server, CustomPacketPayload paquet) {
		for (ServerPlayer destinataire : server.getPlayerList().getPlayers()) {
			if (destinataire.connection.hasChannel(paquet.type())) {
				PacketDistributor.sendToPlayer(destinataire, paquet);
			}
		}
	}

	private static void definirAudience(ServerPlayer joueur, boolean tout, List<UUID> autorises) {
		if (tout) {
			audiences.remove(joueur.getUUID());
		} else {
			Set<UUID> audience = new HashSet<>(autorises);
			audiences.put(joueur.getUUID(), audience);

			// Un joueur retiré ne doit pas garder la tête affichée
			PlayerStopSharePayload retrait = new PlayerStopSharePayload(joueur.getName().getString(), true);
			for (ServerPlayer destinataire : joueur.level().getServer().getPlayerList().getPlayers()) {
				if (!audience.contains(destinataire.getUUID()) && destinataire.connection.hasChannel(retrait.type())) {
					PacketDistributor.sendToPlayer(destinataire, retrait);
				}
			}
		}
		if (!FMLEnvironment.production) {
			LOGGER.info("[XHT Dev] Audience de {} : {}", joueur.getName().getString(), tout ? "tout le monde" : autorises);
		}
	}

	private static void definirPartage(ServerPlayer joueur, boolean actif) {
		if (actif) {
			joueursPartageant.add(joueur.getUUID());
			if (!joueur.level().getServer().getGameRules().getBoolean(PARTAGE_AUTORISE)) {
				joueur.sendSystemMessage(Component.translatable("message.xaeroheadtracker.sharing_disabled"));
			}
		} else if (joueursPartageant.remove(joueur.getUUID())) {
			diffuser(joueur.level().getServer(), new PlayerStopSharePayload(joueur.getName().getString(), true));
		}
		if (!FMLEnvironment.production) {
			LOGGER.info("[XHT Dev] Partage de {} : {}", joueur.getName().getString(), actif);
		}
	}
}
