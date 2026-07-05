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

import net.fabricmc.loader.api.FabricLoader;

public class XaeroHeadTrackerClient implements ClientModInitializer {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();
	public static boolean isMinimapInstalled = false;
	// 1. On déclare officiellement notre propre catégorie dans les paramètres du jeu
	private static final KeyMapping.Category CATEGORIE_MOD = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("xaeroheadtracker", "parametres"));

	// 2. Notre raccourci clavier
	private static KeyMapping toucheParametres;

	private static final Identifier EYE_TEXTURE = Identifier.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

	@Override
	public void onInitializeClient() {

		ModConfig.INSTANCE.charger();

		// On demande à Fabric si l'un des deux mods Minimap de Xaero est présent dans le dossier mods
		isMinimapInstalled = FabricLoader.getInstance().isModLoaded("xaerominimap")
				|| FabricLoader.getInstance().isModLoaded("xaerominimapfair");

		// Dès qu'on rejoint un serveur (ou un monde solo), on annonce notre choix !
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			// On utilise la valeur qui a été chargée depuis le JSON
			ClientPlayNetworking.send(new UpdateShareStatusPayload(ModConfig.INSTANCE.sharePosition));
		});

		// 1. On crée et on enregistre le raccourci (avec KeyMappingHelper !)
		toucheParametres = KeyMappingHelper.registerKeyMapping(new KeyMapping(
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
		});

		ClientPlayNetworking.registerGlobalReceiver(PlayerPositionPayload.TYPE, (payload, context) -> {
			String pseudo = payload.pseudo();
			UUID uuid = payload.uuid();
			double x = payload.x();
			double y = payload.y();
			double z = payload.z();
			String dimension = payload.dimension();

			// L'ASTUCE : Si le joueur est déjà dans notre liste, on met juste à jour ses infos
			PositionData posExistante = positionsJoueurs.get(pseudo);
			if (posExistante != null) {
				posExistante.x = x;
				posExistante.y = y;
				posExistante.z = z;
				posExistante.dimension = dimension;
				posExistante.timestamp = System.currentTimeMillis();
			} else {
				// C'est un nouveau joueur, on l'ajoute
				positionsJoueurs.put(pseudo, new PositionData(x, y, z, uuid, dimension));
			}
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

						// NOUVEAU : On regarde dans quelle dimension on est actuellement
						String maDimension = client.level != null ? client.level.dimension().identifier().toString() : "";

						// La bonne boucle avec le bon type (String) et la bonne liste (positionsJoueurs)
						for (Map.Entry<String, PositionData> entree : positionsJoueurs.entrySet()) {

							// On extrait les variables correctement
							String pseudo = entree.getKey();
							PositionData pos = entree.getValue();
							UUID uuidJoueur = pos.uuid;

							// NOUVELLE CONDITION : On ignore le joueur s'il n'est pas dans NOTRE dimension
							if (!pos.dimension.equals(maDimension)) {
								continue;
							}

							// NOUVELLE CONDITION SÉCURISÉE :
							if (isMinimapInstalled && client.level != null && client.level.getPlayerByUUID(uuidJoueur) != null) {
								continue; // La Minimap gère ce joueur, on passe au suivant
							}

							// L'ASTUCE EST ICI : On ignore le joueur si c'est nous-même
							if (client.player != null && uuidJoueur.equals(client.player.getUUID())) {
								continue; // On passe au joueur suivant sans rien dessiner
							}

							// Calcul de la position sur l'écran
							double drawX = screen.width / 2.0 + (pos.x - cameraX) * scale / screenScale;
							double drawY = screen.height / 2.0 + (pos.z - cameraZ) * scale / screenScale;

							// Si le joueur est visible sur l'écran
							if (drawX > 0 && drawX < screen.width && drawY > 0 && drawY < screen.height) {
								int headSize = 8; // taille d'affichage à l'écran, en pixels GUI

								PlayerInfo info = client.getConnection() != null
										? client.getConnection().getPlayerInfo(uuidJoueur)
										: null;

								// 1. On essaie d'obtenir la peau
								Identifier skinTextureToUse = null;

								if (info != null) {
									// Le joueur est en ligne, on capture sa vraie peau et on la SAUVEGARDE
									skinTextureToUse = info.getSkin().body().texturePath();
									pos.texturePeau = skinTextureToUse;
								} else {
									// Le joueur est déconnecté, on utilise notre sauvegarde !
									skinTextureToUse = pos.texturePeau;
								}

								// 2. Si on a trouvé une peau (soit en ligne, soit via notre sauvegarde)
								if (skinTextureToUse != null) {

									if (ModConfig.INSTANCE.showHeads) {
										guiGraphics.blit(
												RenderPipelines.GUI_TEXTURED,
												skinTextureToUse,
												(int) drawX - headSize / 2, (int) drawY - headSize / 2,
												8.0F, 8.0F,
												headSize, headSize,
												64, 64
										);
									}

									if (ModConfig.INSTANCE.showNames) {
										MapRenderHelper.drawCenteredStringWithBackground(
												guiGraphics, client.font, pseudo,
												(int) drawX, (int) drawY - headSize / 2 - 10,
												-1,
												0.0F, 0.0F, 0.0F, 0.4F
										);

										// On calcule le temps
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

										// On dessine le statut
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
									// Le carré rouge n'apparaîtra QUE si on vient de lancer le jeu
									// et que le joueur s'est déconnecté avant même qu'on ait pu voir sa peau une seule fois.
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
						client.setScreen(new ConfigScreen(screen));
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
				// On utilise translatable pour charger la clé du fichier JSON !
				boutonOeil.setTooltip(Tooltip.create(Component.translatable("key.xaeroheadtracker.open_settings")));

				Screens.getWidgets(screen).add(boutonOeil);
			}
		});
	}

	public static class PositionData {
		public double x, y, z;
		public UUID uuid;
		public String dimension;
		public long timestamp;
		public Identifier texturePeau; // NOUVEAU : La mémoire de la peau !

		public PositionData(double x, double y, double z, UUID uuid, String dimension) {
			this.x = x; this.y = y; this.z = z; this.uuid = uuid;
			this.dimension = dimension;
			this.timestamp = System.currentTimeMillis();
			this.texturePeau = null; // Par défaut, on ne l'a pas encore
		}
	}
}