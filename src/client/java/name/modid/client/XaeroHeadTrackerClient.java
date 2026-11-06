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
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.11 {
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import name.modid.PlayerLastPositionPayload;
import name.modid.PlayerPositionPayload;
import name.modid.PlayerStopSharePayload;
import name.modid.XaeroHeadTracker;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
//? if >=1.21.11 {
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.properties.PropertyMap;
//?}

// L'IMPORT DE LA CARTE XAERO
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import xaero.map.WorldMap;
import xaero.map.common.config.option.WorldMapProfiledConfigOptions;
import xaero.map.gui.GuiMap;
//? if >=26 {
import xaero.map.graphics.MapRenderHelper;
//?}

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.resources.DefaultPlayerSkin;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.components.AbstractButton;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import name.modid.UpdateShareAudiencePayload;
import name.modid.UpdateShareStatusPayload;

public class XaeroHeadTrackerClient implements ClientModInitializer {

	public static final Map<String, PositionData> positionsJoueurs = new HashMap<>();
	public static boolean isMinimapInstalled = false;
	private static String dernierServeur = null;

	private static final String BOT_TEST_NAME = "BotTest";
	private static final UUID BOT_TEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static boolean simulerBotEnLigne = false;
	// Bots supplementaires crees par /xht simulate many, maintenus en ligne a chaque tick
	private static final Set<String> botsMultiplesEnLigne = new HashSet<>();
	private static final Set<String> botsMultiples = new HashSet<>();

	// 1. On déclare officiellement notre propre catégorie dans les paramètres du jeu
	//? if >=1.21.11 {
	private static final KeyMapping.Category CATEGORIE_MOD = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("xaeroheadtracker", "parametres"));
	//?} else {
	/*private static final String CATEGORIE_MOD = "key.category.xaeroheadtracker.parametres";
	*///?}

	// 2. Notre raccourci clavier
	static KeyMapping toucheParametres;

	private static final Identifier EYE_TEXTURE = Identifier.fromNamespaceAndPath("xaeroheadtracker", "textures/gui/eye.png");

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

	/** Envoie qui peut me voir ; a envoyer avant le partage, pour que le serveur ne diffuse jamais avec une liste perimee. */
	static boolean envoyerAudience() {
		if (!ClientPlayNetworking.canSend(UpdateShareAudiencePayload.TYPE)) {
			return false;
		}
		ClientPlayNetworking.send(new UpdateShareAudiencePayload(PlayerVisibilityConfig.INSTANCE.getPartageTous(), PlayerVisibilityConfig.INSTANCE.getJoueursPartage()));
		return true;
	}

	static void envoyerPartage() {
		boolean audienceEnvoyee = envoyerAudience();
		// Un serveur sans la selection diffuserait a tout le monde : avec une liste, on ne partage pas du tout
		boolean partage = PlayerVisibilityConfig.INSTANCE.getPartage()
				&& (audienceEnvoyee || PlayerVisibilityConfig.INSTANCE.getPartageTous());
		ClientPlayNetworking.send(new UpdateShareStatusPayload(partage));
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
			// On identifie le serveur (ou le monde solo) pour charger les joueurs choisis et le choix de partage
			String serveur = identifierServeur(client);
			PlayerVisibilityConfig.INSTANCE.definirServeurCourant(serveur);

			// Les têtes d'un autre serveur n'ont rien à faire sur cette carte ; reconnecté au même, on garde les déconnectés
			if (!serveur.equals(dernierServeur)) {
				positionsJoueurs.clear();
				dernierServeur = serveur;
			}

			envoyerPartage();
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
								// Cree plusieurs bots autour du joueur : 2 sur 3 en ligne, les autres deconnectes depuis quelques minutes
								.then(ClientCommands.literal("many")
										.then(ClientCommands.argument("nombre", IntegerArgumentType.integer(1, 100)).executes(ctx -> {
											int nombre = IntegerArgumentType.getInteger(ctx, "nombre");
											var player = ctx.getSource().getPlayer();
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
											ctx.getSource().sendFeedback(Component.literal("§a[XHT Dev] " + nombre + " bots créés (Bot1 a Bot" + nombre + "), 1 sur 3 déconnecté."));
											return 1;
										})))
								.then(ClientCommands.literal("clear").executes(ctx -> {
									simulerBotEnLigne = false;
									positionsJoueurs.remove(BOT_TEST_NAME);
									supprimerBotsMultiples();
									ctx.getSource().sendFeedback(Component.literal("§c[XHT Dev] BotTest et bots multiples supprimés de la carte."));
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
				if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
					XaeroHeadTracker.LOGGER.info("[XHT Dev] Premiere position recue de {}", pseudo);
				}
			}

			Minecraft client = context.client();
			if (client.getConnection() != null) {
				PlayerInfo info = client.getConnection().getPlayerInfo(uuid);
				if (info != null) {
					pos.texturePeau = info.getSkin().body().texturePath();
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(PlayerStopSharePayload.TYPE, (payload, context) -> {
			if (payload.pseudo().equals(PlayerStopSharePayload.TOUS)) {
				positionsJoueurs.clear();
			} else if (payload.retirer()) {
				positionsJoueurs.remove(payload.pseudo());
			} else {
				PositionData pos = positionsJoueurs.get(payload.pseudo());
				if (pos != null) {
					// Au-dela du seuil Live de 4 s : le statut passe tout de suite a "< 1 min"
					pos.timestamp = System.currentTimeMillis() - 5000L;
				}
			}
		});

		// Joueur parti avant notre connexion : le serveur a garde sa derniere position
		ClientPlayNetworking.registerGlobalReceiver(PlayerLastPositionPayload.TYPE, (payload, context) -> {
			PlayerPositionPayload p = payload.position();
			PositionData pos = new PositionData(p.x(), p.y(), p.z(), p.uuid(), p.dimension());
			pos.timestamp = System.currentTimeMillis() - payload.age();
			PositionData connue = positionsJoueurs.get(p.pseudo());
			if (connue != null && connue.timestamp >= pos.timestamp) {
				return;
			}
			positionsJoueurs.put(p.pseudo(), pos);
			if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
				XaeroHeadTracker.LOGGER.info("[XHT Dev] Derniere position de {} : {} il y a {} s", p.pseudo(), p.dimension(), payload.age() / 1000);
			}
			if (!payload.skin().isEmpty()) {
				chargerSkin(context.client(), pos, p.pseudo(), payload.skin(), payload.signature());
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

			if (screen.getClass().getName().contains("GuiMap")) {

				// 1. On prépare notre bouton sans l'ajouter à la liste de l'écran pour ne pas interférer avec Xaero
				AbstractButton boutonOeil = new AbstractButton(7, 32, 16, 16, Component.literal("O")) {

					@Override
					//? if >=1.21.11 {
					public void onPress(InputWithModifiers input) {
					//?} else {
					/*public void onPress() {
					*///?}
						client.gui.setScreen(new ConfigScreen(screen));
					}

					@Override
					//? if >=26 {
					protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
					//?} else if >=1.21.11 {
					/*protected void renderContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
					*///?} else {
					/*protected void renderWidget(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
					*///?}
						// Si la souris est dessus, le décalage est de -1 pixel (vers le haut), sinon 0
						int decalageY = this.isHovered() ? -1 : 0;

						// Meme teinte au survol que les boutons de Xaero
						//? if >=1.21.11 {
						int teinte = this.isHovered() ? 0xFFE6E6E6 : 0xFFFCFCFC;

						guiGraphics.blit(
								RenderPipelines.GUI_TEXTURED,
								EYE_TEXTURE,
								this.getX(), this.getY() + decalageY,
								0.0F, 0.0F,
								16, 16,
								16, 16,
								teinte
						);
						//?} else {
						/*float gris = (this.isHovered() ? 0xE6 : 0xFC) / 255.0F;

						guiGraphics.setColor(gris, gris, gris, 1.0F);
						guiGraphics.blit(
								EYE_TEXTURE,
								this.getX(), this.getY() + decalageY,
								0.0F, 0.0F,
								16, 16,
								16, 16
						);
						guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
						*///?}
					}

					@Override
					protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
				};

				boutonOeil.setTooltip(Tooltip.create(Component.translatable("gui.xaeroheadtracker.open_settings_tooltip", toucheParametres.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.DARK_GREEN))));

				// 2. RENDU FORCÉ À LA TOUTE FIN : On dessine par-dessus la carte de Xaero sans widget plein écran
				ScreenEvents.afterExtract(screen).register((s, guiGraphics, mouseX, mouseY, partialTick) -> {
					// World Map 1.41.3 (Fabric 26.2) n'a pas GuiMap.hiddenUI
					//? if >=26.3 || <26 {
					if (!GuiMap.hiddenUI) {
					//?}
						boutonOeil.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
						// Cet evenement passe apres le rendu des infobulles de l'ecran : on affiche la notre tout de suite
						//? if >=26 {
						guiGraphics.extractDeferredElements(mouseX, mouseY, partialTick);
						//?} else if >=1.21.11 {
						/*guiGraphics.renderDeferredElements();
						*///?}
					//? if >=26.3 || <26 {
					}
					//?}
				});

				// La touche du raccourci ouvre aussi les reglages depuis la carte (les touches ne sont pas lues en tick quand un ecran est ouvert)
				//? if >=1.21.11 {
				ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
					if (toucheParametres.matches(event)) {
				//?} else {
				/*ScreenKeyboardEvents.allowKeyPress(screen).register((s, touche, scancode, modificateurs) -> {
					if (toucheParametres.matches(touche, scancode)) {
				*///?}
						client.gui.setScreen(new ConfigScreen(screen));
						return false;
					}
					return true;
				});

				// 3. GESTION MANUELLE DES CLICS : On intercepte le clic sur l'œil avant Xaero
				//? if >=26.3 {
				ScreenMouseEvents.allowMouseClick(screen).register((Screen s, MouseButtonEvent context) -> {
					if (!GuiMap.hiddenUI && boutonOeil.isMouseOver(context.x(), context.y())) {
						boutonOeil.mouseClicked(context, false);
				//?} else if >=26 {
				/*ScreenMouseEvents.allowMouseClick(screen).register((Screen s, MouseButtonEvent context) -> {
					if (boutonOeil.isMouseOver(context.x(), context.y())) {
						boutonOeil.mouseClicked(context, false);
				*///?} else if >=1.21.11 {
				/*ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
					if (!GuiMap.hiddenUI && boutonOeil.isMouseOver(event.x(), event.y())) {
						boutonOeil.mouseClicked(event, false);
				*///?} else {
				/*ScreenMouseEvents.allowMouseClick(screen).register((s, sourisX, sourisY, boutonSouris) -> {
					if (!GuiMap.hiddenUI && boutonOeil.isMouseOver(sourisX, sourisY)) {
						boutonOeil.mouseClicked(sourisX, sourisY, boutonSouris);
				*///?}
						return false; // on absorbe le clic, Xaero ne le reçoit jamais
					}
					return true;
				});
			}
		});
	}

	/** Telecharge le skin d'un joueur jamais vu a partir des textures envoyees par le serveur ; sans signature valide, le skin par defaut reste. */
	private static void chargerSkin(Minecraft client, PositionData pos, String pseudo, String textures, String signature) {
		Property propriete = new Property("textures", textures, signature.isEmpty() ? null : signature);
		//? if >=1.21.11 {
		GameProfile profil = new GameProfile(pos.uuid, pseudo, new PropertyMap(ImmutableMultimap.of("textures", propriete)));
		client.getSkinManager().get(profil).thenAccept(skin -> skin.ifPresent(s ->
				client.execute(() -> skinCharge(pos, pseudo, s.body().texturePath()))));
		//?} else {
		/*GameProfile profil = new GameProfile(pos.uuid, pseudo);
		profil.getProperties().put("textures", propriete);
		client.getSkinManager().getOrLoad(profil).thenAccept(s ->
				client.execute(() -> skinCharge(pos, pseudo, s.body().texturePath())));
		*///?}
	}

	private static void skinCharge(PositionData pos, String pseudo, Identifier texture) {
		pos.texturePeau = texture;
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			XaeroHeadTracker.LOGGER.info("[XHT Dev] Skin de {} charge : {}", pseudo, texture);
		}
	}

	// Faux des qu'une Minimap trop ancienne n'a pas l'API du radar : on retombe alors sur le seul reglage de World Map
	private static boolean radarLisible = true;

	/** Vrai si la Minimap dessine deja ce joueur sur la carte : radar affiche par World Map et joueur present dans le radar. */
	private static boolean minimapDessine(Entity joueur) {
		boolean resultat = minimapDessineSansLog(joueur);
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			String etat = "radar World Map=" + WorldMap.INSTANCE.getConfigs().getClientConfigManager().getEffective(WorldMapProfiledConfigOptions.MINIMAP_RADAR)
					+ ", minimap dessine=" + resultat;
			if (!etat.equals(etatsRadar.put(joueur.getName().getString(), etat))) {
				XaeroHeadTracker.LOGGER.info("[XHT Dev] {} proche : {}", joueur.getName().getString(), etat);
			}
		}
		return resultat;
	}

	// Dev : dernier etat journalise par joueur, pour n'ecrire que les changements
	private static final Map<String, String> etatsRadar = new HashMap<>();

	private static boolean minimapDessineSansLog(Entity joueur) {
		if (!isMinimapInstalled || !Boolean.TRUE.equals(WorldMap.INSTANCE.getConfigs().getClientConfigManager()
				.getEffective(WorldMapProfiledConfigOptions.MINIMAP_RADAR))) {
			return false;
		}
		if (radarLisible) {
			try {
				return RadarMinimap.afficheEntite(joueur);
			} catch (LinkageError e) {
				radarLisible = false;
				XaeroHeadTracker.LOGGER.warn("Radar de la Minimap illisible (version trop ancienne ?), les joueurs proches lui sont laisses", e);
			}
		}
		return true;
	}

	/** Dessine les tetes sur la carte ; appele par GuiMapMixin avant les boutons de Xaero pour que ses cadres passent par-dessus. */
	public static void dessinerTetes(Minecraft client, GuiMap mapScreen, GuiGraphicsExtractor guiGraphics) {
		GuiMapAccessor accessor = (GuiMapAccessor) mapScreen;

		double cameraX = accessor.getCameraX();
		double cameraZ = accessor.getCameraZ();
		double scale = accessor.getScale();
		double screenScale = accessor.getScreenScale();
		long tempsActuel = System.currentTimeMillis();

		// Le grand nettoyage : meme duree que celle gardee par le serveur pour les joueurs deconnectes
		positionsJoueurs.entrySet().removeIf(entree ->
				(tempsActuel - entree.getValue().timestamp) > XaeroHeadTracker.DUREE_ABSENT
		);

		// Dimension affichee par la carte, pas celle du joueur : le bouton de Xaero permet d'en voir une autre
		var dimensionCarte = mapScreen.getMapProcessor().getMapWorld().getCurrentDimensionId();
		String maDimension = dimensionCarte != null ? dimensionCarte.identifier().toString() : "";

		for (Map.Entry<String, PositionData> entree : positionsJoueurs.entrySet()) {
			String pseudo = entree.getKey();
			PositionData pos = entree.getValue();
			UUID uuidJoueur = pos.uuid;

			// On ignore le joueur s'il n'est pas dans la dimension affichee
			if (!pos.dimension.equals(maDimension)) {
				continue;
			}

			// Joueur proche deja dessine par le radar de la Minimap : on ne le dessine pas une deuxieme fois
			if (client.level != null) {
				Player proche = client.level.getPlayerByUUID(uuidJoueur);
				if (proche != null && minimapDessine(proche)) {
					continue;
				}
			}

			// On ignore le joueur si c'est nous-même
			if (client.player != null && uuidJoueur.equals(client.player.getUUID())) {
				continue;
			}

			// Filtre des joueurs choisis : on ignore les joueurs masques
			if (!PlayerVisibilityConfig.INSTANCE.estVisible(uuidJoueur)) {
				continue;
			}
			PlayerVisibilityConfig.INSTANCE.mettreAJourPseudo(uuidJoueur, pseudo);

			double drawX = mapScreen.width / 2.0 + (pos.x - cameraX) * scale / screenScale;
			double drawY = mapScreen.height / 2.0 + (pos.z - cameraZ) * scale / screenScale;

			if (drawX > 0 && drawX < mapScreen.width && drawY > 0 && drawY < mapScreen.height) {
				int headSize = 8; // taille d'affichage à l'écran, en pixels GUI

				PlayerInfo info = client.getConnection() != null
						? client.getConnection().getPlayerInfo(uuidJoueur)
						: null;

				if (info != null) {
					pos.texturePeau = info.getSkin().body().texturePath();
				}

				// Joueur jamais vu dont le skin n'est pas (encore) charge : Steve ou Alex selon son UUID, comme en jeu
				Identifier skinTextureToUse = pos.texturePeau != null ? pos.texturePeau : DefaultPlayerSkin.get(uuidJoueur).body().texturePath();

				if (ModConfig.INSTANCE.showHeads) {
					//? if >=26 {
					guiGraphics.blit(
							RenderPipelines.GUI_TEXTURED,
							skinTextureToUse,
							(int) drawX - headSize / 2, (int) drawY - headSize / 2,
							8.0F, 8.0F,
							headSize, headSize,
							64, 64
					);
					//?} else if >=1.21.11 {
					/*guiGraphics.blit(
							RenderPipelines.GUI_TEXTURED,
							skinTextureToUse,
							(int) drawX - headSize / 2, (int) drawY - headSize / 2,
							8.0F, 8.0F,
							headSize, headSize,
							8, 8,
							64, 64
					);
					*///?} else {
					/*guiGraphics.blit(
							skinTextureToUse,
							(int) drawX - headSize / 2, (int) drawY - headSize / 2,
							8.0F, 8.0F,
							headSize, headSize,
							64, 64
					);
					*///?}
				}

				boolean avecTete = ModConfig.INSTANCE.showHeads;
				// Sans tete, le pseudo est centre sur la position du joueur
				int basBloc = avecTete ? (int) drawY + headSize / 2 : (int) drawY + 5;

				if (ModConfig.INSTANCE.showNames) {
					int pseudoY = avecTete ? (int) drawY - headSize / 2 - 10 : (int) drawY - 4;
					//? if >=26 {
					MapRenderHelper.drawCenteredStringWithBackground(
							guiGraphics, client.font, pseudo,
							(int) drawX, pseudoY,
							-1,
							0.0F, 0.0F, 0.0F, 0.4F
					);
					//?} else {
					/*guiGraphics.drawCenteredString(
							client.font, pseudo,
							(int) drawX, pseudoY,
							-1
					);
					*///?}
				}

				if (ModConfig.INSTANCE.showNames || avecTete) {

					long msEcoulees = tempsActuel - pos.timestamp;
					long minutesEcoulees = msEcoulees / 60000L;

					String texteStatut;
					if (msEcoulees <= 4000) {
						texteStatut = "§aLive";
					} else if (minutesEcoulees < 1) {
						texteStatut = "§7< 1 min";
					} else if (minutesEcoulees < 60) {
						texteStatut = "§7" + minutesEcoulees + " min";
					} else {
						texteStatut = "§7" + minutesEcoulees / 60 + " h";
					}

					float echelle = 0.7F;
					guiGraphics.pose().pushMatrix();
					guiGraphics.pose().scale(echelle, echelle);

					int cibleX = (int) (drawX / echelle);
					int cibleY = (int) ((basBloc + 2) / echelle);

					//? if >=26 {
					MapRenderHelper.drawCenteredStringWithBackground(
							guiGraphics, client.font, texteStatut,
							cibleX, cibleY,
							-1,
							0.0F, 0.0F, 0.0F, 0.4F
					);
					//?} else {
					/*guiGraphics.drawCenteredString(
							client.font, texteStatut,
							cibleX, cibleY,
							-1
					);
					*///?}

					guiGraphics.pose().popMatrix();
				}
			}
		}
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