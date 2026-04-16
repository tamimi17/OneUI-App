package com.example.oneuiapp.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static CrashHandler instance;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context ctx;

    private CrashHandler() {}

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new CrashHandler();
            instance.ctx = context.getApplicationContext();
            instance.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(instance);
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stack = sw.toString();

            String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                    .format(new Date());

            StringBuilder report = new StringBuilder();
            report.append("Timestamp: ").append(ts).append("\n");
            report.append("Thread: ").append(t.getName()).append("\n");
            report.append("Package: ").append(ctx.getPackageName()).append("\n");
            report.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
            report.append("SDK: ").append(android.os.Build.VERSION.SDK_INT).append("\n\n");
            report.append(stack);

            String fileName = "crash_" + ts + ".txt";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore to write to public Downloads (no runtime permission required)
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OneUIApp");
                Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                         OutputStreamWriter ow = new OutputStreamWriter(os);
                         BufferedWriter bw = new BufferedWriter(ow)) {
                        bw.write(report.toString());
                    }
                }
            } else {
                // Legacy path for Android 9 and below — requires WRITE_EXTERNAL_STORAGE permission (runtime on M+)
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OneUIApp");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, fileName);
                try (FileWriter fw = new FileWriter(out, false)) {
                    fw.write(report.toString());
                    fw.flush();
                }
            }

            Log.e("CrashHandler", "Crash logged (Downloads/OneUIApp): " + fileName);
        } catch (Exception ex) {
            Log.e("CrashHandler", "Failed to log crash", ex);
        }

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        } else {
            System.exit(2);
        }
    }
              }
