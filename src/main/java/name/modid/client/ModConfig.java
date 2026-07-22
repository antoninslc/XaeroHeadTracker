package name.modid.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths; // Remplacement de FabricLoader

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import name.modid.XaeroHeadTracker;

public class ModConfig {
    public static final ModConfig INSTANCE = new ModConfig();

    // On utilise FMLPaths pour obtenir le dossier config sous NeoForge
    private static final Path FICHIER = FMLPaths.CONFIGDIR.get().resolve("xaeroheadtracker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean showHeads = true;
    public boolean showNames = true;
    public boolean sharePosition = true;

    public void charger() {
        try {
            if (Files.exists(FICHIER)) {
                Reader lecteur = Files.newBufferedReader(FICHIER);
                ModConfig donnees = GSON.fromJson(lecteur, ModConfig.class);

                this.showHeads = donnees.showHeads;
                this.showNames = donnees.showNames;
                this.sharePosition = donnees.sharePosition;

                lecteur.close();
            } else {
                sauvegarder();
            }
        } catch (Exception e) {
            XaeroHeadTracker.LOGGER.error("Impossible de charger la config", e);
        }
    }

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