package name.modid.client;

import name.modid.mixin.GuiMapAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
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

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import name.modid.UpdateShareStatusPayload;

import net.fabricmc.loader.api.FabricLoader;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

public class XaeroHeadTrackerClient implements ClientModInitializer {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();
	public static boolean isMinimapInstalled = false;
	public static final String CATEGORIE_MOD = "category.xaeroheadtracker.parametres";
	private static KeyMapping toucheParametres;

	private static final Identifier EYE_TEXTURE = Identifier.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	@Override
	public void onInitializeClient() {

		ModConfig.INSTANCE.charger();

		isMinimapInstalled = FabricLoader.getInstance().isModLoaded("xaerominimap")
				|| FabricLoader.getInstance().isModLoaded("xaerominimapfair");

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientPlayNetworking.send(new UpdateShareStatusPayload(ModConfig.INSTANCE.sharePosition));
		});

		toucheParametres = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.xaeroheadtracker.open_settings",
				GLFW.GLFW_KEY_H,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath("xaeroheadtracker", "parametres"))
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toucheParametres.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new ConfigScreen(null));
				} else if (client.screen.getClass().getName().contains("GuiMap")) {
					client.setScreen(new ConfigScreen(client.screen));
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(PlayerPositionPayload.TYPE, (payload, context) -> {
			String pseudo = payload.pseudo();
			UUID uuid = payload.uuid();
			double x = payload.x();
			double y = payload.y();
			double z = payload.z();
			String dimension = payload.dimension();

			PositionData posExistante = positionsJoueurs.get(pseudo);
			if (posExistante != null) {
				posExistante.x = x;
				posExistante.y = y;
				posExistante.z = z;
				posExistante.dimension = dimension;
				posExistante.timestamp = System.currentTimeMillis();
			} else {
				positionsJoueurs.put(pseudo, new PositionData(x, y, z, uuid, dimension));
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

			if (screen.getClass().getName().contains("GuiMap")) {

				// 1. On prépare notre bouton sans l'ajouter à la liste de l'écran
				AbstractButton boutonOeil = new AbstractButton(7, 32, 16, 16, Component.literal("O")) {
					@Override
					public void onPress(net.minecraft.client.input.InputWithModifiers input) {
						client.setScreen(new ConfigScreen(screen));
					}

					@Override
					protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
						int decalageY = this.isHovered() ? -1 : 0;
						guiGraphics.blit(
								RenderPipelines.GUI_TEXTURED,        // Le nouveau pipeline obligatoire
								EYE_TEXTURE,
								this.getX(), this.getY() + decalageY, // Position à l'écran
								0, 0,                                  // u, v (coin haut-gauche de la texture source)
								16, 16,                                // width, height : taille à l'écran
								16, 16,                                // regionWidth, regionHeight : taille de la zone à découper dans la texture
								16, 16                                 // textureWidth, textureHeight : taille RÉELLE du fichier PNG
						);
					}

					@Override
					protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
				};

				// boutonOeil.setTooltip(Tooltip.create(Component.translatable("key.xaeroheadtracker.open_settings")));

				// 2. RENDU FORCÉ À LA TOUTE FIN : On dessine par-dessus la carte de Xaero
				ScreenEvents.afterRender(screen).register((s, guiGraphics, mouseX, mouseY, partialTick) -> {
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

					String maDimension = client.level != null ? client.level.dimension().identifier().toString() : "";

					// Dessin des têtes
					for (Map.Entry<String, PositionData> entree : positionsJoueurs.entrySet()) {
						String pseudo = entree.getKey();
						PositionData pos = entree.getValue();
						UUID uuidJoueur = pos.uuid;

						if (!pos.dimension.equals(maDimension)) {
							continue;
						}

						// ATTENTION POUR TES TESTS : J'ai gardé la condition, n'oublie pas que si un joueur
						// est à côté de toi dans le monde, la minimap l'affiche et cette ligne l'ignore sur la grande carte !
						if (isMinimapInstalled && client.level != null && client.level.getPlayerByUUID(uuidJoueur) != null) {
							continue;
						}

						if (client.player != null && uuidJoueur.equals(client.player.getUUID())) {
							continue;
						}

						double drawX = screen.width / 2.0 + (pos.x - cameraX) * scale / screenScale;
						double drawY = screen.height / 2.0 + (pos.z - cameraZ) * scale / screenScale;

						if (drawX > 0 && drawX < screen.width && drawY > 0 && drawY < screen.height) {
							int headSize = 8;

							PlayerInfo info = client.getConnection() != null
									? client.getConnection().getPlayerInfo(uuidJoueur)
									: null;

							Identifier skinTextureToUse = null;

							if (info != null) {
								skinTextureToUse = info.getSkin().body().texturePath();
								pos.texturePeau = skinTextureToUse;
							} else {
								skinTextureToUse = pos.texturePeau;
							}

							if (skinTextureToUse != null) {
								if (ModConfig.INSTANCE.showHeads) {
									guiGraphics.blit(
											RenderPipelines.GUI_TEXTURED,
											skinTextureToUse,
											(int) drawX - headSize / 2, (int) drawY - headSize / 2, // position écran
											8, 8,           // u, v : le décalage pour cibler le visage sur la skin
											headSize, headSize,   // taille AFFICHÉE à l'écran (8px dans ton cas)
											8, 8,           // regionWidth, regionHeight : taille de la zone du visage à découper (8x8 sur la skin)
											64, 64          // textureWidth, textureHeight : taille réelle du fichier skin
									);
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
							} else {
								guiGraphics.fill((int) drawX - 3, (int) drawY - 3, (int) drawX + 3, (int) drawY + 3, 0xFFFF0000);
							}
						}
					}

					// Rendu manuel du bouton par-dessus tout le reste
					boutonOeil.render(guiGraphics, mouseX, mouseY, partialTick);

					if (boutonOeil.isMouseOver(mouseX, mouseY)) {
						String texteTooltip = Component.translatable("key.xaeroheadtracker.open_settings").getString();

						int largeurTexte = client.font.width(texteTooltip);
						int tooltipX = mouseX + 12; // léger décalage pour ne pas être sous le curseur
						int tooltipY = mouseY + 12;

						// Fond du tooltip (petit padding autour du texte)
						guiGraphics.fill(
								tooltipX - 3, tooltipY - 3,
								tooltipX + largeurTexte + 3, tooltipY + client.font.lineHeight + 3,
								0xF0100010 // Fond sombre semi-opaque, couleur classique des tooltips vanilla
						);

						// Le texte lui-même, par-dessus le fond
						guiGraphics.drawString(client.font, texteTooltip, tooltipX, tooltipY, 0xFFFFFFFF, true);
					}

				});

				// 3. GESTION MANUELLE DES CLICS : Utilisation d'un écouteur compatible
				ScreenMouseEvents.allowMouseClick(screen).register(new ScreenMouseEvents.AllowMouseClick() {
					@Override
					public boolean allowMouseClick(Screen screen, MouseButtonEvent context) {
						double mouseX = context.x();
						double mouseY = context.y();

						if (boutonOeil.isMouseOver(mouseX, mouseY)) {
							boutonOeil.mouseClicked(context, false); // <-- on repasse l'objet context tel quel
							return false; // on absorbe le clic, Xaero ne le reçoit jamais
						}
						return true;
					}
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