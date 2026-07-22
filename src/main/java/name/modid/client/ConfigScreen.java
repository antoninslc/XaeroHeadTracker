package name.modid.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import name.modid.UpdateShareStatusPayload;

public class ConfigScreen extends Screen {
    private final Screen ecranPrecedent;

    public ConfigScreen(Screen ecranPrecedent) {
        super(Component.translatable("gui.xaeroheadtracker.title"));
        this.ecranPrecedent = ecranPrecedent;
    }

    @Override
    protected void init() {
        int centreX = this.width / 2;
        int centreY = this.height / 2;

        int largeurTexte = this.font.width(this.title);
        int positionX = (this.width - largeurTexte) / 2;

        this.addRenderableOnly(new net.minecraft.client.gui.components.StringWidget(positionX, 20, largeurTexte, 10, this.title, this.font));

        this.addRenderableWidget(
                Button.builder(getTexteBoutonTetes(), bouton -> {
                            ModConfig.INSTANCE.showHeads = !ModConfig.INSTANCE.showHeads;
                            ModConfig.INSTANCE.sauvegarder();
                            bouton.setMessage(getTexteBoutonTetes());
                        })
                        .bounds(centreX - 100, centreY - 40, 200, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(getTexteBoutonNoms(), bouton -> {
                            ModConfig.INSTANCE.showNames = !ModConfig.INSTANCE.showNames;
                            ModConfig.INSTANCE.sauvegarder();
                            bouton.setMessage(getTexteBoutonNoms());
                        })
                        .bounds(centreX - 100, centreY - 10, 200, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(getTexteBoutonPartage(), bouton -> {
                            ModConfig.INSTANCE.sharePosition = !ModConfig.INSTANCE.sharePosition;
                            ModConfig.INSTANCE.sauvegarder();
                            bouton.setMessage(getTexteBoutonPartage());

                            // CORRECTION : Envoi du paquet via la connexion native du client
                            if (this.minecraft != null && this.minecraft.getConnection() != null) {
                                this.minecraft.getConnection().send(new UpdateShareStatusPayload(ModConfig.INSTANCE.sharePosition));
                            }
                        })
                        .bounds(centreX - 100, centreY + 20, 200, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.xaeroheadtracker.back"), bouton -> {
                            this.minecraft.gui.setScreen(this.ecranPrecedent);
                        })
                        .bounds(centreX - 100, centreY + 60, 200, 20)
                        .build()
        );
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