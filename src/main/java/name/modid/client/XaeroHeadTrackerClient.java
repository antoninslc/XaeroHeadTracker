package name.modid.client;

import name.modid.PlayerPositionPayload;
import name.modid.UpdateShareStatusPayload;
import name.modid.XaeroHeadTracker;
import name.modid.mixin.GuiMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;
import xaero.map.gui.GuiMap;
import xaero.map.graphics.MapRenderHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class XaeroHeadTrackerClient {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();

	// CORRECTION : On redéclare la catégorie explicitement comme dans ton code Fabric
	private static final KeyMapping.Category CATEGORIE_MOD = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("xaeroheadtracker", "parametres"));
	private static KeyMapping toucheParametres;

	private static final Identifier EYE_TEXTURE = Identifier.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	// CORRECTION : Retrait du paramètre 'bus', l'API le déduit automatiquement
	@EventBusSubscriber(modid = XaeroHeadTracker.MOD_ID, value = Dist.CLIENT)
	public static class ClientModEvents {

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			ModConfig.INSTANCE.charger();
		}

		@SubscribeEvent
		public static void onKeyRegister(RegisterKeyMappingsEvent event) {
			// CORRECTION : On passe l'objet CATEGORIE_MOD au lieu d'une String
			toucheParametres = new KeyMapping(
					"key.xaeroheadtracker.open_settings",
					GLFW.GLFW_KEY_H,
					CATEGORIE_MOD
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
					if (client.gui.screen() == null) {
						client.gui.setScreen(new ConfigScreen(null));
					} else if (client.gui.screen().getClass().getName().contains("GuiMap")) {
						client.gui.setScreen(new ConfigScreen(client.gui.screen()));
					}
				}
			}
		}

		@SubscribeEvent
		public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
			// CORRECTION : Envoi du paquet via la connexion native du client
			Minecraft client = Minecraft.getInstance();
			if (client.getConnection() != null) {
				client.getConnection().send(new UpdateShareStatusPayload(ModConfig.INSTANCE.sharePosition));
			}
		}

		@SubscribeEvent
		public static void onScreenInit(ScreenEvent.Init.Post event) {
			net.minecraft.client.gui.screens.Screen screen = event.getScreen();

			if (screen.getClass().getName().contains("GuiMap")) {

				AbstractWidget calqueDessin = new AbstractWidget(0, 0, screen.width, screen.height, Component.empty()) {
					@Override
					protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
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
									Identifier skinTexture = null;
									if (pos.cachedSkin != null) {
										skinTexture = pos.cachedSkin.body().texturePath();
									}
									
									if (skinTexture != null) {
										guiGraphics.blit(
												RenderPipelines.GUI_TEXTURED,
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

								if (ModConfig.INSTANCE.showNames) {
									String pseudo = entree.getKey();
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
					}

					@Override
					public boolean isMouseOver(double mouseX, double mouseY) { return false; }
					@Override
					protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
				};

				event.addListener(calqueDessin);

				AbstractButton boutonOeil = new AbstractButton(7, 32, 16, 16, Component.literal("O")) {
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