package experiment3.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class SoundMeter {

    private static final String TAG = "SoundMeter";
    private static final String PREFS_NAME = "yellingman_cal";
    private static final String KEY_QUIET = "quietDb";
    private static final String KEY_LOUD = "loudDb";
    private static final String KEY_CALIBRATED = "calibrated";

    private static final int SAMPLE_RATE_IN_HZ = 8000;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_IN_HZ,
            AudioFormat.CHANNEL_IN_DEFAULT,
            AudioFormat.ENCODING_PCM_16BIT);

    private AudioRecord mAudioRecord;
    private boolean isRunning;
    private final Object mLock = new Object();

    private double currentDb = 0;
    private int jumpLevel = 0;

    // calibrated thresholds (set after calibration)
    private double thresh1 = 52;
    private double thresh2 = 58;
    private double thresh3 = 65;
    private boolean calibrated = false;

    private static SoundMeter instance;

    private SoundMeter() {
    }

    public static synchronized SoundMeter getInstance() {
        if (instance == null) {
            instance = new SoundMeter();
        }
        return instance;
    }

    // ── Calibration persistence ───────────────────────────────────

    public boolean isCalibrated() {
        return calibrated;
    }

    public void loadCalibration(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        calibrated = prefs.getBoolean(KEY_CALIBRATED, false);
        if (calibrated) {
            double quietDb = prefs.getFloat(KEY_QUIET, 50f);
            double loudDb = prefs.getFloat(KEY_LOUD, 70f);
            computeThresholds(quietDb, loudDb);
        }
    }

    public void saveCalibration(Context ctx, double quietDb, double loudDb) {
        computeThresholds(quietDb, loudDb);
        calibrated = true;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putFloat(KEY_QUIET, (float) quietDb)
                .putFloat(KEY_LOUD, (float) loudDb)
                .putBoolean(KEY_CALIBRATED, true)
                .apply();
    }

    public void clearCalibration(Context ctx) {
        calibrated = false;
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();
        // reset defaults
        thresh1 = 52;
        thresh2 = 58;
        thresh3 = 65;
    }

    private void computeThresholds(double quietDb, double loudDb) {
        double range = loudDb - quietDb;
        if (range < 5) range = 15; // minimum range guard
        thresh1 = quietDb + range * 0.30;
        thresh2 = quietDb + range * 0.60;
        thresh3 = quietDb + range * 0.85;
        Log.d(TAG, "Calibrated thresholds: " + thresh1 + " / " + thresh2 + " / " + thresh3);
    }

    // ── Mic recording ─────────────────────────────────────────────

    public void start() {
        if (isRunning) return;

        mAudioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN_HZ,
                AudioFormat.CHANNEL_IN_DEFAULT,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE);

        if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize");
            return;
        }

        isRunning = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                mAudioRecord.startRecording();
                short[] buffer = new short[BUFFER_SIZE];

                while (isRunning) {
                    int r = mAudioRecord.read(buffer, 0, BUFFER_SIZE);
                    if (r <= 0) continue;

                    long v = 0;
                    for (int i = 0; i < r; i++) {
                        v += buffer[i] * buffer[i];
                    }

                    double mean = v / (double) r;
                    double db;
                    if (mean >= 1) {
                        db = 10 * Math.log10(mean);
                    } else {
                        db = 0;
                    }

                    int level;
                    if (db < thresh1) {
                        level = 0;
                    } else if (db < thresh2) {
                        level = 1;
                    } else if (db < thresh3) {
                        level = 2;
                    } else {
                        level = 3;
                    }

                    synchronized (mLock) {
                        currentDb = db;
                        jumpLevel = level;
                    }

                    synchronized (mLock) {
                        try {
                            mLock.wait(50);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }

                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }).start();
    }

    public void stop() {
        isRunning = false;
    }

    public double getCurrentDb() {
        synchronized (mLock) {
            return currentDb;
        }
    }

    public int getJumpLevel() {
        synchronized (mLock) {
            return jumpLevel;
        }
    }
}
