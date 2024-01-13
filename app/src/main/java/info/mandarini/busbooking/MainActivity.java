package info.mandarini.busbooking;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import info.mandarini.busbooking.threads.Cronometro;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private LocationManager locationManager = null;
    Double latitude;
    Double longitude;
    private static final int MIN_DIST = 20;
    private static final int MIN_PERIOD = 30000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    public void nogps(View view) {
        start(view, LocationManager.NETWORK_PROVIDER);
    }

    public void gps(View view) {
        start(view, LocationManager.GPS_PROVIDER);
    }

    public void start(View view, String providerId) {

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(providerId)) {
            Intent gpsOptionsIntent = new Intent(
                    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(gpsOptionsIntent);
            return;
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 123);
            return;
        } else {
            LinearLayout display = findViewById(R.id.fermate);
            display.removeAllViews();
            TextView helper = findViewById(R.id.helper);
            helper.setVisibility(View.VISIBLE);
            helper.setText(R.string.stopList);

            String bestProvider = locationManager.getBestProvider(new Criteria(), true);
            Log.i("CIAO", "MIGLIOR PROVIDER " + bestProvider);
            locationManager.requestLocationUpdates(providerId, MIN_PERIOD, MIN_DIST, this);

        }
    }
    public void start(Double longitude, Double latitude) {

        RequestQueue queue = Volley.newRequestQueue(this);
        String url = getString(R.string.fermate_url)+"?longitudine="+longitude+"&latitudine="+latitude;

// Request a string response from the provided URL.
        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {

                    @Override
                    public void onResponse(JSONArray response) {
                         sceltaFermata(response);
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(MainActivity.this, "Server non disponibile", Toast.LENGTH_SHORT).show();

                    }
                });

// Add the request to the RequestQueue.
        queue.add(jsonObjectRequest);


    }

                private void sceltaFermata(JSONArray response) {

                    LinearLayout display = findViewById(R.id.fermate);
                    display.removeAllViews();
                    TextView helper = findViewById(R.id.helper);
                    helper.setVisibility(View.GONE);
                    TextView desc = new TextView(MainActivity.this);
                    desc.setText(R.string.selectStop);
                    display.addView(desc);
                    try {
                    for (int index = 0; index<response.length();index++){

                        JSONObject o = response.getJSONObject(index);
                        TextView t = new TextView(MainActivity.this);
                        final String codiceFermata = o.getString("codice");
                        final String denominazione = o.getString("denominazione");
                        final String ubicazione = o.getString("ubicazione");
                        t.setText(codiceFermata+"-"+denominazione);
                        //Log.i("log",o.getString("denominazione")+"");
                        try {
                            t.setBackgroundColor(Color.WHITE);
                            t.setPadding(20,20,20,20);
                            t.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    goToStopDetail(MainActivity.this,codiceFermata, denominazione, ubicazione);
                                }
                            });
                            display.addView(t);
                            //setContentView(findViewById(R.id.display));
                            Log.i("Aggiunto ",t.getText()+"");
                        }
                        catch (Exception e) {
                            Log.e("prova","prova",e);
                        }
                        //display.refreshDrawableState();
                        //display.forceLayout();
                    }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

    static void goToStopDetail(Context context, String codiceFermata, String denominazione, String ubicazione) {
        Cronometro.clear();
        Toast.makeText(context, "Caricamento dati fermata", Toast.LENGTH_LONG).show();
        Intent lineeIntent = new Intent(context,Linee.class);
        lineeIntent.putExtra(Linee.FERMATA, codiceFermata);
        lineeIntent.putExtra(Linee.NOME, denominazione);
        lineeIntent.putExtra(Linee.DOVE, ubicazione);
        context.startActivity(lineeIntent);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.i("CIAO","POSIZIONE RECUPERATA");
        latitude=location.getLatitude();
        longitude=location.getLongitude();
        start(longitude,latitude);
    }

    public void onStatusChanged(String s, int i, Bundle bundle) {
        Log.v("CIAO", "Status changed: " + s);
    }

    public void onProviderEnabled(String s) {
        Log.e("CIAO", "PROVIDER DISABLED: " + s);
    }

    public void onProviderDisabled(String s) {
        Log.e("CIAO", "PROVIDER DISABLED: " + s);
    }
}