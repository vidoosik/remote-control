package com.example.remotecontrolapp;

import android.app.Activity; import android.hardware.ConsumerIrManager; import android.os.Build; import android.os.Bundle; import android.view.MotionEvent; import android.view.View; import android.widget.Button; import android.widget.Toast; import java.util.concurrent.ExecutorService; import java.util.concurrent.Executors;

public class MainActivity extends Activity { private ConsumerIrManager irManager; private final ExecutorService executor = Executors.newSingleThreadExecutor(); private volatile boolean isHolding = false; private volatile boolean isLongPress = false;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
    if (irManager == null || !irManager.hasIrEmitter()) {
        Toast.makeText(this, "ИК-порт не доступен!", Toast.LENGTH_SHORT).show();
        return;
    }

    setupButton(R.id.nextButton, 0x0808E01F);
    setupButton(R.id.volUpButton, 0x08086897);
    setupButton(R.id.volDownButton, 0x0808E817);
}

private void setupButton(int buttonId, final int necCode) {
    Button button = findViewById(buttonId);
    if (button != null) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private Thread holdThread;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isHolding = true;
                        isLongPress = false;

                        holdThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(500);
                                    if (isHolding) {
                                        isLongPress = true;
                                        sendNEC(necCode, false, false);
                                        startHoldTransmission(necCode);
                                    }
                                } catch (InterruptedException ignored) {}
                            }
                        });
                        holdThread.start();
                        break;

                    case MotionEvent.ACTION_UP:
                        isHolding = false;
                        if (!isLongPress) {
                            sendNEC(necCode, false, true);
                        }
                        break;
                }
                return true;
            }
        });
    }
}

private void startHoldTransmission(final int necCode) {
    long cycleTime = 108;
    long packetWithout562Duration = 67;
    long repeatPacketDuration = 23;

    sendNEC(necCode, false, false);

    long remainingTime = cycleTime - packetWithout562Duration;

    try {
        Thread.sleep(remainingTime);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }

    while (isHolding) {
        sendNEC(necCode, true, false);
        try {
            Thread.sleep(cycleTime - repeatPacketDuration);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

private void sendNEC(int necCode, boolean repeat, boolean full) {
    try {
        if (irManager == null || !irManager.hasIrEmitter()) {
            showToast("ИК-излучатель не доступен!");
            return;
        }

        int frequency = 38000;
        int[] pattern;
        if (repeat) {
            pattern = new int[]{9000, 2250, 562};
            showToast("Отправлен повторный пакет");
        } else {
            pattern = createNECPattern(necCode, full);
            showToast(full ? "Отправлен первый пакет (полный)" : "Отправлен первый пакет (без 562)");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            irManager.transmit(frequency, pattern);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}

private int[] createNECPattern(int necCode, boolean includeFinal) {
    int size = includeFinal ? 67 : 66;
    int[] pattern = new int[size];
    int index = 0;

    pattern[index++] = 9000;
    pattern[index++] = 4500;

    for (int i = 31; i >= 0; i--) {
        if ((necCode & (1 << i)) != 0) {
            pattern[index++] = 562;
            pattern[index++] = 1690;
        } else {
            pattern[index++] = 562;
            pattern[index++] = 562;
        }
    }

    if (includeFinal) {
        pattern[index] = 562;
    }

    return pattern;
}

private void showToast(final String message) {
    runOnUiThread(new Runnable() {
        @Override
        public void run() {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    });
}

}

