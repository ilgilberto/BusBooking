package info.mandarini.busbooking;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.List;

import info.mandarini.busbooking.persistence.entities.Fermata;

/**
 * Mappa nativa delle fermate vicine. I dati arrivano dall'elenco esistente:
 * non effettua una seconda chiamata REST e non richiede chiavi API.
 */
public class MappaFermateActivity extends AppCompatActivity {

    private static final String EXTRA_FERMATE = "info.mandarini.busbooking.MAP_STOPS";
    private static final String EXTRA_LAT = "info.mandarini.busbooking.MAP_USER_LAT";
    private static final String EXTRA_LON = "info.mandarini.busbooking.MAP_USER_LON";

    private static final String STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    private static final String USER_SOURCE = "busbooking-user";
    private static final String USER_CIRCLE = "busbooking-user-point";

    private final List<Fermata> fermate = new ArrayList<>();
    private MapView mapView;
    // I marker sono View Android sopra MapView, non layer GeoJSON:
    // in questo modo restano visibili anche sul dispositivo dove le Feature non erano renderizzate.
    private FrameLayout stopsOverlay;
    private final List<StopOverlay> stopOverlays = new ArrayList<>();
    private int lastVisibleLabels = -1;

    private static class StopOverlay {
        final Fermata fermata;
        final FrameLayout pin;
        final TextView label;

        StopOverlay(Fermata fermata, FrameLayout pin, TextView label) {
            this.fermata = fermata;
            this.pin = pin;
            this.label = label;
        }
    }
    private Double userLat;
    private Double userLon;

    /** Prepara i dati delle fermate per la nuova Activity, senza modificare Fermata/Room. */
    public static Intent createIntent(Context context, List<Fermata> fermate,
                                      Double posizioneLat, Double posizioneLon) {
        JSONArray json = new JSONArray();
        for (Fermata fermata : fermate) {
            if (!validCoordinates(fermata.latitudine, fermata.longitudine)) {
                continue;
            }
            try {
                JSONObject stop = new JSONObject();
                stop.put("codice", fermata.codice);
                stop.put("denominazione", fermata.descrizione);
                stop.put("ubicazione", fermata.ubicazione);
                stop.put("latitudine", fermata.latitudine);
                stop.put("longitudine", fermata.longitudine);
                json.put(stop);
            } catch (JSONException e) {
                Log.w("BusBooking", "Fermata non serializzabile per la mappa", e);
            }
        }
        Intent intent = new Intent(context, MappaFermateActivity.class);
        intent.putExtra(EXTRA_FERMATE, json.toString());
        if (validCoordinates(posizioneLat, posizioneLon)) {
            intent.putExtra(EXTRA_LAT, posizioneLat);
            intent.putExtra(EXTRA_LON, posizioneLon);
        }
        return intent;
    }

    private static boolean validCoordinates(Double lat, Double lon) {
        return lat != null && lon != null
                && !Double.isNaN(lat) && !Double.isInfinite(lat) && lat >= -90 && lat <= 90
                && !Double.isNaN(lon) && !Double.isInfinite(lon) && lon >= -180 && lon <= 180;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String payload = getIntent().getStringExtra(EXTRA_FERMATE);
            JSONArray items = new JSONArray(payload == null ? "[]" : payload);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                double lat = item.optDouble("latitudine", Double.NaN);
                double lon = item.optDouble("longitudine", Double.NaN);
                if (!validCoordinates(lat, lon)) {
                    continue;
                }
                Fermata fermata = new Fermata();
                fermata.codice = item.optString("codice", "");
                fermata.descrizione = item.optString("denominazione", "");
                fermata.ubicazione = item.optString("ubicazione", "");
                fermata.latitudine = lat;
                fermata.longitudine = lon;
                fermate.add(fermata);
            }
        } catch (JSONException e) {
            Log.e("BusBooking", "Dati mappa non validi", e);
        }
        Log.i("BusBookingMap", "Fermate ricostruite da Intent: " + fermate.size());
        for (int i = 0; i < Math.min(5, fermate.size()); i++) {
            Fermata f = fermate.get(i);
            Log.i("BusBookingMap", "Fermata " + i + " - " + f.codice
                    + " / " + f.descrizione + " lat=" + f.latitudine
                    + " lon=" + f.longitudine);
        }
        if (fermate.isEmpty()) {
            Toast.makeText(this, R.string.map_no_stops, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (getIntent().hasExtra(EXTRA_LAT) && getIntent().hasExtra(EXTRA_LON)) {
            double lat = getIntent().getDoubleExtra(EXTRA_LAT, Double.NaN);
            double lon = getIntent().getDoubleExtra(EXTRA_LON, Double.NaN);
            if (validCoordinates(lat, lon)) {
                userLat = lat;
                userLon = lon;
            }
        }

        // MapLibre deve essere inizializzato PRIMA dell'inflazione XML del MapView.
        MapLibre.getInstance(getApplicationContext());
        setContentView(R.layout.activity_mappa_fermate);
        setTitle(R.string.map_title);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mapView = findViewById(R.id.map_view_stops);
        stopsOverlay = findViewById(R.id.map_stops_overlay);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(map -> map.setStyle(STYLE_URL, style -> showStops(map)));
    }

    private void showStops(MapLibreMap map) {
        // I layer GeoJSON delle fermate non erano visibili, nonostante le coordinate
        // fossero corrette e dentro il viewport. Per dieci fermate vicine, una
        // sovrapposizione di View Android e' semplice e non dipende da quei layer.
        createStopOverlays();

        // Manteniamo il punto blu dell'utente, gia' funzionante nella versione precedente.
        if (validCoordinates(userLat, userLon)) {
            Feature userPoint = Feature.fromGeometry(Point.fromLngLat(userLon, userLat));
            map.getStyle().addSource(new GeoJsonSource(USER_SOURCE, userPoint));
            map.getStyle().addLayer(new CircleLayer(USER_CIRCLE, USER_SOURCE)
                    .withProperties(
                            PropertyFactory.circleColor(Color.rgb(23, 103, 224)),
                            PropertyFactory.circleRadius(9f),
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor(Color.WHITE)));
        }

        // Proiezione geografica -> coordinate schermo ad ogni movimento della mappa.
        map.addOnCameraMoveListener(() -> positionStopOverlays(map));
        map.addOnCameraIdleListener(() -> positionStopOverlays(map));

        mapView.post(() -> {
            fitAllStops(map);
            positionStopOverlays(map);
        });
        Log.i("BusBookingMap", "Marker Android creati: " + stopOverlays.size());
    }

    private void createStopOverlays() {
        stopsOverlay.removeAllViews();
        stopOverlays.clear();
        final int touchSize = dp(44);
        final int circleSize = dp(17);

        // Aggiungiamo PRIMA tutti i punti: restano tutti disponibili, anche
        // quando i nomi vengono temporaneamente nascosti per evitare sovrapposizioni.
        for (Fermata fermata : fermate) {
            FrameLayout pin = new FrameLayout(this);
            pin.setLayoutParams(new FrameLayout.LayoutParams(touchSize, touchSize));
            pin.setContentDescription("Fermata " + fermata.codice + " - " + fermata.descrizione);
            pin.setClickable(true);
            pin.setElevation(dp(4)); // I punti rimangono sopra le etichette.
            pin.setOnClickListener(v -> openStopDetail(fermata));

            View point = new View(this);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Color.rgb(235, 105, 25));
            dot.setStroke(dp(2), Color.WHITE);
            point.setBackground(dot);
            FrameLayout.LayoutParams pointParams = new FrameLayout.LayoutParams(
                    circleSize, circleSize, Gravity.CENTER);
            pin.addView(point, pointParams);
            stopsOverlay.addView(pin);

            TextView label = new TextView(this);
            label.setText(fermata.codice + " - " + fermata.descrizione);
            label.setContentDescription("Apri fermata " + fermata.codice
                    + " - " + fermata.descrizione);
            label.setTextSize(11f);
            label.setTextColor(Color.rgb(35, 44, 56));
            label.setMaxLines(2);
            label.setMaxWidth(dp(165));
            label.setPadding(dp(7), dp(4), dp(7), dp(4));
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setMinHeight(dp(30));
            label.setClickable(true);
            label.setOnClickListener(v -> openStopDetail(fermata));
            GradientDrawable bubble = new GradientDrawable();
            bubble.setColor(Color.argb(246, 255, 255, 255));
            bubble.setCornerRadius(dp(7));
            bubble.setStroke(dp(1), Color.rgb(235, 105, 25));
            label.setBackground(bubble);
            label.setElevation(dp(2));
            label.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            stopsOverlay.addView(label);

            stopOverlays.add(new StopOverlay(fermata, pin, label));
        }
    }

    private void openStopDetail(Fermata fermata) {
        MainActivity.goToStopDetail(this, fermata.codice,
                fermata.descrizione, fermata.ubicazione);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void positionStopOverlays(MapLibreMap map) {
        if (stopsOverlay == null || mapView == null || stopsOverlay.getWidth() <= 0) {
            return;
        }
        int width = stopsOverlay.getWidth();
        int height = stopsOverlay.getHeight();
        int halfTouch = dp(22);
        int gap = dp(8);
        int margin = dp(4);
        // Il messaggio in alto occupa i primi circa 90 dp: preferiamo non
        // sovrapporre le etichette alle istruzioni (i punti restano cliccabili).
        int helpAreaBottom = dp(96);
        List<RectF> labelsPlaced = new ArrayList<>();
        int visibleLabels = 0;

        for (StopOverlay stop : stopOverlays) {
            PointF p = map.getProjection().toScreenLocation(
                    new LatLng(stop.fermata.latitudine, stop.fermata.longitudine));
            boolean visible = p.x >= -halfTouch && p.y >= -halfTouch
                    && p.x <= width + halfTouch && p.y <= height + halfTouch;
            stop.pin.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            if (!visible) {
                stop.label.setVisibility(View.INVISIBLE);
                continue;
            }
            stop.pin.setX(p.x - halfTouch);
            stop.pin.setY(p.y - halfTouch);
            // La TextView e' gia' stata misurata, dopo il primo passaggio layout.
            // Se ancora non lo fosse, ne richiediamo la misura senza aspettare la camera.
            if (stop.label.getMeasuredWidth() == 0) {
                int wSpec = View.MeasureSpec.makeMeasureSpec(dp(165), View.MeasureSpec.AT_MOST);
                int hSpec = View.MeasureSpec.makeMeasureSpec(dp(120), View.MeasureSpec.AT_MOST);
                stop.label.measure(wSpec, hSpec);
            }
            float lw = stop.label.getMeasuredWidth();
            float lh = stop.label.getMeasuredHeight();
            float x = p.x + gap + dp(5);
            if (x + lw > width - margin) {
                x = p.x - gap - dp(5) - lw;
            }
            x = Math.max(margin, Math.min(x, width - margin - lw));
            float y = Math.max(margin, Math.min(p.y - lh / 2f, height - margin - lh));
            RectF area = new RectF(x - dp(2), y - dp(2),
                    x + lw + dp(2), y + lh + dp(2));

            boolean conflicts = y < helpAreaBottom;
            for (RectF previous : labelsPlaced) {
                if (RectF.intersects(area, previous)) {
                    conflicts = true;
                    break;
                }
            }
            if (conflicts) {
                stop.label.setVisibility(View.INVISIBLE);
            } else {
                stop.label.setX(x);
                stop.label.setY(y);
                stop.label.setVisibility(View.VISIBLE);
                labelsPlaced.add(area);
                visibleLabels++;
            }
        }
        // Un solo log quando cambia il numero delle etichette visibili.
        if (visibleLabels != lastVisibleLabels) {
            lastVisibleLabels = visibleLabels;
            Log.d("BusBookingMap", "Punti fermate: " + stopOverlays.size()
                    + " | etichette non sovrapposte: " + visibleLabels);
        }
    }

    private void fitAllStops(MapLibreMap map) {
        if (mapView == null || mapView.getWidth() == 0 || mapView.getHeight() == 0) {
            return;
        }
        double minLat = 90, minLon = 180, maxLat = -90, maxLon = -180;
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (Fermata f : fermate) {
            bounds.include(new LatLng(f.latitudine, f.longitudine)); // LatLng: latitudine PRIMA.
            minLat = Math.min(minLat, f.latitudine);
            maxLat = Math.max(maxLat, f.latitudine);
            minLon = Math.min(minLon, f.longitudine);
            maxLon = Math.max(maxLon, f.longitudine);
        }
        if (validCoordinates(userLat, userLon)) {
            bounds.include(new LatLng(userLat, userLon));
            minLat = Math.min(minLat, userLat);
            maxLat = Math.max(maxLat, userLat);
            minLon = Math.min(minLon, userLon);
            maxLon = Math.max(maxLon, userLon);
        }
        if (Math.abs(maxLat - minLat) < 0.00001 && Math.abs(maxLon - minLon) < 0.00001) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(fermate.get(0).latitudine, fermate.get(0).longitudine), 16));
        } else {
            int padding = (int) (55 * getResources().getDisplayMetrics().density);
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), padding));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}
