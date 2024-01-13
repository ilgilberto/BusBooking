package info.mandarini.busbooking;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;


import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.DrawableImageViewTarget;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;

public class Linee extends AppCompatActivity {

    public static final String NOME  = "nome";
    public static final String FERMATA = "fermata";
    public static final String DOVE = "dove";

    private static final int FONT_SIZE = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_linee);
        ImageView waiting = findViewById(R.id.waiting);
        waiting.setVisibility(View.VISIBLE);

        Uri path = Uri.parse("android.resource://info.mandarini.busbooking/" + R.drawable.clessidra);
        Glide.with(this).load(path).into(new DrawableImageViewTarget(waiting));

        Intent intent = getIntent();
        String nome = intent.getExtras().getString(NOME);
        String fermata = intent.getExtras().getString(FERMATA);
        String dove = intent.getExtras().getString(DOVE);
        TextView display = findViewById(R.id.titolo);
        display.setText(String.format("%s - %s in %s\nSeleziona la corsa che vuoi seguire",fermata,nome,dove));

        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "https://mandarini.info/iorestoacasa/fermata/"+fermata;

// Request a string response from the provided URL.
        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {

                    @Override
                    public void onResponse(JSONArray response) {
                        sceltaLinea(response,fermata,nome,dove);
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ImageView waiting = findViewById(R.id.waiting);
                        waiting.setVisibility(View.GONE);
                        Toast.makeText(Linee.this, "Server non disponibile", Toast.LENGTH_SHORT).show();

                    }
                });

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
// Add the request to the RequestQueue.
        queue.add(jsonObjectRequest);

    }

    private void sceltaLinea(JSONArray response, String fermata, String nome, String dove) {

        LinearLayout display = findViewById(R.id.fermate);
        try {
            for (int index = 0; index < response.length(); index++) {

                JSONObject o = response.getJSONObject(index);
                TextView t = new TextView(Linee.this);
                final String codiceLinea = o.getString("codice");
                t.setText(codiceLinea);
                JSONArray corse = o.getJSONArray("corse");
                //Log.i("log",o.getString("denominazione")+"");

                t.setBackgroundColor(Color.GREEN);
                t.setTextColor(Color.BLUE);
                t.setPadding(20, 20, 20, 20);
                t.setLayoutParams(new ConstraintLayout.LayoutParams( ConstraintLayout.LayoutParams.MATCH_PARENT,  150));
                t.setTextSize(FONT_SIZE);
                display.addView(t);

                for (int ic = 0; ic < corse.length(); ic++) {
                    JSONObject corsa = corse.getJSONObject(ic);
                    String linea = corsa.getString("codiceLinea");
                    String autobus = corsa.getString("descrizione");
                    String ora = corsa.getString("ora");
                    String tipologiaOrario = corsa.getString("tipologiaOrario");
                    TextView corsaT = new TextView(Linee.this);
                    Drawable icon = null;
                    if (TIPOLOGIA_ORARIA.PREVISTO.name().equals(tipologiaOrario)) {
                        icon =  getDrawable(R.drawable.orologio);
                    }
                    if (TIPOLOGIA_ORARIA.DA_SATELLITE.name().equals(tipologiaOrario)) {
                        icon =  getDrawable(R.drawable.satellite);
                    }
                    if (icon != null) {
                        corsaT.setCompoundDrawablesWithIntrinsicBounds(null,null,icon,null);}
                    corsaT.setLayoutParams(new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT));
                    corsaT.setBackgroundColor(Color.WHITE);
                    corsaT.setPadding(20, 20, 200, 20);
                    corsaT.setTextSize(FONT_SIZE);
                    if (!TIPOLOGIA_ORARIA.NESSUNA_ALTRA_CORSA.name().equals(tipologiaOrario)) {
                    corsaT.setText(linea + " " + ora);
                    corsaT.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if ("DA_SATELLITE".equals(tipologiaOrario)) {
                            Toast.makeText(Linee.this, "Ti fornirò assitenza sull'arrivo del "+linea, Toast.LENGTH_LONG).show();
                            Intent corsaIntent = new Intent(Linee.this,Corsa.class);
                            corsaIntent.putExtra(Corsa.AUTOBUS,autobus);
                            corsaIntent.putExtra(Corsa.LINEA,codiceLinea);
                            corsaIntent.putExtra(Corsa.LINEA_DETTAGLIO,linea);
                            corsaIntent.putExtra(Corsa.FERMATA,fermata);
                            corsaIntent.putExtra(NOME,nome);
                            corsaIntent.putExtra(DOVE,dove);
                            startActivity(corsaIntent);}
                            else {
                                Toast.makeText(Linee.this, "Orario solo da tabellone, non da satellite", Toast.LENGTH_LONG).show();
                            }
                        }
                    });}
                    else {
                        corsaT.setTextSize(FONT_SIZE/1.5f);
                        corsaT.setText(linea + " "+getString(R.string.notrips));
                    }
                    display.addView(corsaT);
                }
                ImageView waiting = findViewById(R.id.waiting);
                waiting.setVisibility(View.GONE);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
    private enum TIPOLOGIA_ORARIA {DA_SATELLITE,PREVISTO,NESSUNA_ALTRA_CORSA}}