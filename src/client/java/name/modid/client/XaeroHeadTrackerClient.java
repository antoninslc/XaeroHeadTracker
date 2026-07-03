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

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import name.modid.UpdateShareStatusPayload;

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

		// Dès qu'on rejoint un serveur (ou un monde solo), on annonce notre choix !
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			// On utilise la valeur qui a été chargée depuis le JSON
			ClientPlayNetworking.send(new UpdateShareStatusPayload(ModConfig.INSTANCE.sharePosition));
		});

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
						long tempsActuel = System.currentTimeMillis();

						// Le grand nettoyage : on supprime tous ceux dont le timestamp est plus vieux que 120 minutes
						// (120 min * 60 sec * 1000 millisecondes)
						positionsJoueurs.entrySet().removeIf(entree ->
								(tempsActuel - entree.getValue().timestamp) > 120 * 60 * 1000L
						);

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

										// 2. On calcule le temps exact écoulé en millisecondes
										long msEcoulees = tempsActuel - pos.timestamp;
										long minutesEcoulees = msEcoulees / 60000L;

										// 3. On choisit le texte et la couleur
										String texteStatut;
										if (msEcoulees <= 4000) {
											// Si la donnée a moins de 4 secondes (tolérance réseau incluse)
											texteStatut = "§aLive";
										} else if (minutesEcoulees < 1) {
											// Entre 4 secondes et 59 secondes
											texteStatut = "§7< 1 min";
										} else {
											// 1 minute et plus
											texteStatut = "§7" + minutesEcoulees + " min";
										}

										// 4. On dessine le statut (en dessous de la tête) AVEC ÉCHELLE RÉDUITE
										float echelle = 0.7F; // 70% de la taille normale

										// Nouvelle méthode 1.21.4 : on sauvegarde la matrice 2D
										guiGraphics.pose().pushMatrix();

										// On rétrécit tout de 70% (Uniquement X et Y, l'axe Z a disparu !)
										guiGraphics.pose().scale(echelle, echelle);

										// L'astuce mathématique pour garder la bonne position
										int cibleX = (int) (drawX / echelle);
										int cibleY = (int) ((drawY + headSize / 2 + 2) / echelle);

										MapRenderHelper.drawCenteredStringWithBackground(
												guiGraphics, client.font, texteStatut,
												cibleX, cibleY,
												-1,
												0.0F, 0.0F, 0.0F, 0.4F
										);

										// Nouvelle méthode 1.21.4 : on restaure la matrice normale
										guiGraphics.pose().popMatrix();
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
		public long timestamp; // L'heure de réception en millisecondes

		public PositionData(double x, double y, double z, UUID uuid) {
			this.x = x; this.y = y; this.z = z; this.uuid = uuid;
			this.timestamp = System.currentTimeMillis(); // On enregistre l'heure actuelle !
		}
	}
}