package name.modid.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.map.graphics.MapRenderHelper;

public class ConfigScreen extends Screen {
    private final Screen ecranPrecedent; // Pour pouvoir retourner sur la carte en quittant

    public ConfigScreen(Screen ecranPrecedent) {
        super(Component.literal("XaeroHeadTracker Configuration"));
        this.ecranPrecedent = ecranPrecedent;
    }

    @Override
    protected void init() {
        int centreX = this.width / 2;
        int centreY = this.height / 2;

        // 1. On calcule la largeur de ton titre en pixels
        int largeurTexte = this.font.width(this.title);

        // 2. On calcule la position X exacte pour qu'il soit au milieu de l'écran
        int positionX = (this.width - largeurTexte) / 2;

        // 3. On crée le widget de texte directement aux bonnes coordonnées !
        this.addRenderableOnly(new net.minecraft.client.gui.components.StringWidget(positionX, 20, largeurTexte, 10, this.title, this.font));

        // Bouton 1 : Activer/Désactiver les Têtes
        this.addRenderableWidget(
                Button.builder(getTexteBoutonTetes(), bouton -> {
                            ModConfig.INSTANCE.showHeads = !ModConfig.INSTANCE.showHeads; // On inverse la valeur
                            ModConfig.INSTANCE.sauvegarder(); // On sauvegarde dans le JSON
                            bouton.setMessage(getTexteBoutonTetes()); // On met à jour le texte
                        })
                        .bounds(centreX - 100, centreY - 40, 200, 20)
                        .build()
        );

        // Bouton 2 : Activer/Désactiver les Noms
        this.addRenderableWidget(
                Button.builder(getTexteBoutonNoms(), bouton -> {
                            ModConfig.INSTANCE.showNames = !ModConfig.INSTANCE.showNames;
                            ModConfig.INSTANCE.sauvegarder();
                            bouton.setMessage(getTexteBoutonNoms());
                        })
                        .bounds(centreX - 100, centreY - 10, 200, 20)
                        .build()
        );

        // Bouton 3 : Retour
        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), bouton -> {
                            this.minecraft.gui.setScreen(this.ecranPrecedent); // On ferme le menu
                        })
                        .bounds(centreX - 100, centreY + 30, 200, 20)
                        .build()
        );
    }

    // Petites méthodes pour générer le texte dynamiquement selon l'état de la config
    private Component getTexteBoutonTetes() {
        return Component.literal("Heads : " + (ModConfig.INSTANCE.showHeads ? "§aYES" : "§cNO"));
    }

    private Component getTexteBoutonNoms() {
        return Component.literal("Names : " + (ModConfig.INSTANCE.showNames ? "§aYES" : "§cNO"));
    }
}