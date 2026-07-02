package name.modid.client;

import name.modid.mixin.GuiMapAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
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

import net.minecraft.client.gui.components.AbstractButton;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class XaeroHeadTrackerClient implements ClientModInitializer {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();

	// 1. On déclare officiellement notre propre catégorie dans les paramètres du jeu
	private static final KeyMapping.Category CATEGORIE_MOD = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("xaeroheadtracker", "parametres"));

	// 2. Notre raccourci clavier
	private static KeyMapping toucheParametres;

	private static final Identifier EYE_TEXTURE = Identifier.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	@Override
	public void onInitializeClient() {

		ModConfig.INSTANCE.charger();

		// 1. On crée et on enregistre le raccourci (avec KeyMappingHelper !)
		toucheParametres = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"Paramètres Head Tracker",
				GLFW.GLFW_KEY_H,
				CATEGORIE_MOD // On utilise l'objet Catégorie créé plus haut
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
		});

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

							// L'ASTUCE EST ICI : On ignore le joueur si c'est nous-même
							if (client.player != null && pos.uuid.equals(client.player.getUUID())) {
								continue; // On passe au joueur suivant de la liste sans rien dessiner
							}

							double drawX = screen.width / 2.0 + (pos.x - cameraX) * scale / screenScale;
							double drawY = screen.height / 2.0 + (pos.z - cameraZ) * scale / screenScale;

							if (drawX > 0 && drawX < screen.width && drawY > 0 && drawY < screen.height) {
								int headSize = 8; // taille d'affichage à l'écran, en pixels GUI

								PlayerInfo info = client.getConnection() != null
										? client.getConnection().getPlayerInfo(pos.uuid)
										: null;

								if (info != null) {
									if (ModConfig.INSTANCE.showHeads) {
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
									}

									if (ModConfig.INSTANCE.showNames) {
										String pseudo = entree.getKey();
										MapRenderHelper.drawCenteredStringWithBackground(
												guiGraphics, client.font, pseudo,
												(int) drawX, (int) drawY - headSize / 2 - 10,
												-1,
												0.0F, 0.0F, 0.0F, 0.4F
										);
									}
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

				// On passe la taille cliquable à 16x16, et on ajuste un peu X et Y pour l'alignement
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
								this.getX(), this.getY() + decalageY,  // On ajoute notre décalage ici !
								0.0F, 0.0F,
								16, 16,
								16, 16
						);
					}

					@Override
					protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
				};

				// On attache l'infobulle officielle de Minecraft au bouton
				boutonOeil.setTooltip(Tooltip.create(Component.literal("Head Tracker Settings")));

				Screens.getWidgets(screen).add(boutonOeil);
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