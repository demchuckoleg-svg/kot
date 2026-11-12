package com.kotokompas.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int BG = 0xFF10131D;
    private static final int CARD = 0xFF1A1F2D;
    private static final int ACCENT = 0xFFFFB454;
    private static final int MUTED = 0xFF9AA3B5;
    private static final int WHITE = 0xFFF6F4EF;
    private static final int GREEN = 0xFF92E6B5;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private final float[] gravity = new float[3];
    private final float[] magnetic = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private float heading;
    private int target = new Random().nextInt(360);
    private int score;
    private boolean found;
    private boolean hasHeading;
    private TextView headingText;
    private TextView statusText;
    private TextView targetText;
    private TextView scoreText;
    private TextView actionButton;
    private CompassView compassView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(20));
        root.setBackgroundColor(BG);

        TextView eyebrow = text("✦  МИНИ-ИГРА", 12, ACCENT, true);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow, wrap());

        TextView title = text("Котокомпас", 32, WHITE, true);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(7);
        root.addView(title, titleParams);

        TextView subtitle = text("Найди тайного кота — крути телефон!", 14, MUTED, false);
        LinearLayout.LayoutParams subParams = wrap();
        subParams.topMargin = dp(6);
        root.addView(subtitle, subParams);

        LinearLayout targetCard = new LinearLayout(this);
        targetCard.setOrientation(LinearLayout.VERTICAL);
        targetCard.setGravity(Gravity.CENTER);
        targetCard.setPadding(dp(16), dp(13), dp(16), dp(13));
        targetCard.setBackground(roundRect(CARD, 18));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(22);
        root.addView(targetCard, cardParams);

        targetText = text("КОТ ПРЯЧЕТСЯ НА СЕВЕРЕ", 12, MUTED, true);
        targetText.setText("КОТ ПРЯЧЕТСЯ НА " + compassName(target).toUpperCase(Locale.ROOT));
        targetText.setLetterSpacing(0.08f);
        targetCard.addView(targetText);
        TextView goalHint = text("Ищи направление на циферблате", 14, WHITE, false);
        LinearLayout.LayoutParams hintParams = wrap();
        hintParams.topMargin = dp(5);
        targetCard.addView(goalHint, hintParams);

        compassView = new CompassView(this);
        LinearLayout.LayoutParams compassParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        compassParams.topMargin = dp(12);
        compassParams.bottomMargin = dp(8);
        root.addView(compassView, compassParams);

        headingText = text("---°", 30, WHITE, true);
        root.addView(headingText, wrap());
        statusText = text("Ищу компас…", 15, MUTED, false);
        LinearLayout.LayoutParams statusParams = wrap();
        statusParams.topMargin = dp(5);
        root.addView(statusText, statusParams);

        LinearLayout scoreCard = new LinearLayout(this);
        scoreCard.setGravity(Gravity.CENTER);
        scoreCard.setPadding(dp(16), dp(10), dp(16), dp(10));
        scoreCard.setBackground(roundRect(CARD, 16));
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(-1, -2);
        scoreParams.topMargin = dp(17);
        root.addView(scoreCard, scoreParams);
        scoreText = text("🐾  Найдено котов: 0", 14, WHITE, true);
        scoreCard.addView(scoreText);

        actionButton = text("Новая цель  ↻", 16, BG, true);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setBackground(roundRect(ACCENT, 18));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(54));
        buttonParams.topMargin = dp(13);
        root.addView(actionButton, buttonParams);
        actionButton.setOnClickListener(v -> newTarget());

        TextView footer = text("Держи телефон ровно и медленно поворачивайся", 12, MUTED, false);
        LinearLayout.LayoutParams footerParams = wrap();
        footerParams.topMargin = dp(13);
        root.addView(footer, footerParams);
        setContentView(root);
    }

    private void newTarget() {
        target = new Random().nextInt(360);
        found = false;
        targetText.setText("КОТ ПРЯЧЕТСЯ НА " + compassName(target).toUpperCase(Locale.ROOT));
        actionButton.setText("Новая цель  ↻");
        updateReadout();
        compassView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            if (magnetometer != null) sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            hasHeading = true;
        } else {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, gravity, 0, 3);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetic, 0, 3);
            }
            hasHeading = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magnetic);
        }
        if (!hasHeading) return;
        SensorManager.getOrientation(rotationMatrix, orientation);
        float degrees = (float) Math.toDegrees(orientation[0]);
        heading = (degrees + 360f) % 360f;
        updateReadout();
        compassView.invalidate();
        float difference = signedDifference(target, Math.round(heading));
        if (!found && Math.abs(difference) <= 10f) celebrate();
    }

    private void updateReadout() {
        if (headingText == null) return;
        if (!hasHeading) {
            headingText.setText("---°");
            statusText.setText("Компас не найден — проверь датчики");
            return;
        }
        headingText.setText(String.format(Locale.getDefault(), "%03d°", Math.round(heading) % 360));
        if (found) {
            statusText.setText("Кот найден! Мяу! Поищем ещё? 🐱");
            statusText.setTextColor(GREEN);
        } else {
            float difference = signedDifference(target, Math.round(heading));
            if (Math.abs(difference) <= 10f) {
                statusText.setText("Почти! Не отпускай телефон…");
            } else if (Math.abs(difference) < 35f) {
                statusText.setText("Горячо! Ещё чуть-чуть " + (difference > 0 ? "вправо →" : "← влево"));
            } else {
                statusText.setText("Повернись " + (difference > 0 ? "вправо →" : "← влево") + " к коту");
            }
            statusText.setTextColor(MUTED);
        }
    }

    private void celebrate() {
        found = true;
        score++;
        scoreText.setText("🐾  Найдено котов: " + score);
        actionButton.setText("Искать ещё  ↻");
        updateReadout();
        if (Build.VERSION.SDK_INT >= 26) {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(100);
        }
    }

    private static float signedDifference(int destination, int current) {
        return ((destination - current + 540) % 360) - 180;
    }

    private static String compassName(int degrees) {
        String[] names = {"север", "северо-востоке", "востоке", "юго-востоке", "юге", "юго-западе", "западе", "северо-западе"};
        return names[(int) Math.round(degrees / 45.0) % 8];
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private class CompassView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] cardinal = {"N", "E", "S", "W"};

        CompassView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.39f;

            paint.setColor(CARD);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, radius + dp(18), paint);
            paint.setColor(0xFF32394A);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            canvas.drawCircle(cx, cy, radius + dp(4), paint);

            for (int i = 0; i < 36; i++) {
                double a = Math.toRadians(i * 10 - 90 - heading);
                float inner = radius - (i % 3 == 0 ? dp(10) : dp(5));
                paint.setColor(i % 9 == 0 ? ACCENT : 0xFF697286);
                paint.setStrokeWidth(i % 9 == 0 ? dp(2) : dp(1));
                canvas.drawLine(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                        cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(14));
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90 - 90 - heading);
                float labelRadius = radius - dp(23);
                paint.setColor(i == 0 ? ACCENT : MUTED);
                canvas.drawText(cardinal[i], cx + (float) Math.cos(a) * labelRadius,
                        cy + (float) Math.sin(a) * labelRadius + dp(5), paint);
            }

            float relative = (float) signedDifference(target, Math.round(heading));
            double targetAngle = Math.toRadians(relative - 90);
            float markerRadius = radius + dp(4);
            float tx = cx + (float) Math.cos(targetAngle) * markerRadius;
            float ty = cy + (float) Math.sin(targetAngle) * markerRadius;
            paint.setColor(ACCENT);
            canvas.drawCircle(tx, ty, dp(10), paint);
            paint.setColor(BG);
            paint.setTextSize(dp(11));
            canvas.drawText("🐱", tx, ty + dp(4), paint);

            Path needle = new Path();
            canvas.save();
            canvas.rotate(relative, cx, cy);
            needle.moveTo(cx, cy - radius + dp(12));
            needle.lineTo(cx - dp(10), cy + dp(7));
            needle.lineTo(cx, cy + dp(2));
            needle.lineTo(cx + dp(10), cy + dp(7));
            needle.close();
            paint.setColor(WHITE);
            canvas.drawPath(needle, paint);
            canvas.restore();
            paint.setColor(ACCENT);
            canvas.drawCircle(cx, cy, dp(5), paint);

            paint.setColor(MUTED);
            paint.setTextSize(dp(11));
            canvas.drawText("ТВОЁ НАПРАВЛЕНИЕ", cx, cy + radius + dp(42), paint);
        }
    }
}
