package name.modid;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod(XaeroHeadTracker.MOD_ID)
public class XaeroHeadTracker {
	public static final String MOD_ID = "xaeroheadtracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String VERSION_PROTOCOLE = "1";
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(MOD_ID, "main"), () -> VERSION_PROTOCOLE, VERSION_PROTOCOLE::equals, VERSION_PROTOCOLE::equals);

	private int compteurTemps = 0;
	public static final Set<UUID> joueursCaches = new HashSet<>();

	public XaeroHeadTracker() {
		registerNetworking();

		// Enregistrement de la boucle (tick) sur le bus global du jeu
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void registerNetworking() {
		// Reception du paquet client -> serveur
		CHANNEL.registerMessage(0, UpdateShareStatusPayload.class, UpdateShareStatusPayload::encode, UpdateShareStatusPayload::decode,
				(payload, ctx) -> {
					ctx.get().enqueueWork(() -> {
						ServerPlayer joueur = ctx.get().getSender();
						if (joueur == null) {
							return;
						}
						if (payload.sharePosition()) {
							joueursCaches.remove(joueur.getUUID());
						} else {
							joueursCaches.add(joueur.getUUID());
						}
					});
					ctx.get().setPacketHandled(true);
				});

		// Declaration du paquet serveur -> client
		CHANNEL.registerMessage(1, PlayerPositionPayload.class, PlayerPositionPayload::encode, PlayerPositionPayload::decode,
				(payload, ctx) -> {
					DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> name.modid.client.XaeroHeadTrackerClient.recevoirPositions(payload));
					ctx.get().setPacketHandled(true);
				});
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		compteurTemps++;
		if (compteurTemps >= 20) {
			compteurTemps = 0;

			for (ServerPlayer joueur : event.getServer().getPlayerList().getPlayers()) {
				if (joueursCaches.contains(joueur.getUUID())) {
					continue;
				}

				PlayerPositionPayload paquet = new PlayerPositionPayload(joueur.getName().getString(), joueur.getUUID(), joueur.getX(), joueur.getY(), joueur.getZ());
				CHANNEL.send(PacketDistributor.ALL.noArg(), paquet);
			}
		}
	}
}
