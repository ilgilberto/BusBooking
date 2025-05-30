package info.mandarini.busbooking;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.signature.ObjectKey;

import org.apache.commons.lang3.StringUtils;

import info.mandarini.busbooking.threads.Cronometro;

public class Corsa extends AppCompatActivity {

    public final static String LINEA = "linea";
    public final static String LINEA_DETTAGLIO = "lineaD";
    public final static String AUTOBUS = "Autobus";
    public final static String FERMATA = "stop";
    public static final int MAX_FERMATA_LENGTH = 20;

    public String linea;
    public String dettaglio;
    public String autobus;
    public String fermata;
    public String nomeFermata;
    public String doveFermata;
    public boolean beep;

    public static MediaPlayer arrivingSound;
    public static MediaPlayer updateSound;

    private ImageView bannerImageView;

    private Runnable slideshowRunnable;
    private int currentIndex = 0;
    // Campi banner (verranno inizializzati nel onCreate quando il Context è stato a sua volta inizializzato)
    private int delayMillis = 0;
    private String baseUrlBanner =  "https://conoscenzacreativa.it/banner/imm";
    private String linkUrlBanner = "https://www.conoscenzacreativa.it/booksite/portfolio";
    private String extensionImageBanner = ".jpg";
    private boolean atLeastOneImageFound = false;
    private long cacheLifeInHours = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private final int maxTries = 20;

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
        busStop.setText(getString(R.string.to)+ " "+fermata+"-"+cut(nomeFermata, MAX_FERMATA_LENGTH));

        this.arrivingSound = new MediaPlayer().create(this,R.raw.arrivo);
        this.updateSound = new MediaPlayer().create(this,R.raw.suono);

        findViewById(R.id.stopBooking).setOnClickListener(
             new View.OnClickListener() {

                 @Override
                 public void onClick(View view) {
                     if (beep && arrivingSound != null) {
                         arrivingSound.stop();}
                     MainActivity.goToStopDetail(Corsa.this,fermata, nomeFermata,doveFermata);
                 }
             }
        );

        beep = true;
        findViewById(R.id.stopBeep).setVisibility(View.GONE);
        findViewById(R.id.stopBeep).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        findViewById(R.id.stopBeep).setVisibility(View.GONE);
                       beep = false;
                       if (arrivingSound != null) {
                            arrivingSound.stop();}
                    }
                }
        );
        Cronometro.clear();
        booking(false);

        // ____________________________________________
        // Implementazione banner

        // inizializzaione campi
        delayMillis = Integer.parseInt(getString(R.string.delayMillisBanner));
        cacheLifeInHours = Long.parseLong(getString(R.string.cacheLifeHours));
        baseUrlBanner =  getString(R.string.baseUrlBanner);
        linkUrlBanner = getString(R.string.linkUrlBanner);
        extensionImageBanner = getString(R.string.extensionImageBanner);
        // gestione
        bannerImageView = findViewById(R.id.bannerImageView);
        bannerImageView.setOnClickListener(view -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkUrlBanner));
            view.getContext().startActivity(browserIntent);
        });
        // Avvia lo slideshow
        startSlideshow();
    }


    private void startSlideshow() {

        slideshowRunnable = new Runnable() {
            @Override
            public void run() {
                String url = baseUrlBanner + currentIndex + extensionImageBanner;
                long sixHourSignature = System.currentTimeMillis() / (cacheLifeInHours * 60 * 60 * 1000);
                Log.d("SLIDESHOW", "Carico immagine: " + url);

                Glide.with(Corsa.this)
                        .load(url)
                        .signature(new ObjectKey(sixHourSignature))  // Cambia ogni 6 ore
                        .into(new CustomTarget<Drawable>() {
                            @Override
                            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                bannerImageView.setImageDrawable(resource);
                                bannerImageView.setVisibility(ImageView.VISIBLE);
                                advanceIndex();
                                handler.postDelayed(slideshowRunnable, delayMillis); // delay solo su successo
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {
                                // Non serve gestire
                            }

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                Log.d("SLIDESHOW", "Immagine non trovata: " + url);
                                currentIndex = 0;
                                handler.post(slideshowRunnable); // Nessun delay su errore
                            }
                        });
            }
        };

        handler.post(slideshowRunnable); // avvia il ciclo
    }

    private void advanceIndex() {
        currentIndex++;
        if (currentIndex >= maxTries) {
            currentIndex = 0;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(slideshowRunnable);
    }

    public void booking(final boolean makeCheck) {
        final long time = this.tempo;
        String url = getString(R.string.linee_url)+fermata+"/"+linea+"?autobus="+autobus;
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
            showTime(this,totale,beep,
                    Integer.parseInt(getString(R.string.inArrivingBeep)),
                    Integer.parseInt(getString(R.string.inArrivingText)),
                    findViewById(R.id.stopBeep));
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

    public static void showTime(AppCompatActivity context, long totale,boolean beep
                                 ,long inArrivingBeep,long inArrivingText,View stopButton) {
        TextView timer = context.findViewById(R.id.timer);

        if (beep && totale<inArrivingBeep && arrivingSound != null) {
           arrivingSound.start();
            stopButton.setVisibility(View.VISIBLE);
;        }
        if (totale < inArrivingText) {
            timer.setText("IN ARRIVO");
            timer.setTextColor(Color.GREEN);
            //stopButton.setVisibility(View.GONE);
        } else {
            String ore = String.format("%02d", totale / 3600L);
            String minuti = String.format("%02d", (totale % 3600) / 60L);
            String secondi = String.format("%02d", (totale % 3600) % 60L);
            timer.setTextColor(Color.BLUE);
            timer.setText(ore + ":" + minuti + ":" + secondi);
        }
    }

    private void check(long totale) {
        if (Math.abs(totale-this.tempo)>60) {
            this.tempo = totale;
            updateSound.start();
            Toast.makeText(Corsa.this, "Aggiornamento del tempo stimato", Toast.LENGTH_SHORT).show();
        }
        showTime(this,this.tempo,this.beep,
                Integer.parseInt(getString(R.string.inArrivingBeep)),
                Integer.parseInt(getString(R.string.inArrivingText)),
                findViewById(R.id.stopBeep));
        Cronometro cron = new Cronometro(this);
        cron.play();
    }

    private String cut(String value, int max) {
        if (StringUtils.isNotBlank(value)) {
            if (value.length() > max) {
                return value.substring(0, max) + "...";
            } else {
                return value;
            }
        }
        return "";
    }
}