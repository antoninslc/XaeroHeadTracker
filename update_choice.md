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
