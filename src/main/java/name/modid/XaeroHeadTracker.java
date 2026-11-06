package name.modid;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
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

	// Duree pendant laquelle la derniere position d'un partageur deconnecte reste visible
	public static final long DUREE_ABSENT = 7L * 24 * 60 * 60 * 1000;

	private int compteurTemps = 0;

	// UUID des joueurs qui ont demandé à partager : sans le mod ou juste après la connexion, un joueur n'y figure pas
	public static final Set<UUID> joueursPartageant = new HashSet<>();

	// Joueurs autorisés à voir chaque partageur. Pas d'entrée = tout le monde : c'est ce qu'a choisi un client d'une version sans sélection
	private static final Map<UUID, Set<UUID>> audiences = new HashMap<>();

	private static final DeferredRegister<GameRule<?>> REGLES = DeferredRegister.create(Registries.GAME_RULE, MOD_ID);

	// Permet à l'admin de couper tout le partage du serveur ; le choix de chaque joueur reste en mémoire
	public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> PARTAGE_AUTORISE = REGLES.register("allow_sharing",
			() -> new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
					Codec.BOOL, autorise -> autorise ? 1 : 0, true, FeatureFlagSet.of()));

	private boolean partageEtaitAutorise = true;

	public XaeroHeadTracker(IEventBus modEventBus) {
		REGLES.register(modEventBus);

		// Enregistrement des événements réseau spécifiques au mod
		modEventBus.addListener(this::registerNetworking);

		// Enregistrement de la boucle (tick) sur le bus global du jeu
		NeoForge.EVENT_BUS.addListener(this::onServerTick);
		NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
		NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> PositionsAbsents.charger(event.getServer()));
	}

	// Le joueur qui revient reprend la diffusion en direct ; il recoit les partageurs partis avant lui
	private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		ServerPlayer joueur = (ServerPlayer) event.getEntity();
		PositionsAbsents.retirer(joueur.getUUID());
		if (joueur.level().getServer().getGameRules().get(PARTAGE_AUTORISE.get())) {
			PositionsAbsents.envoyer(joueur);
		}
	}

	private void registerNetworking(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("1");

		// Réception du paquet client -> serveur
		registrar.playToServer(
				UpdateShareStatusPayload.TYPE,
				UpdateShareStatusPayload.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					ServerPlayer joueur = (ServerPlayer) context.player();
					definirPartage(joueur, payload.isSharing());

					// Le client annonce son choix à chaque connexion : il a pu être absent au moment de la coupure
					if (!joueur.level().getServer().getGameRules().get(PARTAGE_AUTORISE.get())
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
		optionnel.playToClient(
				PlayerLastPositionPayload.TYPE,
				PlayerLastPositionPayload.CODEC,
				name.modid.client.XaeroHeadTrackerClient::recevoirDernierePosition
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
			if (joueur.level().getServer().getGameRules().get(PARTAGE_AUTORISE.get())) {
				PositionsAbsents.ajouter(joueur, audiences.get(joueur.getUUID()));
			}
		}
		audiences.remove(joueur.getUUID());
	}

	private void onServerTick(ServerTickEvent.Post event) {
		compteurTemps++;
		if (compteurTemps >= 20) {
			compteurTemps = 0;

			MinecraftServer server = event.getServer();
			boolean autorise = server.getGameRules().get(PARTAGE_AUTORISE.get());
			if (autorise != partageEtaitAutorise) {
				partageEtaitAutorise = autorise;
				if (!autorise) {
					diffuser(server, new PlayerStopSharePayload(PlayerStopSharePayload.TOUS, true));
					PositionsAbsents.vider();
				}
				if (!FMLEnvironment.isProduction()) {
					LOGGER.info("[XHT Dev] Partage autorise sur le serveur : {}", autorise);
				}
			}
			if (!autorise) {
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

				String dimensionJoueur = joueur.level().dimension().identifier().toString();

				PlayerPositionPayload paquet = new PlayerPositionPayload(pseudo, uuid, x, y, z, dimensionJoueur);

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
		if (!FMLEnvironment.isProduction()) {
			LOGGER.info("[XHT Dev] Audience de {} : {}", joueur.getName().getString(), tout ? "tout le monde" : autorises);
		}
	}

	private static void definirPartage(ServerPlayer joueur, boolean actif) {
		if (actif) {
			joueursPartageant.add(joueur.getUUID());
			if (!joueur.level().getServer().getGameRules().get(PARTAGE_AUTORISE.get())) {
				joueur.sendSystemMessage(Component.translatable("message.xaeroheadtracker.sharing_disabled"));
			}
		} else if (joueursPartageant.remove(joueur.getUUID())) {
			diffuser(joueur.level().getServer(), new PlayerStopSharePayload(joueur.getName().getString(), true));
		}
		if (!FMLEnvironment.isProduction()) {
			LOGGER.info("[XHT Dev] Partage de {} : {}", joueur.getName().getString(), actif);
		}
	}
}
