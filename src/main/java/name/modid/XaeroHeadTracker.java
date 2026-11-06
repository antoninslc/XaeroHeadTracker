package name.modid;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//? if >=1.21.11 {
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.minecraft.world.level.gamerules.GameRule;
//?} else {
/*import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;
*///?}
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class XaeroHeadTracker implements ModInitializer {
	public static final String MOD_ID = "xaeroheadtracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Duree pendant laquelle la derniere position d'un partageur deconnecte reste visible
	public static final long DUREE_ABSENT = 7L * 24 * 60 * 60 * 1000;

	private int compteurTemps = 0;

	// UUID des joueurs qui ont demandé à partager : sans le mod ou juste après la connexion, un joueur n'y figure pas
	public static final Set<UUID> joueursPartageant = new HashSet<>();

	// Joueurs autorisés à voir chaque partageur. Pas d'entrée = tout le monde : c'est ce qu'a choisi un client d'une version sans sélection
	private static final Map<UUID, Set<UUID>> audiences = new HashMap<>();

	// Permet à l'admin de couper tout le partage du serveur ; le choix de chaque joueur reste en mémoire
	//? if >=1.21.11 {
	public static final GameRule<Boolean> PARTAGE_AUTORISE = GameRuleBuilder.forBoolean(true)
			.buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "allow_sharing"));
	//?} else {
	/*public static final GameRules.Key<GameRules.BooleanValue> PARTAGE_AUTORISE = GameRuleRegistry.register(MOD_ID + ":allow_sharing",
			GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true, (server, regle) -> partageAutoriseChange(server, regle.get())));
	*///?}

	//? if >=1.21.11 {
	private static void partageAutoriseChange(boolean autorise, MinecraftServer server) {
	//?} else {
	/*private static void partageAutoriseChange(MinecraftServer server, boolean autorise) {
	*///?}
		if (!autorise) {
			diffuser(server, new PlayerStopSharePayload(PlayerStopSharePayload.TOUS, true));
			PositionsAbsents.vider();
		}
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			LOGGER.info("[XHT Dev] Partage autorise sur le serveur : {}", autorise);
		}
	}

	@Override
	public void onInitialize() {
		// 1. Déclarations S2C et C2S
		PayloadTypeRegistry.clientboundPlay().register(PlayerPositionPayload.TYPE, PlayerPositionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerStopSharePayload.TYPE, PlayerStopSharePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerLastPositionPayload.TYPE, PlayerLastPositionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateShareStatusPayload.TYPE, UpdateShareStatusPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateShareAudiencePayload.TYPE, UpdateShareAudiencePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UpdateShareAudiencePayload.TYPE, (payload, context) ->
				definirAudience(context.player(), payload.tout(), payload.joueurs()));

		// 2. Écouteur C2S : Quand un client change ses paramètres
		ServerPlayNetworking.registerGlobalReceiver(UpdateShareStatusPayload.TYPE, (payload, context) -> {
			definirPartage(context.player(), payload.isSharing());

			// Le client annonce son choix à chaque connexion : il a pu être absent au moment de la coupure
			if (!context.player().level().getServer().getGameRules().get(PARTAGE_AUTORISE)
					&& ServerPlayNetworking.canSend(context.player(), PlayerStopSharePayload.TYPE)) {
				ServerPlayNetworking.send(context.player(), new PlayerStopSharePayload(PlayerStopSharePayload.TOUS, true));
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(PositionsAbsents::charger);

		// Le joueur qui revient reprend la diffusion en direct ; il recoit les partageurs partis avant lui
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PositionsAbsents.retirer(handler.getPlayer().getUUID());
			if (server.getGameRules().get(PARTAGE_AUTORISE)) {
				PositionsAbsents.envoyer(handler.getPlayer());
			}
		});

		// Une connexion coupee arrive sur un thread reseau : les listes ne sont touchees que sur le thread du serveur
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> quitter(handler.getPlayer(), server)));

		//? if >=1.21.11 {
		GameRuleEvents.changeCallback(PARTAGE_AUTORISE).register(XaeroHeadTracker::partageAutoriseChange);
		//?}

		// Dev uniquement : un faux joueur Carpet n'a pas de client pour envoyer son choix de partage
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
					dispatcher.register(Commands.literal("xhtdev")
							// Carpet ne declenche pas DISCONNECT de Fabric : a lancer avant /player <nom> kill
							.then(Commands.literal("quitter")
									.then(Commands.argument("joueur", EntityArgument.player()).executes(ctx -> {
										ServerPlayer joueur = EntityArgument.getPlayer(ctx, "joueur");
										quitter(joueur, joueur.level().getServer());
										return 1;
									})))
							.then(Commands.literal("partage")
									.then(Commands.argument("joueur", EntityArgument.player())
											.then(Commands.argument("actif", BoolArgumentType.bool())
													.executes(ctx -> {
														definirPartage(EntityArgument.getPlayer(ctx, "joueur"), BoolArgumentType.getBool(ctx, "actif"));
														return 1;
													}))))
								.then(Commands.literal("audience")
										.then(Commands.argument("joueur", EntityArgument.player())
												.then(Commands.literal("tous").executes(ctx -> {
													definirAudience(EntityArgument.getPlayer(ctx, "joueur"), true, List.of());
													return 1;
												}))
												.then(Commands.literal("aucun").executes(ctx -> {
													definirAudience(EntityArgument.getPlayer(ctx, "joueur"), false, List.of());
													return 1;
												}))
												.then(Commands.literal("avec")
														.then(Commands.argument("cible", EntityArgument.player()).executes(ctx -> {
															definirAudience(EntityArgument.getPlayer(ctx, "joueur"), false,
																	List.of(EntityArgument.getPlayer(ctx, "cible").getUUID()));
															return 1;
														})))))));
		}

		// 3. Boucle de diffusion
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			compteurTemps++;
			if (compteurTemps >= 20) {
				compteurTemps = 0;

				if (!server.getGameRules().get(PARTAGE_AUTORISE)) {
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
								&& ServerPlayNetworking.canSend(destinataire, paquet.type())) {
							ServerPlayNetworking.send(destinataire, paquet);
						}
					}
				}
			}
		});
	}

	private static void quitter(ServerPlayer joueur, MinecraftServer server) {
		if (joueursPartageant.remove(joueur.getUUID())) {
			diffuser(server, new PlayerStopSharePayload(joueur.getName().getString(), false));
			if (server.getGameRules().get(PARTAGE_AUTORISE)) {
				PositionsAbsents.ajouter(joueur, audiences.get(joueur.getUUID()));
			}
		}
		audiences.remove(joueur.getUUID());
	}

	private static void diffuser(MinecraftServer server, CustomPacketPayload paquet) {
		for (ServerPlayer destinataire : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(destinataire, paquet.type())) {
				ServerPlayNetworking.send(destinataire, paquet);
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
				if (!audience.contains(destinataire.getUUID()) && ServerPlayNetworking.canSend(destinataire, retrait.type())) {
					ServerPlayNetworking.send(destinataire, retrait);
				}
			}
		}
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			LOGGER.info("[XHT Dev] Audience de {} : {}", joueur.getName().getString(), tout ? "tout le monde" : autorises);
		}
	}

	private static void definirPartage(ServerPlayer joueur, boolean actif) {
		if (actif) {
			joueursPartageant.add(joueur.getUUID());
			if (!joueur.level().getServer().getGameRules().get(PARTAGE_AUTORISE)) {
				joueur.sendSystemMessage(Component.translatable("message.xaeroheadtracker.sharing_disabled"));
			}
		} else if (joueursPartageant.remove(joueur.getUUID())) {
			diffuser(joueur.level().getServer(), new PlayerStopSharePayload(joueur.getName().getString(), true));
		}
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			LOGGER.info("[XHT Dev] Partage de {} : {}", joueur.getName().getString(), actif);
		}
	}
}