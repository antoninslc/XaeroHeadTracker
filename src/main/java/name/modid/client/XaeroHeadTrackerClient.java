package name.modid.client;

import com.mojang.blaze3d.platform.InputConstants;
import name.modid.PlayerPositionPayload;
import name.modid.UpdateShareStatusPayload;
import name.modid.XaeroHeadTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import xaero.map.graphics.MapRenderHelper;
import xaero.map.gui.GuiMap;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class XaeroHeadTrackerClient {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();
	public static boolean isMinimapInstalled = false;

	private static final String BOT_TEST_NAME = "BotTest";
	private static final UUID BOT_TEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static boolean simulerBotEnLigne = false;
	// Bots supplementaires crees par /xht simulate many, maintenus en ligne a chaque tick
	private static final Set<String> botsMultiplesEnLigne = new HashSet<>();
	private static final Set<String> botsMultiples = new HashSet<>();

	private static final KeyMapping.Category CATEGORIE_MOD = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("xaeroheadtracker", "parametres"));
	private static KeyMapping toucheParametres;

	private static final Identifier EYE_TEXTURE = Identifier.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	/** Retire de la carte les bots crees par /xht simulate many. */
	private static void supprimerBotsMultiples() {
		for (String nom : botsMultiples) {
			positionsJoueurs.remove(nom);
		}
		botsMultiples.clear();
		botsMultiplesEnLigne.clear();
	}

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

	private static Field cameraXField;
	private static Field cameraZField;
	private static Field scaleField;
	private static Field screenScaleField;
	private static AbstractButton boutonOeilActif;

	static {
		try {
			cameraXField = GuiMap.class.getDeclaredField("cameraX");
			cameraXField.setAccessible(true);
			cameraZField = GuiMap.class.getDeclaredField("cameraZ");
			cameraZField.setAccessible(true);
			scaleField = GuiMap.class.getDeclaredField("scale");
			scaleField.setAccessible(true);
			screenScaleField = GuiMap.class.getDeclaredField("screenScale");
			screenScaleField.setAccessible(true);
		} catch (NoSuchFieldException e) {
			XaeroHeadTracker.LOGGER.error("Impossible d'acceder aux champs de GuiMap", e);
		}
	}

	@EventBusSubscriber(modid = XaeroHeadTracker.MOD_ID, value = Dist.CLIENT)
	public static class ClientModEvents {

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			ModConfig.INSTANCE.charger();
			PlayerVisibilityConfig.INSTANCE.charger();
			isMinimapInstalled = ModList.get().isLoaded("xaerominimap")
					|| ModList.get().isLoaded("xaerominimapfair");
		}

		@SubscribeEvent
		public static void onKeyRegister(RegisterKeyMappingsEvent event) {
			toucheParametres = new KeyMapping(
					"key.xaeroheadtracker.open_settings",
					InputConstants.KEY_H,
					CATEGORIE_MOD
			);
			event.register(toucheParametres);
		}
	}

	@EventBusSubscriber(modid = XaeroHeadTracker.MOD_ID, value = Dist.CLIENT)
	public static class ClientGameEvents {

		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			Minecraft client = Minecraft.getInstance();
			if (toucheParametres != null && client.player != null) {
				while (toucheParametres.consumeClick()) {
					if (client.gui.screen() == null) {
						client.gui.setScreen(new ConfigScreen(null));
					} else if (client.gui.screen().getClass().getName().contains("GuiMap")) {
						client.gui.setScreen(new ConfigScreen(client.gui.screen()));
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
			if (!FMLEnvironment.isProduction()) {
				event.getDispatcher().register(Commands.literal("xht")
						.then(Commands.literal("simulate")
								.then(Commands.literal("live").executes(ctx -> {
									Minecraft client = Minecraft.getInstance();
									var player = client.player;
									if (player == null) return 0;
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
											String dim = player.level().dimension().identifier().toString();
											supprimerBotsMultiples();
											for (int i = 1; i <= nombre; i++) {
												String nom = "Bot" + i;
												UUID uuid = UUID.fromString(String.format("00000000-0000-0000-0001-%012d", i));
												// Repartition en cercle autour du joueur, rayon 20 a 60 blocs
												double angle = 2 * Math.PI * i / nombre;
												double rayon = 20.0 + (i % 5) * 10.0;
												PositionData pos = new PositionData(player.getX() + Math.cos(angle) * rayon, player.getY(),
														player.getZ() + Math.sin(angle) * rayon, uuid, dim);
												pos.texturePeau = DefaultPlayerSkin.get(uuid).body().texturePath();
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
		}

		@SubscribeEvent
		public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
			Minecraft client = Minecraft.getInstance();
			PlayerVisibilityConfig.INSTANCE.definirServeurCourant(identifierServeur(client));
			if (client.getConnection() != null) {
				client.getConnection().send(new UpdateShareStatusPayload(ModConfig.INSTANCE.sharePosition));
			}
		}

		@SubscribeEvent
		public static void onScreenInit(ScreenEvent.Init.Post event) {
			Screen screen = event.getScreen();

			if (screen.getClass().getName().contains("GuiMap")) {
				boutonOeilActif = new AbstractButton(7, 32, 16, 16, Component.literal("O")) {
					@Override
					public void onPress(InputWithModifiers input) {
						Minecraft.getInstance().gui.setScreen(new ConfigScreen(screen));
					}

					@Override
					protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
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

				boutonOeilActif.setTooltip(Tooltip.create(Component.translatable("key.xaeroheadtracker.open_settings")));
			}
		}

		@SubscribeEvent
		public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
			Screen screen = event.getScreen();
			if (!screen.getClass().getName().contains("GuiMap")) {
				return;
			}

			GuiMap mapScreen = (GuiMap) screen;
			double cameraX;
			double cameraZ;
			double scale;
			double screenScale;
			try {
				cameraX = cameraXField.getDouble(mapScreen);
				cameraZ = cameraZField.getDouble(mapScreen);
				scale = scaleField.getDouble(mapScreen);
				screenScale = screenScaleField.getDouble(mapScreen);
			} catch (IllegalAccessException | NullPointerException e) {
				return;
			}

			GuiGraphicsExtractor guiGraphics = event.getGuiGraphics();
			int mouseX = event.getMouseX();
			int mouseY = event.getMouseY();
			float partialTick = event.getPartialTick();
			long tempsActuel = System.currentTimeMillis();

			positionsJoueurs.entrySet().removeIf(entree ->
					(tempsActuel - entree.getValue().timestamp) > 120 * 60 * 1000L
			);

			Minecraft client = Minecraft.getInstance();
			String maDimension = client.level != null ? client.level.dimension().identifier().toString() : "";

			for (Map.Entry<String, PositionData> entree : positionsJoueurs.entrySet()) {
				String pseudo = entree.getKey();
				PositionData pos = entree.getValue();
				UUID uuidJoueur = pos.uuid;

				if (!pos.dimension.equals(maDimension)) {
					continue;
				}

				if (isMinimapInstalled && client.level != null && client.level.getPlayerByUUID(uuidJoueur) != null) {
					continue;
				}

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
					int headSize = 8;

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

					boolean avecTete = ModConfig.INSTANCE.showHeads;
					// Sans tete, le pseudo est centre sur la position du joueur
					int basBloc = avecTete ? (int) drawY + headSize / 2 : (int) drawY + 5;

					if (ModConfig.INSTANCE.showNames) {
						int pseudoY = avecTete ? (int) drawY - headSize / 2 - 10 : (int) drawY - 4;
						MapRenderHelper.drawCenteredStringWithBackground(
								guiGraphics, client.font, pseudo,
								(int) drawX, pseudoY,
								-1,
								0.0F, 0.0F, 0.0F, 0.4F
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
						guiGraphics.pose().pushMatrix();
						guiGraphics.pose().scale(echelle, echelle);

						int cibleX = (int) (drawX / echelle);
						int cibleY = (int) ((basBloc + 2) / echelle);

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

			if (boutonOeilActif != null) {
				boutonOeilActif.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
			}
		}

		@SubscribeEvent
		public static void onMouseButtonPressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
			Screen screen = event.getScreen();
			if (screen.getClass().getName().contains("GuiMap") && boutonOeilActif != null) {
				MouseButtonEvent mouseEvent = event.getMouseButtonEvent();
				if (boutonOeilActif.isMouseOver(mouseEvent.x(), mouseEvent.y())) {
					boutonOeilActif.mouseClicked(mouseEvent, event.isDoubleClick());
					event.setCanceled(true);
				}
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

			Minecraft client = Minecraft.getInstance();
			if (client.getConnection() != null) {
				PlayerInfo info = client.getConnection().getPlayerInfo(uuid);
				if (info != null) {
					pos.texturePeau = info.getSkin().body().texturePath();
				}
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
			this.x = x;
			this.y = y;
			this.z = z;
			this.uuid = uuid;
			this.dimension = dimension;
			this.timestamp = System.currentTimeMillis();
			this.texturePeau = null;
		}
	}
}
