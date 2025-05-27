package info.mandarini.busbooking.threads;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.Set;

import info.mandarini.busbooking.Corsa;
import info.mandarini.busbooking.R;

public class Cronometro extends Thread implements Runnable {

    int volte = 0;
    Corsa context = null;
    private boolean _stop = false;
    private final static Set<Cronometro> actives = new HashSet<>();
    public static boolean FORCE_CHECK = false;
    public Cronometro(Corsa  context) {
        this.context = context;
    }

    private Cronometro(Corsa  context,int volte) {
        this.context = context;
        this.volte = volte;
    }

    @Override
        public void run() {
            int refreshTime = Integer.parseInt(context.getString(R.string.refreshTime));
            try {
                sleep(1000);
                this.context.tempo--;
                if (volte>refreshTime || FORCE_CHECK) {
                    FORCE_CHECK = false;
                    context.booking(true);
                }
                else {
                    final long item = this.context.tempo;

                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Corsa.showTime(context, item, context.beep,
                                    Integer.parseInt(context.getString(R.string.inArrivingBeep)),
                                    Integer.parseInt(context.getString(R.string.inArrivingText)),
                                    context.findViewById(R.id.stopBeep));
                        }
                    });

                    Cronometro next = new Cronometro(this.context, this.volte + 1);
                    if (!_stop) {
                        actives.add(next);
                        next.start();
                    }
                }
            } catch (Exception e) {
                Log.e("corsa","thread",e);
            }
        }

    public void play() {
        _stop = false;
        try {
            this.start();
            actives.add(this);
        }
        catch (Exception e) {
            Log.e("Cronometro","play",e);
        }
    }

    public static void clear() {
        for (Cronometro c : actives) {
            c._stop = true;
        }
        actives.clear();
    }
}
