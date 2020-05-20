package com.google.android.exoplayer2.demo;


import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Extend sample chooser with testing functionality: save logcat to file
 */
public class SampleChooserActivityExt extends SampleChooserActivity {

    private MenuItem saveLogsItem;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        try {
            return super.onCreateOptionsMenu(menu);
        } finally {
            saveLogsItem = menu.findItem(R.id.save_logs);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == saveLogsItem) {
            tryToSaveLogcat();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 4321;
    @SuppressLint("NewApi")
    private void tryToSaveLogcat() {

        // for versions before 6.0 - we don't have new permission model. So it means - permission
        // is always granted
        int permissionCheck = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ?
            PackageManager.PERMISSION_GRANTED :
            this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            saveLogcat();
        } else {
            if (this.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "WRITE_EXTERNAL_STORAGE permission was denied." +
                                     " Trying to send as email",
                               Toast.LENGTH_LONG).show();
                sendAsEmail();
            } else {
                // No explanation needed, we can request the permission.
                this.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                saveLogcat();

            } else {
                sendAsEmail();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private StringBuilder getLog() throws IOException {
        String[] command = new String[] { "logcat",
                                          "-v", "threadtime",
                                          "-d"};

        Process process = Runtime.getRuntime().exec(command);
        try (InputStream is = process.getInputStream()) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
            StringBuilder log = new StringBuilder(4096);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line).append("\n");
            }
            return log;
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void saveLogcat() {

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            // even with right granted? try to send
            sendAsEmail();
            return;
        }
        try {
            StringBuilder log = getLog();
            File file = new File(dir, "exoplayer_demo_log.txt");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(log.toString().getBytes());
                Toast.makeText(this, "Log saved to: " + file.toString(), Toast.LENGTH_LONG).show();
            }

        } catch (IOException e) {
            Toast.makeText(this, "Failed to grab logcat", Toast.LENGTH_LONG).show();
        }
    }

    private void sendAsEmail() {
        try {
            StringBuilder log = getLog();
            // if failed to create dir - try to send via app
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("message/rfc822");
            i.putExtra(Intent.EXTRA_EMAIL, new String[]{"tvirl.by@gmail.com"});
            i.putExtra(Intent.EXTRA_SUBJECT, "ExoPlayer test mpeg-2/mp2 on Sony");
            i.putExtra(Intent.EXTRA_TEXT, log.toString());
            try {
                startActivity(Intent.createChooser(i, "Send mail..."));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, "There are no email clients installed.", Toast.LENGTH_SHORT)
                     .show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to grab logcat", Toast.LENGTH_LONG).show();
        }
    }
}
