package com.nevra.browser;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NevraWebPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        if (!NevraWebPlugin.goBackIfPossible()) {
            super.onBackPressed();
        }
    }
}
