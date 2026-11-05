package name.modid.mixin;

import name.modid.client.XaeroHeadTrackerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

@Mixin(GuiMap.class)
public class GuiMapMixin {
	// Juste avant les boutons de Xaero : leurs survols et cadres sont dessines apres et passent par-dessus les tetes
	@Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lxaero/lib/client/gui/ScreenBase;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void xaeroheadtracker$tetes(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		XaeroHeadTrackerClient.dessinerTetes(Minecraft.getInstance(), (GuiMap) (Object) this, graphics);
	}
}
