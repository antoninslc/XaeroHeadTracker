package name.modid.mixin;

import name.modid.client.XaeroHeadTrackerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.lib.client.gui.ScreenBase;
import xaero.map.gui.GuiMap;

// Cible ScreenBase et non GuiMap : FML recalcule les frames de la classe modifiee et GuiMap.extractRenderState depasse sa limite (crash au lancement)
@Mixin(ScreenBase.class)
public class GuiMapMixin {
	// Juste avant les boutons de Xaero : leurs survols et cadres sont dessines apres et passent par-dessus les tetes
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void xaeroheadtracker$tetes(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if ((Object) this instanceof GuiMap carte) {
			XaeroHeadTrackerClient.dessinerTetes(Minecraft.getInstance(), carte, graphics);
		}
	}
}
