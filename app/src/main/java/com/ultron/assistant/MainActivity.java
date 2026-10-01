package com.ultron.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView status = findViewById(R.id.statusText);
        EditText aliasName = findViewById(R.id.aliasName);
        EditText aliasNumber = findViewById(R.id.aliasNumber);
        Button saveAlias = findViewById(R.id.saveAlias);
        Button startBtn = findViewById(R.id.startBtn);
        Button accessibilityBtn = findViewById(R.id.accessibilityBtn);

        requestPermissionsIfNeeded();

        saveAlias.setOnClickListener(v -> {
            String name = aliasName.getText().toString().trim().toLowerCase();
            String number = aliasNumber.getText().toString().trim();
            if (name.isEmpty() || number.isEmpty()) {
                Toast.makeText(this, "Enter alias and number", Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences p = getSharedPreferences("ultron_aliases", MODE_PRIVATE);
            p.edit().putString(name, number).apply();
            Toast.makeText(this, "Saved: " + name, Toast.LENGTH_SHORT).show();
        });

        startBtn.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
                Toast.makeText(this, "Enable 'Display over other apps', then tap Start ULTRON again.", Toast.LENGTH_LONG).show();
                return;
            }
            Intent service = new Intent(this, UltronService.class);
            startForegroundService(service);
            status.setText("ULTRON active — say: Hey ULTRON");
        });

        accessibilityBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void requestPermissionsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.POST_NOTIFICATIONS
            }, REQ);
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS
            }, REQ);
        }
    }
}
