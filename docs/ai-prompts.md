Dokumentation der KI-Prompts
Projekt: DEZSYS GK71 — Hochwasser-Frühwarnsystem (REST)  
Werkzeug: Claude (Opus 5.5)  
Zeitraum: September bis Oktober 2026

Phase 1 — Planung, noch kein Code
Prompt 1 — Architektur und Arbeitsplan
> hier ist die angabe fürs schulprojekt (siehe anhang). hab nur ein leeres intellij-projekt.
>
> schreib noch keinen code! mach mir erst:
> 1. einen vorschlag für die paketstruktur (passend zu punkt 8)
> 2. liste aller arbeitsschritte in der richtigen reihenfolge
> 3. zu jedem schritt kurz was dazu, was schwierig werden könnte
>
> begründe auch kurz warum die reihenfolge so sinn macht.
---
Prompt 2 — Datenmodell diskutieren statt bauen
> bevor du die model-klassen baust, erklär mir kurz die entscheidungen:
>
> - record oder class für station und measurement? was ist besser und warum?
> - welcher typ aus java.time für zeitstempel? angabe sagt iso-8601. warum nicht localdatetime?
> - wie mach ich eine liste von stationen in xml? json hat ja arrays, aber xml braucht ein wurzelelement oder?
>
> erstmal nur begründungen erklären, noch keinen code schreiben.
---
Phase 2 — Grundgerüst
Prompt 3 — Projekt-Setup
> erstell mir die build.gradle für spring boot 4.1.1 mit java 21. brauch spring web, validation und jackson für xml.
>
> schreib zu jeder dependency kurz einen kommentar wofür die gut ist. und sag bescheid falls es bei boot 4 fallstricke gibt die bei boot 3 anders waren.
---
Prompt 4 — Model-Klassen und Enums
> jetzt die model-schicht bauen wie eben besprochen: station, location, measurement, warninglevel, stationstatus.
>
> felder wie in der angabe-tabelle, enums genau mit den werten aus der angabe.
>
> schreib bei jedem feld die einheit als javadoc dazu, damit man weiß ob cm oder m gemeint ist.
---
Prompt 5 — Repositories und Thread-Sicherheit
> jetzt die repo-schicht in-memory machen (ohne datenbank).
>
> stationrepository mit mindestens 3 stationen als startdaten und measurementrepository für die historie.
>
> kurze frage vorher: der simulator schreibt später aus einem hintergrund-thread und http-requests lesen gleichzeitig. welche collection-typen brauch ich da und warum reicht ne normale hashmap nicht? erklär das kurz bevor du code schreibst.
---
Prompt 6 — Warnstufenberechnung als eigene Klasse
> punkt 3 aus der angabe: warnstufe serverseitig berechnen.
>
> bau dafür eine eigene klasse warninglevelcalculator, keine methode im service. erklär mir aber vorher kurz warum das besser ist.
>
> und wegen den grenzen: "ab warnwert" heißt inklusive (>=), oder?
---
Phase 3 — Simulation
Prompt 7 — Simulator nach den Regeln der Angabe
> jetzt den simulator aus punkt 2 umsetzen mit allen regeln:
> - wasserstand nie negativ, akku 0-100
> - starker regen hebt den pegel
> - höherer pegel -> höhere durchflussmenge
> - pegel darf sich nicht zufällig ändern zwischen messungen, alter wert muss einfließen
> - niedriger akku -> maintenance
> - keine messwerte für deaktivierte/ausgefallene stationen
> - intervall konfigurierbar über simulation.interval
>
> erklär mir im javadoc kurz die formel für den pegel damit ich das beim abgabegespräch erklären kann.
---
Prompt 8 — Simulation nachmessen
> mir fällt auf dass /api/v1/stations?warningLevel=NORMAL immer leer ist und im webclient alles rot ist.
>
> lass die simulation mal ein paar hundert schritte laufen und mess nach wie sich die warnstufen verteilen. stimmt das modell überhaupt?
---
Prompt 9 — Simulationsparameter konfigurierbar machen
> die konstanten im simulationservice (mean_reversion, rain_to_level, ...) will ich ändern können ohne neu zu kompilieren fürs abgabegespräch.
>
> mach daraus @configurationproperties. erklär mir dabei:
> - warum record und keine klasse mit settern?
> - was passiert wenn ich eine property in der datei vergesse?
---
Phase 4 — REST-Schnittstelle
Prompt 10 — Die fünf Pflicht-Endpunkte
> jetzt die controller machen. erstmal nur die pflicht-endpunkte aus der tabelle, ohne query-parameter.
>
> wichtig: im controller darf laut angabe keine geschäftslogik stehen. halte dich strikt dran und sag mir wo du die grenze ziehst.
---
Prompt 11 — Content Negotiation JSON und XML
> punkt 5 aus der angabe: json und xml über accept-header, json als standard, 406 bei anderen formaten.
>
> zeig mir wie das geht — muss ich im controller zwischen den formaten unterscheiden? und wie krieg ich ne liste von stationen sauber in xml hin? bei mir war das vorhin doppelt verschachtelt (<stations><stations>...).
---
Prompt 12 — Query-Parameter
> jetzt die filter aus punkt 4:
> - /stations: river, isActive, warningLevel
> - /measurements: from, to, limit, warningLevel
>
> drei fragen dazu:
> 1. gehört die filterlogik in den controller oder service? warum?
> 2. isactive als boolean oder Boolean?
> 3. wenn jemand ?warningLevel=HOCH schickt — muss ich das selbst prüfen?
---
Phase 5 — Fehlerbehandlung
Prompt 13 — Zentrale Fehlerbehandlung
> punkt 6 der angabe: zentrale fehlerbehandlung mit @controlleradvice, einheitlicher fehler-body in json und xml, keine stacktraces.
>
> abfangen: unbekannte station, from nach to, ungültige warnstufe, negatives/zu großes limit, kaputtes datum, nicht unterstütztes format.
>
> für limit brauch ich ne ober- und untergrenze — wie mach ich das am besten?
---
Prompt 14 — Fehlerfälle systematisch durchtesten
> geh mal bitte jeden fehlerfall einzeln durch und prüf am laufenden server welcher statuscode wirklich zurückkommt. nicht nur aus der angabe, sondern auch:
> - pfad den es nicht gibt (/api/v1/tippfehler)
> - falsches http-verb (post auf /stations)
>
> zeig mir ne tabelle mit erwartet/tatsächlich.
---
Phase 6 — Warnungen und Statistik
Prompt 15 — Neue Endpunkte, und was sich dabei ändert
> es fehlen noch /api/v1/alerts und die statistik aus punkt 4.
>
> mach das so dass ich danach frage 6 der angabe beantworten kann ("welche stellen des quellcodes muss ich ändern wenn ich einen neuen endpunkt anbiete?"). zeig mir beim bauen genau welche dateien du anfasst und warum.
>
> frage noch: soll /alerts ein eigener controller sein oder ne methode im stationcontroller?
---
Prompt 16 — Statistik an dieselbe Filterung hängen
> die statistik soll die selben filter unterstützen wie die historie (from, to, limit, warningLevel).
>
> wie verhindere ich dass die filterlogik doppelt da ist und beide endpunkte unterschiedliche daten auswerten?
>
> und was liefert die statistik wenn im zeitraum keine messwerte liegen? 404 oder 200?
---
Phase 7 — Webclient
Prompt 17 — Erst die Architektur des Clients besprechen
> punkt 7, der webclient mit html, css und jquery.
>
> problem: er muss die selben daten aus json UND xml anzeigen. wollt erst zwei funktionen schreiben (eine pro format), kommt mir aber falsch vor.
>
> diskutier das kurz mit mir bevor du code schreibst. wie strukturiere ich das damit die ansichten nicht auseinanderlaufen?
---
Prompt 18 — Client implementieren
> gut, bau das so. dazu:
> - tabelle mit den 8 spalten aus der angabe
> - NORMAL grün, WARNING orange, CRITICAL rot, UNKNOWN grau
> - filter für zeitraum und warnstufe
> - umschalter JSON/XML
> - auto-refresh
> - ladeanzeige und fehlermeldungen
>
> worauf muss ich beim xml-parsen mit jquery achten? und die datumsfelder liefern lokalzeit, api will utc — wer rechnet um?
---
Phase 8 — Tests, Dokumentation, Abgabe
Prompt 19 — Tests, die nicht zufällig fehlschlagen
> punkt 9: die 12 geforderten tests.
>
> tests dürfen laut angabe nicht zufällig fehlschlagen. problem: simulator erzeugt ständig neue messwerte, test auf "genau 3 messwerte" wär mal grün und mal rot.
>
> wie löse ich das? und @SpringBootTest oder @WebMvcTest — was passt hier besser und warum?
---
Prompt 20 — OpenAPI
> punkt 10: api-doku mit openapi.
> doku muss endpunkte, parameter, bodies, formate, statuscodes, fehlerantworten und auth enthalten.
>
> hab aber keine auth — wie dokumentiere ich das ehrlich?
---
Prompt 21 — Dokumentation und Commits
> zum schluss:
> 1. readme mit allen punkten aus der abgabeliste: startanleitung, architektur, datenmodell, simulationsregeln, endpunkte, json/xml-beispiele, fehlerbehandlung, tests, konfig, reflexion. für die beispiele bitte echte antworten ausm laufenden system nehmen.
> 2. konfig-beispiel ohne secrets.
> 3. teile meine offenen änderungen in thematische commits auf ("nachvollziehbare git-commits"). prüf dass jeder commit einzeln baut.
---
Prompt 22 — Überarbeitung & Code-Review
> schau nochmal über alles drüber, teste das und überarbeite nochmal meine dokumentationen und so. kommentiere noch notwendige stellen. formatiere das prompt protokoll besser