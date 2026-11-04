package name.modid;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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

	// UUID des joueurs qui ont demandé à partager : sans le mod ou juste après la connexion, un joueur n'y figure pas
	public static final Set<UUID> joueursPartageant = new HashSet<>();

	@Override
	public void onInitialize() {
		// 1. Déclarations S2C et C2S
		PayloadTypeRegistry.playS2C().register(PlayerPositionPayload.TYPE, PlayerPositionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlayerStopSharePayload.TYPE, PlayerStopSharePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UpdateShareStatusPayload.TYPE, UpdateShareStatusPayload.CODEC);

		// 2. Écouteur C2S : Quand un client change ses paramètres
		ServerPlayNetworking.registerGlobalReceiver(UpdateShareStatusPayload.TYPE, (payload, context) -> {
			definirPartage(context.player(), payload.isSharing());
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (joueursPartageant.remove(handler.getPlayer().getUUID())) {
				diffuser(server, new PlayerStopSharePayload(handler.getPlayer().getName().getString(), false));
			}
		});

		// Dev uniquement : un faux joueur Carpet n'a pas de client pour envoyer son choix de partage
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
					dispatcher.register(Commands.literal("xhtdev")
							.then(Commands.literal("partage")
									.then(Commands.argument("joueur", EntityArgument.player())
											.then(Commands.argument("actif", BoolArgumentType.bool())
													.executes(ctx -> {
														definirPartage(EntityArgument.getPlayer(ctx, "joueur"), BoolArgumentType.getBool(ctx, "actif"));
														return 1;
													}))))));
		}

		// 3. Boucle de diffusion
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			compteurTemps++;
			if (compteurTemps >= 20) {
				compteurTemps = 0;

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

					diffuser(server, paquet);
				}
			}
		});
	}

	private static void diffuser(MinecraftServer server, CustomPacketPayload paquet) {
		for (ServerPlayer destinataire : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(destinataire, paquet.type())) {
				ServerPlayNetworking.send(destinataire, paquet);
			}
		}
	}

	private static void definirPartage(ServerPlayer joueur, boolean actif) {
		if (actif) {
			joueursPartageant.add(joueur.getUUID());
		} else if (joueursPartageant.remove(joueur.getUUID())) {
			diffuser(joueur.level().getServer(), new PlayerStopSharePayload(joueur.getName().getString(), true));
		}
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			LOGGER.info("[XHT Dev] Partage de {} : {}", joueur.getName().getString(), actif);
		}
	}
}