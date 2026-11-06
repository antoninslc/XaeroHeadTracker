package name.modid.client;

import net.minecraft.world.entity.Entity;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.radar.state.RadarList;

/** Seule classe qui touche la Minimap : n'est chargee que si elle est installee. */
final class RadarMinimap {

	private RadarMinimap() {}

	/**
	 * Vrai si l'entite est dans les listes du radar de la Minimap, donc dessinee par elle sur la carte.
	 * Les listes sont refaites a chaque tick et tiennent deja compte des categories, du radar coupe et des restrictions du serveur.
	 */
	static boolean afficheEntite(Entity entite) {
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		if (session == null) {
			return false;
		}
		for (RadarList liste : session.getRadarSession().getState().getRadarLists()) {
			for (Entity e : liste.getEntities()) {
				if (e == entite) {
					return true;
				}
			}
		}
		return false;
	}
}
