/*
 * Webclient des Hochwasser-Fruehwarnsystems (Angabe Punkt 7).
 *
 * ARCHITEKTUR
 * -----------
 * Der Client muss dieselben Daten aus JSON *und* aus XML darstellen. Die
 * naheliegende Loesung - zwei Render-Pfade - fuehrt dazu, dass jede Aenderung
 * an der Tabelle zweimal gemacht werden muss und die beiden Ansichten
 * auseinanderlaufen. Stattdessen gibt es hier drei getrennte Schichten:
 *
 *   1. api.*         holt Daten (kennt HTTP und den Accept-Header)
 *   2. parse.*       formt JSON *oder* XML in immer dasselbe JS-Objekt um
 *   3. render.*      zeichnet aus diesem Objekt die Oberflaeche
 *
 * Nur Schicht 2 weiss ueberhaupt, dass es zwei Formate gibt. Schicht 3 sieht
 * nie ein XML-Dokument. Deshalb ist garantiert, dass JSON und XML dieselbe
 * Darstellung ergeben - es gibt nur eine Darstellung.
 */

/* global jQuery */
(function ($) {
    'use strict';

    if (!$) { return; }   // jQuery nicht geladen - index.html zeigt den Hinweis

    var BASE = '/api/v1';

    /* Laufende Abrufe. Verhindert, dass sich der Lade-Indikator bei
       mehreren parallelen Requests zu frueh abschaltet. */
    var pending = 0;

    var timer = null;

    /* ===================================================================
     * Hilfsfunktionen
     * =================================================================== */

    /** Das aktuell gewaehlte Format ('json' oder 'xml'). */
    function format() {
        return $('input[name="format"]:checked').val();
    }

    /**
     * Textinhalt eines *direkten* Kindelements.
     *
     * children() und nicht find(): find() wuerde auch in <location> hinein
     * suchen. Bei <station> ist das harmlos, aber sobald zwei Ebenen dasselbe
     * Elementnamen benutzen, liest find() den falschen Wert.
     */
    function text($el, name) {
        return $el.children(name).first().text();
    }

    function num($el, name) {
        var raw = text($el, name);
        return raw === '' ? null : Number(raw);
    }

    /** Zahl mit fester Nachkommastelle, null wird zu einem Gedankenstrich. */
    function fmtNum(value, digits) {
        if (value === null || value === undefined || isNaN(value)) { return '–'; }
        return value.toLocaleString('de-AT', {
            minimumFractionDigits: digits,
            maximumFractionDigits: digits
        });
    }

    /** ISO-Zeitstempel als lesbare lokale Zeit. */
    function fmtTime(iso) {
        if (!iso) { return '–'; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return iso; }
        return d.toLocaleString('de-AT', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit'
        });
    }

    /** Schutz gegen HTML-Injection in Stationsnamen und Fehlermeldungen. */
    function esc(value) {
        return $('<div>').text(value === null || value === undefined ? '' : value).html();
    }

    function badge(level) {
        var l = level || 'UNKNOWN';
        return '<span class="badge wl-' + esc(l) + '">' + esc(l) + '</span>';
    }

    /**
     * Wert eines datetime-local-Feldes als ISO-8601 in UTC.
     *
     * Das Feld liefert lokale Zeit ohne Zeitzone ("2026-05-01T08:00"). Die API
     * erwartet UTC. new Date(...) interpretiert den Wert als Lokalzeit,
     * toISOString() rechnet nach UTC um - genau die gewuenschte Umrechnung.
     */
    function toIsoUtc(localValue) {
        if (!localValue) { return null; }
        var d = new Date(localValue);
        return isNaN(d.getTime()) ? null : d.toISOString();
    }

    /* ===================================================================
     * Schicht 1: HTTP
     * =================================================================== */

    var api = {
        /**
         * Ein GET gegen die API im gerade gewaehlten Format.
         *
         * dataType steuert, wie jQuery die Antwort auspackt: 'xml' ergibt ein
         * XMLDocument, 'json' ein JS-Objekt. Der Accept-Header sagt dem Server,
         * was er schicken soll - beides muss zusammenpassen.
         */
        get: function (path, params) {
            var fmt = format();
            showLoading(true);

            return $.ajax({
                url: BASE + path,
                data: params || {},
                dataType: fmt === 'xml' ? 'xml' : 'json',
                headers: { Accept: fmt === 'xml' ? 'application/xml' : 'application/json' },
                cache: false
            }).always(function () {
                showLoading(false);
            });
        }
    };

    /**
     * Fehlermeldung aus einer Fehlerantwort herausholen.
     *
     * Der Server liefert bei Fehlern denselben ApiError-Body - in JSON oder in
     * XML, je nach Accept-Header. Beide Varianten muessen gelesen werden, damit
     * der Benutzer auch im XML-Modus die fachliche Meldung sieht und nicht nur
     * eine Statusnummer. Einzige Ausnahme: 406 kommt immer als JSON, weil der
     * Server das verlangte Format ja gerade nicht erzeugen kann.
     */
    function apiErrorMessage(xhr) {
        if (!xhr || !xhr.status) {
            return 'Der Server ist nicht erreichbar. Läuft die Anwendung?';
        }

        var raw = xhr.responseText || '';

        try {
            var json = JSON.parse(raw);
            if (json && json.message) { return json.message + ' (HTTP ' + xhr.status + ')'; }
        } catch (ignored) { /* kein JSON - weiter mit XML */ }

        try {
            var msg = $($.parseXML(raw)).find('message').first().text();
            if (msg) { return msg + ' (HTTP ' + xhr.status + ')'; }
        } catch (ignored2) { /* auch kein XML */ }

        return 'HTTP ' + xhr.status + ' ' + (xhr.statusText || '');
    }

    /* ===================================================================
     * Schicht 2: JSON oder XML -> immer dasselbe Objekt
     * =================================================================== */

    var parse = {

        stations: function (data) {
            if (format() === 'json') { return data.stations || []; }

            return $(data).find('station').map(function () {
                var $s = $(this);
                var $loc = $s.children('location');
                return {
                    id: text($s, 'id'),
                    name: text($s, 'name'),
                    river: text($s, 'river'),
                    location: {
                        latitude: num($loc, 'latitude'),
                        longitude: num($loc, 'longitude')
                    },
                    normalWaterLevel: num($s, 'normalWaterLevel'),
                    warningWaterLevel: num($s, 'warningWaterLevel'),
                    criticalWaterLevel: num($s, 'criticalWaterLevel'),
                    // XML kennt keinen Boolean-Typ: "true"/"false" sind Text.
                    isActive: text($s, 'isActive') === 'true'
                };
            }).get();
        },

        measurement: function (data) {
            if (format() === 'json') { return data; }
            return parse.oneMeasurement($(data).find('measurement').first());
        },

        measurements: function (data) {
            if (format() === 'json') { return data.measurements || []; }
            return $(data).find('measurement').map(function () {
                return parse.oneMeasurement($(this));
            }).get();
        },

        oneMeasurement: function ($m) {
            return {
                stationId: text($m, 'stationId'),
                timestamp: text($m, 'timestamp'),
                waterLevel: num($m, 'waterLevel'),
                flowRate: num($m, 'flowRate'),
                rainfall: num($m, 'rainfall'),
                temperature: num($m, 'temperature'),
                batteryLevel: num($m, 'batteryLevel'),
                status: text($m, 'status'),
                warningLevel: text($m, 'warningLevel')
            };
        },

        alerts: function (data) {
            if (format() === 'json') { return data.alerts || []; }
            return $(data).find('alert').map(function () {
                var $a = $(this);
                return {
                    stationId: text($a, 'stationId'),
                    stationName: text($a, 'stationName'),
                    river: text($a, 'river'),
                    warningLevel: text($a, 'warningLevel'),
                    waterLevel: num($a, 'waterLevel'),
                    warningWaterLevel: num($a, 'warningWaterLevel'),
                    criticalWaterLevel: num($a, 'criticalWaterLevel'),
                    timestamp: text($a, 'timestamp')
                };
            }).get();
        },

        statistics: function (data) {
            if (format() === 'json') { return data; }
            var $s = $(data).find('statistics').first();
            return {
                stationId: text($s, 'stationId'),
                measurementCount: num($s, 'measurementCount'),
                minWaterLevel: num($s, 'minWaterLevel'),
                maxWaterLevel: num($s, 'maxWaterLevel'),
                avgWaterLevel: num($s, 'avgWaterLevel'),
                avgFlowRate: num($s, 'avgFlowRate'),
                totalRainfall: num($s, 'totalRainfall'),
                warningCount: num($s, 'warningCount'),
                criticalCount: num($s, 'criticalCount')
            };
        }
    };

    /* ===================================================================
     * Schicht 3: Darstellung
     * =================================================================== */

    function showLoading(starting) {
        pending += starting ? 1 : -1;
        if (pending < 0) { pending = 0; }
        $('#loading').prop('hidden', pending === 0);
    }

    function showError(message) {
        $('#errorText').text(message);
        $('#errorBox').prop('hidden', false);
    }

    function clearError() {
        $('#errorBox').prop('hidden', true);
    }

    function stamp() {
        $('#lastUpdate').text('Stand ' + new Date().toLocaleTimeString('de-AT'));
        $('#formatNote').text('Format: ' + format().toUpperCase());
    }

    var render = {

        stationOptions: function (stations) {
            var $sel = $('#stationSelect');
            var previous = $sel.val();

            $sel.empty();
            stations.forEach(function (s) {
                $sel.append($('<option>')
                    .val(s.id)
                    .text(s.id + ' – ' + s.name + (s.isActive ? '' : ' (inaktiv)')));
            });

            // Auswahl beibehalten, solange die Station noch in der Liste ist.
            // Ohne das wuerde jede automatische Aktualisierung den Benutzer
            // zurueck auf die erste Station werfen.
            if (previous && $sel.find('option[value="' + previous + '"]').length) {
                $sel.val(previous);
            }
            return $sel.val();
        },

        riverOptions: function (stations) {
            var rivers = [];
            stations.forEach(function (s) {
                if (s.river && rivers.indexOf(s.river) === -1) { rivers.push(s.river); }
            });
            rivers.sort();

            ['#alertRiver', '#stationRiver'].forEach(function (sel) {
                var $s = $(sel);
                var keep = $s.val();
                $s.find('option:not(:first)').remove();
                rivers.forEach(function (r) {
                    $s.append($('<option>').val(r).text(r));
                });
                if (keep) { $s.val(keep); }
            });
        },

        stationInfo: function (station) {
            if (!station) { $('#stationInfo').empty(); return; }
            $('#stationInfo').html(
                '<b>' + esc(station.name) + '</b> am ' + esc(station.river) + ' · '
                + 'Position ' + fmtNum(station.location.latitude, 4) + ', '
                + fmtNum(station.location.longitude, 4) + ' · '
                + 'Schwellwerte: normal ' + fmtNum(station.normalWaterLevel, 0)
                + ' / Warnung ' + fmtNum(station.warningWaterLevel, 0)
                + ' / kritisch ' + fmtNum(station.criticalWaterLevel, 0) + ' cm · '
                + (station.isActive ? 'in Betrieb' : '<b>außer Betrieb</b>')
            );
        },

        latest: function (m) {
            var metrics = [
                ['Wasserstand', fmtNum(m.waterLevel, 1), 'cm'],
                ['Durchflussmenge', fmtNum(m.flowRate, 1), 'm³/s'],
                ['Niederschlag', fmtNum(m.rainfall, 1), 'mm/h'],
                ['Temperatur', fmtNum(m.temperature, 1), '°C'],
                ['Akkustand', fmtNum(m.batteryLevel, 0), '%'],
                ['Status', esc(m.status), ''],
                ['Warnstufe', badge(m.warningLevel), ''],
                ['Zeitpunkt', fmtTime(m.timestamp), '']
            ];

            $('#latestBox').html(metrics.map(function (row) {
                return '<div class="metric"><div class="label">' + row[0] + '</div>'
                    + '<div class="value">' + row[1]
                    + (row[2] ? ' <span class="unit">' + row[2] + '</span>' : '')
                    + '</div></div>';
            }).join(''));
        },

        latestMissing: function (message) {
            $('#latestBox').html('<p class="muted">' + esc(message) + '</p>');
        },

        alerts: function (alerts) {
            if (!alerts.length) {
                $('#alertList').html('<p class="muted">Keine Warnungen für diese Auswahl.</p>');
                return;
            }

            $('#alertList').html(alerts.map(function (a) {
                return '<div class="alert row-' + esc(a.warningLevel) + '">'
                    + '<div class="top"><span class="name">' + esc(a.stationName) + '</span>'
                    + badge(a.warningLevel) + '</div>'
                    + '<dl>'
                    + '<dt>Station</dt><dd>' + esc(a.stationId) + ' · ' + esc(a.river) + '</dd>'
                    + '<dt>Pegel</dt><dd>' + fmtNum(a.waterLevel, 1) + ' cm '
                    + '<span class="muted">(Warnung ' + fmtNum(a.warningWaterLevel, 0)
                    + ' / kritisch ' + fmtNum(a.criticalWaterLevel, 0) + ')</span></dd>'
                    + '<dt>Zeitpunkt</dt><dd>' + fmtTime(a.timestamp) + '</dd>'
                    + '</dl></div>';
            }).join(''));
        },

        statistics: function (s) {
            if (!s || !s.measurementCount) {
                $('#statsBox').html('<p class="muted">Keine Messwerte in dieser Auswahl – '
                    + 'keine Statistik berechenbar.</p>');
                return;
            }

            var stats = [
                ['Messungen', s.measurementCount, ''],
                ['Pegel min', fmtNum(s.minWaterLevel, 1), 'cm'],
                ['Pegel Ø', fmtNum(s.avgWaterLevel, 1), 'cm'],
                ['Pegel max', fmtNum(s.maxWaterLevel, 1), 'cm'],
                ['Durchfluss Ø', fmtNum(s.avgFlowRate, 1), 'm³/s'],
                ['Niederschlag Σ', fmtNum(s.totalRainfall, 1), 'mm/h'],
                ['Warnungen', s.warningCount, ''],
                ['davon kritisch', s.criticalCount, '']
            ];

            $('#statsBox').html(stats.map(function (row) {
                return '<div class="stat">' + row[0] + '<b>' + row[1]
                    + (row[2] ? ' <span class="muted">' + row[2] + '</span>' : '')
                    + '</b></div>';
            }).join(''));
        },

        history: function (measurements, stationName) {
            var $body = $('#historyBody');

            if (!measurements.length) {
                $body.empty();
                $('#historyEmpty').prop('hidden', false);
                return;
            }
            $('#historyEmpty').prop('hidden', true);

            // Neueste zuerst: der Server liefert chronologisch, in einer
            // Monitoring-Tabelle will man aber das Aktuellste oben sehen.
            var rows = measurements.slice().reverse().map(function (m) {
                return '<tr class="row-' + esc(m.warningLevel) + '">'
                    + '<td>' + esc(m.stationId)
                    + (stationName ? ' <span class="muted">' + esc(stationName) + '</span>' : '')
                    + '</td>'
                    + '<td>' + fmtTime(m.timestamp) + '</td>'
                    + '<td class="num">' + fmtNum(m.waterLevel, 1) + '</td>'
                    + '<td class="num">' + fmtNum(m.flowRate, 1) + '</td>'
                    + '<td class="num">' + fmtNum(m.rainfall, 1) + '</td>'
                    + '<td class="num">' + fmtNum(m.batteryLevel, 0) + '</td>'
                    + '<td>' + esc(m.status) + '</td>'
                    + '<td>' + badge(m.warningLevel) + '</td>'
                    + '</tr>';
            });

            $body.html(rows.join(''));
        }
    };

    /* ===================================================================
     * Abläufe
     * =================================================================== */

    /** Zuletzt geladene Stationen, damit Name und Schwellwerte verfuegbar sind. */
    var stationCache = [];

    function loadStations() {
        var params = {};
        if ($('#stationRiver').val()) { params.river = $('#stationRiver').val(); }
        if ($('#onlyActive').is(':checked')) { params.isActive = true; }

        return api.get('/stations', params)
            .done(function (data) {
                stationCache = parse.stations(data);
                render.riverOptions(stationCache);
                render.stationOptions(stationCache);
                clearError();
                stamp();
            })
            .fail(function (xhr) {
                showError('Stationen konnten nicht geladen werden. ' + apiErrorMessage(xhr));
            });
    }

    function selectedStation() {
        var id = $('#stationSelect').val();
        return stationCache.filter(function (s) { return s.id === id; })[0] || null;
    }

    function loadLatest() {
        var id = $('#stationSelect').val();
        if (!id) { return $.Deferred().resolve().promise(); }

        render.stationInfo(selectedStation());

        return api.get('/stations/' + encodeURIComponent(id) + '/measurements/latest')
            .done(function (data) {
                render.latest(parse.measurement(data));
                stamp();
            })
            .fail(function (xhr) {
                // 404 heisst hier: die Station hat noch nie gemeldet. Das ist
                // ein normaler Zustand (inaktive Station), kein Fehler der
                // Anwendung - deshalb nur im Panel und nicht im Fehlerbalken.
                if (xhr.status === 404) {
                    render.latestMissing('Diese Station hat noch keinen Messwert geliefert.');
                } else {
                    render.latestMissing('–');
                    showError('Aktueller Messwert nicht abrufbar. ' + apiErrorMessage(xhr));
                }
            });
    }

    /** Die Filter der Historie als Query-Parameter. */
    function historyParams() {
        var params = {};

        var from = toIsoUtc($('#from').val());
        var to = toIsoUtc($('#to').val());
        if (from) { params.from = from; }
        if (to) { params.to = to; }

        if ($('#historyLevel').val()) { params.warningLevel = $('#historyLevel').val(); }

        var limit = $('#limit').val();
        if (limit) { params.limit = limit; }

        return params;
    }

    function loadHistory() {
        var id = $('#stationSelect').val();
        if (!id) { return $.Deferred().resolve().promise(); }

        var station = selectedStation();
        var params = historyParams();
        var path = '/stations/' + encodeURIComponent(id);

        var history = api.get(path + '/measurements', params)
            .done(function (data) {
                render.history(parse.measurements(data), station ? station.name : '');
                stamp();
            })
            .fail(function (xhr) {
                $('#historyBody').empty();
                $('#historyEmpty').prop('hidden', false);
                showError('Historie nicht abrufbar. ' + apiErrorMessage(xhr));
            });

        // Statistik ueber genau dieselben Filter - der Server rechnet, der
        // Client zeigt nur. Keine Kennzahl wird hier nachgerechnet.
        var stats = api.get(path + '/statistics', params)
            .done(function (data) { render.statistics(parse.statistics(data)); })
            .fail(function () { $('#statsBox').empty(); });

        return $.when(history, stats);
    }

    function loadAlerts() {
        var params = {};
        if ($('#alertLevel').val()) { params.warningLevel = $('#alertLevel').val(); }
        if ($('#alertRiver').val()) { params.river = $('#alertRiver').val(); }

        return api.get('/alerts', params)
            .done(function (data) {
                render.alerts(parse.alerts(data));
                stamp();
            })
            .fail(function (xhr) {
                showError('Warnungen nicht abrufbar. ' + apiErrorMessage(xhr));
            });
    }

    /** Alles neu laden - benutzt vom Autorefresh und beim Formatwechsel. */
    function refreshAll() {
        return loadStations().then(function () {
            return $.when(loadAlerts(), loadLatest(), loadHistory());
        });
    }

    /* ===================================================================
     * Autorefresh
     * =================================================================== */

    function restartTimer() {
        if (timer) { clearInterval(timer); timer = null; }

        if (!$('#autoRefresh').is(':checked')) { return; }

        var seconds = Number($('#refreshInterval').val());
        timer = setInterval(function () {
            // Kein neuer Durchlauf, solange noch Requests offen sind. Bei einem
            // langsamen Server wuerden sich sonst Abrufe stapeln.
            if (pending === 0) { refreshAll(); }
        }, seconds * 1000);
    }

    /* ===================================================================
     * Verdrahtung
     * =================================================================== */

    $(function () {

        $('#errorClose').on('click', clearError);

        // Formatwechsel laedt alles neu - erst dadurch ist sichtbar, dass
        // beide Formate dieselbe Darstellung ergeben.
        $('input[name="format"]').on('change', function () {
            clearError();
            refreshAll();
        });

        $('#stationSelect').on('change', function () {
            render.stationInfo(selectedStation());
            loadLatest();
            loadHistory();
        });

        $('#stationRiver, #onlyActive').on('change', function () {
            loadStations().then(function () {
                loadLatest();
                loadHistory();
            });
        });

        $('#alertLevel, #alertRiver').on('change', loadAlerts);

        $('#historyFilter').on('submit', function (event) {
            event.preventDefault();   // sonst laedt der Browser die Seite neu
            clearError();
            loadHistory();
        });

        $('#resetFilter').on('click', function () {
            $('#from, #to').val('');
            $('#historyLevel').val('');
            $('#limit').val(25);
            clearError();
            loadHistory();
        });

        $('#autoRefresh, #refreshInterval').on('change', restartTimer);

        refreshAll();
        restartTimer();
    });

}(window.jQuery));
