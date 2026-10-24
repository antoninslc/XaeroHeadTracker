package name.modid.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import name.modid.XaeroHeadTracker;

/**
 * Stockage des joueurs a afficher sur la carte, par serveur.
 * Par defaut tout le monde est masque : seuls les joueurs coches (ou tous si "afficher les autres" est actif) sont visibles.
 * Les joueurs sont identifies par UUID, le pseudo n'est conserve que pour l'affichage hors ligne.
 */
public class PlayerVisibilityConfig {
    public static final PlayerVisibilityConfig INSTANCE = new PlayerVisibilityConfig();

    private static final Path FICHIER = FMLPaths.CONFIGDIR.get().resolve("xaeroheadtracker_players.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Reglages d'un joueur coche. */
    public static class SelectedPlayer {
        public String lastKnownName;

        public SelectedPlayer(String lastKnownName) {
            this.lastKnownName = lastKnownName;
        }
    }

    /** Reglages d'un serveur. */
    public static class ServerSettings {
        public boolean showOthers = false;
        // null = pas de choix propre a ce serveur, on suit le reglage global de ModConfig
        public Boolean sharePosition = null;
        // Cle : UUID du joueur sous forme de texte
        public Map<String, SelectedPlayer> selected = new HashMap<>();
    }

    // Vrai tant que le message de migration n'a pas ete affiche (existant avant la mise a jour : fichier absent)
    public boolean migrationNoticeShown = false;

    // Cle : identifiant du serveur (voir normaliserCleServeur)
    public Map<String, ServerSettings> servers = new HashMap<>();

    // Serveur actuellement rejoint (non sauvegarde)
    private transient String serveurCourant = null;

    /** Normalise une adresse de serveur ou un nom de monde solo en cle de stockage. */
    public static String normaliserCleServeur(String adresse) {
        return adresse == null ? null : adresse.trim().toLowerCase(Locale.ROOT);
    }

    /** A appeler quand on rejoint un serveur ou un monde solo. */
    public void definirServeurCourant(String cleBrute) {
        this.serveurCourant = normaliserCleServeur(cleBrute);
    }

    public String getServeurCourant() {
        return serveurCourant;
    }

    private ServerSettings reglages(boolean creer) {
        if (serveurCourant == null) {
            return null;
        }
        ServerSettings s = servers.get(serveurCourant);
        if (s == null && creer) {
            s = new ServerSettings();
            servers.put(serveurCourant, s);
        }
        return s;
    }

    /** Un joueur est visible s'il est coche, ou si "afficher les autres" est actif. */
    public boolean estVisible(UUID uuid) {
        ServerSettings s = reglages(false);
        if (s == null) {
            return false;
        }
        return s.showOthers || s.selected.containsKey(uuid.toString());
    }

    public boolean estCoche(UUID uuid) {
        ServerSettings s = reglages(false);
        return s != null && s.selected.containsKey(uuid.toString());
    }

    public void definirCoche(UUID uuid, String pseudo, boolean coche) {
        ServerSettings s = reglages(true);
        if (s == null) {
            return;
        }
        if (coche) {
            s.selected.put(uuid.toString(), new SelectedPlayer(pseudo));
        } else {
            s.selected.remove(uuid.toString());
        }
        sauvegarder();
    }

    /** Met a jour le dernier pseudo connu d'un joueur coche (pseudo change). */
    public void mettreAJourPseudo(UUID uuid, String pseudo) {
        ServerSettings s = reglages(false);
        if (s == null) {
            return;
        }
        SelectedPlayer p = s.selected.get(uuid.toString());
        if (p != null && pseudo != null && !pseudo.equals(p.lastKnownName)) {
            p.lastKnownName = pseudo;
            sauvegarder();
        }
    }

    /** Partage de ma position sur le serveur courant : choix du serveur, sinon reglage global. */
    public boolean getPartage() {
        ServerSettings s = reglages(false);
        return s != null && s.sharePosition != null ? s.sharePosition : ModConfig.INSTANCE.sharePosition;
    }

    public void definirPartage(boolean valeur) {
        ServerSettings s = reglages(true);
        if (s == null) {
            // Hors serveur (pas de cle), on retombe sur le reglage global
            ModConfig.INSTANCE.sharePosition = valeur;
            ModConfig.INSTANCE.sauvegarder();
            return;
        }
        s.sharePosition = valeur;
        sauvegarder();
    }

    public boolean getAfficherAutres() {
        ServerSettings s = reglages(false);
        return s != null && s.showOthers;
    }

    public void definirAfficherAutres(boolean valeur) {
        ServerSettings s = reglages(true);
        if (s == null) {
            return;
        }
        s.showOthers = valeur;
        sauvegarder();
    }

    /** Liste des UUID coches du serveur courant (pour afficher les joueurs hors ligne dans l'ecran). */
    public List<UUID> getJoueursCoches() {
        List<UUID> resultat = new ArrayList<>();
        ServerSettings s = reglages(false);
        if (s != null) {
            for (String cle : s.selected.keySet()) {
                try {
                    resultat.add(UUID.fromString(cle));
                } catch (IllegalArgumentException ignore) {
                    // Entree invalide dans le fichier, ignoree
                }
            }
        }
        return resultat;
    }

    public String getPseudoConnu(UUID uuid) {
        ServerSettings s = reglages(false);
        if (s == null) {
            return null;
        }
        SelectedPlayer p = s.selected.get(uuid.toString());
        return p == null ? null : p.lastKnownName;
    }

    public void marquerMigrationAffichee() {
        this.migrationNoticeShown = true;
        sauvegarder();
    }

    public void charger() {
        try {
            if (Files.exists(FICHIER)) {
                try (Reader lecteur = Files.newBufferedReader(FICHIER)) {
                    PlayerVisibilityConfig donnees = GSON.fromJson(lecteur, PlayerVisibilityConfig.class);
                    if (donnees != null) {
                        this.migrationNoticeShown = donnees.migrationNoticeShown;
                        this.servers = donnees.servers != null ? donnees.servers : new HashMap<>();
                    }
                }
            } else {
                sauvegarder();
            }
        } catch (Exception e) {
            XaeroHeadTracker.LOGGER.error("Impossible de charger les joueurs affiches", e);
        }
    }

    public void sauvegarder() {
        try (Writer ecrivain = Files.newBufferedWriter(FICHIER)) {
            GSON.toJson(this, ecrivain);
        } catch (Exception e) {
            XaeroHeadTracker.LOGGER.error("Impossible de sauvegarder les joueurs affiches", e);
        }
    }
}
