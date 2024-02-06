package info.mandarini.busbooking;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class TitleActivity extends AppCompatActivity {

    private boolean auto = false;
    private ActivityResultLauncher<Intent> pdfWriter = null;
    private File pdfWriterFile;
    private InputStream pdfWriterIS;
    private File openInResume = null;
    private boolean flagOK= true;
;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_title);

        pdfWriter = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            onActivityResultPdf(result.getResultCode(),result.getData());
                        }
                    }
                });

    }

    @Override
    protected void onResume() {
        super.onResume();
        long delay = 2000;
        if (!this.flagOK) {
            this.flagOK = true;
            delay = 5000;
        }

        if (Build.VERSION.SDK_INT > 29) {

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                this.flagOK=false;
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION}, 123);
            }
        } else {

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                this.flagOK = false;
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 123);
            }

        }

        if (!this.flagOK)  {
            return;
        }

        if (openInResume != null) {
            try {
                save(this.openInResume,getInstructionFile());
                startPDF(openInResume);
                this.openInResume = null;
                auto = false;
                waitWatchTitle(3000);
            } catch (Exception e) {
                Log.e("TitleActivity","Errore",e);
            }
        }

        else {
            auto = true;
            waitWatchTitle(delay);
        }
    }

    private void waitWatchTitle(long ms) {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(ms);
                    if (auto) {
                        goToStartPage();
                    }
                } catch (Exception e) {
                    Log.e("start", "Si è verificato un errore", e);
                }

            }
        };
        t.start();
    }

    public void readPrivacyRules(View view) {
        try {
            this.auto = false;
            this.flagOK = false;
            Intent myIntent = new Intent(Intent.ACTION_VIEW,Uri.parse(getString(R.string.privacy_url)));
            startActivity(myIntent);
        }
        catch (ActivityNotFoundException anfe) {
            Toast.makeText(this,"No application", Toast.LENGTH_LONG).show();
        }

    }

    public void downloadInstructions(View view) {

        auto = false;
        File f = null;
        try {

            InputStream in = getInstructionFile();
            String name = getInstructionFileName();
            f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+File.separator+name);
            if (Build.VERSION.SDK_INT>29) {

                this.pdfWriterFile = f;
                this.pdfWriterIS = in;
                f = new File(getApplicationInfo().dataDir+File.separator+"files"+File.separator+name);

                if (!this.pdfWriterFile.exists()) {
                    // Tramite il framework SAF salva il pdf nell'EXTERNAL STAGE (/download)
                    saveFileWithStorageAccessFramework();

                    if (this.openInResume == null) {
                        this.openInResume = f;
                    }
                    return;
                }
            }

            // Salva nell'external stage o nel folder dell'APP a seconda della versione di SDK
            if (!f.exists()) {save(f,in);}

        } catch (Exception e) {
            Log.e("TitleActivity","Errore mentro cerco di copiare il file delle istruzioni",e);
        }
        if (f!=null && f.exists()) {
        startPDF(f);}
        else {
            if (f == null) {
                Toast.makeText(TitleActivity.this,"Non riesco a creare il file", Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(TitleActivity.this, f + " non esiste", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getInstructionFileName() {
        return isItalian() ? getString(R.string.instructions_IT) :getString(R.string.instructions_EN);
    }

    private InputStream getInstructionFile() {
        return getResources().openRawResource(isItalian() ? R.raw.istruzioni : R.raw.istructions);
    }

    private boolean isItalian() {
        return  Locale.getDefault().getLanguage().equalsIgnoreCase("IT");
    }

    private void save(File f, InputStream in) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        byte[] buff = new byte[1024];
        int read = 0;

        try {
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    private void startPDF(File file) {

        try {
            Intent target = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(this,this.getApplicationContext().getPackageName()+".provider",file);
            target.setDataAndType(uri,"application/pdf");
            target.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            target.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Intent intent = Intent.createChooser(target, "Open File");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(TitleActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("TitleActivity","startPDF",e);
        }
    }

    private void goToStartPage() {
        Intent main = new Intent(TitleActivity.this,MainActivity.class);
        TitleActivity.this.startActivity(main);
    }

    // Request code for creating a PDF document.
    private static final int CREATE_FILE = 1;

    private void saveFileWithStorageAccessFramework() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, this.pdfWriterFile.getName());

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when your app creates the document.
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)));
       // startActivityForResult(intent, CREATE_FILE);
        pdfWriter.launch(intent);
    }




    // Handling result
    protected void onActivityResultPdf(int resultCode, Intent data) {

        InputStream is = null;
        OutputStream os = null;

        // Note: you may use try-with resources if your API is 19+
        try {
            // InputStream constructor takes File, String (path), or FileDescriptor
            is = this.pdfWriterIS;
            // data.getData() holds the URI of the path selected by the picker
            os = getContentResolver().openOutputStream(data.getData());

            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } catch (IOException e) {

            e.printStackTrace();
        } finally {
            try {
                is.close();
                os.close();
            } catch (IOException e) {
                //
            }
        }
    }

}