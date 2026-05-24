package experiment3.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    // ── Core ──
    private MainThread thread;
    private SoundMeter soundMeter;
    private GameState state = GameState.MENU;

    // ── Level ──
    private LevelData level;
    private int currentLevel = 0;

    // ── Entities ──
    private Player player;
    private List<Star> stars;
    private List<Obstacle> obstacles;

    // ── Camera ──
    private int cameraX;
    private int screenW, screenH;

    // ── Score & Combo ──
    private int score;
    private int comboCount;
    private float comboTimer;
    private static final float COMBO_WINDOW = 2.0f;
    private String comboLabel = "";
    private float comboLabelTimer;
    private int comboLabelX, comboLabelY;

    // ── Screen shake ──
    private int shakeFrames;
    private static final int SHAKE_DURATION = 10;
    private static final float SHAKE_POWER = 6f;
    private Random rand = new Random();

    // ── Hints ──
    private int hintIndex;
    private float hintTimer;
    private static final float HINT_DURATION = 3.0f;

    // ── Calibration ──
    private int calPhase;       // 0=speak, 1=yell, 2=done
    private float calTimer;
    private double calMinDb, calMaxDb;
    private static final float CAL_PHASE_TIME = 3.0f;

    // ── Menu ──
    private Rect btnStart, btnLevels;
    private Rect[] levelBtns;

    // ── Pause / Death / Victory buttons ──
    private Rect pauseBtn;
    private Rect pauseResumeBtn, pauseRestartBtn, pauseQuitBtn;
    private Rect deathRetryBtn, deathQuitBtn;
    private Rect victoryNextBtn, victoryQuitBtn;

    // ── Bitmaps ──
    private Bitmap playerSheet;
    private Bitmap starSheet;
    private Bitmap obstacleSprite;
    private Bitmap bgBitmap;
    private Bitmap initBgBitmap;

    // ── Paints ──
    private Paint floorPaint;
    private Paint hintPaint;
    private Paint hintBgPaint;
    private Paint hudPaint;
    private Paint pausePaint;

    // ── Timers ──
    private float stateTimer;

    // progress
    private int maxCompletedLevel;  // 0-3, tracks highest completed level
    private ScoreDBHelper scoreDB;
    private boolean scoreSaved;     // prevent double-save on victory

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        thread = new MainThread(getHolder(), this);
        setFocusable(true);

        floorPaint = new Paint();
        floorPaint.setColor(Color.rgb(101, 67, 33));

        hintBgPaint = new Paint();
        hintBgPaint.setColor(Color.argb(160, 0, 0, 0));

        hintPaint = new Paint();
        hintPaint.setColor(Color.WHITE);
        hintPaint.setTextSize(42);
        hintPaint.setAntiAlias(true);
        hintPaint.setTextAlign(Paint.Align.CENTER);

        hudPaint = new Paint();
        hudPaint.setColor(Color.WHITE);
        hudPaint.setTextSize(36);
        hudPaint.setAntiAlias(true);
        hudPaint.setShadowLayer(3, 1, 1, Color.BLACK);

        pausePaint = new Paint();
        pausePaint.setColor(Color.argb(120, 255, 255, 255));
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        screenW = w;
        screenH = h;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        screenW = getWidth();
        screenH = getHeight();

        playerSheet = BitmapFactory.decodeResource(getResources(), R.drawable.yellingman);
        starSheet = BitmapFactory.decodeResource(getResources(), R.drawable.stars);
        obstacleSprite = BitmapFactory.decodeResource(getResources(), R.drawable.s_patch);
        bgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.gamebg);
        initBgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.init);

        soundMeter = SoundMeter.getInstance();
        soundMeter.start();
        soundMeter.loadCalibration(getContext());

        scoreDB = ScoreDBHelper.getInstance(getContext());

        SharedPreferences prefs = getContext().getSharedPreferences("yellingman_progress", Context.MODE_PRIVATE);
        maxCompletedLevel = prefs.getInt("maxCompletedLevel", 0);

        // always calibrate on each launch — environment noise varies
        startCalibration();

        thread.setRunning(true);
        thread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        soundMeter.stop();
        boolean retry = true;
        while (retry) {
            try {
                thread.setRunning(false);
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            retry = false;
        }
    }

    // ── Calibration ───────────────────────────────────────────────

    private void startCalibration() {
        state = GameState.CALIBRATE;
        calPhase = 0;
        calTimer = CAL_PHASE_TIME;
        calMinDb = 999;
        calMaxDb = 0;
    }

    private void updateCalibration(float dt) {
        double db = soundMeter.getCurrentDb();
        calTimer -= dt;

        if (calPhase == 0) {
            // recording normal speech
            if (db > 0 && db < calMinDb) calMinDb = db;
            if (calTimer <= 0) {
                calPhase = 1;
                calTimer = CAL_PHASE_TIME;
            }
        } else if (calPhase == 1) {
            // recording loud yell
            if (db > calMaxDb) calMaxDb = db;
            if (calTimer <= 0) {
                if (calMaxDb < calMinDb + 5) calMaxDb = calMinDb + 15;
                soundMeter.saveCalibration(getContext(), calMinDb, calMaxDb);
                calPhase = 2;
                calTimer = 2.0f;
            }
        } else {
            // done, show result then go to menu
            if (calTimer <= 0) {
                state = GameState.MENU;
                setupMenuButtons();
            }
        }
    }

    // ── Menu ──────────────────────────────────────────────────────

    private void setupMenuButtons() {
        int cx = screenW / 2;
        int cy = screenH / 2;
        btnStart = new Rect(cx - 150, cy - 60, cx + 150, cy + 20);
        btnLevels = new Rect(cx - 150, cy + 40, cx + 150, cy + 120);

        levelBtns = new Rect[3];
        int spacing = screenW / 4;
        for (int i = 0; i < 3; i++) {
            levelBtns[i] = new Rect(spacing * i + 60, screenH / 2 - 40,
                    spacing * i + spacing - 60, screenH / 2 + 40);
        }
    }

    // ── Level loading ─────────────────────────────────────────────

    private void loadLevel(LevelData data) {
        level = data;
        player = new Player(playerSheet);

        stars = new ArrayList<>();
        for (int[] s : level.stars) {
            stars.add(new Star(starSheet, s[0], s[1]));
        }

        obstacles = new ArrayList<>();
        for (int[] o : level.obstacles) {
            obstacles.add(new Obstacle(obstacleSprite, o[0], o[1]));
        }

        score = 0;
        comboCount = 0;
        comboTimer = 0;
        comboLabel = "";
        comboLabelTimer = 0;
        shakeFrames = 0;
        hintIndex = 0;
        hintTimer = 0;
        stateTimer = 0;
        scoreSaved = false;
        cameraX = 0;

        pauseBtn = new Rect(screenW - 80, 20, screenW - 20, 70);
    }

    // ── Update ────────────────────────────────────────────────────

    public void update() {
        float dt = 1f / 60f;

        switch (state) {
            case CALIBRATE:
                updateCalibration(dt);
                break;
            case MENU:
            case SELECT:
                break;
            case PLAYING:
                updatePlaying(dt);
                break;
            case PAUSED:
                break;
            case DEAD:
                stateTimer += dt;
                break;
            case VICTORY:
                stateTimer += dt;
                break;
        }
    }

    private void updatePlaying(float dt) {
        int jumpLevel = soundMeter.getJumpLevel();
        if (jumpLevel > 0 && player.isGrounded) {
            player.jump(jumpLevel);
            if (jumpLevel == 3) shakeFrames = SHAKE_DURATION;
        }

        player.update(dt);
        checkFloorCollision();
        checkStarCollision();
        checkObstacleCollision();

        if (player.y > LevelData.LEVEL_HEIGHT) {
            player.die();
            state = GameState.DEAD;
            stateTimer = 0;
            return;
        }

        if (player.x > level.finishTile * LevelData.TILE_SIZE) {
            state = GameState.VICTORY;
            stateTimer = 0;
            if (!scoreSaved) {
                scoreSaved = true;
                scoreDB.insertScore(currentLevel, score);
                if (currentLevel >= maxCompletedLevel) {
                    maxCompletedLevel = currentLevel + 1;
                    if (maxCompletedLevel > 3) maxCompletedLevel = 3;
                    getContext().getSharedPreferences("yellingman_progress", Context.MODE_PRIVATE)
                            .edit().putInt("maxCompletedLevel", maxCompletedLevel).apply();
                }
            }
            return;
        }

        for (Star s : stars) s.update(dt);
        for (Obstacle o : obstacles) o.update(dt);

        cameraX = (int) player.x - screenW / 4;
        if (cameraX < 0) cameraX = 0;
        int maxCamX = level.mapWidthTiles * LevelData.TILE_SIZE - screenW;
        if (cameraX > maxCamX) cameraX = maxCamX;

        if (comboCount > 0 && comboLabelTimer <= 0) {
            comboTimer -= dt;
            if (comboTimer <= 0) comboCount = 0;
        }
        if (comboLabelTimer > 0) {
            comboLabelTimer -= dt;
            if (comboLabelTimer <= 0) comboLabel = "";
        }

        if (shakeFrames > 0) shakeFrames--;

        if (hintTimer > 0) {
            hintTimer -= dt;
        } else if (hintIndex < level.hints.length) {
            int triggerTile = level.hintTiles[hintIndex];
            if (player.x > triggerTile * LevelData.TILE_SIZE) {
                hintTimer = HINT_DURATION;
                hintIndex++;
            }
        }
    }

    private void checkFloorCollision() {
        int left = (int) (player.x / LevelData.TILE_SIZE);
        int right = (int) ((player.x + player.width) / LevelData.TILE_SIZE);
        float bottom = player.y + player.height;
        boolean onFloor = false;

        for (int col = left; col <= right; col++) {
            if (col >= 0 && col < level.mapWidthTiles && level.floor[col]) {
                float tileTop = level.floorY;
                if (bottom >= tileTop - 4 && bottom <= tileTop + 16 && player.vy >= 0) {
                    player.landOnFloor(tileTop);
                    onFloor = true;
                    break;
                }
            }
        }
        if (!onFloor) player.isGrounded = false;
    }

    private void checkStarCollision() {
        Rect pb = player.getBounds();
        for (Star s : stars) {
            if (s.collected) continue;
            if (Rect.intersects(pb, s.getBounds())) {
                s.collected = true;
                comboCount++;
                comboTimer = COMBO_WINDOW;
                score += 100 * comboCount;
                comboLabel = "x" + comboCount + "!";
                comboLabelTimer = 0.6f;
                comboLabelX = (int) s.x;
                comboLabelY = (int) s.y;
            }
        }
    }

    private void checkObstacleCollision() {
        Rect pb = player.getBounds();
        for (Obstacle o : obstacles) {
            if (Rect.intersects(pb, o.getBounds())) {
                player.die();
                state = GameState.DEAD;
                stateTimer = 0;
                return;
            }
        }
    }

    // ── Touch ─────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
        int tx = (int) event.getX();
        int ty = (int) event.getY();

        switch (state) {
            case CALIBRATE:
                break;
            case MENU:
                if (btnStart.contains(tx, ty)) {
                    // START → directly enter last uncompleted level
                    currentLevel = maxCompletedLevel;
                    if (currentLevel > 2) currentLevel = 2;
                    loadLevelByIndex(currentLevel);
                    state = GameState.PLAYING;
                    hintIndex = 0;
                    hintTimer = 0;
                } else if (btnLevels.contains(tx, ty)) {
                    state = GameState.SELECT;
                }
                break;
            case SELECT:
                for (int i = 0; i < 3; i++) {
                    if (levelBtns[i].contains(tx, ty)) {
                        currentLevel = i;
                        loadLevelByIndex(i);
                        state = GameState.PLAYING;
                        hintIndex = 0;
                        hintTimer = 0;
                        return true;
                    }
                }
                // tap outside buttons → back to menu
                state = GameState.MENU;
                break;
            case PLAYING:
                if (pauseBtn.contains(tx, ty)) state = GameState.PAUSED;
                break;
            case PAUSED:
                if (pauseResumeBtn.contains(tx, ty)) {
                    state = GameState.PLAYING;
                } else if (pauseRestartBtn.contains(tx, ty)) {
                    reloadCurrentLevel();
                    state = GameState.PLAYING;
                    hintIndex = 0;
                    hintTimer = 0;
                } else if (pauseQuitBtn.contains(tx, ty)) {
                    state = GameState.SELECT;
                    setupMenuButtons();
                }
                break;
            case DEAD:
                if (stateTimer > 0.5f) {
                    if (deathRetryBtn.contains(tx, ty)) {
                        reloadCurrentLevel();
                        state = GameState.PLAYING;
                    } else if (deathQuitBtn.contains(tx, ty)) {
                        state = GameState.SELECT;
                        setupMenuButtons();
                    }
                }
                break;
            case VICTORY:
                if (stateTimer > 0.5f) {
                    if (victoryNextBtn.contains(tx, ty)) {
                        if (currentLevel < 2) {
                            currentLevel++;
                            loadLevelByIndex(currentLevel);
                            state = GameState.PLAYING;
                            hintIndex = 0;
                            hintTimer = 0;
                        } else {
                            state = GameState.SELECT;
                            setupMenuButtons();
                        }
                    } else if (victoryQuitBtn.contains(tx, ty)) {
                        state = GameState.SELECT;
                        setupMenuButtons();
                    }
                }
                break;
        }
        return true;
    }

    private void loadLevelByIndex(int idx) {
        if (idx == 0) loadLevel(LevelData.level1());
        else if (idx == 1) loadLevel(LevelData.level2());
        else loadLevel(LevelData.level3());
    }

    private void reloadCurrentLevel() {
        loadLevelByIndex(currentLevel);
    }

    // ── Draw ──────────────────────────────────────────────────────

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas == null) return;

        switch (state) {
            case CALIBRATE:
                drawCalibration(canvas);
                break;
            case MENU:
                drawMenu(canvas);
                break;
            case SELECT:
                drawLevelSelect(canvas);
                break;
            case PLAYING:
            case PAUSED:
                drawGame(canvas);
                break;
            case DEAD:
                drawGame(canvas);
                drawDeathOverlay(canvas);
                break;
            case VICTORY:
                drawGame(canvas);
                drawVictoryOverlay(canvas);
                break;
        }
    }

    // ── Draw: Calibration ─────────────────────────────────────────

    private void drawCalibration(Canvas canvas) {
        canvas.drawColor(Color.rgb(10, 10, 30));

        Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setTextSize(44);
        p.setAntiAlias(true);
        p.setTextAlign(Paint.Align.CENTER);

        Paint sub = new Paint(p);
        sub.setTextSize(28);
        sub.setColor(Color.argb(200, 255, 255, 255));

        int cx = screenW / 2;

        if (calPhase == 0) {
            canvas.drawText("请用正常音量说话", cx, screenH / 2 - 50, p);
            canvas.drawText("" + (int) Math.ceil(calTimer), cx, screenH / 2 + 20, p);
        } else if (calPhase == 1) {
            canvas.drawText("请大声喊叫！", cx, screenH / 2 - 50, p);
            canvas.drawText("" + (int) Math.ceil(calTimer), cx, screenH / 2 + 20, p);
        } else {
            canvas.drawText("校准完成！", cx, screenH / 2 - 30, p);
            canvas.drawText("正常: " + String.format("%.0f", calMinDb) + " dB   喊叫: "
                    + String.format("%.0f", calMaxDb) + " dB", cx, screenH / 2 + 40, sub);
        }

        // volume meter
        double db = soundMeter.getCurrentDb();
        Paint meterBg = new Paint();
        meterBg.setColor(Color.DKGRAY);
        canvas.drawRect(cx - 200, screenH / 2 + 80, cx + 200, screenH / 2 + 100, meterBg);
        Paint meter = new Paint();
        meter.setColor(Color.rgb(255, 100, 50));
        float w = (float) (400 * Math.min(db, 100) / 100);
        canvas.drawRect(cx - 200, screenH / 2 + 80, cx - 200 + w, screenH / 2 + 100, meter);
    }

    // ── Draw: Menu ────────────────────────────────────────────────

    private void drawMenu(Canvas canvas) {
        canvas.drawBitmap(initBgBitmap, null,
                new Rect(0, 0, screenW, screenH), null);

        Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setTextSize(64);
        p.setAntiAlias(true);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(6, 3, 3, Color.BLACK);
        canvas.drawText("YellingMan", screenW / 2, screenH / 2 - 100, p);

        // start button
        Paint btnPaint = new Paint();
        btnPaint.setColor(Color.argb(180, 255, 107, 43));
        canvas.drawRoundRect(btnStart.left, btnStart.top, btnStart.right, btnStart.bottom, 12, 12, btnPaint);

        Paint btnText = new Paint(p);
        btnText.setTextSize(40);
        btnText.setShadowLayer(0, 0, 0, 0);
        canvas.drawText("START", btnStart.centerX(), btnStart.centerY() + 14, btnText);

        btnPaint.setColor(Color.argb(180, 60, 60, 120));
        canvas.drawRoundRect(btnLevels.left, btnLevels.top, btnLevels.right, btnLevels.bottom, 12, 12, btnPaint);
        canvas.drawText("LEVELS", btnLevels.centerX(), btnLevels.centerY() + 14, btnText);

        Paint ver = new Paint();
        ver.setColor(Color.argb(160, 255, 255, 255));
        ver.setTextSize(20);
        ver.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("声音控制跑酷游戏", screenW / 2, screenH - 40, ver);
    }

    // ── Draw: Level Select ────────────────────────────────────────

    private void drawLevelSelect(Canvas canvas) {
        canvas.drawColor(Color.rgb(10, 10, 30));

        Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setTextSize(48);
        p.setAntiAlias(true);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Select Level", screenW / 2, 80, p);

        String[] names = {"First Steps", "Voice Control", "Ultimate"};
        int[] colors = {Color.rgb(76, 175, 80), Color.rgb(255, 152, 0), Color.rgb(244, 67, 54)};

        for (int i = 0; i < 3; i++) {
            Rect btn = levelBtns[i];
            Paint bp = new Paint();
            bp.setColor(colors[i]);
            canvas.drawRoundRect(btn.left, btn.top, btn.right, btn.bottom, 16, 16, bp);

            Paint tp = new Paint(p);
            tp.setTextSize(36);
            tp.setShadowLayer(4, 2, 2, Color.BLACK);
            canvas.drawText("" + (i + 1), btn.centerX(), btn.centerY() - 8, tp);
            tp.setTextSize(18);
            canvas.drawText(names[i], btn.centerX(), btn.centerY() + 28, tp);

            // show best score
            int best = scoreDB.getBestScore(i);
            if (best > 0) {
                tp.setTextSize(22);
                tp.setColor(Color.YELLOW);
                canvas.drawText("Best: " + best, btn.centerX(), btn.centerY() + 55, tp);
            }
        }

        Paint back = new Paint(p);
        back.setTextSize(28);
        back.setColor(Color.GRAY);
        canvas.drawText("Tap outside to return", screenW / 2, screenH - 30, back);
    }

    // ── Draw: Game ────────────────────────────────────────────────

    private void drawGame(Canvas canvas) {
        canvas.save();
        canvas.drawColor(Color.BLACK);

        if (shakeFrames > 0) {
            float intensity = SHAKE_POWER * ((float) shakeFrames / SHAKE_DURATION);
            float ox = (rand.nextFloat() - 0.5f) * intensity * 2;
            float oy = (rand.nextFloat() - 0.5f) * intensity * 2;
            canvas.translate(ox, oy);
        }

        // parallax bg
        int bgOffset = cameraX / 3;
        int bgW = bgBitmap.getWidth();
        int bgH = bgBitmap.getHeight();
        float bgScale = (float) screenH / bgH;
        int scaledBgW = (int) (bgW * bgScale);
        int startTile = (bgOffset % scaledBgW) - scaledBgW;
        for (int bx = startTile; bx < screenW + scaledBgW; bx += scaledBgW) {
            canvas.drawBitmap(bgBitmap, null, new Rect(bx, 0, bx + scaledBgW, screenH), null);
        }

        // floor
        int firstTile = cameraX / LevelData.TILE_SIZE;
        int lastTile = (cameraX + screenW) / LevelData.TILE_SIZE + 1;
        for (int col = firstTile; col <= lastTile; col++) {
            if (col >= 0 && col < level.mapWidthTiles && level.floor[col]) {
                int dx = col * LevelData.TILE_SIZE - cameraX;
                canvas.drawRect(dx, level.floorY, dx + LevelData.TILE_SIZE, level.floorY + 20, floorPaint);
            }
        }

        // finish
        int finishX = level.finishTile * LevelData.TILE_SIZE - cameraX;
        Paint fp = new Paint();
        fp.setColor(Color.argb(180, 255, 215, 0));
        canvas.drawRect(finishX, 0, finishX + 8, screenH, fp);

        for (Star s : stars) s.draw(canvas, cameraX, 0);
        for (Obstacle o : obstacles) o.draw(canvas, cameraX, 0);
        player.draw(canvas, cameraX, 0);

        // combo text
        if (comboLabelTimer > 0) {
            Paint cp = new Paint();
            cp.setColor(Color.YELLOW);
            cp.setTextSize(48);
            cp.setAntiAlias(true);
            cp.setShadowLayer(4, 2, 2, Color.BLACK);
            float alpha = comboLabelTimer / 0.6f;
            cp.setAlpha((int) (255 * alpha));
            int dx = comboLabelX - cameraX;
            int dy = (int) (comboLabelY - (1.0f - alpha) * 40);
            canvas.drawText(comboLabel, dx, dy, cp);
        }

        // hints
        if (hintTimer > 0 && hintIndex > 0 && hintIndex <= level.hints.length) {
            String hint = level.hints[hintIndex - 1];
            int cx = screenW / 2;
            int cy = screenH / 2;
            canvas.drawRect(cx - 360, cy - 40, cx + 360, cy + 40, hintBgPaint);
            canvas.drawText(hint, cx, cy + 14, hintPaint);
        }

        // HUD
        canvas.drawText("Score: " + score, 20, 50, hudPaint);
        if (comboCount > 1 && comboTimer > 0) {
            Paint ch = new Paint(hudPaint);
            ch.setColor(Color.YELLOW);
            canvas.drawText("Combo x" + comboCount, 20, 90, ch);
        }

        // pause
        canvas.drawRect(pauseBtn, pausePaint);
        Paint pText = new Paint(hudPaint);
        pText.setTextSize(28);
        pText.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("| |", pauseBtn.centerX(), pauseBtn.centerY() + 10, pText);

        canvas.restore();

        // pause overlay
        if (state == GameState.PAUSED) {
            Paint over = new Paint();
            over.setColor(Color.argb(180, 0, 0, 0));
            canvas.drawRect(0, 0, screenW, screenH, over);

            Paint pp = new Paint();
            pp.setColor(Color.WHITE);
            pp.setTextSize(64);
            pp.setTextAlign(Paint.Align.CENTER);
            pp.setAntiAlias(true);
            int titleY = screenH / 2 - 100;
            canvas.drawText("PAUSED", screenW / 2, titleY, pp);

            // compute button layouts
            int btnW = 220, btnH = 70, gap = 30;
            int totalW = btnW * 3 + gap * 2;
            int startX = (screenW - totalW) / 2;
            int btnY = screenH / 2;

            pauseResumeBtn = new Rect(startX, btnY, startX + btnW, btnY + btnH);
            pauseRestartBtn = new Rect(startX + btnW + gap, btnY, startX + btnW * 2 + gap, btnY + btnH);
            pauseQuitBtn = new Rect(startX + btnW * 2 + gap * 2, btnY, startX + totalW, btnY + btnH);

            drawOverlayButton(canvas, pauseResumeBtn, "Resume", Color.rgb(76, 175, 80));
            drawOverlayButton(canvas, pauseRestartBtn, "Restart", Color.rgb(255, 152, 0));
            drawOverlayButton(canvas, pauseQuitBtn, "Quit", Color.rgb(244, 67, 54));
        }
    }

    private void drawDeathOverlay(Canvas canvas) {
        Paint over = new Paint();
        over.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, screenW, screenH, over);

        Paint dp = new Paint();
        dp.setColor(Color.RED);
        dp.setTextSize(56);
        dp.setAntiAlias(true);
        dp.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("YOU DIED", screenW / 2, screenH / 2 - 60, dp);
        dp.setColor(Color.WHITE);
        dp.setTextSize(36);
        canvas.drawText("Score: " + score, screenW / 2, screenH / 2 - 10, dp);

        if (stateTimer > 0.5f) {
            int btnW = 220, btnH = 70, gap = 40;
            int totalW = btnW * 2 + gap;
            int startX = (screenW - totalW) / 2;
            int btnY = screenH / 2 + 20;

            deathRetryBtn = new Rect(startX, btnY, startX + btnW, btnY + btnH);
            deathQuitBtn = new Rect(startX + btnW + gap, btnY, startX + totalW, btnY + btnH);

            drawOverlayButton(canvas, deathRetryBtn, "Retry", Color.rgb(76, 175, 80));
            drawOverlayButton(canvas, deathQuitBtn, "Quit", Color.rgb(244, 67, 54));
        }
    }

    private void drawVictoryOverlay(Canvas canvas) {
        Paint over = new Paint();
        over.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, screenW, screenH, over);

        Paint vp = new Paint();
        vp.setColor(Color.rgb(255, 215, 0));
        vp.setTextSize(56);
        vp.setAntiAlias(true);
        vp.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("LEVEL COMPLETE!", screenW / 2, screenH / 2 - 80, vp);
        vp.setColor(Color.WHITE);
        vp.setTextSize(36);
        canvas.drawText("Score: " + score, screenW / 2, screenH / 2 - 30, vp);

        int best = scoreDB.getBestScore(currentLevel);
        if (best > 0) {
            vp.setTextSize(28);
            vp.setColor(Color.YELLOW);
            if (score >= best) {
                canvas.drawText("NEW RECORD!", screenW / 2, screenH / 2 + 5, vp);
            } else {
                canvas.drawText("Best: " + best, screenW / 2, screenH / 2 + 5, vp);
            }
        }

        if (stateTimer > 0.5f) {
            int btnW = 220, btnH = 70, gap = 40;
            int totalW = btnW * 2 + gap;
            int startX = (screenW - totalW) / 2;
            int btnY = screenH / 2 + 25;

            victoryNextBtn = new Rect(startX, btnY, startX + btnW, btnY + btnH);
            victoryQuitBtn = new Rect(startX + btnW + gap, btnY, startX + totalW, btnY + btnH);

            String nextLabel = (currentLevel < 2) ? "Next Level" : "Done";
            drawOverlayButton(canvas, victoryNextBtn, nextLabel, Color.rgb(76, 175, 80));
            drawOverlayButton(canvas, victoryQuitBtn, "Quit", Color.rgb(244, 67, 54));
        }
    }

    // ── Helper: draw a rounded button with text ─────────────────

    private void drawOverlayButton(Canvas canvas, Rect btn, String text, int color) {
        Paint bp = new Paint();
        bp.setColor(color);
        bp.setAntiAlias(true);
        canvas.drawRoundRect(btn.left, btn.top, btn.right, btn.bottom, 12, 12, bp);

        Paint tp = new Paint();
        tp.setColor(Color.WHITE);
        tp.setTextSize(32);
        tp.setAntiAlias(true);
        tp.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, btn.centerX(), btn.centerY() + 12, tp);
    }
}
