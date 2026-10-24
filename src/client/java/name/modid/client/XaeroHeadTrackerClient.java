package name.modid.client;

import name.modid.mixin.GuiMapAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import name.modid.PlayerPositionPayload;

// L'IMPORT DE LA CARTE XAERO
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import xaero.map.gui.GuiMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.resources.DefaultPlayerSkin;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.components.AbstractButton;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import name.modid.UpdateShareStatusPayload;

public class XaeroHeadTrackerClient implements ClientModInitializer {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();
	public static boolean isMinimapInstalled = false;

	private static final String BOT_TEST_NAME = "BotTest";
	private static final UUID BOT_TEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static boolean simulerBotEnLigne = false;
	// Bots supplementaires crees par /xht simulate many, maintenus en ligne a chaque tick
	private static final Set<String> botsMultiplesEnLigne = new HashSet<>();
	private static final Set<String> botsMultiples = new HashSet<>();

	// 1. On déclare officiellement notre propre catégorie dans les paramètres du jeu
	public static final String CATEGORIE_MOD = "key.category.xaeroheadtracker.parametres";
	// 2. Notre raccourci clavier
	private static KeyMapping toucheParametres;

	private static final ResourceLocation EYE_TEXTURE = ResourceLocation.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	/** Retire de la carte les bots crees par /xht simulate many. */
	private static void supprimerBotsMultiples() {
		for (String nom : botsMultiples) {
			positionsJoueurs.remove(nom);
		}
		botsMultiples.clear();
		botsMultiplesEnLigne.clear();
	}

	/** Cle de stockage du serveur courant : adresse en multijoueur, dossier de sauvegarde en solo (le nom affiche n'est pas unique). */
	private static String identifierServeur(Minecraft client) {
		if (client.getSingleplayerServer() != null) {
			return "solo:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
		}
		if (client.getCurrentServer() != null) {
			return client.getCurrentServer().ip;
		}
		return null;
	}

	@Override
	public void onInitializeClient() {

		ModConfig.INSTANCE.charger();
		PlayerVisibilityConfig.INSTANCE.charger();

		// On demande à Fabric si l'un des deux mods Minimap de Xaero est présent dans le dossier mods
		isMinimapInstalled = FabricLoader.getInstance().isModLoaded("xaerominimap")
				|| FabricLoader.getInstance().isModLoaded("xaerominimapfair");

		// Dès qu'on rejoint un serveur (ou un monde solo), on annonce notre choix !
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			// On identifie le serveur (ou le monde solo) pour charger les joueurs choisis et le choix de partage
			PlayerVisibilityConfig.INSTANCE.definirServeurCourant(identifierServeur(client));

			ClientPlayNetworking.send(new UpdateShareStatusPayload(PlayerVisibilityConfig.INSTANCE.getPartage()));
		});

		// 1. On crée et on enregistre le raccourci (avec KeyBindingHelper !)
		toucheParametres = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.xaeroheadtracker.open_settings", // La clé magique est ici
				GLFW.GLFW_KEY_H,
				CATEGORIE_MOD
		));

		// 2. On écoute en permanence (à chaque "tick" du jeu) si la touche est pressée
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toucheParametres.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new ConfigScreen(null));
				} else if (client.screen.getClass().getName().contains("GuiMap")) {
					client.setScreen(new ConfigScreen(client.screen));
				}
			}

			for (String nomBot : botsMultiplesEnLigne) {
				PositionData pos = positionsJoueurs.get(nomBot);
				if (pos != null) {
					pos.timestamp = System.currentTimeMillis();
				}
			}

			if (simulerBotEnLigne) {
				PositionData botPos = positionsJoueurs.get(BOT_TEST_NAME);
				if (botPos != null) {
					botPos.timestamp = System.currentTimeMillis();
				}
			}
		});

		// Commandes de test disponibles UNIQUEMENT en environnement de développement
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
				dispatcher.register(ClientCommandManager.literal("xht")
						.then(ClientCommandManager.literal("simulate")
								.then(ClientCommandManager.literal("live").executes(ctx -> {
									var player = ctx.getSource().getPlayer();
									var client = ctx.getSource().getClient();
									String dim = player.level().dimension().location().toString();
									PositionData botPos = new PositionData(player.getX() + 15.0, player.getY(), player.getZ() + 15.0, BOT_TEST_UUID, dim);
									if (client.getConnection() != null) {
										PlayerInfo myInfo = client.getConnection().getPlayerInfo(player.getUUID());
										if (myInfo != null) {
											botPos.texturePeau = myInfo.getSkin().texture();
										}
									}
									positionsJoueurs.put(BOT_TEST_NAME, botPos);
									simulerBotEnLigne = true;
									ctx.getSource().sendFeedback(Component.literal("§a[XHT Dev] BotTest en ligne créé à +15 blocs de ta position !"));
									return 1;
								}))
								.then(ClientCommandManager.literal("disconnect").executes(ctx -> {
									simulerBotEnLigne = false;
									PositionData botPos = positionsJoueurs.get(BOT_TEST_NAME);
									if (botPos != null) {
										botPos.timestamp = System.currentTimeMillis() - 5000L;
										ctx.getSource().sendFeedback(Component.literal("§e[XHT Dev] BotTest déconnecté (statut < 1 min) !"));
									} else {
										ctx.getSource().sendError(Component.literal("Lance d'abord /xht simulate live"));
									}
									return 1;
								}))
								.then(ClientCommandManager.literal("old").executes(ctx -> {
									simulerBotEnLigne = false;
									PositionData botPos = positionsJoueurs.get(BOT_TEST_NAME);
									if (botPos != null) {
										botPos.timestamp = System.currentTimeMillis() - 5 * 60 * 1000L;
										ctx.getSource().sendFeedback(Component.literal("§7[XHT Dev] BotTest déconnecté depuis 5 min !"));
									} else {
										ctx.getSource().sendError(Component.literal("Lance d'abord /xht simulate live"));
									}
									return 1;
								}))
								// Cree plusieurs bots autour du joueur : 2 sur 3 en ligne, les autres deconnectes depuis quelques minutes
								.then(ClientCommandManager.literal("many")
										.then(ClientCommandManager.argument("nombre", IntegerArgumentType.integer(1, 100)).executes(ctx -> {
											int nombre = IntegerArgumentType.getInteger(ctx, "nombre");
											var player = ctx.getSource().getPlayer();
											String dim = player.level().dimension().location().toString();
											supprimerBotsMultiples();
											for (int i = 1; i <= nombre; i++) {
												String nom = "Bot" + i;
												UUID uuid = UUID.fromString(String.format("00000000-0000-0000-0001-%012d", i));
												// Repartition en cercle autour du joueur, rayon 20 a 60 blocs
												double angle = 2 * Math.PI * i / nombre;
												double rayon = 20.0 + (i % 5) * 10.0;
												PositionData pos = new PositionData(player.getX() + Math.cos(angle) * rayon, player.getY(),
														player.getZ() + Math.sin(angle) * rayon, uuid, dim);
												pos.texturePeau = DefaultPlayerSkin.get(uuid).texture();
												if (i % 3 == 0) {
													pos.timestamp = System.currentTimeMillis() - i * 60 * 1000L;
												} else {
													botsMultiplesEnLigne.add(nom);
												}
												positionsJoueurs.put(nom, pos);
												botsMultiples.add(nom);
											}
											ctx.getSource().sendFeedback(Component.literal("§a[XHT Dev] " + nombre + " bots créés (Bot1 a Bot" + nombre + "), 1 sur 3 déconnecté."));
											return 1;
										})))
								.then(ClientCommandManager.literal("clear").executes(ctx -> {
									simulerBotEnLigne = false;
									positionsJoueurs.remove(BOT_TEST_NAME);
									supprimerBotsMultiples();
									ctx.getSource().sendFeedback(Component.literal("§c[XHT Dev] BotTest et bots multiples supprimés de la carte."));
									return 1;
								}))
						)
				);
			});
		}

		ClientPlayNetworking.registerGlobalReceiver(PlayerPositionPayload.TYPE, (payload, context) -> {
			String pseudo = payload.pseudo();
			UUID uuid = payload.uuid();
			double x = payload.x();
			double y = payload.y();
			double z = payload.z();
			String dimension = payload.dimension();

			PositionData pos = positionsJoueurs.get(pseudo);
			if (pos != null) {
				pos.x = x;
				pos.y = y;
				pos.z = z;
				pos.uuid = uuid;
				pos.dimension = dimension;
				pos.timestamp = System.currentTimeMillis();
			} else {
				pos = new PositionData(x, y, z, uuid, dimension);
				positionsJoueurs.put(pseudo, pos);
			}

			Minecraft client = context.client();
			if (client.getConnection() != null) {
				PlayerInfo info = client.getConnection().getPlayerInfo(uuid);
				if (info != null) {
					pos.texturePeau = info.getSkin().texture();
				}
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

			if (screen.getClass().getName().contains("GuiMap")) {

				// 1. On prépare notre bouton sans l'ajouter à la liste de l'écran pour ne pas interférer avec Xaero
				AbstractButton boutonOeil = new AbstractButton(7, 32, 16, 16, Component.literal("O")) {

					@Override
					public void onPress() {
						client.setScreen(new ConfigScreen(screen));
					}

					@Override
					protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
						// Si la souris est dessus, le décalage est de -1 pixel (vers le haut), sinon 0
						int decalageY = this.isHovered() ? -1 : 0;

						guiGraphics.blit(
								EYE_TEXTURE,
								this.getX(), this.getY() + decalageY,
								0.0F, 0.0F,
								16, 16,
								16, 16
						);
					}

					@Override
					protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
				};

				boutonOeil.setTooltip(Tooltip.create(Component.translatable("key.xaeroheadtracker.open_settings")));

				// 2. RENDU FORCÉ À LA TOUTE FIN : On dessine par-dessus la carte de Xaero sans widget plein écran
				ScreenEvents.afterRender(screen).register((s, guiGraphics, mouseX, mouseY, partialTick) -> {
					GuiMap mapScreen = (GuiMap) screen;
					GuiMapAccessor accessor = (GuiMapAccessor) mapScreen;

					double cameraX = accessor.getCameraX();
					double cameraZ = accessor.getCameraZ();
					double scale = accessor.getScale();
					double screenScale = accessor.getScreenScale();
					long tempsActuel = System.currentTimeMillis();

					// Le grand nettoyage : on supprime tous ceux dont le timestamp est plus vieux que 120 minutes
					// (120 min * 60 sec * 1000 millisecondes)
					positionsJoueurs.entrySet().removeIf(entree ->
							(tempsActuel - entree.getValue().timestamp) > 120 * 60 * 1000L
					);

					String maDimension = client.level != null ? client.level.dimension().location().toString() : "";

					for (Map.Entry<String, PositionData> entree : positionsJoueurs.entrySet()) {
						String pseudo = entree.getKey();
						PositionData pos = entree.getValue();
						UUID uuidJoueur = pos.uuid;

						// On ignore le joueur s'il n'est pas dans NOTRE dimension
						if (!pos.dimension.equals(maDimension)) {
							continue;
						}

						// Si la Minimap est installée et que le joueur est dans notre portée locale, la Minimap le gère
						if (isMinimapInstalled && client.level != null && client.level.getPlayerByUUID(uuidJoueur) != null) {
							continue;
						}

						// On ignore le joueur si c'est nous-même
						if (client.player != null && uuidJoueur.equals(client.player.getUUID())) {
							continue;
						}

						// Filtre des joueurs choisis : on ignore les joueurs masques
						if (!PlayerVisibilityConfig.INSTANCE.estVisible(uuidJoueur)) {
							continue;
						}
						PlayerVisibilityConfig.INSTANCE.mettreAJourPseudo(uuidJoueur, pseudo);

						double drawX = screen.width / 2.0 + (pos.x - cameraX) * scale / screenScale;
						double drawY = screen.height / 2.0 + (pos.z - cameraZ) * scale / screenScale;

						if (drawX > 0 && drawX < screen.width && drawY > 0 && drawY < screen.height) {
							int headSize = 8; // taille d'affichage à l'écran, en pixels GUI

							PlayerInfo info = client.getConnection() != null
									? client.getConnection().getPlayerInfo(uuidJoueur)
									: null;

							if (info != null) {
								pos.texturePeau = info.getSkin().texture();
							}

							ResourceLocation skinTextureToUse = pos.texturePeau;

							if (ModConfig.INSTANCE.showHeads) {
								if (skinTextureToUse != null) {
									guiGraphics.blit(
											skinTextureToUse,
											(int) drawX - headSize / 2, (int) drawY - headSize / 2,
											8.0F, 8.0F,
											headSize, headSize,
											64, 64
									);
								} else {
									guiGraphics.fill((int) drawX - 3, (int) drawY - 3, (int) drawX + 3, (int) drawY + 3, 0xFFFF0000);
								}
							}

							boolean avecTete = ModConfig.INSTANCE.showHeads;
							// Sans tete, le pseudo est centre sur la position du joueur
							int basBloc = avecTete ? (int) drawY + headSize / 2 : (int) drawY + 5;

							if (ModConfig.INSTANCE.showNames) {
								int pseudoY = avecTete ? (int) drawY - headSize / 2 - 10 : (int) drawY - 4;
								guiGraphics.drawCenteredString(
										client.font, pseudo,
										(int) drawX, pseudoY,
										-1
								);
							}

							if (ModConfig.INSTANCE.showNames || avecTete) {

								long msEcoulees = tempsActuel - pos.timestamp;
								long minutesEcoulees = msEcoulees / 60000L;

								String texteStatut;
								if (msEcoulees <= 4000) {
									texteStatut = "§aLive";
								} else if (minutesEcoulees < 1) {
									texteStatut = "§7< 1 min";
								} else {
									texteStatut = "§7" + minutesEcoulees + " min";
								}

								float echelle = 0.7F;
								guiGraphics.pose().pushPose();
								guiGraphics.pose().scale(echelle, echelle, 1.0F);

								int cibleX = (int) (drawX / echelle);
								int cibleY = (int) ((basBloc + 2) / echelle);

								guiGraphics.drawCenteredString(
										client.font, texteStatut,
										cibleX, cibleY,
										-1
								);

								guiGraphics.pose().popPose();
							}
						}
					}

					// F1 : l'oeil disparait comme les boutons de Xaero
					if (!GuiMap.hiddenUI) {
						// Rendu manuel du bouton par-dessus tout le reste (inclut son tooltip officiel)
						boutonOeil.render(guiGraphics, mouseX, mouseY, partialTick);
					}
				});

				// 3. GESTION MANUELLE DES CLICS : On intercepte le clic sur l'œil avant Xaero
				ScreenMouseEvents.allowMouseClick(screen).register((s, sourisX, sourisY, boutonSouris) -> {
					if (!GuiMap.hiddenUI && boutonOeil.isMouseOver(sourisX, sourisY)) {
						boutonOeil.mouseClicked(sourisX, sourisY, boutonSouris);
						return false; // on absorbe le clic, Xaero ne le reçoit jamais
					}
					return true;
				});
			}
		});
	}

	public static class PositionData {
		public double x, y, z;
		public UUID uuid;
		public String dimension;
		public long timestamp;
		public ResourceLocation texturePeau;

		public PositionData(double x, double y, double z, UUID uuid, String dimension) {
			this.x = x; this.y = y; this.z = z; this.uuid = uuid;
			this.dimension = dimension;
			this.timestamp = System.currentTimeMillis();
			this.texturePeau = null;
		}
	}
}
