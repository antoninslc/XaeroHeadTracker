package name.modid;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dernieres positions des partageurs deconnectes : gardees 7 jours, sauvegardees dans le monde, envoyees a ceux qui se connectent. */
final class PositionsAbsents {

	// audience null = tout le monde, comme dans XaeroHeadTracker.audiences
	record Absent(String pseudo, double x, double y, double z, String dimension, long depart, String skin, String signature, Set<UUID> audience) {}

	private static final Gson GSON = new Gson();
	private static final Type TYPE = new TypeToken<Map<UUID, Absent>>() {}.getType();
	private static final Map<UUID, Absent> absents = new HashMap<>();
	private static Path fichier;

	private PositionsAbsents() {}

	static void charger(MinecraftServer server) {
		absents.clear();
		fichier = server.getWorldPath(LevelResource.ROOT).resolve("xaeroheadtracker_last_positions.json");
		if (Files.exists(fichier)) {
			try (Reader lecteur = Files.newBufferedReader(fichier)) {
				Map<UUID, Absent> lus = GSON.fromJson(lecteur, TYPE);
				if (lus != null) {
					absents.putAll(lus);
				}
			} catch (IOException | JsonParseException e) {
				XaeroHeadTracker.LOGGER.warn("Lecture impossible de {}", fichier, e);
			}
		}
		purger();
		journal("Positions gardees chargees : {}", absents.size());
	}

	private static void journal(String message, Object valeur) {
		if (!FMLEnvironment.production) {
			XaeroHeadTracker.LOGGER.info("[XHT Dev] " + message, valeur);
		}
	}

	static void ajouter(ServerPlayer joueur, Set<UUID> audience) {
		Property textures = joueur.getGameProfile().getProperties().get("textures").stream().findFirst().orElse(null);
		absents.put(joueur.getUUID(), new Absent(joueur.getName().getString(), joueur.getX(), joueur.getY(), joueur.getZ(),
				joueur.level().dimension().location().toString(), System.currentTimeMillis(),
				textures != null ? textures.getValue() : "",
				textures != null && textures.getSignature() != null ? textures.getSignature() : "",
				audience != null ? new HashSet<>(audience) : null));
		journal("Position gardee de {}", joueur.getName().getString() + (textures != null ? " (avec skin)" : " (sans skin)"));
		sauvegarder();
	}

	static void retirer(UUID uuid) {
		if (absents.remove(uuid) != null) {
			sauvegarder();
		}
	}

	static void vider() {
		if (!absents.isEmpty()) {
			absents.clear();
			sauvegarder();
		}
	}

	static void envoyer(ServerPlayer destinataire) {
		if (!XaeroHeadTracker.CANAL_DERNIERES.isRemotePresent(destinataire.connection.connection)) {
			return;
		}
		purger();
		long maintenant = System.currentTimeMillis();
		absents.forEach((uuid, a) -> {
			if (a.audience() == null || a.audience().contains(destinataire.getUUID())) {
				XaeroHeadTracker.CANAL_DERNIERES.send(PacketDistributor.PLAYER.with(() -> destinataire), new PlayerLastPositionPayload(
						new PlayerPositionPayload(a.pseudo(), uuid, a.x(), a.y(), a.z(), a.dimension()),
						maintenant - a.depart(), a.skin(), a.signature()));
			}
		});
	}

	private static void purger() {
		long limite = System.currentTimeMillis() - XaeroHeadTracker.DUREE_ABSENT;
		if (absents.values().removeIf(a -> a.depart() < limite)) {
			sauvegarder();
		}
	}

	private static void sauvegarder() {
		if (fichier == null) {
			return;
		}
		// Fichier temporaire puis remplacement : un arret brutal ne laisse pas un fichier a moitie ecrit
		Path temporaire = fichier.resolveSibling(fichier.getFileName() + ".tmp");
		try {
			Files.writeString(temporaire, GSON.toJson(absents, TYPE));
			Files.move(temporaire, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			XaeroHeadTracker.LOGGER.warn("Ecriture impossible de {}", fichier, e);
		}
	}
}
