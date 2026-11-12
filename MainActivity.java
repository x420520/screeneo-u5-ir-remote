package com.screeneo.u5irremote;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xff10151e;
    private static final int SURFACE = 0xff202c3d;
    private static final int FOREGROUND = 0xffedf3fc;
    private static final int SECONDARY = 0xffb7c4d5;
    private static final int ACCENT = 0xffbcdcff;
    private static final int POWER_FILL = 0xff5d3039;
    private static final String AIM_HINT = "小米 15 Pro 请用背面摄像头模组对准投影仪。";

    // The U5 reports Changhong NEC scancodes with the 0x40BF header.
    private enum Key {
        POWER("⏻ 电源", "电源", 0x12, POWER_FILL, FOREGROUND, true),
        INPUT("信号源", "信号源", 0x14),
        UP("↑", "上", 0x19),
        LEFT("←", "左", 0x46),
        OK("确定", "确定", 0x0a, ACCENT, BACKGROUND, false),
        RIGHT("→", "右", 0x47),
        DOWN("↓", "下", 0x1d),
        BACK("返回", "返回", 0x40),
        HOME("主页", "Android TV 主屏幕", -1),
        SETTINGS("设置", "系统设置", 0x0f),
        FOCUS("电子对焦", "电子对焦", 0x70),
        ASSIST("Google 助理", "Google 助理搜索入口", 0x44),
        VOLUME_DOWN("音量 −", "音量减", 0x1e),
        MUTE("静音", "静音", 0x10),
        VOLUME_UP("音量 +", "音量加", 0x1a),
        DISNEY("Disney+", "Disney+", 0x41),
        PRIME("Prime Video", "Prime Video", 0x50),
        YOUTUBE("YouTube", "YouTube", 0x55);

        final String label;
        final String description;
        final int command;
        final int background;
        final int foreground;
        final boolean supportsHold;

        Key(String label, String description, int command) {
            this(label, description, command, SURFACE, FOREGROUND, false);
        }

        Key(String label, String description, int command, int background, int foreground,
                boolean supportsHold) {
            this.label = label;
            this.description = description;
            this.command = command;
            this.background = background;
            this.foreground = foreground;
            this.supportsHold = supportsHold;
        }
    }

    private final ExecutorService emitter = Executors.newSingleThreadExecutor();
    private ConsumerIrManager infrared;
    private TextView status;
    private boolean hasEmitter;
    private boolean transmitting;
    private Button heldVolumeButton;
    private CountDownLatch volumeReleased;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        infrared = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        hasEmitter = infrared != null && infrared.hasIrEmitter();
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(32), dp(24), dp(24));
        scroll.addView(layout);
        setContentView(scroll);

        TextView heading = label("U5 红外遥控", 30, FOREGROUND);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(heading);
        add(layout, label("Philips Screeneo U5 · SCN550/INT", 15, SECONDARY), 5);
        add(layout, label(AIM_HINT, 14, SECONDARY), 11);

        add(layout, row(Key.POWER, Key.INPUT), 24);
        add(layout, row(null, Key.UP, null), 20);
        add(layout, row(Key.LEFT, Key.OK, Key.RIGHT), 8);
        add(layout, row(null, Key.DOWN, null), 8);
        add(layout, row(Key.BACK, Key.HOME, Key.SETTINGS), 20);
        add(layout, row(Key.FOCUS, Key.ASSIST), 10);
        add(layout, row(Key.VOLUME_DOWN, Key.MUTE, Key.VOLUME_UP), 10);
        add(layout, row(Key.DISNEY, Key.PRIME, Key.YOUTUBE), 14);

        status = label(hasEmitter ? "" : "这台手机未向应用提供红外发射器。", 14, SECONDARY);
        status.setGravity(Gravity.CENTER);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(layout, status, 16);
    }

    private LinearLayout row(Key... keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < keys.length; i++) {
            Key key = keys[i];
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1f);
            if (i > 0) params.leftMargin = dp(8);
            if (key == null) {
                row.addView(new Space(this), params);
            } else {
                row.addView(button(key), params);
            }
        }
        return row;
    }

    private Button button(Key key) {
        Button button = new Button(this);
        button.setText(key.label);
        button.setContentDescription(key.description);
        button.setAllCaps(false);
        button.setTextColor(key.foreground);
        button.setTextSize(key.label.length() == 1 ? 24 : 16);
        button.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(key.background);
        shape.setCornerRadius(dp(17));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff), shape, null));
        button.setEnabled(hasEmitter);
        button.setAlpha(hasEmitter ? 1f : 0.55f);
        button.setOnClickListener(view -> send(key, false));
        if (key == Key.VOLUME_UP || key == Key.VOLUME_DOWN) {
            setVolumeHoldListener(button, key);
        } else if (key.supportsHold) {
            button.setOnLongClickListener(view -> {
                send(key, true);
                return true;
            });
        }
        return button;
    }

    private void setVolumeHoldListener(Button button, Key key) {
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    stopVolumeHold();
                    heldVolumeButton = button;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    if (heldVolumeButton == button) stopVolumeHold();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (heldVolumeButton == button && (event.getX() < 0 || event.getY() < 0
                            || event.getX() >= view.getWidth() || event.getY() >= view.getHeight())) {
                        stopVolumeHold();
                    }
                    break;
                default:
                    break;
            }
            return false;
        });
        button.setOnLongClickListener(view -> {
            if (heldVolumeButton != button || !hasEmitter || transmitting) return false;
            CountDownLatch released = new CountDownLatch(1);
            volumeReleased = released;
            send(key, () -> repeatVolume(key, released));
            return true;
        });
    }

    private void stopVolumeHold() {
        heldVolumeButton = null;
        if (volumeReleased != null) {
            volumeReleased.countDown();
            volumeReleased = null;
        }
    }

    private void repeatVolume(Key key, CountDownLatch released) {
        IrSignal signal = IrSignal.changhong(key.command);
        int[] frame = signal.pattern;
        while (released.getCount() != 0) {
            long nextFrame = SystemClock.elapsedRealtime() + IrSignal.REPEAT_INTERVAL_MS;
            infrared.transmit(signal.frequency, frame);
            frame = IrSignal.repeatPattern();
            long remaining = nextFrame - SystemClock.elapsedRealtime();
            if (remaining > 0) {
                try {
                    if (released.await(remaining, TimeUnit.MILLISECONDS)) return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void send(Key key, boolean hold) {
        send(key, () -> {
            if (key == Key.HOME) {
                returnToHome();
            } else {
                transmit(key, hold);
            }
        });
    }

    private void send(Key key, Runnable transmission) {
        if (!hasEmitter || transmitting) return;
        transmitting = true;
        status.setText("正在发射 " + key.description);
        emitter.execute(() -> {
            String result;
            try {
                transmission.run();
                result = "已发送 " + key.description;
            } catch (RuntimeException failure) {
                result = "红外发射失败";
            }
            String message = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                transmitting = false;
                status.setText(message);
            });
        });
    }

    private void returnToHome() {
        // The U5 input panel has three entries and stops at Android TV home at the bottom.
        transmit(Key.INPUT, false);
        SystemClock.sleep(650);
        transmit(Key.DOWN, false);
        SystemClock.sleep(190);
        transmit(Key.DOWN, false);
        SystemClock.sleep(190);
        transmit(Key.OK, false);
    }

    private void transmit(Key key, boolean hold) {
        IrSignal signal = IrSignal.changhong(key.command);
        infrared.transmit(signal.frequency, hold ? signal.heldPattern() : signal.pattern);
    }

    @Override
    protected void onPause() {
        stopVolumeHold();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) stopVolumeHold();
    }

    @Override
    protected void onDestroy() {
        stopVolumeHold();
        emitter.shutdown();
        super.onDestroy();
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private void add(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
