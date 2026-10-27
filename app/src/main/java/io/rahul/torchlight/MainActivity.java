package io.rahul.torchlight;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.RenderEffect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST = 7001;

    private CameraManager cameraManager;
    private String torchCameraId;
    private int maxTorchStrength = 1;
    private boolean strengthSupported;
    private boolean flashAvailable;
    private boolean flashDiscoveryPending;
    private boolean permissionDialogPending;
    private int currentLevel = 0;
    private int pendingLevel = 0;

    private TextView statusPill;
    private TextView percentageText;
    private TextView switchHint;
    private TextView hardwareHint;
    private SeekBar brightnessSeekBar;
    private FrameLayout torchButton;
    private View torchGlow;
    private GlassPanel strengthPanel;

    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (torchCameraId != null && torchCameraId.equals(cameraId)) {
                if (!enabled && currentLevel > 0) {
                    currentLevel = 0;
                    updateUi();
                } else if (enabled && currentLevel == 0) {
                    currentLevel = 65;
                    if (brightnessSeekBar != null) brightnessSeekBar.setProgress(currentLevel);
                    updateUi();
                }
            }
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (torchCameraId != null && torchCameraId.equals(cameraId)) {
                if (hardwareHint != null) {
                    hardwareHint.setText("Flash is temporarily unavailable. Another app may be using the camera.");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            discoverFlash();
        } else {
            // Keep the controls available until the user grants permission and the camera list can be inspected.
            flashDiscoveryPending = true;
            flashAvailable = true;
        }
        buildInterface();
        cameraManager.registerTorchCallback(torchCallback, new Handler(Looper.getMainLooper()));
        updateUi();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void discoverFlash() {
        flashAvailable = false;
        torchCameraId = null;
        maxTorchStrength = 1;
        strengthSupported = false;
        try {
            String fallbackId = null;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (!Boolean.TRUE.equals(hasFlash)) continue;
                if (fallbackId == null) fallbackId = id;
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id;
                    break;
                }
            }
            if (torchCameraId == null) torchCameraId = fallbackId;
            flashAvailable = torchCameraId != null;
            if (flashAvailable && Build.VERSION.SDK_INT >= 33) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(torchCameraId);
                Integer max = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                if (max != null && max > 1) {
                    maxTorchStrength = max;
                    strengthSupported = true;
                }
            }
        } catch (CameraAccessException | SecurityException ignored) {
            flashAvailable = false;
            torchCameraId = null;
        }
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 10, 20));

        BackgroundGlow background = new BackgroundGlow(this);
        root.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(22));
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                top = insets.getInsets(WindowInsets.Type.systemBars()).top;
                bottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(dp(22), dp(18) + top, dp(22), dp(22) + bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("TORCHLIGHT", 17, Color.WHITE, true);
        title.setLetterSpacing(0.14f);
        titleStack.addView(title);
        TextView subtitle = text("A little light. Just right.", 12, 0xFF9AA3B8, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-2, -2);
        subParams.topMargin = dp(5);
        titleStack.addView(subtitle, subParams);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1f));

        statusPill = text("READY", 10, 0xFFFF8295, true);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dp(13), dp(8), dp(13), dp(8));
        statusPill.setBackground(rounded(0x25FF526D, 0x66FF526D, 30));
        header.addView(statusPill);
        content.addView(header);

        FrameLayout center = new FrameLayout(this);
        LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        centerParams.topMargin = dp(10);
        centerParams.bottomMargin = dp(6);
        content.addView(center, centerParams);

        torchGlow = new View(this);
        GradientDrawable glowDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x55FF526D, 0x16FF526D, 0x00FF526D});
        glowDrawable.setShape(GradientDrawable.OVAL);
        torchGlow.setBackground(glowDrawable);
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER);
        center.addView(torchGlow, glowParams);
        if (Build.VERSION.SDK_INT >= 31) {
            torchGlow.setRenderEffect(RenderEffect.createBlurEffect(32f, 32f, Shader.TileMode.CLAMP));
        }

        torchButton = new FrameLayout(this);
        GradientDrawable orb = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF7185, 0xFFEC3155, 0xFFB91F42});
        orb.setShape(GradientDrawable.OVAL);
        orb.setStroke(dp(1), 0x66FFFFFF);
        torchButton.setBackground(orb);
        torchButton.setElevation(dp(18));
        torchButton.setClickable(true);
        torchButton.setFocusable(true);
        torchButton.setContentDescription("Toggle flashlight");
        torchButton.setForeground(makeRipple(0x44FFFFFF));
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER);
        center.addView(torchButton, buttonParams);
        torchButton.addView(new FlashIcon(this), new FrameLayout.LayoutParams(dp(48), dp(58), Gravity.CENTER));
        torchButton.setOnClickListener(v -> {
            if (currentLevel > 0) applyTorchLevel(0);
            else applyTorchLevel(65);
        });

        LinearLayout centerText = new LinearLayout(this);
        centerText.setGravity(Gravity.CENTER);
        centerText.setOrientation(LinearLayout.VERTICAL);
        TextView bigLabel = text("YOUR LIGHT, YOUR WAY", 11, 0xFFE6E9F3, true);
        bigLabel.setLetterSpacing(0.12f);
        centerText.addView(bigLabel);
        switchHint = text("Tap the button to switch on", 12, 0xFF929BB0, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-2, -2);
        hintParams.topMargin = dp(8);
        centerText.addView(switchHint, hintParams);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        textParams.bottomMargin = dp(13);
        center.addView(centerText, textParams);

        strengthPanel = new GlassPanel(this);
        LinearLayout panelContent = new LinearLayout(this);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContent.setPadding(dp(18), dp(18), dp(18), dp(16));
        strengthPanel.addView(panelContent, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2);
        panelParams.bottomMargin = dp(12);
        content.addView(strengthPanel, panelParams);

        LinearLayout levelHeader = new LinearLayout(this);
        levelHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView intensity = text("BRIGHTNESS", 11, 0xFFD4D9E7, true);
        intensity.setLetterSpacing(0.12f);
        levelHeader.addView(intensity, new LinearLayout.LayoutParams(0, -2, 1f));
        percentageText = text("0%", 25, Color.WHITE, true);
        levelHeader.addView(percentageText);
        panelContent.addView(levelHeader);

        brightnessSeekBar = new SeekBar(this);
        brightnessSeekBar.setMax(100);
        brightnessSeekBar.setProgress(0);
        brightnessSeekBar.setContentDescription("Flashlight brightness from 0 to 100 percent");
        brightnessSeekBar.setSplitTrack(false);
        brightnessSeekBar.setProgressTintList(ColorStateList.valueOf(0xFFFF526D));
        brightnessSeekBar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        brightnessSeekBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(-1, dp(38));
        seekParams.topMargin = dp(10);
        panelContent.addView(brightnessSeekBar, seekParams);
        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                percentageText.setText(progress + "%");
                if (fromUser) applyTorchLevel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        LinearLayout rangeLabels = new LinearLayout(this);
        rangeLabels.setGravity(Gravity.CENTER_VERTICAL);
        TextView offLabel = text("0 · OFF", 10, 0xFF8C95AA, false);
        TextView maxLabel = text("100 · MAX", 10, 0xFF8C95AA, false);
        rangeLabels.addView(offLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        maxLabel.setGravity(Gravity.RIGHT);
        rangeLabels.addView(maxLabel);
        panelContent.addView(rangeLabels);

        hardwareHint = text("Ready when you are.", 11, 0xFF9AA3B8, false);
        hardwareHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hardwareParams = new LinearLayout.LayoutParams(-1, -2);
        hardwareParams.topMargin = dp(12);
        panelContent.addView(hardwareHint, hardwareParams);

        TextView footer = text("CAMERA FLASH  ·  ON-DEVICE CONTROL", 9, 0xFF6D768B, true);
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.10f);
        content.addView(footer);

        setContentView(root);
    }

    private void applyTorchLevel(int requestedLevel) {
        int level = Math.max(0, Math.min(100, requestedLevel));
        pendingLevel = level;
        currentLevel = level;
        updateUi();

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionDialogPending) {
                permissionDialogPending = true;
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            }
            if (hardwareHint != null) hardwareHint.setText("Allow camera access to control the flash.");
            return;
        }

        if (flashDiscoveryPending || torchCameraId == null) {
            discoverFlash();
            flashDiscoveryPending = false;
        }
        if (!flashAvailable || torchCameraId == null) {
            currentLevel = 0;
            updateUi();
            if (hardwareHint != null) hardwareHint.setText("This device does not expose a camera flash.");
            return;
        }

        try {
            if (level == 0) {
                cameraManager.setTorchMode(torchCameraId, false);
            } else if (Build.VERSION.SDK_INT >= 33 && strengthSupported) {
                int strength = 1 + Math.round((level - 1) * (maxTorchStrength - 1) / 99f);
                strength = Math.max(1, Math.min(maxTorchStrength, strength));
                cameraManager.setTorchStrengthLevel(torchCameraId, strength);
            } else {
                cameraManager.setTorchMode(torchCameraId, true);
            }
        } catch (CameraAccessException | IllegalArgumentException | SecurityException exception) {
            currentLevel = 0;
            updateUi();
            if (hardwareHint != null) {
                hardwareHint.setText("Flash unavailable. Another app may be using the camera.");
            }
            Toast.makeText(this, "Couldn't access the flashlight", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            permissionDialogPending = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                applyTorchLevel(pendingLevel);
            } else {
                currentLevel = 0;
                if (brightnessSeekBar != null) brightnessSeekBar.setProgress(0);
                updateUi();
                if (hardwareHint != null) hardwareHint.setText("Camera permission is needed to use the flashlight.");
            }
        }
    }

    private void updateUi() {
        if (percentageText != null) percentageText.setText(currentLevel + "%");
        if (brightnessSeekBar != null && brightnessSeekBar.getProgress() != currentLevel) {
            brightnessSeekBar.setProgress(currentLevel);
        }
        boolean on = currentLevel > 0;
        if (statusPill != null) {
            statusPill.setText(on ? "ON" : "READY");
            statusPill.setTextColor(on ? 0xFFFFA3B0 : 0xFFFF8295);
            statusPill.setBackground(rounded(on ? 0x33FF526D : 0x25FF526D, 0x66FF526D, 30));
        }
        if (switchHint != null) {
            switchHint.setText(on ? "Tap the button to switch off" : "Tap the button to switch on");
        }
        if (torchGlow != null) {
            torchGlow.setAlpha(on ? (0.30f + (currentLevel / 100f) * 0.70f) : 0.40f);
        }
        if (torchButton != null) torchButton.setAlpha(on ? 1f : 0.88f);
        if (hardwareHint != null && flashDiscoveryPending) {
            hardwareHint.setText("Allow camera access to detect flash controls.");
        } else if (hardwareHint != null && flashAvailable) {
            if (strengthSupported) {
                hardwareHint.setText("Fine control available · " + maxTorchStrength + " hardware levels");
            } else {
                hardwareHint.setText("This flash supports ON/OFF only; fine brightness needs compatible hardware.");
            }
        } else if (hardwareHint != null && !flashAvailable) {
            hardwareHint.setText("No camera flash was detected on this device.");
            if (brightnessSeekBar != null) brightnessSeekBar.setEnabled(false);
            if (torchButton != null) torchButton.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (cameraManager != null) {
            try {
                cameraManager.unregisterTorchCallback(torchCallback);
            } catch (Exception ignored) { }
            if (currentLevel > 0 && torchCameraId != null
                    && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try { cameraManager.setTorchMode(torchCameraId, false); } catch (Exception ignored) { }
            }
        }
        super.onDestroy();
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private android.graphics.drawable.Drawable makeRipple(int color) {
        android.graphics.drawable.Drawable mask = new GradientDrawable();
        ((GradientDrawable) mask).setShape(GradientDrawable.OVAL);
        return new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(color), null, mask);
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class GlassPanel extends FrameLayout {
        GlassPanel(Context context) {
            super(context);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0x1EFFFFFF);
            background.setCornerRadius(dp(25));
            background.setStroke(dp(1), 0x35FFFFFF);
            setBackground(background);
            setClipToPadding(false);
            setElevation(dp(5));
        }
    }

    private final class BackgroundGlow extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BackgroundGlow(Context context) { super(context); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            paint.setShader(new RadialGradient(w * 0.16f, h * 0.24f, w * 0.60f,
                    new int[]{0x33FF365D, 0x14A12C67, 0x00080A14}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new RadialGradient(w * 0.92f, h * 0.57f, w * 0.58f,
                    new int[]{0x242C5ED6, 0x10274FAD, 0x00080A14}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    private final class FlashIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path bolt = new Path();
        FlashIcon(Context context) {
            super(context);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sx = getWidth() / 48f;
            float sy = getHeight() / 58f;
            canvas.save();
            canvas.scale(sx, sy);
            bolt.reset();
            bolt.moveTo(27, 2);
            bolt.lineTo(9, 31);
            bolt.lineTo(21, 31);
            bolt.lineTo(18, 55);
            bolt.lineTo(40, 23);
            bolt.lineTo(27, 23);
            bolt.close();
            canvas.drawPath(bolt, paint);
            canvas.restore();
        }
    }
}
