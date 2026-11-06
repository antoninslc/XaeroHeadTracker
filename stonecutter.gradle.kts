plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

// Construit les jars de toutes les versions
tasks.register("chiseledBuild") {
    group = "project"
    dependsOn(stonecutter.tasks.named("build").map { it.values })
}

// Simples renommages : src/ garde le nom de 26.x, les versions plus anciennes recoivent l'ancien nom.
// Stonecutter les applique dans les deux sens : le texte de droite doit rester unique dans src/.
stonecutter parameters {
    replacements {
        string(current.parsed < "26") {
            replace("GuiGraphicsExtractor", "GuiGraphics")
            replace("extractBackground", "renderBackground")
            replace(".extractRenderState(", ".render(")
            replace("void extractRenderState(", "void render(")
            replace("method = \"extractRenderState\"", "method = \"render\"")
            replace(";extractRenderState(L", ";render(L")
            replace("this.minecraft.gui.setScreen", "this.minecraft.setScreen")
            replace("client.gui.setScreen", "client.setScreen")
            replace("client.gui.screen() == null", "client.screen == null")
            replace("client.gui.screen().getClass()", "client.screen.getClass()")
            replace("ConfigScreen(client.gui.screen())", "ConfigScreen(client.screen)")
            replace("graphics.centeredText", "graphics.drawCenteredString")
            replace("graphics.text", "graphics.drawString")
            replace("ClientCommands", "ClientCommandManager")
            replace("api.client.keymapping.v1.KeyMappingHelper", "api.client.keybinding.v1.KeyBindingHelper")
            replace("KeyMappingHelper.registerKeyMapping", "KeyBindingHelper.registerKeyBinding")
            replace("PayloadTypeRegistry.clientboundPlay", "PayloadTypeRegistry.playS2C")
            replace("PayloadTypeRegistry.serverboundPlay", "PayloadTypeRegistry.playC2S")
            replace("ScreenEvents.afterExtract", "ScreenEvents.afterRender")
            replace("getServer().getGameRules", "getServer().overworld().getGameRules")
            replace("server.getGameRules", "server.overworld().getGameRules")
        }
        string(current.parsed < "1.21.11") {
            replace("Identifier", "ResourceLocation")
            replace(".identifier()", ".location()")
            replace("getProfile().id()", "getProfile().getId()")
            replace("getProfile().name()", "getProfile().getName()")
            replace(".body().texturePath()", ".texture()")
            replace(".get(PARTAGE_AUTORISE)", ".getBoolean(PARTAGE_AUTORISE)")
            replace("pose().pushMatrix()", "pose().pushPose()")
            replace("pose().popMatrix()", "pose().popPose()")
            replace("scale(echelle, echelle)", "scale(echelle, echelle, 1.0F)")
            replace("xaero/lib/client/gui/ScreenBase", "xaero/map/gui/ScreenBase")
        }
    }
}
