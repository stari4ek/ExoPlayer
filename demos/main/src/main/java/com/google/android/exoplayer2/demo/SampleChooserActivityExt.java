package com.google.android.exoplayer2.demo;


import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.Toast;

import com.google.android.exoplayer2.util.Assertions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Extend sample chooser with testing functionality: save logcat to file
 */
public class SampleChooserActivityExt extends SampleChooserActivity {

    private final Sample logcatSample = new Sample(
        "Save logcat", false, null, null) {
        @Override
        public Intent buildIntent(Context context) {
            Assertions.checkState(false, "Should not be called");
            return null;
        }
    };


    /* private */ void onSampleGroups(final List<SampleGroup> groups, boolean sawError) {
        // TVirl: inject own group with special handlers
        SampleGroup toolsGroup = new SampleGroup("=== Tools");
        toolsGroup.samples.add(logcatSample);
        groups.add(toolsGroup);
        // !TVirl

        super.onSampleGroups(groups, sawError);
    }

    @Override
    public boolean onChildClick(
        ExpandableListView parent, View view, int groupPosition, int childPosition, long id) {
        Sample sample = (Sample) view.getTag();

        // intercept own sample
        if (sample == logcatSample) {
            tryToSaveLogcat();
            return true;
        } else {
            return super.onChildClick(parent, view, groupPosition, childPosition, id);
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
            @NonNull String permissions[], @NonNull int[] grantResults) {

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    saveLogcat();

                } else {
                    sendAsEmail();
                }
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
