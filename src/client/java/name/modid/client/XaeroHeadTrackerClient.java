package name.modid.client;

import name.modid.mixin.GuiMapAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import name.modid.PlayerPositionPayload;
import name.modid.XaeroHeadTracker;

// L'IMPORT DE LA CARTE XAERO
import net.minecraft.resources.Identifier;
import xaero.map.gui.GuiMap;
import xaero.map.graphics.MapRenderHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.components.AbstractButton;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import name.modid.UpdateShareStatusPayload;

public class XaeroHeadTrackerClient implements ClientModInitializer {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();
	public static boolean isMinimapInstalled = false;

	private static final String BOT_TEST_NAME = "BotTest";
	private static final UUID BOT_TEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static boolean simulerBotEnLigne = false;

	// 1. On déclare officiellement notre propre catégorie dans les paramètres du jeu
	private static final KeyMapping.Category CATEGORIE_MOD = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("xaeroheadtracker", "parametres"));

	// 2. Notre raccourci clavier
	private static KeyMapping toucheParametres;

	private static final Identifier EYE_TEXTURE = Identifier.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	/** Cle de stockage du serveur courant : adresse en multijoueur, nom du monde en solo. */
	private static String identifierServeur(Minecraft client) {
		if (client.getSingleplayerServer() != null) {
			return "solo:" + client.getSingleplayerServer().getWorldData().getLevelName();
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
			// On utilise la valeur qui a été chargée depuis le JSON
			ClientPlayNetworking.send(new UpdateShareStatusPayload(ModConfig.INSTANCE.sharePosition));

			// On identifie le serveur (ou le monde solo) pour charger les joueurs choisis
			PlayerVisibilityConfig.INSTANCE.definirServeurCourant(identifierServeur(client));
		});

		// 1. On crée et on enregistre le raccourci (avec KeyMappingHelper !)
		toucheParametres = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.xaeroheadtracker.open_settings", // La clé magique est ici
				InputConstants.KEY_H,
				CATEGORIE_MOD
		));

		// 2. On écoute en permanence (à chaque "tick" du jeu) si la touche est pressée
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toucheParametres.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new ConfigScreen(null));
				} else if (client.gui.screen().getClass().getName().contains("GuiMap")) {
					client.gui.setScreen(new ConfigScreen(client.gui.screen()));
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
				dispatcher.register(ClientCommands.literal("xht")
						.then(ClientCommands.literal("simulate")
								.then(ClientCommands.literal("live").executes(ctx -> {
									var player = ctx.getSource().getPlayer();
									var client = ctx.getSource().getClient();
									String dim = player.level().dimension().identifier().toString();
									PositionData botPos = new PositionData(player.getX() + 15.0, player.getY(), player.getZ() + 15.0, BOT_TEST_UUID, dim);
									if (client.getConnection() != null) {
										PlayerInfo myInfo = client.getConnection().getPlayerInfo(player.getUUID());
										if (myInfo != null) {
											botPos.texturePeau = myInfo.getSkin().body().texturePath();
										}
									}
									positionsJoueurs.put(BOT_TEST_NAME, botPos);
									simulerBotEnLigne = true;
									ctx.getSource().sendFeedback(Component.literal("§a[XHT Dev] BotTest en ligne créé à +15 blocs de ta position !"));
									return 1;
								}))
								.then(ClientCommands.literal("disconnect").executes(ctx -> {
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
								.then(ClientCommands.literal("old").executes(ctx -> {
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
								.then(ClientCommands.literal("clear").executes(ctx -> {
									simulerBotEnLigne = false;
									positionsJoueurs.remove(BOT_TEST_NAME);
									ctx.getSource().sendFeedback(Component.literal("§c[XHT Dev] BotTest supprimé de la carte."));
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
					pos.texturePeau = info.getSkin().body().texturePath();
				}
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

			if (screen.getClass().getName().contains("GuiMap")) {

				// 1. On prépare notre bouton sans l'ajouter à la liste de l'écran pour ne pas interférer avec Xaero
				AbstractButton boutonOeil = new AbstractButton(7, 32, 16, 16, Component.literal("O")) {

					@Override
					public void onPress(InputWithModifiers input) {
						client.gui.setScreen(new ConfigScreen(screen));
					}

					@Override
					protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
						// Si la souris est dessus, le décalage est de -1 pixel (vers le haut), sinon 0
						int decalageY = this.isHovered() ? -1 : 0;

						guiGraphics.blit(
								RenderPipelines.GUI_TEXTURED,
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
				ScreenEvents.afterExtract(screen).register((s, guiGraphics, mouseX, mouseY, partialTick) -> {
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

					String maDimension = client.level != null ? client.level.dimension().identifier().toString() : "";

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

						double drawX = screen.width / 2.0 + (pos.x - cameraX) * scale / screenScale;
						double drawY = screen.height / 2.0 + (pos.z - cameraZ) * scale / screenScale;

						if (drawX > 0 && drawX < screen.width && drawY > 0 && drawY < screen.height) {
							int headSize = 8; // taille d'affichage à l'écran, en pixels GUI

							PlayerInfo info = client.getConnection() != null
									? client.getConnection().getPlayerInfo(uuidJoueur)
									: null;

							if (info != null) {
								pos.texturePeau = info.getSkin().body().texturePath();
							}

							Identifier skinTextureToUse = pos.texturePeau;

							if (ModConfig.INSTANCE.showHeads) {
								if (skinTextureToUse != null) {
									guiGraphics.blit(
											RenderPipelines.GUI_TEXTURED,
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

							if (ModConfig.INSTANCE.showNames) {
								MapRenderHelper.drawCenteredStringWithBackground(
										guiGraphics, client.font, pseudo,
										(int) drawX, (int) drawY - headSize / 2 - 10,
										-1,
										0.0F, 0.0F, 0.0F, 0.4F
								);

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
								guiGraphics.pose().pushMatrix();
								guiGraphics.pose().scale(echelle, echelle);

								int cibleX = (int) (drawX / echelle);
								int cibleY = (int) ((drawY + headSize / 2 + 2) / echelle);

								MapRenderHelper.drawCenteredStringWithBackground(
										guiGraphics, client.font, texteStatut,
										cibleX, cibleY,
										-1,
										0.0F, 0.0F, 0.0F, 0.4F
								);

								guiGraphics.pose().popMatrix();
							}
						}
					}

					// Rendu manuel du bouton par-dessus tout le reste (inclut son tooltip officiel)
					boutonOeil.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
				});

				// 3. GESTION MANUELLE DES CLICS : On intercepte le clic sur l'œil avant Xaero
				ScreenMouseEvents.allowMouseClick(screen).register((Screen s, MouseButtonEvent context) -> {
					if (boutonOeil.isMouseOver(context.x(), context.y())) {
						boutonOeil.mouseClicked(context, false);
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
		public Identifier texturePeau;

		public PositionData(double x, double y, double z, UUID uuid, String dimension) {
			this.x = x; this.y = y; this.z = z; this.uuid = uuid;
			this.dimension = dimension;
			this.timestamp = System.currentTimeMillis();
			this.texturePeau = null;
		}
	}
}