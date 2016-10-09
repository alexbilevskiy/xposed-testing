package com.example.xposedtesting;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Created by Alex on 07/09/2016.
 */
public class DefaultActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.default_activity);

        SharedPreferences pref = getSharedPreferences("user_settings", MODE_WORLD_READABLE);
        final SharedPreferences.Editor editor = pref.edit();

        editor.putFloat("upX", 0f);
        editor.putFloat("upY", 0f);
        editor.putFloat("upZ", 0f);
        editor.putFloat("posX", 0f);
        editor.putFloat("posY", 0f);
        editor.putFloat("posZ", 0f);
        editor.putFloat("dirX", 0f);
        editor.putFloat("dirY", 0f);
        editor.putFloat("dirZ", 0f);
        editor.putBoolean("upEnabled", false);
        editor.putBoolean("posEnabled", false);
        editor.putBoolean("dirEnabled", false);

        final EditText nearText = (EditText) findViewById(R.id.nearValue);
        final EditText farText = (EditText) findViewById(R.id.farValue);
        final EditText fovText = (EditText) findViewById(R.id.fieldOfViewValue);

        nearText.setText(Float.toString(pref.getFloat("near", 15f)));
        farText.setText(Float.toString(pref.getFloat("far", 2048f)));
        fovText.setText(Float.toString(pref.getFloat("fov", 40f)));

        nearText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                editor.putFloat("near", Float.parseFloat(nearText.getText().toString()));
                editor.apply();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        farText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                editor.putFloat("far", Float.parseFloat(farText.getText().toString()));
                editor.apply();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        fovText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                editor.putFloat("fov", Float.parseFloat(fovText.getText().toString()));
                editor.apply();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
    }


}