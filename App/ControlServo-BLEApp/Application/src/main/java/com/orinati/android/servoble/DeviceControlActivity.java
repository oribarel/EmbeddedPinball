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

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME       = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS    = "DEVICE_ADDRESS";
    public static final String GAME_OVER    = "GAME OVER";

    private TextView                    mConnectionState;
    private TextView                    mDataField;
    private TextView                    mLivesText;
    private TextView                    mLivesLeft;
    private String                      mDeviceName;
    private String                      mDeviceAddress;
    private BluetoothLeService          mBluetoothLeService;
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private Button                      mPauseButton;
    private Button                      mLeftPaddleButton;
    private Button                      mRightPaddleButton;
    //private PaddleManager               mPaddleManager;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                updateConnectionState(R.string.connected);
                mLivesText.setVisibility(View.VISIBLE);
                mLivesLeft.setVisibility(View.VISIBLE);
                mLeftPaddleButton.setClickable(true);
                invalidateOptionsMenu();
            }
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateConnectionState(R.string.disconnected);
                mLivesText.setVisibility(View.INVISIBLE);
                mLivesLeft.setVisibility(View.INVISIBLE);
                invalidateOptionsMenu();
            }
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Find the write and notification characteristic of the board's GATT service
                initializeCharacteristic(mBluetoothLeService.getSupportedGattServices());
            }
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void initializeCharacteristic(List<BluetoothGattService> supportedGattServices) {
        if (supportedGattServices != null) {
            String service_uuid = null;
            String wr_uuid      = Constants.BOARD_WR;
            String notify_uuid  = Constants.BOARD_NOTIFY;
            // Finds the appropriate service
            for (BluetoothGattService gattService : supportedGattServices) {
                service_uuid = gattService.getUuid().toString();
                if (service_uuid.equals(Constants.BOARD_SERVICES)) {
                    // Retrieves the write characteristic
                    mWriteCharacteristic = gattService.getCharacteristic(UUID.fromString(wr_uuid));
                    // Retrives the notification characteristic
                    mNotifyCharacteristic = gattService.getCharacteristic((UUID.fromString(notify_uuid)));
                    // Enable the notification characteristic
                    mBluetoothLeService.enablePeerDeviceNotifyMe(true,mNotifyCharacteristic);
                    break;
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_control_layout);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mPauseButton = findViewById(R.id.button_pause);
        mPauseButton.setOnClickListener(new PauseClickListener());
        mConnectionState    = findViewById(R.id.connection_state);
        mDataField          = findViewById(R.id.data_value);
        mLeftPaddleButton   = findViewById(R.id.left_button);
        mLeftPaddleButton.setOnClickListener(new PaddleClickListener(true));
        mLeftPaddleButton.setOnLongClickListener(new PaddleLongClickListener(true));
        mLeftPaddleButton.setOnTouchListener(new PaddleTouchListener(true));
        mRightPaddleButton   = findViewById(R.id.right_button);
        mRightPaddleButton.setOnClickListener(new PaddleClickListener(false));
        mRightPaddleButton.setOnLongClickListener(new PaddleLongClickListener(false));
        mRightPaddleButton.setOnTouchListener(new PaddleTouchListener(false));
        mLivesText          = findViewById(R.id.text_lives);
        mLivesLeft          = findViewById(R.id.text_lives_number);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
            // Update number of lives left (based on the notification sent)
            if (Integer.parseInt(data) <= 0)
                mLivesLeft.setText(GAME_OVER);
            else
                mLivesLeft.setText(data);

        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    private class PauseClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //mPaddleManager.unregisterListener();
            mBluetoothLeService.disconnect();
            DeviceControlActivity.this.finish();
            onBackPressed();
        }
    }

    private class PaddleClickListener implements View.OnClickListener {
        boolean isLeft;

        public PaddleClickListener(boolean isLeft)
        {
            this.isLeft = isLeft;
        }

        @Override
        public void onClick(View v)
        {
            // sleep for 0.05 sec
            try
            {
                Thread.sleep(50);
            }
            catch(InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
            mBluetoothLeService.writeCharacteristic(mWriteCharacteristic,
                    getInstructionCode(isLeft,false));
            Log.d("myTag", "Hello from PaddleClickListener");


        }
    }


    private class PaddleLongClickListener implements View.OnLongClickListener {
        boolean isLeft;

        public PaddleLongClickListener(boolean isLeft)
        {
            this.isLeft = isLeft;
        }

        @Override
        public boolean onLongClick(View v)
        {
            Log.d("myTag", "Hello from PaddleLongClickListener");
            /*mBluetoothLeService.writeCharacteristic(mWriteCharacteristic,
                    getInstructionCode(isLeft,true));*/

            //telling the framework that the long click event is not consumed and further event handling is required
            return true;
        }
    }

    private class PaddleTouchListener implements View.OnTouchListener {
        boolean isLeft;

        public PaddleTouchListener(boolean isLeft)
        {
            this.isLeft = isLeft;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
            if(event.getAction()==MotionEvent.ACTION_DOWN )
            {
                Log.d("myTag", "Hello from PaddleTouchListener - ACTION_DOWN");
                mBluetoothLeService.writeCharacteristic(mWriteCharacteristic,
                        getInstructionCode(isLeft, true));
            }
            if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL)
            {
                Log.d("myTag", "Hello from PaddleTouchListener - ACTION_UP");
                mBluetoothLeService.writeCharacteristic(mWriteCharacteristic,
                        getInstructionCode(isLeft, false));
            }

            return false;
        }
    }

    public static byte[] getInstructionCode(Boolean isLeft,Boolean isUp)
    {
        if (isLeft && isUp)
            return new byte[]{0x1B};
        if (isLeft && !isUp)
            return new byte[]{0x1A};
        if (!isLeft && isUp)
            return new byte[]{0x2B};
        if (!isLeft && !isUp)
            return new byte[]{0x2A};
        // Should not get here
        return new byte[]{0x00};
    }

}
