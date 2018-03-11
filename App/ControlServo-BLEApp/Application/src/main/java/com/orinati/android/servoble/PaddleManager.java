package com.orinati.android.servoble;

/**
 * Created by Ori Bar El on 09/03/2018.
 */

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public class PaddleManager {
    private BluetoothGattCharacteristic mCharacteristic;
    private BluetoothGatt               mGatt;
    boolean paused  = false;

    public void registerListener(BluetoothGatt gatt,
                                 BluetoothGattCharacteristic characteristic) {
        if (!paused) {
            return;
        }
        mGatt = gatt;
        mCharacteristic = characteristic;
        paused = false;
    }

    public void unregisterListener() {
        if (paused) {
            return;
        }
        paused = true;
        // Unregister listener
        //mSensorManager.unregisterListener(mRotationListener, mRotationSensor);
    }

    private void writePaddleState(Boolean isLeft,Boolean isUp) {
        mCharacteristic.setValue(getInstructionCode(isLeft,isUp));
        mGatt.writeCharacteristic(mCharacteristic);
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
            return new byte[]{0x1A};
        // Should not get here
        return new byte[]{0x00};
    }

}
