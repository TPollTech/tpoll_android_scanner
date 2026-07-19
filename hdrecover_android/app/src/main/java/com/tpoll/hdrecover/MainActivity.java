package com.tpoll.hdrecover;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.tpoll.hdrecover.USB_PERMISSION";
    private static final int REQUEST_OUTPUT_TREE = 2001;

    private final int bg = Color.rgb(11, 16, 23);
    private final int surface = Color.rgb(23, 31, 42);
    private final int text = Color.rgb(235, 241, 249);
    private final int muted = Color.rgb(158, 171, 188);
    private final int accent = Color.rgb(105, 167, 255);

    private UsbManager usbManager;
    private Spinner deviceSpinner;
    private Button refreshButton;
    private Button folderButton;
    private Button startButton;
    private Button cancelButton;
    private TextView folderText;
    private TextView statusText;
    private TextView statsText;
    private TextView logText;
    private ProgressBar progressBar;

    private final List<UsbEntry> usbEntries = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean running = false;
    private Uri outputTree;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device;
                if (Build.VERSION.SDK_INT >= 33) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
                } else {
                    //noinspection deprecation
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                }
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted && device != null) {
                    beginRecovery(device);
                } else {
                    setStatus("Permissão USB negada.");
                    appendLog("O Android não liberou acesso ao dispositivo USB.");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                cancelled.set(true);
                appendLog("Dispositivo USB removido. A operação será interrompida com segurança.");
                refreshDevices();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            Toast.makeText(this, "Este aparelho não oferece USB Host/OTG.", Toast.LENGTH_LONG).show();
        }

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerUsbReceiver();
        buildUi();
        restoreOutputFolder();
        refreshDevices();
    }

    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = makeText("HdRecover Mobile", 27, text, true);
        root.addView(title);
        TextView subtitle = makeText(
                "Recuperação profunda de HD, SSD e pendrive conectado por USB OTG.",
                14, muted, false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        root.addView(sectionTitle("1. Dispositivo de origem"));
        LinearLayout deviceRow = horizontal();
        deviceSpinner = new Spinner(this);
        deviceSpinner.setBackgroundColor(surface);
        deviceRow.addView(deviceSpinner, new LinearLayout.LayoutParams(0, dp(52), 1f));
        refreshButton = new Button(this);
        refreshButton.setText("Atualizar");
        refreshButton.setOnClickListener(v -> refreshDevices());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(52));
        refreshParams.setMargins(dp(8), 0, 0, 0);
        deviceRow.addView(refreshButton, refreshParams);
        root.addView(deviceRow);

        root.addView(sectionTitle("2. Pasta para salvar"));
        folderButton = new Button(this);
        folderButton.setText("Escolher pasta de destino");
        folderButton.setOnClickListener(v -> chooseOutputFolder());
        root.addView(folderButton, matchWrap());
        folderText = makeText("Nenhuma pasta escolhida", 13, muted, false);
        folderText.setPadding(dp(4), dp(7), dp(4), dp(13));
        root.addView(folderText);

        TextView warning = makeText(
                "Importante: salve os arquivos em outro dispositivo. Nunca grave os resultados no mesmo HD que está sendo recuperado.",
                13, Color.rgb(255, 201, 107), true);
        warning.setBackgroundColor(Color.rgb(48, 39, 22));
        warning.setPadding(dp(13), dp(12), dp(13), dp(12));
        root.addView(warning, matchWrap());

        LinearLayout actionRow = horizontal();
        actionRow.setPadding(0, dp(16), 0, dp(8));
        startButton = new Button(this);
        startButton.setText("Iniciar recuperação");
        startButton.setOnClickListener(v -> requestStart());
        actionRow.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        cancelButton = new Button(this);
        cancelButton.setText("Cancelar");
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(v -> {
            cancelled.set(true);
            setStatus("Cancelamento solicitado…");
            appendLog("Aguardando o término seguro da leitura atual.");
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(54), 0.55f);
        cancelParams.setMargins(dp(8), 0, 0, 0);
        actionRow.addView(cancelButton, cancelParams);
        root.addView(actionRow);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));

        statusText = makeText("Pronto para começar.", 16, text, true);
        statusText.setPadding(0, dp(12), 0, dp(4));
        root.addView(statusText);
        statsText = makeText("0% • 0 arquivos encontrados", 13, muted, false);
        root.addView(statsText);

        root.addView(sectionTitle("Registro"));
        logText = makeText("Conecte o HD usando um adaptador USB OTG com alimentação adequada.\n", 12, muted, false);
        logText.setTextIsSelectable(true);
        logText.setBackgroundColor(Color.rgb(7, 11, 16));
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(logText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(280)));

        TextView formats = makeText(
                "Formatos desta versão: JPG, PNG, PDF, ZIP/Office, GIF, BMP, WAV, AVI, SQLite e contêineres MP4/HEIC contíguos.",
                12, muted, false);
        formats.setPadding(0, dp(14), 0, 0);
        root.addView(formats);

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView sectionTitle(String value) {
        TextView view = makeText(value, 15, accent, true);
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView makeText(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private void restoreOutputFolder() {
        String saved = getSharedPreferences("hdrecover", MODE_PRIVATE)
                .getString("output_tree", null);
        if (saved != null) {
            outputTree = Uri.parse(saved);
            folderText.setText(outputTree.toString());
        }
    }

    private void chooseOutputFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OUTPUT_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OUTPUT_TREE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (SecurityException ignored) {
                appendLog("A pasta foi selecionada, mas o Android não permitiu manter a autorização permanentemente.");
            }
            outputTree = uri;
            getSharedPreferences("hdrecover", MODE_PRIVATE)
                    .edit().putString("output_tree", uri.toString()).apply();
            folderText.setText(uri.toString());
            appendLog("Pasta de destino selecionada.");
        }
    }

    private void refreshDevices() {
        if (running) return;
        usbEntries.clear();
        List<String> labels = new ArrayList<>();
        Map<String, UsbDevice> devices = usbManager.getDeviceList();
        for (UsbDevice device : devices.values()) {
            if (!hasMassStorageInterface(device)) continue;
            String name = device.getProductName();
            if (name == null || name.isBlank()) name = "Dispositivo USB";
            String label = name + "  •  VID " + hex(device.getVendorId())
                    + " / PID " + hex(device.getProductId());
            usbEntries.add(new UsbEntry(device, label));
            labels.add(label);
        }
        if (labels.isEmpty()) labels.add("Nenhum armazenamento USB encontrado");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
        appendLog(usbEntries.isEmpty()
                ? "Nenhum dispositivo USB Mass Storage detectado."
                : usbEntries.size() + " dispositivo(s) USB detectado(s).");
    }

    private boolean hasMassStorageInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == 8) return true;
        }
        return false;
    }

    private void requestStart() {
        if (running) return;
        if (usbEntries.isEmpty()) {
            Toast.makeText(this, "Conecte um HD, SSD ou pendrive por USB OTG.", Toast.LENGTH_LONG).show();
            return;
        }
        if (outputTree == null) {
            Toast.makeText(this, "Escolha primeiro uma pasta de destino.", Toast.LENGTH_LONG).show();
            return;
        }
        int position = deviceSpinner.getSelectedItemPosition();
        if (position < 0 || position >= usbEntries.size()) position = 0;
        UsbDevice device = usbEntries.get(position).device;
        if (usbManager.hasPermission(device)) {
            beginRecovery(device);
        } else {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
            setStatus("Aguardando permissão USB…");
        }
    }

    private void beginRecovery(UsbDevice device) {
        if (running) return;
        running = true;
        cancelled.set(false);
        setControlsRunning(true);
        progressBar.setProgress(0);
        setStatus("Abrindo o dispositivo USB…");
        statsText.setText("Preparando leitura em modo somente leitura");
        appendLog("Iniciando sessão. O dispositivo de origem será aberto apenas para leitura.");

        executor.execute(() -> {
            PowerManager.WakeLock wakeLock = null;
            UsbMassStorageDevice storage = null;
            try {
                PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = power.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "HdRecover:UsbRecovery");
                wakeLock.acquire(6 * 60 * 60 * 1000L);

                storage = new UsbMassStorageDevice(usbManager, device);
                storage.open();
                long capacity = storage.getCapacityBytes();
                appendLog("Dispositivo aberto: " + formatBytes(capacity)
                        + " • setor " + storage.getBlockSize() + " bytes.");

                SafOutput output = new SafOutput(getContentResolver(), outputTree);
                RecoveryEngine engine = new RecoveryEngine(storage, output, new RecoveryEngine.Listener() {
                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }

                    @Override
                    public void onLog(String message) {
                        runOnUiThread(() -> appendLog(message));
                    }

                    @Override
                    public void onProgress(String phase, double fraction, long scannedBytes,
                                           double bytesPerSecond, int candidates, int recovered) {
                        runOnUiThread(() -> {
                            int value = (int) Math.max(0, Math.min(1000, fraction * 1000.0));
                            progressBar.setProgress(value);
                            statusText.setText(phase);
                            String speed = bytesPerSecond > 0
                                    ? formatBytes((long) bytesPerSecond) + "/s"
                                    : "calculando…";
                            statsText.setText(String.format(Locale.getDefault(),
                                    "%.1f%% • %s • %d candidatos • %d recuperados",
                                    fraction * 100.0, speed, candidates, recovered));
                        });
                    }
                });

                RecoveryEngine.Result result = engine.run();
                if (result.cancelled) {
                    runOnUiThread(() -> {
                        setStatus("Recuperação cancelada.");
                        appendLog("Sessão cancelada. Os arquivos concluídos foram mantidos.");
                    });
                } else {
                    runOnUiThread(() -> {
                        progressBar.setProgress(1000);
                        setStatus("Recuperação concluída.");
                        statsText.setText(result.recovered + " arquivo(s) recuperado(s) • "
                                + result.candidates + " candidato(s) analisado(s)");
                        appendLog("Concluído. Pasta criada: " + result.sessionName);
                    });
                }
            } catch (Exception error) {
                String message = error.getMessage();
                if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
                String finalMessage = message;
                runOnUiThread(() -> {
                    setStatus("Não foi possível concluir.");
                    appendLog("ERRO: " + finalMessage);
                    appendLog("Tente outro cabo/adaptador OTG, alimentação externa ou um pendrive menor para validar.");
                });
            } finally {
                if (storage != null) storage.close();
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                running = false;
                runOnUiThread(() -> setControlsRunning(false));
            }
        });
    }

    private void setControlsRunning(boolean active) {
        startButton.setEnabled(!active);
        refreshButton.setEnabled(!active);
        folderButton.setEnabled(!active);
        deviceSpinner.setEnabled(!active);
        cancelButton.setEnabled(active);
    }

    private void setStatus(String value) {
        runOnUiThread(() -> statusText.setText(value));
    }

    private void appendLog(String message) {
        String current = logText.getText().toString();
        String next = current + "\n" + message;
        if (next.length() > 18000) next = next.substring(next.length() - 18000);
        logText.setText(next);
    }

    private String hex(int value) {
        return String.format(Locale.US, "%04X", value);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024.0 && unit < units.length - 1);
        return new DecimalFormat(value >= 100 ? "0" : value >= 10 ? "0.0" : "0.00")
                .format(value) + " " + units[unit];
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        cancelled.set(true);
        executor.shutdownNow();
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private static final class UsbEntry {
        final UsbDevice device;
        final String label;

        UsbEntry(UsbDevice device, String label) {
            this.device = device;
            this.label = label;
        }
    }
}
