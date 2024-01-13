package info.mandarini.busbooking;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class TitleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_title);
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                    Intent main = new Intent(TitleActivity.this,MainActivity.class);
                    TitleActivity.this.startActivity(main);
                } catch (Exception e) {
                    Log.e("start","Si è verificato un errore",e);
                }

            }
        };
        t.start();
    }
}