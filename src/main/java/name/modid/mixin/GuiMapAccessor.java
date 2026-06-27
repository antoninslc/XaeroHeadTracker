package name.modid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.gui.GuiMap;

@Mixin(GuiMap.class)
public interface GuiMapAccessor {
    @Accessor("cameraX")
    double getCameraX();

    @Accessor("cameraZ")
    double getCameraZ();

    @Accessor("scale")
    double getScale();

    @Accessor("screenScale")
    double getScreenScale();
}