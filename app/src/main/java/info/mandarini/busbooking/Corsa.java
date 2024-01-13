package info.mandarini.busbooking;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import info.mandarini.busbooking.threads.Cronometro;

public class Corsa extends AppCompatActivity {

    public final static String LINEA = "linea";
    public final static String LINEA_DETTAGLIO = "lineaD";
    public final static String AUTOBUS = "Autobus";
    public final static String FERMATA = "stop";

    public String linea;
    public String dettaglio;
    public String autobus;
    public String fermata;
    public String nomeFermata;
    public String doveFermata;

    public static MediaPlayer arrivingSound;
    public static MediaPlayer updateSound;

    public long tempo = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_corsa);
        Intent intent = getIntent();
        linea = intent.getExtras().getString(LINEA);
        dettaglio = intent.getExtras().getString(LINEA_DETTAGLIO);
        autobus = intent.getExtras().getString(AUTOBUS);
        fermata = intent.getExtras().getString(FERMATA);
        nomeFermata = intent.getExtras().getString(Linee.NOME);
        doveFermata = intent.getExtras().getString(Linee.DOVE);
        TextView header = findViewById(R.id.descrizioneVeicoloInArrivo);
        header.setText(dettaglio+"\n"+autobus);
        TextView busStop = findViewById(R.id.fermata);
        busStop.setText(getString(R.string.to)+ " "+fermata+"-"+nomeFermata);

        this.arrivingSound = new MediaPlayer().create(this,R.raw.arrivo);
        this.updateSound = new MediaPlayer().create(this,R.raw.suono);

        findViewById(R.id.stopBooking).setOnClickListener(
             new View.OnClickListener() {

                 @Override
                 public void onClick(View view) {
                     MainActivity.goToStopDetail(Corsa.this,fermata, nomeFermata,doveFermata);
                 }
             }
        );
        Cronometro.clear();
        booking(false);
    }

    public void booking(final boolean makeCheck) {
        final long time = this.tempo;
        String url = "https://mandarini.info/iorestoacasa/fermata/"+fermata+"/"+linea+"?autobus="+autobus;
        RequestQueue queue = Volley.newRequestQueue(this);
// Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (StringUtils.isBlank(response) || "-1".equals(response)) {
                            MainActivity.goToStopDetail(Corsa.this,fermata, nomeFermata,doveFermata);
                        }
                        else {
                            booking(Integer.parseInt(response)*60L,makeCheck);}
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        booking(time,makeCheck);
                        Toast.makeText(Corsa.this, "Server non disponibile", Toast.LENGTH_SHORT).show();
                    }
                });

// Add the request to the RequestQueue.
        queue.add(stringRequest);

    }

    @Override
    protected void onResume() {
        super.onResume();
        Cronometro.FORCE_CHECK = true;
    }

        private void booking(final long totale,boolean makeCheck) {

            if (!makeCheck) {
                this.tempo = totale;
            showTime(this,totale);
            Cronometro cron = new Cronometro(this);
            cron.play();}
            else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        check(totale);
                    }
                });
            }
        }

    private void check(long totale) {
        if (Math.abs(totale-this.tempo)>60) {
            this.tempo = totale;
            updateSound.start();
            Toast.makeText(Corsa.this, "Aggiornamento del tempo stimato", Toast.LENGTH_SHORT).show();
        }
        showTime(this,this.tempo);
        Cronometro cron = new Cronometro(this);
        cron.play();
    }

    public static void showTime(AppCompatActivity context, long totale) {
        TextView timer = context.findViewById(R.id.timer);

        if (totale< 120 && arrivingSound != null) {
           arrivingSound.start();
;        }
        if (totale < 60) {
            timer.setText("IN ARRIVO");
            timer.setTextColor(Color.GREEN);
        } else {
            String ore = String.format("%02d", totale / 3600L);
            String minuti = String.format("%02d", (totale % 3600) / 60L);
            String secondi = String.format("%02d", (totale % 3600) % 60L);
            timer.setTextColor(Color.BLUE);
            timer.setText(ore + ":" + minuti + ":" + secondi);
        }
    }
}