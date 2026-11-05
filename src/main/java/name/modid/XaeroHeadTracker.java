package name.modid;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
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

	// 2 depuis la 2.2.0 : un serveur en version 1 diffuserait à tout le monde, et un client en version 1 ne connaît pas les nouveaux paquets
	private static final String VERSION_PROTOCOLE = "2";
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(MOD_ID, "main"), () -> VERSION_PROTOCOLE, VERSION_PROTOCOLE::equals, VERSION_PROTOCOLE::equals);

	private int compteurTemps = 0;

	// UUID des joueurs qui ont demandé à partager : sans le mod ou juste après la connexion, un joueur n'y figure pas
	public static final Set<UUID> joueursPartageant = new HashSet<>();

	// Joueurs autorisés à voir chaque partageur. Pas d'entrée = tout le monde
	private static final Map<UUID, Set<UUID>> audiences = new HashMap<>();

	// Permet à l'admin de couper tout le partage du serveur ; le choix de chaque joueur reste en mémoire
	public static final GameRules.Key<GameRules.BooleanValue> PARTAGE_AUTORISE = GameRules.register(MOD_ID + ":allow_sharing",
			GameRules.Category.MISC, GameRules.BooleanValue.create(true, (server, regle) -> partageAutoriseChange(server, regle.get())));

	public XaeroHeadTracker() {
		registerNetworking();

		// Enregistrement de la boucle (tick) sur le bus global du jeu
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void registerNetworking() {
		// Reception du paquet client -> serveur
		CHANNEL.registerMessage(0, UpdateShareStatusPayload.class, UpdateShareStatusPayload::encode, UpdateShareStatusPayload::decode,
				(payload, ctx) -> {
					ctx.get().enqueueWork(() -> {
						ServerPlayer joueur = ctx.get().getSender();
						if (joueur == null) {
							return;
						}
						definirPartage(joueur, payload.sharePosition());

						// Le client annonce son choix à chaque connexion : il a pu être absent au moment de la coupure
						if (!joueur.getServer().getGameRules().getBoolean(PARTAGE_AUTORISE)) {
							envoyerA(joueur, new PlayerStopSharePayload(PlayerStopSharePayload.TOUS, true));
						}
					});
					ctx.get().setPacketHandled(true);
				});

		// Declaration du paquet serveur -> client
		CHANNEL.registerMessage(1, PlayerPositionPayload.class, PlayerPositionPayload::encode, PlayerPositionPayload::decode,
				(payload, ctx) -> {
					DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> name.modid.client.XaeroHeadTrackerClient.recevoirPositions(payload));
					ctx.get().setPacketHandled(true);
				});

		CHANNEL.registerMessage(2, PlayerStopSharePayload.class, PlayerStopSharePayload::encode, PlayerStopSharePayload::decode,
				(payload, ctx) -> {
					DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> name.modid.client.XaeroHeadTrackerClient.recevoirArretPartage(payload));
					ctx.get().setPacketHandled(true);
				});

		CHANNEL.registerMessage(3, UpdateShareAudiencePayload.class, UpdateShareAudiencePayload::encode, UpdateShareAudiencePayload::decode,
				(payload, ctx) -> {
					ctx.get().enqueueWork(() -> {
						ServerPlayer joueur = ctx.get().getSender();
						if (joueur != null) {
							definirAudience(joueur, payload.tout(), payload.joueurs());
						}
					});
					ctx.get().setPacketHandled(true);
				});
	}

	@SubscribeEvent
	public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		ServerPlayer joueur = (ServerPlayer) event.getEntity();
		if (joueursPartageant.remove(joueur.getUUID())) {
			CHANNEL.send(PacketDistributor.ALL.noArg(), new PlayerStopSharePayload(joueur.getName().getString(), false));
		}
		audiences.remove(joueur.getUUID());
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		compteurTemps++;
		if (compteurTemps >= 20) {
			compteurTemps = 0;

			MinecraftServer server = event.getServer();
			if (!server.getGameRules().getBoolean(PARTAGE_AUTORISE)) {
				return;
			}

			for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
				UUID uuid = joueur.getUUID();
				if (!joueursPartageant.contains(uuid)) {
					continue;
				}

				PlayerPositionPayload paquet = new PlayerPositionPayload(joueur.getName().getString(), uuid, joueur.getX(), joueur.getY(), joueur.getZ());

				Set<UUID> audience = audiences.get(uuid);
				for (ServerPlayer destinataire : server.getPlayerList().getPlayers()) {
					if (audience == null || audience.contains(destinataire.getUUID())) {
						envoyerA(destinataire, paquet);
					}
				}
			}
		}
	}

	private static void envoyerA(ServerPlayer destinataire, Object paquet) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> destinataire), paquet);
	}

	private static void partageAutoriseChange(MinecraftServer server, boolean autorise) {
		if (!autorise) {
			CHANNEL.send(PacketDistributor.ALL.noArg(), new PlayerStopSharePayload(PlayerStopSharePayload.TOUS, true));
		}
		if (!FMLEnvironment.production) {
			LOGGER.info("[XHT Dev] Partage autorise sur le serveur : {}", autorise);
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
			for (ServerPlayer destinataire : joueur.getServer().getPlayerList().getPlayers()) {
				if (!audience.contains(destinataire.getUUID())) {
					envoyerA(destinataire, retrait);
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
			if (!joueur.getServer().getGameRules().getBoolean(PARTAGE_AUTORISE)) {
				joueur.sendSystemMessage(Component.translatable("message.xaeroheadtracker.sharing_disabled"));
			}
		} else if (joueursPartageant.remove(joueur.getUUID())) {
			CHANNEL.send(PacketDistributor.ALL.noArg(), new PlayerStopSharePayload(joueur.getName().getString(), true));
		}
		if (!FMLEnvironment.production) {
			LOGGER.info("[XHT Dev] Partage de {} : {}", joueur.getName().getString(), actif);
		}
	}
}
