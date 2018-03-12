/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.orinati.android.servoble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends Activity {
    private ArrayList<BluetoothDevice>  mLeDevices;
    private BluetoothDevice             mBoardDevice;
    private BluetoothAdapter            mBluetoothAdapter;
    private boolean                     mScanning;
    private Handler                     mHandler;
    private View                        mView;
    private Button                      mStartButton;
    private Toast                       mToast;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 456;
    private boolean permissionChecked = false;
    private boolean scanStarted       = false;
    private boolean board_found        = false;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mView = getLayoutInflater().inflate(R.layout.actionbar_indeterminate_progress, null);
        setContentView(mView);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // grant permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, yay! Start the Bluetooth device scan.
                } else {
                    // Alert the user that this application requires the location permission to perform the scan.
                }
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) { // If not scanning for device
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            mView.findViewById(R.id.prog_bar).setVisibility(View.INVISIBLE);

            // Start button - connect to board application and send data
            mStartButton = mView.findViewById(R.id.button_start);

            if (boardFound()) { // If can connect to board
                if (mToast != null) {
                    mToast.cancel();
                }
                mStartButton.setClickable(true);
                mStartButton.setText(R.string.button_start);
                mStartButton.setOnClickListener(new StartClickListener());
                mStartButton.setVisibility(View.VISIBLE);
            }
            else { // Couldn't connect to board
                if (scanStarted) {
                    mToast = Toast.makeText(this, R.string.dev_not_found, Toast.LENGTH_SHORT);
                    mToast.show();
                }
                mStartButton.setVisibility(View.INVISIBLE);
                mStartButton.setClickable(false);
            }
        }
        else {
            mView.findViewById(R.id.button_start).setVisibility(View.INVISIBLE);
            mView.findViewById(R.id.prog_bar).setVisibility(View.VISIBLE);
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
        }
        return true;
    }

    private boolean boardFound() {
        String devName;
        if(board_found)
            return true;
        for (BluetoothDevice dev : mLeDevices) { // Check if the board is one the found devices
            devName = dev.getName();
            if (devName != null && devName.equals(Constants.TI_CC1350_APP)) {
                mBoardDevice = dev;
                board_found = true;
                return true;
            }
        }
        // Board not amongst the devices that were found
        mBoardDevice = null;
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDevices.clear();
                mBoardDevice = null;
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes device list
        mLeDevices = new ArrayList<>();
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDevices.clear();
        if (mToast != null) {
            mToast.cancel();
        }
    }

    private void scanLeDevice(final boolean enable) {
        scanStarted       = false;
        board_found        = false;
        if (enable) {
            scanStarted = true;
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
        else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }


    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // For each device - if not in device list --> add it
                    if (!mLeDevices.contains(device)) {
                        mLeDevices.add(device);
                    }
                }
            });
        }
    };

    // Listener for start button
    private class StartClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (mBoardDevice == null) {
                return;
            }
            final Intent intent = new Intent(DeviceScanActivity.this,
                                             DeviceControlActivity.class);
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, mBoardDevice.getName());
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, mBoardDevice.getAddress());
            if (mScanning) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mScanning = false;
            }
            // Start the connect activity
            startActivity(intent);
        }
    }
}