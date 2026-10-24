package name.modid.client;

import name.modid.PlayerPositionPayload;
import name.modid.UpdateShareStatusPayload;
import name.modid.XaeroHeadTracker;
import name.modid.mixin.GuiMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;
import xaero.map.gui.GuiMap;
import xaero.map.graphics.MapRenderHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class XaeroHeadTrackerClient {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();

	private static KeyMapping toucheParametres;

	private static final String BOT_TEST_NAME = "BotTest";
	private static final UUID BOT_TEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static boolean simulerBotEnLigne = false;
	// Bots crees par /xht simulate many, maintenus en ligne a chaque tick
	private static final Set<String> botsMultiplesEnLigne = new HashSet<>();
	private static final Set<String> botsMultiples = new HashSet<>();

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

	private static final ResourceLocation EYE_TEXTURE = ResourceLocation.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	// CORRECTION : Retrait du paramètre 'bus', l'API le déduit automatiquement
	@EventBusSubscriber(modid = XaeroHeadTracker.MOD_ID, value = Dist.CLIENT)
	public static class ClientModEvents {

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			ModConfig.INSTANCE.charger();
			PlayerVisibilityConfig.INSTANCE.charger();
		}

		@SubscribeEvent
		public static void onKeyRegister(RegisterKeyMappingsEvent event) {
			toucheParametres = new KeyMapping(
					"key.xaeroheadtracker.open_settings",
					GLFW.GLFW_KEY_H,
					"key.category.xaeroheadtracker.parametres"
			);
			event.register(toucheParametres);
		}
	}

	// CORRECTION : Retrait du paramètre 'bus'
	@EventBusSubscriber(modid = XaeroHeadTracker.MOD_ID, value = Dist.CLIENT)
	public static class ClientGameEvents {

		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			Minecraft client = Minecraft.getInstance();
			if (toucheParametres != null && client.player != null) {
				while (toucheParametres.consumeClick()) {
					if (client.screen == null) {
						client.setScreen(new ConfigScreen(null));
					} else if (client.screen.getClass().getName().contains("GuiMap")) {
						client.setScreen(new ConfigScreen(client.screen));
					}
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
		}

		@SubscribeEvent
		public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
			if (FMLEnvironment.production) {
				return;
			}
			event.getDispatcher().register(Commands.literal("xht")
					.then(Commands.literal("simulate")
							.then(Commands.literal("live").executes(ctx -> {
								Minecraft client = Minecraft.getInstance();
								var player = client.player;
								if (player == null) return 0;
								PositionData botPos = new PositionData(player.getX() + 15.0, player.getY(), player.getZ() + 15.0, BOT_TEST_UUID);
								if (client.getConnection() != null) {
									PlayerInfo myInfo = client.getConnection().getPlayerInfo(player.getUUID());
									if (myInfo != null) {
										botPos.cachedSkin = myInfo.getSkin();
									}
								}
								positionsJoueurs.put(BOT_TEST_NAME, botPos);
								simulerBotEnLigne = true;
								ctx.getSource().sendSuccess(() -> Component.literal("§a[XHT Dev] BotTest en ligne créé à +15 blocs de ta position !"), false);
								return 1;
							}))
							.then(Commands.literal("disconnect").executes(ctx -> {
								simulerBotEnLigne = false;
								PositionData botPos = positionsJoueurs.get(BOT_TEST_NAME);
								if (botPos != null) {
									botPos.timestamp = System.currentTimeMillis() - 5000L;
									ctx.getSource().sendSuccess(() -> Component.literal("§e[XHT Dev] BotTest déconnecté (statut < 1 min) !"), false);
								} else {
									ctx.getSource().sendFailure(Component.literal("Lance d'abord /xht simulate live"));
								}
								return 1;
							}))
							.then(Commands.literal("old").executes(ctx -> {
								simulerBotEnLigne = false;
								PositionData botPos = positionsJoueurs.get(BOT_TEST_NAME);
								if (botPos != null) {
									botPos.timestamp = System.currentTimeMillis() - 5 * 60 * 1000L;
									ctx.getSource().sendSuccess(() -> Component.literal("§7[XHT Dev] BotTest déconnecté depuis 5 min !"), false);
								} else {
									ctx.getSource().sendFailure(Component.literal("Lance d'abord /xht simulate live"));
								}
								return 1;
							}))
							// Cree plusieurs bots autour du joueur : 2 sur 3 en ligne, les autres deconnectes depuis quelques minutes
							.then(Commands.literal("many")
									.then(Commands.argument("nombre", IntegerArgumentType.integer(1, 100)).executes(ctx -> {
										int nombre = IntegerArgumentType.getInteger(ctx, "nombre");
										var player = Minecraft.getInstance().player;
										if (player == null) return 0;
										supprimerBotsMultiples();
										for (int i = 1; i <= nombre; i++) {
											String nom = "Bot" + i;
											UUID uuid = UUID.fromString(String.format("00000000-0000-0000-0001-%012d", i));
											// Repartition en cercle autour du joueur, rayon 20 a 60 blocs
											double angle = 2 * Math.PI * i / nombre;
											double rayon = 20.0 + (i % 5) * 10.0;
											PositionData pos = new PositionData(player.getX() + Math.cos(angle) * rayon, player.getY(),
													player.getZ() + Math.sin(angle) * rayon, uuid);
											pos.cachedSkin = DefaultPlayerSkin.get(uuid);
											if (i % 3 == 0) {
												pos.timestamp = System.currentTimeMillis() - i * 60 * 1000L;
											} else {
												botsMultiplesEnLigne.add(nom);
											}
											positionsJoueurs.put(nom, pos);
											botsMultiples.add(nom);
										}
										ctx.getSource().sendSuccess(() -> Component.literal("§a[XHT Dev] " + nombre + " bots créés (Bot1 a Bot" + nombre + "), 1 sur 3 déconnecté."), false);
										return 1;
									})))
							.then(Commands.literal("clear").executes(ctx -> {
								simulerBotEnLigne = false;
								positionsJoueurs.remove(BOT_TEST_NAME);
								supprimerBotsMultiples();
								ctx.getSource().sendSuccess(() -> Component.literal("§c[XHT Dev] BotTest et bots multiples supprimés de la carte."), false);
								return 1;
							}))
					)
			);
		}

		@SubscribeEvent
		public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
			// CORRECTION : Envoi du paquet via la connexion native du client
			Minecraft client = Minecraft.getInstance();
			PlayerVisibilityConfig.INSTANCE.definirServeurCourant(identifierServeur(client));
			if (client.getConnection() != null) {
				client.getConnection().send(new UpdateShareStatusPayload(PlayerVisibilityConfig.INSTANCE.getPartage()));
			}
		}

		@SubscribeEvent
		public static void onMouseClickPre(ScreenEvent.MouseButtonPressed.Pre event) {
			if (event.getScreen().getClass().getName().contains("GuiMap")) {
				double mouseX = event.getMouseX();
				double mouseY = event.getMouseY();
				// Le bouton est à x=7, y=32, largeur=16, hauteur=16
				if (mouseX >= 7 && mouseX <= 23 && mouseY >= 32 && mouseY <= 48 && event.getButton() == 0 && !GuiMap.hiddenUI) {
					Minecraft.getInstance().setScreen(new ConfigScreen(event.getScreen()));
					event.setCanceled(true);
				}
			}
		}

		@SubscribeEvent
		public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
			net.minecraft.client.gui.screens.Screen screen = event.getScreen();
			if (screen.getClass().getName().contains("GuiMap")) {
				GuiGraphics guiGraphics = event.getGuiGraphics();
				GuiMap mapScreen = (GuiMap) screen;
				GuiMapAccessor accessor = (GuiMapAccessor) mapScreen;

				double cameraX = accessor.getCameraX();
				double cameraZ = accessor.getCameraZ();
				double scale = accessor.getScale();
				double screenScale = accessor.getScreenScale();
				long tempsActuel = System.currentTimeMillis();

				positionsJoueurs.entrySet().removeIf(entree ->
						(tempsActuel - entree.getValue().timestamp) > 120 * 60 * 1000L
				);

				Minecraft client = Minecraft.getInstance();

				for (Map.Entry<String, PositionData> entree : positionsJoueurs.entrySet()) {
					PositionData pos = entree.getValue();

					if (client.player != null && pos.uuid.equals(client.player.getUUID())) {
						continue;
					}

					// Filtre des joueurs choisis : on ignore les joueurs masques
					String pseudo = entree.getKey();
					if (!PlayerVisibilityConfig.INSTANCE.estVisible(pos.uuid)) {
						continue;
					}
					PlayerVisibilityConfig.INSTANCE.mettreAJourPseudo(pos.uuid, pseudo);

					double drawX = screen.width / 2.0 + (pos.x - cameraX) * scale / screenScale;
					double drawY = screen.height / 2.0 + (pos.z - cameraZ) * scale / screenScale;

					if (drawX > 0 && drawX < screen.width && drawY > 0 && drawY < screen.height) {
						int headSize = 8;

						PlayerInfo info = client.getConnection() != null
								? client.getConnection().getPlayerInfo(pos.uuid)
								: null;

						if (info != null) {
							pos.cachedSkin = info.getSkin();
						}

						if (ModConfig.INSTANCE.showHeads) {
							ResourceLocation skinTexture = null;
							if (pos.cachedSkin != null) {
								skinTexture = pos.cachedSkin.texture();
							}
							
							if (skinTexture != null) {
								guiGraphics.blit(
										skinTexture,
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
			}
		}

		@SubscribeEvent
		public static void onScreenInit(ScreenEvent.Init.Post event) {
			net.minecraft.client.gui.screens.Screen screen = event.getScreen();

			if (screen.getClass().getName().contains("GuiMap")) {

				AbstractButton boutonOeil = new AbstractButton(7, 32, 16, 16, Component.literal("O")) {
					@Override
					public void onPress() {
						Minecraft.getInstance().setScreen(new ConfigScreen(screen));
					}

					@Override
					public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
						// F1 : l'oeil disparait comme les boutons de Xaero
						if (GuiMap.hiddenUI) {
							return;
						}
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
				event.addListener(boutonOeil);
			}
		}
	}

	public static void recevoirPositions(PlayerPositionPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			String pseudo = payload.pseudo();
			UUID uuid = payload.uuid();
			double x = payload.x();
			double y = payload.y();
			double z = payload.z();
			PositionData existing = positionsJoueurs.get(pseudo);
			if (existing != null) {
				existing.x = x;
				existing.y = y;
				existing.z = z;
				existing.timestamp = System.currentTimeMillis();
			} else {
				positionsJoueurs.put(pseudo, new PositionData(x, y, z, uuid));
			}
		});
	}

	public static class PositionData {
		public double x, y, z;
		public UUID uuid;
		public long timestamp;
		public PlayerSkin cachedSkin;

		public PositionData(double x, double y, double z, UUID uuid) {
			this.x = x; this.y = y; this.z = z; this.uuid = uuid;
			this.timestamp = System.currentTimeMillis();
		}
	}
}