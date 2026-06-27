package name.modid.client;

import name.modid.mixin.GuiMapAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import name.modid.PlayerPositionPayload;
import name.modid.XaeroHeadTracker;

// L'IMPORT DE LA CARTE XAERO
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import xaero.map.gui.GuiMap;
import xaero.map.graphics.MapRenderHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class XaeroHeadTrackerClient implements ClientModInitializer {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();

	@Override
	public void onInitializeClient() {

		ClientPlayNetworking.registerGlobalReceiver(PlayerPositionPayload.TYPE, (payload, context) -> {
			String pseudo = payload.pseudo();
			UUID uuid = payload.uuid();
			double x = payload.x();
			double y = payload.y();
			double z = payload.z();
			positionsJoueurs.put(pseudo, new PositionData(x, y, z, uuid));
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

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

						Minecraft client = Minecraft.getInstance();

						for (Map.Entry<String, PositionData> entree : positionsJoueurs.entrySet()) {
							PositionData pos = entree.getValue();

							double drawX = screen.width / 2.0 + (pos.x - cameraX) * scale / screenScale;
							double drawY = screen.height / 2.0 + (pos.z - cameraZ) * scale / screenScale;

							if (drawX > 0 && drawX < screen.width && drawY > 0 && drawY < screen.height) {
								int headSize = 8; // taille d'affichage à l'écran, en pixels GUI

								PlayerInfo info = client.getConnection() != null
										? client.getConnection().getPlayerInfo(pos.uuid)
										: null;

								if (info != null) {
									PlayerSkin skin = info.getSkin();
									Identifier skinTexture = skin.body().texturePath();

									guiGraphics.blit(
											RenderPipelines.GUI_TEXTURED,
											skinTexture,
											(int) drawX - headSize / 2, (int) drawY - headSize / 2,
											8.0F, 8.0F,
											headSize, headSize,
											64, 64
									);

									String pseudo = entree.getKey();
									MapRenderHelper.drawCenteredStringWithBackground(
											guiGraphics, client.font, pseudo,
											(int) drawX, (int) drawY - headSize / 2 - 10,
											-1,
											0.0F, 0.0F, 0.0F, 0.4F
									);
								} else {
									guiGraphics.fill((int) drawX - 3, (int) drawY - 3, (int) drawX + 3, (int) drawY + 3, 0xFFFF0000);
								}
							}
						}
					}

					@Override
					public boolean isMouseOver(double mouseX, double mouseY) { return false; }
					@Override
					protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
				};

				Screens.getWidgets(screen).add(calqueDessin);
			}
		});
	}

	public static class PositionData {
		public double x, y, z;
		public UUID uuid;
		public PositionData(double x, double y, double z, UUID uuid) {
			this.x = x; this.y = y; this.z = z; this.uuid = uuid;
		}
	}
}