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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;
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

	private static KeyMapping toucheParametres;

	private static final ResourceLocation EYE_TEXTURE = ResourceLocation.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	// CORRECTION : Retrait du paramètre 'bus', l'API le déduit automatiquement
	@EventBusSubscriber(modid = XaeroHeadTracker.MOD_ID, value = Dist.CLIENT)
	public static class ClientModEvents {

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			ModConfig.INSTANCE.charger();
		}

		@SubscribeEvent
		public static void onKeyRegister(RegisterKeyMappingsEvent event) {
			toucheParametres = new KeyMapping(
					"key.xaeroheadtracker.open_settings",
					GLFW.GLFW_KEY_H,
					"key.categories.xaeroheadtracker"
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
		public static void onMouseClickPre(ScreenEvent.MouseButtonPressed.Pre event) {
			if (event.getScreen().getClass().getName().contains("GuiMap")) {
				double mouseX = event.getMouseX();
				double mouseY = event.getMouseY();
				// Le bouton est à x=7, y=32, largeur=16, hauteur=16
				if (mouseX >= 7 && mouseX <= 23 && mouseY >= 32 && mouseY <= 48 && event.getButton() == 0) {
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

						if (ModConfig.INSTANCE.showNames) {
							String pseudo = entree.getKey();
							guiGraphics.drawCenteredString(
									client.font, pseudo,
									(int) drawX, (int) drawY - headSize / 2 - 10,
									-1
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
							guiGraphics.pose().pushPose();
							guiGraphics.pose().scale(echelle, echelle, 1.0F);

							int cibleX = (int) (drawX / echelle);
							int cibleY = (int) ((drawY + headSize / 2 + 2) / echelle);

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