package name.modid.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import name.modid.XaeroHeadTracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModConfig {
    // 1. On crée une instance unique (un Singleton) accessible de partout
    public static final ModConfig INSTANCE = new ModConfig();

    // 2. On demande à Fabric le bon dossier de sauvegarde (le dossier "config" de Minecraft)
    private static final Path FICHIER = FabricLoader.getInstance().getConfigDir().resolve("xaeroheadtracker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 3. Tes variables de configuration
    public boolean showHeads = true;
    public boolean showNames = true;

    // 4. La méthode pour lire le fichier au lancement
    public void charger() {
        try {
            if (Files.exists(FICHIER)) {
                Reader lecteur = Files.newBufferedReader(FICHIER);
                ModConfig donnees = GSON.fromJson(lecteur, ModConfig.class);

                this.showHeads = donnees.showHeads;
                this.showNames = donnees.showNames;

                lecteur.close();
            } else {
                sauvegarder(); // Si le fichier n'existe pas, on le crée avec les valeurs par défaut
            }
        } catch (Exception e) {
            XaeroHeadTracker.LOGGER.error("Impossible de charger la config", e);
        }
    }

    // 5. La méthode pour écrire dans le fichier (quand on clique sur un bouton)
    public void sauvegarder() {
        try {
            Writer ecrivain = Files.newBufferedWriter(FICHIER);
            GSON.toJson(this, ecrivain);
            ecrivain.close();
        } catch (Exception e) {
            XaeroHeadTracker.LOGGER.error("Impossible de sauvegarder la config", e);
        }
    }


}