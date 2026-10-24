package name.modid;

import net.minecraft.network.FriendlyByteBuf;

public record UpdateShareStatusPayload(boolean sharePosition) {

    public static void encode(UpdateShareStatusPayload p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.sharePosition());
    }

    public static UpdateShareStatusPayload decode(FriendlyByteBuf buf) {
        return new UpdateShareStatusPayload(buf.readBoolean());
    }
}
