# Update : choix des joueurs affichés

## Principe
Tous les joueurs sont masqués par défaut. L'utilisateur choisit lui-même les joueurs à afficher sur la carte.

## Écran de sélection
- Cet écran remplace la page de paramètres actuelle : il devient la page de paramètres du mod.
- Mise en page en deux colonnes :
  - Colonne principale : la liste des joueurs (recherche, cochés, autres).
  - Colonne de droite : les réglages existants, dont l'affichage tête/nom et le bouton share.
- Barre de recherche en haut pour filtrer par pseudo.
- Liste des joueurs : à gauche la tête et le pseudo, à droite une case à cocher.
- Section du haut : joueurs cochés (affichés).
- Section du bas : les autres joueurs (masqués).
- Bouton "Afficher les autres joueurs" : bascule simple pour afficher ou masquer d'un coup tous les joueurs non cochés.

## Décisions prises
- Le bouton "Afficher les autres joueurs" est persistant d'une session à l'autre et s'applique aussi aux joueurs qui se connectent après.
- Stockage par UUID (les pseudos peuvent changer).
- Les choix sont propres à chaque serveur.

## Joueurs déconnectés (proposition)
- Les joueurs cochés restent toujours dans la liste, même hors ligne, grisés avec le temps depuis la déconnexion (ex. `< 1 min`, `5 min`). On peut ainsi cocher un ami à l'avance. On stocke donc UUID, dernier pseudo connu et skin pour les joueurs cochés.
- Les joueurs non cochés n'apparaissent que s'ils ont été vus pendant la session (ou récemment), pour éviter une liste qui grossit sans fin.

## Points restants
- Filtrage appliqué en un seul endroit avant l'affichage, pour être repris facilement sur chaque branche (`fabric/<mc>`, `neoforge/<mc>`).
- Adapter les commandes `/xht simulate ...` pour tester le filtre avec `BotTest`.
- Migration : message affiché à la première ouverture après la mise à jour, pour expliquer que tous les joueurs sont désormais masqués par défaut et où les choisir (texte exact et affichage unique à définir).


---

# Guide de portage vers les autres versions et loaders

Implémentation de référence : branche `v2/fabric/26.3` (commits `ecbaef0` à `7d24679`).
Pour chaque cible, créer `v2/<loader>/<mc>` depuis la branche existante, puis reprendre les étapes ci-dessous.

## Fichiers à reprendre
| Fichier | Rôle | Portabilité |
|---|---|---|
| `src/client/java/name/modid/client/PlayerVisibilityConfig.java` | Stockage par serveur et par UUID (JSON `config/xaeroheadtracker_players.json`) | Copie quasi directe (Gson, pas d'API Minecraft hormis le dossier config et l'adresse du serveur) |
| `ConfigScreen.java` | Écran 2 colonnes, recherche, liste, cases | À réécrire selon l'API GUI de la version |
| `XaeroHeadTrackerClient.java` (boucle de rendu) | Filtre et affichage nom/statut | Reprendre la logique, adapter les appels de dessin |
| `lang/fr_fr.json`, `lang/en_us.json` | Clés de texte | Copie directe |

## API de `PlayerVisibilityConfig` (inchangée d'une version à l'autre)
`definirServeurCourant`, `estVisible`, `estCoche`, `definirCoche`, `mettreAJourPseudo`, `getAfficherAutres`, `definirAfficherAutres`, `getJoueursCoches`, `getPseudoConnu`, `marquerMigrationAffichee`, `charger`, `sauvegarder`, champ `migrationNoticeShown`.
Seul point dépendant de la version : récupérer l'identifiant du serveur courant (adresse) et appeler `definirServeurCourant` à la connexion.

## Étapes de portage
1. Appliquer d'abord le workflow dev de `AGENTS.md` (quickPlay, commandes `/xht simulate`).
2. Copier `PlayerVisibilityConfig.java` et les fichiers de langue. Appeler `definirServeurCourant` à la connexion au serveur.
3. Filtre dans la boucle de rendu de la carte : pour chaque joueur, appeler `mettreAJourPseudo(uuid, pseudo)` puis ignorer le joueur si `!estVisible(uuid)`. Un seul endroit à modifier.
4. Rendu nom/statut (dans la même boucle) :
   - Sans tête (`avecTete` faux) : le pseudo est centré sur la position du joueur (`drawY - 4`), le statut (Live, `< 1 min`, `N min`) est dessiné juste dessous.
   - Tête seule (noms désactivés) : le statut reste affiché sous la tête.
   - Condition du bloc statut : `showNames || avecTete`. Variables : `avecTete`, `basBloc`.
5. `ConfigScreen` : reprendre la structure (colonne joueurs avec scroll et recherche, cochés en haut, autres en bas, cochés hors ligne grisés ; colonne droite 38 % de l'écran bornée à 110-180 px avec les réglages existants, libellés explicites et infobulles). Clic sur une ligne = cocher.
6. Commandes dev : `/xht simulate live|disconnect|old|clear` et `/xht simulate many <1-100>` (bots Bot1..BotN en cercle, supprimés par `clear`).
7. Vérifier `gradlew build`, tester en jeu, committer.

## Pièges connus de l'API (26.3), à re-vérifier sur chaque version
- Rendu : `GuiGraphicsExtractor` (`text`, `centeredText`, `fill`, `blit(RenderPipelines.GUI_TEXTURED, ...)`, `enableScissor`). Les versions plus anciennes utilisent `GuiGraphics` (`drawString`, `fill`, `blit`, `enableScissor`).
- Screen : `extractRenderState`, `mouseClicked(MouseButtonEvent, boolean)`, `mouseScrolled`. Les signatures changent selon la version (`render`, `mouseClicked(double, double, int)`).
- Inspecter l'API avec `javap -cp <jar minecraft du loom-cache> <classe>`.
- NeoForge : les commandes client passent par `RegisterClientCommandsEvent`, et l'écran de config par le point d'extension de config du mod.

## Reste à faire
- Message de migration à la première ouverture (`migrationNoticeShown`), tolérable aussi pour une installation neuve.
- Débordement de texte sur très petits écrans (colonne droite à 110 px).
- Portage : `v2/neoforge/26.3`, puis les autres versions.
- Optionnel : commande dev `/xht reload`.
