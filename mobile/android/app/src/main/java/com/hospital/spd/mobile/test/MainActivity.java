package com.hospital.spd.mobile.test;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SpdSecureStorePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
