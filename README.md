# h-da Plan 2.0

Android-App für den h-da-Stundenplan: du wählst einmal deine Veranstaltungen aus, die App trägt sie in deinen Kalender ein (Google-Konto → Google Kalender), hält sie aktuell und zeigt sie dir in einer eigenen Oberfläche.

## Update einspielen (einmalig etwas aufwendiger)
Die neue Version hat eine feste Signatur. Deshalb lässt sie sich **dieses eine Mal nicht über die alte App installieren**.
1. Alte App auf dem Handy deinstallieren. Deine Kalendertermine bleiben und werden von der neuen App wiedererkannt.
2. Im GitHub-Repository den **Inhalt dieses Ordners** hochladen (Add file → Upload files; gleichnamige Dateien werden ersetzt). Wichtig: auch `app/debug.keystore` und den Ordner `.github` mit hochladen. Repository am besten privat lassen.
3. Tab *Actions* → *Build APK* → fertigen Lauf öffnen → Artefakt `app-debug` herunterladen, entpacken, APK installieren.
4. In der App Kalender-Zugriff erlauben, Kurse unter „Auswahl“ ankreuzen und „Speichern & eintragen“ tippen.

Ab dem nächsten Update reicht es, die APK direkt über die installierte App zu installieren.

## Die Tabs
- **Heute**: nächste Veranstaltung mit Countdown, die kommenden Tage, Pausen dazwischen. Runterziehen = aktualisieren.
- **Woche**: dein eigener Wochenplan (farbig nach Typ, wischen oder Pfeile für andere Wochen, Termin antippen für Details und „Ausblenden“). Daneben der **Original-Plan** der Seite.
- **Auswahl**: Kurse und Zeitfenster ankreuzen (Suche oben, Stift = eigenes Kürzel). Speichern-Knopf unten.
- **Einstellungen**: Pläne (ändern, hinzufügen, entfernen; Auswahl aus einer Liste oder per Link), Kalender, Termin-Titel, Sync-Intervall, Benachrichtigungen, Änderungsprotokoll, Diagnose, Aufräumen.

## Gut zu wissen
- **x- und y-Wochen:** Einträge, die der Plan mit „x, y“ anbietet (z. B. Labore), sind in zwei Auswahlpunkte getrennt: „x-Wochen“ und „y-Wochen“. Bei jedem steht, wann die nächsten Termine sind; in der Wochenansicht steht, ob die Woche x oder y ist. Haken nur bei deiner Woche setzen. Eine frühere Auswahl bleibt für beide Wochen angehakt, bis du eine abwählst.
- Die Kalendertage kommen aus dem x/y-Raster der Seite (inkl. Ferien). Bei Laboren legt der Dozent die echten Termine fest: Einzelne Tage kannst du in der Wochenansicht ausblenden.
- Die Plan-Liste liest die Startseite von splan.eit.h-da.de aus und zeigt nur Pläne mit eindeutigem Namen (Studiengang, Abschluss, Semester), sortiert. Alles andere geht über „Link einfügen“.
- Das Widget fügst du über langes Drücken auf dem Homescreen hinzu („h-da Plan“).
- Die App ändert und löscht nur Termine, die in der Beschreibung `[hdaplan:…]` tragen. Diese Zeile nicht löschen.
- Tipp: Lege im Google Kalender (Web) einen eigenen Kalender „Uni“ an und wähle ihn unter Einstellungen → Kalender.
- Fehler? Einstellungen → „Diagnose kopieren“ und die Infos weiterschicken.
