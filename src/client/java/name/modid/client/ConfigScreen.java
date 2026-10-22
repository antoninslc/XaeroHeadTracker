package name.modid.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import name.modid.UpdateShareStatusPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Page de parametres du mod.
 * Colonne principale : recherche et liste des joueurs a afficher (coches en haut, autres en bas).
 * Colonne de droite : reglages existants (tetes, noms, partage de position).
 */
public class ConfigScreen extends Screen {
    private final Screen ecranPrecedent; // Pour pouvoir retourner sur la carte en quittant

    // Mise en page
    private static final int MARGE = 10;
    private static final int LARGEUR_COLONNE_DROITE = 180;
    private static final int Y_ENTETES = 26; // Titres des colonnes
    private static final int Y_WIDGETS = 38; // Premiere ligne de widgets
    private static final int Y_CONFIDENTIALITE = 92; // Titre de la section confidentialite (colonne de droite)
    private static final int HAUTEUR_LIGNE = 20;
    private static final int HAUTEUR_ENTETE = 14;
    private static final int TAILLE_TETE = 16;
    private static final int TAILLE_CASE = 12;

    private EditBox barreRecherche;
    private String recherche = "";

    // Zone de la liste des joueurs
    private int listeX, listeY, listeLargeur, listeHauteur;
    private double defilement = 0;

    /** Une ligne de la liste : soit un entete de section, soit un joueur. */
    private static class Ligne {
        final Component entete;
        final UUID uuid;
        final String pseudo;
        final Identifier peau;
        final boolean enLigne;

        Ligne(Component entete) {
            this.entete = entete;
            this.uuid = null;
            this.pseudo = null;
            this.peau = null;
            this.enLigne = false;
        }

        Ligne(UUID uuid, String pseudo, Identifier peau, boolean enLigne) {
            this.entete = null;
            this.uuid = uuid;
            this.pseudo = pseudo;
            this.peau = peau;
            this.enLigne = enLigne;
        }

        int hauteur() {
            return entete != null ? HAUTEUR_ENTETE : HAUTEUR_LIGNE;
        }
    }

    private final List<Ligne> lignes = new ArrayList<>();

    public ConfigScreen(Screen ecranPrecedent) {
        // Le jeu ira chercher le texte en anglais ou en français selon les paramètres du joueur
        super(Component.translatable("gui.xaeroheadtracker.title"));
        this.ecranPrecedent = ecranPrecedent;
    }

    @Override
    protected void init() {
        int droiteX = this.width - MARGE - LARGEUR_COLONNE_DROITE;
        int principaleLargeur = droiteX - MARGE * 2;

        // Colonne principale : barre de recherche + bouton "Afficher les autres joueurs"
        int largeurBoutonAutres = 170;
        int largeurRecherche = principaleLargeur - largeurBoutonAutres - 4;
        this.barreRecherche = new EditBox(this.font, MARGE, Y_WIDGETS, largeurRecherche, 20, Component.translatable("gui.xaeroheadtracker.search"));
        this.barreRecherche.setHint(Component.translatable("gui.xaeroheadtracker.search"));
        this.barreRecherche.setValue(this.recherche);
        this.barreRecherche.setResponder(texte -> {
            this.recherche = texte;
            this.defilement = 0;
            reconstruireLignes();
        });
        this.addRenderableWidget(this.barreRecherche);

        this.addRenderableWidget(
                Button.builder(getTexteBoutonAutres(), bouton -> {
                            PlayerVisibilityConfig.INSTANCE.definirAfficherAutres(!PlayerVisibilityConfig.INSTANCE.getAfficherAutres());
                            bouton.setMessage(getTexteBoutonAutres());
                            reconstruireLignes(); // Le titre de la section du bas depend de ce reglage
                        })
                        .bounds(MARGE + largeurRecherche + 4, Y_WIDGETS, largeurBoutonAutres, 20)
                        .tooltip(Tooltip.create(Component.translatable("gui.xaeroheadtracker.show_others.tooltip")))
                        .build()
        );

        // Ligne d'aide sous la recherche, puis la liste
        this.listeX = MARGE;
        this.listeY = Y_WIDGETS + 38;
        this.listeLargeur = principaleLargeur;
        this.listeHauteur = this.height - this.listeY - MARGE;

        // Colonne de droite : reglages existants, regroupes par theme
        int y = Y_WIDGETS;

        // Bouton 1 : Activer/Désactiver les Têtes
        this.addRenderableWidget(
                Button.builder(getTexteBoutonTetes(), bouton -> {
                            ModConfig.INSTANCE.showHeads = !ModConfig.INSTANCE.showHeads; // On inverse la valeur
                            ModConfig.INSTANCE.sauvegarder(); // On sauvegarde dans le JSON
                            bouton.setMessage(getTexteBoutonTetes()); // On met à jour le texte
                        })
                        .bounds(droiteX, y, LARGEUR_COLONNE_DROITE, 20)
                        .tooltip(Tooltip.create(Component.translatable("gui.xaeroheadtracker.heads.tooltip")))
                        .build()
        );

        // Bouton 2 : Activer/Désactiver les Noms
        this.addRenderableWidget(
                Button.builder(getTexteBoutonNoms(), bouton -> {
                            ModConfig.INSTANCE.showNames = !ModConfig.INSTANCE.showNames;
                            ModConfig.INSTANCE.sauvegarder();
                            bouton.setMessage(getTexteBoutonNoms());
                        })
                        .bounds(droiteX, y + 24, LARGEUR_COLONNE_DROITE, 20)
                        .tooltip(Tooltip.create(Component.translatable("gui.xaeroheadtracker.names.tooltip")))
                        .build()
        );

        // Bouton 3 : Partager ma position (OUI/NON)
        this.addRenderableWidget(
                Button.builder(getTexteBoutonPartage(), bouton -> {
                            ModConfig.INSTANCE.sharePosition = !ModConfig.INSTANCE.sharePosition;
                            ModConfig.INSTANCE.sauvegarder();
                            bouton.setMessage(getTexteBoutonPartage());

                            // On envoie instantanément la nouvelle décision au serveur !
                            ClientPlayNetworking.send(new UpdateShareStatusPayload(ModConfig.INSTANCE.sharePosition));
                        })
                        .bounds(droiteX, Y_CONFIDENTIALITE + 12, LARGEUR_COLONNE_DROITE, 20)
                        .tooltip(Tooltip.create(Component.translatable("gui.xaeroheadtracker.share.tooltip")))
                        .build()
        );

        // Bouton Retour, en bas de la colonne de droite
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.xaeroheadtracker.back"), bouton -> {
                            this.minecraft.gui.setScreen(this.ecranPrecedent); // On ferme le menu
                        })
                        .bounds(droiteX, this.height - MARGE - 20, LARGEUR_COLONNE_DROITE, 20)
                        .build()
        );

        reconstruireLignes();
    }

    /**
     * Construit la liste des joueurs connus : en ligne, vus pendant la session (carte) et coches hors ligne.
     * Les joueurs coches sont places en haut, les autres en bas, chaque section triee par pseudo.
     */
    private void reconstruireLignes() {
        lignes.clear();
        Map<UUID, Ligne> joueurs = new LinkedHashMap<>();
        UUID monUuid = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        long maintenant = System.currentTimeMillis();

        // 1. Joueurs en ligne sur le serveur
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            for (PlayerInfo info : this.minecraft.getConnection().getOnlinePlayers()) {
                UUID uuid = info.getProfile().id();
                if (uuid == null || uuid.equals(monUuid)) {
                    continue;
                }
                joueurs.put(uuid, new Ligne(uuid, info.getProfile().name(), info.getSkin().body().texturePath(), true));
            }
        }

        // 2. Joueurs vus sur la carte pendant la session (dont ceux deconnectes recemment)
        for (Map.Entry<String, XaeroHeadTrackerClient.PositionData> entree : XaeroHeadTrackerClient.positionsJoueurs.entrySet()) {
            XaeroHeadTrackerClient.PositionData pos = entree.getValue();
            if (pos.uuid == null || pos.uuid.equals(monUuid) || joueurs.containsKey(pos.uuid)) {
                continue;
            }
            boolean enLigne = maintenant - pos.timestamp <= 4000;
            joueurs.put(pos.uuid, new Ligne(pos.uuid, entree.getKey(), pos.texturePeau, enLigne));
        }

        // 3. Joueurs coches mais absents (hors ligne)
        for (UUID uuid : PlayerVisibilityConfig.INSTANCE.getJoueursCoches()) {
            if (joueurs.containsKey(uuid)) {
                continue;
            }
            String pseudo = PlayerVisibilityConfig.INSTANCE.getPseudoConnu(uuid);
            joueurs.put(uuid, new Ligne(uuid, pseudo != null ? pseudo : uuid.toString(), null, false));
        }

        // Filtre de recherche et repartition en deux sections
        String filtre = recherche.trim().toLowerCase(Locale.ROOT);
        List<Ligne> coches = new ArrayList<>();
        List<Ligne> autres = new ArrayList<>();
        for (Ligne l : joueurs.values()) {
            if (!filtre.isEmpty() && !l.pseudo.toLowerCase(Locale.ROOT).contains(filtre)) {
                continue;
            }
            if (PlayerVisibilityConfig.INSTANCE.estCoche(l.uuid)) {
                coches.add(l);
            } else {
                autres.add(l);
            }
        }
        coches.sort((a, b) -> a.pseudo.compareToIgnoreCase(b.pseudo));
        autres.sort((a, b) -> a.pseudo.compareToIgnoreCase(b.pseudo));

        lignes.add(new Ligne(Component.translatable("gui.xaeroheadtracker.section_shown", coches.size())));
        lignes.addAll(coches);
        lignes.add(new Ligne(Component.translatable(PlayerVisibilityConfig.INSTANCE.getAfficherAutres()
                ? "gui.xaeroheadtracker.section_others_shown"
                : "gui.xaeroheadtracker.section_others_hidden", autres.size())));
        lignes.addAll(autres);

        limiterDefilement();
    }

    private int hauteurTotale() {
        int total = 0;
        for (Ligne l : lignes) {
            total += l.hauteur();
        }
        return total;
    }

    private void limiterDefilement() {
        double max = Math.max(0, hauteurTotale() - listeHauteur);
        defilement = Math.max(0, Math.min(defilement, max));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Titre
        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // Titres des colonnes et des sections
        int droiteX = this.width - MARGE - LARGEUR_COLONNE_DROITE;
        graphics.text(this.font, Component.translatable("gui.xaeroheadtracker.header_players"), MARGE, Y_ENTETES, 0xFFFFFF55);
        graphics.text(this.font, Component.translatable("gui.xaeroheadtracker.header_display"), droiteX, Y_ENTETES, 0xFFFFFF55);
        graphics.text(this.font, Component.translatable("gui.xaeroheadtracker.header_privacy"), droiteX, Y_CONFIDENTIALITE, 0xFFFFFF55);

        // Ligne d'aide sous la recherche
        graphics.text(this.font, Component.translatable("gui.xaeroheadtracker.help"), MARGE, Y_WIDGETS + 25, 0xFFAAAAAA);

        // Fond de la liste
        graphics.fill(listeX, listeY, listeX + listeLargeur, listeY + listeHauteur, 0x80000000);

        graphics.enableScissor(listeX, listeY, listeX + listeLargeur, listeY + listeHauteur);
        int y = listeY - (int) defilement;
        for (Ligne l : lignes) {
            int h = l.hauteur();
            if (y + h >= listeY && y <= listeY + listeHauteur) {
                if (l.entete != null) {
                    graphics.text(this.font, l.entete, listeX + 4, y + 3, 0xFFFFFF55);
                } else {
                    dessinerJoueur(graphics, l, y, mouseX, mouseY);
                }
            }
            y += h;
        }
        graphics.disableScissor();

        // Message si aucun joueur ne correspond
        if (lignes.size() <= 2) {
            graphics.centeredText(this.font, Component.translatable("gui.xaeroheadtracker.no_players"),
                    listeX + listeLargeur / 2, listeY + listeHauteur / 2, 0xFFAAAAAA);
        }
    }

    private void dessinerJoueur(GuiGraphicsExtractor graphics, Ligne l, int y, int mouseX, int mouseY) {
        boolean survol = mouseX >= listeX && mouseX < listeX + listeLargeur
                && mouseY >= y && mouseY < y + HAUTEUR_LIGNE
                && mouseY >= listeY && mouseY < listeY + listeHauteur;
        if (survol) {
            graphics.fill(listeX, y, listeX + listeLargeur, y + HAUTEUR_LIGNE, 0x30FFFFFF);
        }

        // Tete a gauche (visage + calque du chapeau)
        Identifier peau = l.peau != null ? l.peau : DefaultPlayerSkin.get(l.uuid).body().texturePath();
        int teteX = listeX + 4;
        int teteY = y + (HAUTEUR_LIGNE - TAILLE_TETE) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, peau, teteX, teteY, 8.0F, 8.0F, TAILLE_TETE, TAILLE_TETE, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, peau, teteX, teteY, 40.0F, 8.0F, TAILLE_TETE, TAILLE_TETE, 8, 8, 64, 64);

        // Pseudo (gris si hors ligne)
        int couleur = l.enLigne ? 0xFFFFFFFF : 0xFF808080;
        graphics.text(this.font, l.pseudo, teteX + TAILLE_TETE + 6, y + (HAUTEUR_LIGNE - 8) / 2, couleur);

        // Case a cocher a droite
        boolean coche = PlayerVisibilityConfig.INSTANCE.estCoche(l.uuid);
        int caseX = listeX + listeLargeur - TAILLE_CASE - 6;
        int caseY = y + (HAUTEUR_LIGNE - TAILLE_CASE) / 2;
        graphics.fill(caseX, caseY, caseX + TAILLE_CASE, caseY + TAILLE_CASE, 0xFFA0A0A0);
        graphics.fill(caseX + 1, caseY + 1, caseX + TAILLE_CASE - 1, caseY + TAILLE_CASE - 1, 0xFF202020);
        if (coche) {
            graphics.fill(caseX + 3, caseY + 3, caseX + TAILLE_CASE - 3, caseY + TAILLE_CASE - 3, 0xFF55FF55);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        if (mx < listeX || mx >= listeX + listeLargeur || my < listeY || my >= listeY + listeHauteur) {
            return false;
        }

        // Clic sur une ligne de joueur : on coche ou decoche
        int y = listeY - (int) defilement;
        for (Ligne l : lignes) {
            int h = l.hauteur();
            if (my >= y && my < y + h) {
                if (l.uuid != null) {
                    boolean coche = PlayerVisibilityConfig.INSTANCE.estCoche(l.uuid);
                    PlayerVisibilityConfig.INSTANCE.definirCoche(l.uuid, l.pseudo, !coche);
                    reconstruireLignes();
                    return true;
                }
                return false;
            }
            y += h;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listeX && mouseX < listeX + listeLargeur && mouseY >= listeY && mouseY < listeY + listeHauteur) {
            defilement -= scrollY * HAUTEUR_LIGNE;
            limiterDefilement();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Component getTexteBoutonAutres() {
        Component etat = PlayerVisibilityConfig.INSTANCE.getAfficherAutres()
                ? Component.literal("§a").append(Component.translatable("gui.xaeroheadtracker.yes"))
                : Component.literal("§c").append(Component.translatable("gui.xaeroheadtracker.no"));
        return Component.translatable("gui.xaeroheadtracker.show_others").append(etat);
    }

    private Component getTexteBoutonTetes() {
        Component etat = ModConfig.INSTANCE.showHeads
                ? Component.literal("§a").append(Component.translatable("gui.xaeroheadtracker.yes"))
                : Component.literal("§c").append(Component.translatable("gui.xaeroheadtracker.no"));
        return Component.translatable("gui.xaeroheadtracker.heads").append(etat);
    }

    private Component getTexteBoutonNoms() {
        Component etat = ModConfig.INSTANCE.showNames
                ? Component.literal("§a").append(Component.translatable("gui.xaeroheadtracker.yes"))
                : Component.literal("§c").append(Component.translatable("gui.xaeroheadtracker.no"));
        return Component.translatable("gui.xaeroheadtracker.names").append(etat);
    }

    private Component getTexteBoutonPartage() {
        Component etat = ModConfig.INSTANCE.sharePosition
                ? Component.literal("§a").append(Component.translatable("gui.xaeroheadtracker.yes"))
                : Component.literal("§c").append(Component.translatable("gui.xaeroheadtracker.no"));
        return Component.translatable("gui.xaeroheadtracker.share").append(etat);
    }
}
