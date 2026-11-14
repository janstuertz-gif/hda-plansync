# h-da Plan-Sync

Android-App: liest einen h-da-Stundenplan (z. B. `https://splan.eit.h-da.de/s/S_EIT_BA_1EIT.htm`), du kreuzt einmal an, was du brauchst, und die App trägt die Termine in einen Kalender auf deinem Handy ein (Google-Konto → erscheint im Google Kalender). Alle 6 Stunden wird der Plan neu gelesen: geänderte Räume, Dozenten und Zeiten werden aktualisiert, gestrichene Termine gelöscht.

## App bauen

**Mit Android Studio (empfohlen)**
1. Android Studio installieren, `File > Open` → diesen Ordner wählen, Gradle-Sync abwarten (Fehlende SDK-Pakete lässt du von Android Studio installieren).
2. Handy per USB anschließen (Entwickleroptionen → USB-Debugging) und ▶ drücken.
   Alternativ: `Build > Build APK(s)`, die Datei `app/build/outputs/apk/debug/app-debug.apk` aufs Handy kopieren und installieren (unbekannte Quellen erlauben).

**Ohne Android Studio (GitHub)**
1. Neues GitHub-Repository anlegen und den Inhalt dieses Ordners hochladen (inkl. `.github`).
2. Tab *Actions* → *Build APK* → fertigen Lauf öffnen → Artefakt `app-debug` herunterladen, entpacken, APK aufs Handy.

## Benutzen
1. Link prüfen/einfügen → **Stundenplan laden**.
2. Ankreuzen, was du brauchst. Bei Laboren/Übungen gibt es oft mehrere Gruppen – nur deine wählen.
3. Kalender wählen → **Speichern & in Kalender eintragen**.
4. Fertig. Danach synchronisiert die App selbst im Hintergrund.

**Neues Semester:** neuen Link einfügen → laden → neu ankreuzen → speichern. Alte, noch kommende Termine werden dabei entfernt.

## Gut zu wissen
- Tipp: Lege im Google Kalender (Web) einen eigenen Kalender "Uni" an und wähle ihn in der App. So kannst du ihn ausblenden und umfärben.
- Die Kalendertage kommen aus dem x/y-Raster der Seite (inkl. Weihnachtsferien), du musst keine Semesterdaten eingeben.
- Bei Laboren legt der Dozent die echten Termine fest. Im Kalender stehen alle Raster-Termine – überzählige ignorierst du.
- Die App ändert/löscht nur Termine, die in der Beschreibung `[hdaplan:…]` tragen. Diese Zeile nicht löschen.
- Vergangene Termine bleiben unberührt.
- Leere Liste oder Fehler? Der Parser wurde nach der Textansicht der Seite geschrieben, nicht nach dem echten Quelltext, und konnte nicht getestet werden. Fehlermeldung oder Seitenquelltext an Claude schicken.
