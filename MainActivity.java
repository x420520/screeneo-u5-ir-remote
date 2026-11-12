package com.screeneo.u5irremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xff101721;
    private static final int SURFACE = 0xff243244;
    private static final int PANEL = 0xff192331;
    private static final int OUTLINE = 0xff334558;
    private static final int FOREGROUND = 0xffedf3fc;
    private static final int SECONDARY = 0xffb7c4d5;
    private static final int ACCENT = 0xffa9f1d7;
    private static final int POWER_FILL = 0xff482b37;
    private static final int POWER_TEXT = 0xffffbdc8;
    private static final String HAPTIC_FEEDBACK = "haptic_feedback";

    // The U5 reports Changhong NEC scancodes with the 0x40BF header.
    private enum Key {
        POWER("电源", "电源", 0x12),
        INPUT("信号源", "信号源", 0x14),
        UP("", "上", 0x19),
        LEFT("", "左", 0x46),
        OK("确定", "确定", 0x0a),
        RIGHT("", "右", 0x47),
        DOWN("", "下", 0x1d),
        BACK("返回", "返回", 0x40),
        HOME("主页", "主页", -1),
        SETTINGS("投影设置", "投影设置", 0x0f),
        FOCUS("电子对焦", "电子对焦", 0x70),
        ASSIST("Google 助理", "Google 助理搜索入口", 0x44),
        VOLUME_DOWN("音量", "音量减", 0x1e),
        MUTE("静音", "静音", 0x10),
        VOLUME_UP("音量", "音量加", 0x1a),
        DISNEY("Disney+", "Disney+", 0x41),
        PRIME("Prime Video", "Prime Video", 0x50),
        YOUTUBE("YouTube", "YouTube", 0x55);

        final String label;
        final String description;
        final int command;
        Key(String label, String description, int command) {
            this.label = label;
            this.description = description;
            this.command = command;
        }
    }

    private final ExecutorService emitter = Executors.newSingleThreadExecutor();
    private ConsumerIrManager infrared;
    private TextView status;
    private View feedbackView;
    private SharedPreferences preferences;
    private boolean hasEmitter;
    private boolean transmitting;
    private Button heldVolumeButton;
    private CountDownLatch volumeReleased;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("remote_settings", MODE_PRIVATE);
        infrared = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        hasEmitter = infrared != null && infrared.hasIrEmitter();
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(BACKGROUND);
        FrameLayout content = new FrameLayout(this);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(12));
        int width = Math.min(460, getResources().getConfiguration().screenWidthDp);
        content.addView(layout, new FrameLayout.LayoutParams(dp(width), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        feedbackView = layout;
        feedbackView.setHapticFeedbackEnabled(true);
        setContentView(scroll);

        add(layout, header(), 0);
        add(layout, row(64, Key.POWER, Key.INPUT), 16);
        add(layout, directionPad(), 14);
        add(layout, row(62, Key.BACK, Key.HOME, Key.SETTINGS), 14);
        add(layout, row(68, Key.VOLUME_DOWN, Key.MUTE, Key.VOLUME_UP), 10);
        add(layout, row(60, Key.FOCUS, Key.ASSIST), 10);
        add(layout, row(52, Key.DISNEY, Key.PRIME, Key.YOUTUBE), 10);

        status = label(hasEmitter ? "" : "这台手机没有可用的红外发射器", 12, SECONDARY);
        status.setGravity(Gravity.CENTER);
        status.setMinHeight(dp(24));
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(layout, status, 8);
    }

    private View header() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.mipmap.ic_launcher);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -2, 1f);
        identityParams.setMarginStart(dp(10));
        identityParams.setMarginEnd(dp(8));
        header.addView(identity, identityParams);
        TextView heading = label("U5 红外遥控", 22, FOREGROUND);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        identity.addView(heading);
        add(identity, label("Screeneo U5 · SCN550/INT", 12, SECONDARY), 2);

        Button settings = control("", "应用设置", RemoteIcon.Shape.SETTINGS, PANEL, ACCENT, 17);
        settings.setOnClickListener(view -> showSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private void showSettings() {
        Switch vibration = new Switch(this);
        vibration.setText("按键震动");
        vibration.setTextSize(16);
        vibration.setMinHeight(dp(52));
        vibration.setHapticFeedbackEnabled(false);
        vibration.setChecked(preferences.getBoolean(HAPTIC_FEEDBACK, true));
        vibration.setOnCheckedChangeListener((view, checked) ->
                preferences.edit().putBoolean(HAPTIC_FEEDBACK, checked).apply());
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(8), dp(24), dp(12));
        panel.addView(vibration, new LinearLayout.LayoutParams(-1, -2));
        add(panel, label("震动强度和可用性取决于手机的系统触感设置。", 14, SECONDARY), 4);
        new AlertDialog.Builder(this).setTitle("应用设置")
                .setView(panel).setPositiveButton("完成", null).show();
    }

    private View directionPad() {
        FrameLayout frame = new FrameLayout(this);
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(shape(PANEL, 118, OUTLINE));
        frame.addView(disc, new FrameLayout.LayoutParams(dp(232), dp(232), Gravity.CENTER));
        placeDirection(disc, Key.UP, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 56);
        placeDirection(disc, Key.LEFT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 56);
        placeDirection(disc, Key.OK, Gravity.CENTER, 76);
        placeDirection(disc, Key.RIGHT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 56);
        placeDirection(disc, Key.DOWN, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 56);
        return frame;
    }

    private void placeDirection(FrameLayout disc, Key key, int gravity, int size) {
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(dp(size), dp(size), gravity);
        position.setMargins(dp(9), dp(9), dp(9), dp(9));
        disc.addView(button(key, size / 2), position);
    }

    private LinearLayout row(int height, Key... keys) {
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < keys.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
            if (i > 0) params.setMarginStart(dp(8));
            row.addView(button(keys[i], 17), params);
        }
        return row;
    }

    private GradientDrawable shape(int fill, int corner, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(corner));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private Button control(String text, String description, RemoteIcon.Shape icon,
            int fill, int color, int corner) {
        Button button = new Button(this);
        button.setText(text);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setTextColor(color);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4), icon != null && !text.isEmpty() ? dp(8) : 0, dp(4), 0);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setStateListAnimator(null);
        button.setHapticFeedbackEnabled(false);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff),
                shape(fill, corner, fill == PANEL ? OUTLINE : 0), null));
        if (icon != null) {
            RemoteIcon glyph = new RemoteIcon(icon, color, dp(text.isEmpty() ? 27 : 21));
            if (text.isEmpty()) {
                button.setForegroundGravity(Gravity.CENTER);
                button.setForeground(glyph);
            } else {
                button.setCompoundDrawablePadding(dp(4));
                button.setCompoundDrawables(null, glyph, null, null);
            }
        }
        return button;
    }

    private Button button(Key key, int corner) {
        int fill = SURFACE;
        int color = FOREGROUND;
        RemoteIcon.Shape icon;
        switch (key) {
            case POWER: icon = RemoteIcon.Shape.POWER; fill = POWER_FILL; color = POWER_TEXT; break;
            case INPUT: icon = RemoteIcon.Shape.INPUT; break;
            case UP: icon = RemoteIcon.Shape.UP; break;
            case DOWN: icon = RemoteIcon.Shape.DOWN; break;
            case LEFT: icon = RemoteIcon.Shape.LEFT; break;
            case RIGHT: icon = RemoteIcon.Shape.RIGHT; break;
            case OK: icon = null; fill = ACCENT; color = BACKGROUND; break;
            case BACK: icon = RemoteIcon.Shape.BACK; break;
            case HOME: icon = RemoteIcon.Shape.HOME; break;
            case SETTINGS: icon = RemoteIcon.Shape.SETTINGS; break;
            case FOCUS: icon = RemoteIcon.Shape.FOCUS; break;
            case ASSIST: icon = RemoteIcon.Shape.ASSIST; break;
            case VOLUME_DOWN: icon = RemoteIcon.Shape.MINUS; color = ACCENT; break;
            case MUTE: icon = RemoteIcon.Shape.MUTE; break;
            case VOLUME_UP: icon = RemoteIcon.Shape.PLUS; color = ACCENT; break;
            default: icon = null; fill = PANEL; color = SECONDARY; break;
        }
        Button button = control(key.label, key.description, icon, fill, color, corner);
        if (key == Key.OK) button.setTextSize(19);
        button.setEnabled(hasEmitter);
        button.setAlpha(hasEmitter ? 1f : 0.55f);
        button.setOnClickListener(view -> send(key, false));
        if (key == Key.VOLUME_UP || key == Key.VOLUME_DOWN) {
            setVolumeHoldListener(button, key);
        } else if (key == Key.POWER) {
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
        if (preferences.getBoolean(HAPTIC_FEEDBACK, true)) {
            feedbackView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
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
