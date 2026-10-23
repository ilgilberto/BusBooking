package info.mandarini.busbooking;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
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
import org.maplibre.android.maps.ImageContent;
import org.maplibre.android.maps.ImageStretches;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.mandarini.busbooking.persistence.entities.Fermata;

/**
 * Visualizza le fermate vicine con layer MapLibre nativi.
 *
 * I cerchi sono TUTTI disegnati da CircleLayer. Le etichette sono disegnate
 * da due SymbolLayer (a sinistra o a destra della fermata) e selezionate
 * dopo ogni spostamento della mappa per evitare collisioni tra i nomi.
 * Nessun componente View viene sovrapposto per rappresentare le fermate.
 */
public class MappaFermateActivity extends AppCompatActivity {

    private static final String EXTRA_FERMATE = "info.mandarini.busbooking.MAP_STOPS";
    private static final String EXTRA_LAT = "info.mandarini.busbooking.MAP_USER_LAT";
    private static final String EXTRA_LON = "info.mandarini.busbooking.MAP_USER_LON";
    private static final String STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";

    // Identificatori del test C riuscito: stessa sorgente FeatureCollection,
    // stesso CircleLayer, attivato passando da NONE a VISIBLE.
    private static final String STOPS_SOURCE = "busbooking-test-collection-all";
    private static final String STOPS_CIRCLES = "busbooking-test-circle-collection-all";
    private static final String LABELS_RIGHT_SOURCE = "busbooking-labels-right-source";
    private static final String LABELS_LEFT_SOURCE = "busbooking-labels-left-source";
    private static final String LABELS_RIGHT_LAYER = "busbooking-labels-right-layer";
    private static final String LABELS_LEFT_LAYER = "busbooking-labels-left-layer";
    private static final String USER_SOURCE = "busbooking-user";
    private static final String USER_CIRCLE = "busbooking-user-point";
    private static final String TAG = "BusBookingMap";

    private static final float LABEL_SIZE_SP = 11f;
    // Fondo opaco per le etichette. Non interferisce con i CircleLayer delle fermate.
    private static final String LABEL_BACKGROUND = "busbooking-label-white-box";
    private final List<Fermata> fermate = new ArrayList<>();
    private MapView mapView;
    private TextView mapHelp;
    private GeoJsonSource labelsRightSource;
    private GeoJsonSource labelsLeftSource;
    private Double userLat;
    private Double userLon;
    private boolean labelsReady;

    /** Trasferisce le coordinate che MainActivity ha gia' ricevuto via REST. */
    public static Intent createIntent(Context context, List<Fermata> fermate,
                                      Double posizioneLat, Double posizioneLon) {
        JSONArray json = new JSONArray();
        for (Fermata f : fermate) {
            if (!validCoordinates(f.latitudine, f.longitudine)) {
                continue;
            }
            try {
                JSONObject item = new JSONObject();
                item.put("codice", f.codice);
                item.put("denominazione", f.descrizione);
                item.put("ubicazione", f.ubicazione);
                item.put("latitudine", f.latitudine);
                item.put("longitudine", f.longitudine);
                json.put(item);
            } catch (JSONException e) {
                Log.w(TAG, "Impossibile serializzare una fermata", e);
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
                && !Double.isNaN(lat) && !Double.isInfinite(lat)
                && !Double.isNaN(lon) && !Double.isInfinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
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
                Fermata f = new Fermata();
                f.codice = item.optString("codice", "");
                f.descrizione = item.optString("denominazione", "");
                f.ubicazione = item.optString("ubicazione", "");
                f.latitudine = lat;
                f.longitudine = lon;
                fermate.add(f);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Dati mappa non validi", e);
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

        MapLibre.getInstance(getApplicationContext());
        setContentView(R.layout.activity_mappa_fermate);
        setTitle(R.string.map_title);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mapView = findViewById(R.id.map_view_stops);
        mapHelp = findViewById(R.id.map_help);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(map -> map.setStyle(STYLE_URL, style -> showStops(map)));
    }

    private void showStops(MapLibreMap map) {
        // Manteniamo ESATTAMENTE la forma di creazione sorgente/cerchi
        // del test C che ha restituito 10 feature su 10 renderizzate.
        List<Feature> all = new ArrayList<>();
        for (Fermata f : fermate) {
            all.add(makeFeature(f));
        }
        map.getStyle().addSource(new GeoJsonSource(STOPS_SOURCE,
                FeatureCollection.fromFeatures(all.toArray(new Feature[0]))));
        map.getStyle().addLayer(new CircleLayer(STOPS_CIRCLES, STOPS_SOURCE)
                .withProperties(
                        PropertyFactory.circleColor(Color.rgb(238, 112, 25)),
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.visibility(Property.NONE)));

        if (validCoordinates(userLat, userLon)) {
            Feature user = Feature.fromGeometry(Point.fromLngLat(userLon, userLat));
            map.getStyle().addSource(new GeoJsonSource(USER_SOURCE, user));
            map.getStyle().addLayer(new CircleLayer(USER_CIRCLE, USER_SOURCE)
                    .withProperties(
                            PropertyFactory.circleColor(Color.rgb(23, 103, 224)),
                            PropertyFactory.circleRadius(9f),
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor(Color.WHITE)));
        }

        // Ogni fermata rimane cliccabile anche quando il suo nome e' nascosto.
        map.addOnMapClickListener(tap -> {
            PointF px = map.getProjection().toScreenLocation(tap);
            int radius = dp(23);
            RectF hitBox = new RectF(px.x - radius, px.y - radius,
                    px.x + radius, px.y + radius);
            List<Feature> pinHits = map.queryRenderedFeatures(hitBox, STOPS_CIRCLES);
            Fermata nearest = closestStop(px, pinHits, map);
            if (nearest != null) {
                openStopDetail(nearest);
                return true;
            }
            // Le etichette hanno la stessa proprieta' "codice" della fermata.
            if (labelsReady) {
                List<Feature> labelHits = map.queryRenderedFeatures(hitBox,
                        LABELS_RIGHT_LAYER, LABELS_LEFT_LAYER);
                if (labelHits != null && !labelHits.isEmpty()) {
                    Feature labelFeature = labelHits.get(0);
                    if (labelFeature.hasProperty("codice")) {
                        Fermata f = findStop(labelFeature.getStringProperty("codice"));
                        if (f != null) {
                            openStopDetail(f);
                            return true;
                        }
                    }
                }
            }
            return false;
        });

        // I nomi si ricalcolano solo a camera ferma; i cerchi rimangono
        // sempre gestiti interamente dal rendering nativo di MapLibre.
        map.addOnCameraIdleListener(() -> updateSmartLabels(map));
        mapView.post(() -> {
            fitAllStops(map);
            // Cambio esplicito NONE -> VISIBLE, gia' collaudato nel test C.
            mapView.postDelayed(() -> {
                if (isFinishing() || isDestroyed() || map.getStyle() == null) {
                    return;
                }
                CircleLayer stops = map.getStyle().getLayerAs(STOPS_CIRCLES);
                if (stops != null) {
                    stops.setProperties(PropertyFactory.visibility(Property.VISIBLE));
                }
                // I SymbolLayer vengono creati DOPO l'attivazione dei cerchi.
                // Nel test riuscito i nomi erano infatti stati aggiunti dopo C.
                mapView.postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed() && map.getStyle() != null) {
                        createLabelLayers(map);
                        updateSmartLabels(map);
                    }
                }, 350L);
            }, 500L);
        });
        Log.i(TAG, "GeoJSON nativo: " + fermate.size() + " fermate disponibili");
    }

    private Feature makeFeature(Fermata f) {
        Feature result = Feature.fromGeometry(Point.fromLngLat(f.longitudine, f.latitudine));
        result.addStringProperty("codice", f.codice);
        result.addStringProperty("denominazione", f.descrizione);
        result.addStringProperty("ubicazione", f.ubicazione == null ? "" : f.ubicazione);
        return result;
    }

    private void createLabelLayers(MapLibreMap map) {
        if (labelsReady) {
            return;
        }
        FeatureCollection empty = FeatureCollection.fromFeatures(new Feature[0]);
        labelsRightSource = new GeoJsonSource(LABELS_RIGHT_SOURCE, empty);
        labelsLeftSource = new GeoJsonSource(LABELS_LEFT_SOURCE, empty);
        map.getStyle().addSource(labelsRightSource);
        map.getStyle().addSource(labelsLeftSource);
        addLabelBackground(map);

        // Le collisioni con i nomi gia' presenti nella mappa Liberty avevano
        // nascosto tutte le denominazioni. Selezioniamo NOI solo le etichette
        // che non si sovrappongono tra loro e chiediamo al SymbolLayer di
        // visualizzarle, con il font Noto Sans compatibile con lo stile.
        map.getStyle().addLayer(makeLabelLayer(LABELS_RIGHT_LAYER,
                LABELS_RIGHT_SOURCE, Property.TEXT_ANCHOR_LEFT, 1.5f));
        map.getStyle().addLayer(makeLabelLayer(LABELS_LEFT_LAYER,
                LABELS_LEFT_SOURCE, Property.TEXT_ANCHOR_RIGHT, -1.5f));
        labelsReady = true;
    }

    private SymbolLayer makeLabelLayer(String layerId, String sourceId,
                                       String anchor, float offsetX) {
        return new SymbolLayer(layerId, sourceId).withProperties(
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(new String[]{"Noto Sans Regular"}),
                PropertyFactory.textSize(LABEL_SIZE_SP),
                PropertyFactory.textMaxWidth(30f),
                PropertyFactory.textAnchor(anchor),
                PropertyFactory.textOffset(new Float[]{offsetX, 0f}),
                PropertyFactory.textColor(Color.rgb(30, 40, 52)),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(0f),
                // Immagine a nove sezioni: sfondo bianco completamente opaco
                // e sottile bordo arancione, allargato alla dimensione del testo.
                PropertyFactory.iconImage(LABEL_BACKGROUND),
                PropertyFactory.iconTextFit(Property.ICON_TEXT_FIT_BOTH),
                PropertyFactory.iconTextFitPadding(new Float[]{6f, 9f, 6f, 9f}),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true));
    }

    /**
     * Disegna un unico piccolo fondo, condiviso da tutte le etichette.
     * Le fasce centrali stretchX/stretchY sono deformabili; i bordi e gli
     * angoli arrotondati restano integri, qualunque sia la lunghezza del nome.
     *
     * MapLibre espande l'immagine con iconTextFit(BOTH) senza aggiungere
     * nuove View Android sopra la mappa.
     */
    private void addLabelBackground(MapLibreMap map) {
        final int width = 48;
        final int height = 36;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE); // Alpha 255: rettangolo non trasparente.
        canvas.drawRoundRect(new RectF(1, 1, width - 1, height - 1), 8f, 8f, fill);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f);
        border.setColor(Color.rgb(224, 114, 29));
        canvas.drawRoundRect(new RectF(1, 1, width - 1, height - 1), 8f, 8f, border);

        map.getStyle().addImage(
                LABEL_BACKGROUND,
                bitmap,
                Collections.singletonList(new ImageStretches(23f, 25f)),
                Collections.singletonList(new ImageStretches(17f, 19f)),
                new ImageContent(7f, 6f, 41f, 30f));
    }

    /**
     * Mostra tante etichette quante entrano nello schermo senza sovrapporsi.
     * Si prova prima a destra del punto, poi a sinistra. A zoom maggiore
     * c'e' piu' spazio e quindi diventano leggibili piu' fermate.
     */
    private void updateSmartLabels(MapLibreMap map) {
        if (!labelsReady || mapView == null || isFinishing() || isDestroyed()
                || mapView.getWidth() <= 0 || mapView.getHeight() <= 0) {
            return;
        }
        final int screenWidth = mapView.getWidth();
        final int screenHeight = mapView.getHeight();
        final int helpBottom = mapHelp == null ? dp(105)
                : (int) (mapHelp.getY() + mapHelp.getHeight() + dp(8));
        final float gap = dp(17);
        final float inset = dp(8);

        List<Feature> labelsRight = new ArrayList<>();
        List<Feature> labelsLeft = new ArrayList<>();
        List<RectF> occupied = new ArrayList<>();
        List<PointF> pins = new ArrayList<>();
        for (Fermata f : fermate) {
            pins.add(map.getProjection().toScreenLocation(new LatLng(f.latitudine, f.longitudine)));
        }
        PointF userPin = validCoordinates(userLat, userLon)
                ? map.getProjection().toScreenLocation(new LatLng(userLat, userLon)) : null;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                LABEL_SIZE_SP, getResources().getDisplayMetrics()));
        final float maxLineWidth = dp(144);

        for (int i = 0; i < fermate.size(); i++) {
            Fermata f = fermate.get(i);
            PointF center = pins.get(i);
            if (center.x < 0 || center.y < helpBottom
                    || center.x > screenWidth || center.y > screenHeight) {
                continue;
            }
            String label = wrapText(f.codice + " · " + f.descrizione,
                    paint, maxLineWidth);
            String[] lines = label.split("\n", -1);
            float width = 0;
            for (String line : lines) {
                width = Math.max(width, paint.measureText(line));
            }
            // Comprende il padding interno e il bordo dei nuovi riquadri.
            // Stima prudente: meglio mostrare un'etichetta in meno che
            // sovrapporne due o coprire un'altra fermata.
            width += dp(30);
            float height = lines.length * paint.getTextSize() * 1.45f + dp(18);
            RectF right = new RectF(center.x + gap, center.y - height / 2f,
                    center.x + gap + width, center.y + height / 2f);
            RectF left = new RectF(center.x - gap - width, center.y - height / 2f,
                    center.x - gap, center.y + height / 2f);

            boolean useRight = acceptable(right, screenWidth, screenHeight,
                    helpBottom, inset, occupied, pins, i, userPin);
            boolean useLeft = !useRight && acceptable(left, screenWidth, screenHeight,
                    helpBottom, inset, occupied, pins, i, userPin);
            if (!useRight && !useLeft) {
                continue;
            }
            RectF placed = useRight ? right : left;
            occupied.add(new RectF(placed.left - dp(5), placed.top - dp(5),
                    placed.right + dp(5), placed.bottom + dp(5)));
            Feature nameFeature = makeFeature(f);
            nameFeature.addStringProperty("label", label);
            (useRight ? labelsRight : labelsLeft).add(nameFeature);
        }
        labelsRightSource.setGeoJson(FeatureCollection.fromFeatures(
                labelsRight.toArray(new Feature[0])));
        labelsLeftSource.setGeoJson(FeatureCollection.fromFeatures(
                labelsLeft.toArray(new Feature[0])));
        Log.d(TAG, "Fermate=" + fermate.size() + " | etichette leggibili="
                + (labelsRight.size() + labelsLeft.size())
                + " | zoom=" + map.getCameraPosition().zoom);
    }

    private boolean acceptable(RectF candidate, int width, int height,
                               int helpBottom, float inset, List<RectF> occupied,
                               List<PointF> pins, int self, PointF userPin) {
        if (candidate.left < inset || candidate.right > width - inset
                || candidate.top < helpBottom || candidate.bottom > height - inset) {
            return false;
        }
        for (RectF rect : occupied) {
            if (RectF.intersects(candidate, rect)) {
                return false;
            }
        }
        // Non coprire altri punti fermata con le denominazioni.
        float pinMargin = dp(12);
        for (int i = 0; i < pins.size(); i++) {
            if (i == self) {
                continue;
            }
            PointF p = pins.get(i);
            if (candidate.intersects(p.x - pinMargin, p.y - pinMargin,
                    p.x + pinMargin, p.y + pinMargin)) {
                return false;
            }
        }
        return userPin == null || !candidate.intersects(
                userPin.x - pinMargin, userPin.y - pinMargin,
                userPin.x + pinMargin, userPin.y + pinMargin);
    }

    private static String wrapText(String value, Paint paint, float maxWidth) {
        StringBuilder result = new StringBuilder();
        StringBuilder current = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            String proposal = current.length() == 0 ? word : current + " " + word;
            if (current.length() > 0 && paint.measureText(proposal) > maxWidth) {
                if (result.length() > 0) {
                    result.append('\n');
                }
                result.append(current);
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(proposal);
            }
        }
        if (current.length() > 0) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(current);
        }
        return result.toString();
    }

    private Fermata closestStop(PointF tap, List<Feature> hits, MapLibreMap map) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        Fermata best = null;
        double bestDistance2 = Double.MAX_VALUE;
        for (Feature feature : hits) {
            if (!feature.hasProperty("codice")) {
                continue;
            }
            Fermata f = findStop(feature.getStringProperty("codice"));
            if (f == null) {
                continue;
            }
            PointF p = map.getProjection().toScreenLocation(
                    new LatLng(f.latitudine, f.longitudine));
            double distance2 = Math.pow(p.x - tap.x, 2) + Math.pow(p.y - tap.y, 2);
            if (distance2 < bestDistance2) {
                bestDistance2 = distance2;
                best = f;
            }
        }
        return best;
    }

    private Fermata findStop(String code) {
        for (Fermata f : fermate) {
            if (f.codice.equals(code)) {
                return f;
            }
        }
        return null;
    }

    private void openStopDetail(Fermata f) {
        MainActivity.goToStopDetail(this, f.codice, f.descrizione, f.ubicazione);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void fitAllStops(MapLibreMap map) {
        if (mapView == null || mapView.getWidth() == 0 || mapView.getHeight() == 0) {
            return;
        }
        double minLat = 90, minLon = 180, maxLat = -90, maxLon = -180;
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (Fermata f : fermate) {
            bounds.include(new LatLng(f.latitudine, f.longitudine));
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
            int padding = dp(55);
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
