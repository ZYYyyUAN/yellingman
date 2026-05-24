# YellingMan Rewrite — Design Spec

## Tech Stack (matching class experiment guide)

- Android Java (no game engine)
- `SurfaceView` + `Canvas` for rendering
- `MainThread extends Thread` for game loop
- `CharacterSprite` pattern for game objects
- `AudioRecord` for microphone input
- No backend, local SQLite for score storage

## Core Mechanic: Voice Volume → Jump Force

Extracted from the original `JumpLevel.java`:

```
SAMPLE_RATE  = 8000 Hz
ENCODING     = PCM 16-bit
BUFFER_SIZE  = AudioRecord.getMinBufferSize(8000, CHANNEL_IN_DEFAULT, PCM_16BIT)

Loop (every ~50ms):
  1. Read BUFFER_SIZE shorts from mic into buffer[]
  2. v = sum(buffer[i]^2) for all samples
  3. mean = v / samplesRead
  4. if mean >= 1:  volume(dB) = 10 * log10(mean)
     else:           volume = 0
  5. Map dB to jump level:
       volume < 80    → level 0 (no jump)
       80 <= vol < 82 → level 1 (small jump)
       82 <= vol < 85 → level 2 (medium jump)
       vol >= 85      → level 3 (high jump)
```

Note: original only had 3 levels (0,1,2). We add level 3 at 85dB for richer gameplay — the original `GameScreen.java` already handled level 3 with `applyForceToCenter(0, 300)`.

## Game Rules (from original `GameScreen.java`)

- Character auto-runs right at constant speed
- Player controls **only jump height** via voice volume
- Jump only allowed when standing on a platform (grounded check)
- **Collect**: stars/hearts for score
- **Death conditions**: fall below bottom of map → game over; touch obstacle (flame) → game over; velocity stuck near 0 (blocked) → game over
- **Win condition**: reach right edge of map → level complete

## 6+ Functional Interfaces

To meet the teacher's requirement of at least 6 functional interfaces:

| # | Screen | Description |
|---|--------|-------------|
| 1 | **Calibration** | First-launch only: "speak normally" → "now YELL!" → stores personal dB range |
| 2 | **Main Menu** | Title "YellingMan", Start button, High Scores button, Settings (recalibrate) |
| 3 | **Level Select** | 3 level buttons with lock/unlock, shows best score and star rating per level |
| 4 | **Game Play** | Canvas side-scroller with voice jump + combo scoring + screen shake |
| 5 | **Pause Overlay** | Pause button during game → overlay with Resume / Restart / Quit |
| 6 | **Game Over** | "You Died" + final score + combo record, Retry / Menu buttons |
| 7 | **Victory** | "Level Complete" + score + star rating, Next Level / Menu buttons |

## Architecture (following experiment guide pattern)

```
experiment3.game/
  MainActivity.java          — Fullscreen setup, setContentView(new GameView(this))
  GameView.java              — extends SurfaceView, implements SurfaceHolder.Callback
  MainThread.java            — extends Thread, game loop: update() + draw(canvas)
  GameState.java             — enum: CALIBRATE, MENU, SELECT, PLAYING, PAUSED, DEAD, VICTORY
  SoundMeter.java            — AudioRecord wrapper, getJumpLevel(), calibration via SharedPreferences
  sprite/
    CharacterSprite.java     — Player with x,y, velocity, animation frames, isGrounded
    Platform.java            — Floor segment (rectangle)
    Obstacle.java            — Flame/enemy (rectangle, bobbing animation)
    Star.java                — Collectible item (rectangle, spinning animation)
    ComboText.java           — Floating "x2!" text that fades up and out over 0.5s
  screen/
    CalibrationRenderer.java — Draws calibration UI (instructions + progress)
    MenuRenderer.java        — Draws main menu UI on Canvas
    LevelSelectRenderer.java — Draws level selection UI with star ratings
    GameRenderer.java        — Draws game world (scrolling background, platforms, sprites)
    HUD.java                 — Draws score, combo count, pause button overlay
    GameOverRenderer.java    — Draws death screen with score summary
    VictoryRenderer.java     — Draws win screen with star rating
  data/
    ScoreDBHelper.java       — SQLite helper for high scores
    LevelData.java           — Hardcoded level layouts (adapted from .tmx files)
```

## Game Loop Flow (in MainThread.run)

```
while (running):
    canvas = surfaceHolder.lockCanvas()
    synchronized (surfaceHolder):
        gameView.update()    // physics, collision, state transitions
        gameView.draw(canvas) // render current state
    surfaceHolder.unlockCanvasAndPost(canvas)
```

## Level Layout Format

Adapted from original `.tmx` files. Each level is defined as:

```java
class LevelData {
    int mapWidthTiles;     // total level width in tiles
    int tileSize;          // pixels per tile (64px)
    int floorY;            // pixel Y of floor surface from top
    boolean[] floor;       // floor[col] = tile exists at this column
    int[][] stars;         // [n][2] → [col, rowOffset] row relative to floor
    int[][] obstacles;     // [n][2] → [col, type] 0=ground, 1=floating
    int finishTile;        // column where finish line sits
    String[] hints;        // on-screen text hints (Level 3 = empty array)
    int[] hintTiles;       // tile position that triggers each hint
}
```

## Level 1 — First Steps (教学关卡)

**120 tiles, ~30 seconds, 12 stars, 4 obstacles**

```
tile: 0         1         2         3         4         5         6         7         8         9         10        11
      0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890

上空:     *              *       *      *  *  *         X     X       *                                     *  *          F
地面: ###############################################################################..........##############..........####
      |-- 平地学收集 --| |小坑| |障碍教学| |中等坑| |连击入门| |大坑| |--长平地--| |冲刺|

区域:
  0-21:  平地 — 星星在 tile 6, 16, 学习走路 + 收集
  22-24: 2格小坑 — level 1 可过，tile 24 有星星奖励跳跃
  25-33: 障碍教学 — tile 30 有地面障碍物 X，学习喊叫跳越
  34-40: 4格中坑 — 需要 level 2，tiles 36,38 两颗星奖励大跳
  41-55: 连击入门 — tiles 43,45,47 三颗星密集排列，首次体验 combo
  56-60: 5格大坑 — 需要 level 3，tile 62 有星星
  61-85: 长平地 — tile 68, 76 有障碍物，练习躲避
  86-100: 平地 — tiles 91,94 两颗星
  101-105: 3格坑 — 最后跳跃
  106-120: 冲刺 — tile 115 到达 Finish

屏幕中央提示:
  tile  3 → "触碰星星收集分数"
  tile 22 → "小声说话让小跳，跳过坑！"
  tile 28 → "遇到障碍物！大声点跳过去！"
  tile 34 → "喊大声一点！跳得更远！"
  tile 43 → "连续收集星星获得 Combo 加分！"
  tile 56 → "用最大声喊叫！飞跃大坑！"
  tile 101→ "冲向终点！"
```

## Level 2 — Voice Control (声音控制)

**160 tiles, ~40 seconds, 18 stars, 8 obstacles (4 ground + 4 floating)**

```
tile: 0         1         2         3         4         5         6         7         8         9         10        11        12        13        14        15
      01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890

上空:   *   *         * *     ^     *         * * *     ^   ^     *       *       X   *     * * * *       ^       *   *       *     ^
地面: #############..###....#####.....####....#####.....#########.....#####.......####....#######..........###########......###########...#########.......
      |热身| |小坑| |选跳| |浮障| |三连坑| |加速段| |双浮障| |障碍星| |大坑| |连击段| |浮障大坑| |收尾|

区域:
  0-16:  热身 — 平地 + 2颗分散星星
  17-20: 小坑 — 2格坑，复习 level 1
  21-27: 选跳 — 3格坑 + 2格坑相连，考验连续不同力度
  28-31: 浮空障碍 ^ — 跳跃太高会撞到，训练高度精确控制
  32-45: 三连坑 — 2格→4格→3格连续坑，快速切换音量
  46-55: 加速段 — 平地 + 3颗星星一条线，combo 缓冲
  56-61: 双浮障 — 两个浮空障碍相邻不同高度
  62-67: 障碍星 — 地面障碍物 X 正上方有一颗星星（风险收益）
  68-73: 6格大坑 — 需要 level 3 最大声
  74-85: 连击段 — 平地 + 4颗密集星星
  86-95: 浮障大坑 — 5格坑 + 坑中央浮空障碍，组合考验
  96-120: 收尾 — 平地 + 2颗星 + 2个浮空障碍
  121-160: 终点段 — tile 155 到达 Finish

屏幕中央提示:
  tile  5 → "准备好了吗？声音要稳！"
  tile 17 → "试试控制音量跳不同距离"
  tile 28 → "空中也有障碍！控制跳跃高度！"
  tile 36 → "连续三个坑！调整你的声音！"
  tile 52 → "两个浮空障碍！精准控制！"
  tile 62 → "障碍物上方有星星！敢拿吗？"
  tile 74 → "大声跳过大坑后立刻连击！"
  tile 92 → "最后的挑战！浮空障碍加大坑！"
```

## Level 3 — Ultimate Challenge (终极挑战)

**200 tiles, ~50 seconds, 28 stars, 12 obstacles (6 ground + 6 floating)**

```
tile: 0         1         2         3         4         5         6         7         8         9         10        11        12        13        14        15        16        17        18        19
      012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890

上空: * * *       * * *     ^   X   ^       *     *     ^     *   * * * * * * * *       X ^ X ^ X       * * *     ^       *   *       * * * *     X   *   X       * * *
地面: #########........####.....####.........#####........####.....###############........#########.........#####........####.....#######.........#############.....#########.........####
      |群星开场| |三连坑| |混合| |浮地混| |双大坑| |浮障| |加速| |连击天堂| |X^走廊| |浮+坑| |冲刺| |星+障| |收尾|

区域:
  0-15:  群星开场 — 平地 + 6颗星星（3+3两组），开局抢连击
  16-25: 三连坑 — 2格→3格→4格连续坑，中间有星星
  26-36: 混合 — 浮空障碍 ^ + 地面 X + 3格坑
  37-46: 浮地混 — 地面障碍 + 浮空障碍交错排列
  47-58: 双大坑 — 5格坑 + 4格坑，中间一颗星星，耐力考验
  59-64: 浮障 — 2个浮空障碍
  65-82: 加速 — 长平地喘息段
  83-100: 连击天堂 — 8颗星星连续排列，最高 x8 combo
  101-115: X^走廊 — 地面+浮空障碍 5 连交替，节奏感考验
  116-130: 浮+坑 — 3格坑 + 浮空障碍 + 2格坑，综合考验
  131-155: 冲刺 — 平地 + 4颗星星 + 2个地面障碍
  156-175: 星+障 — 星星与障碍混合密集排列
  176-200: 收尾 — 平地冲刺 + 3颗星星，tile 190 到达 Finish

无屏幕提示 — Level 3 要求玩家独立完成
```

## Difficulty Curve

```
         Level 1        Level 2        Level 3
         ────────       ────────       ────────
长度       120 格         160 格         200 格
时间       ~30秒          ~40秒          ~50秒
星星数      ~12颗          ~18颗          ~28颗
障碍数      ~4个           ~8个           ~12个
浮空障碍    无             4个            6个
最大坑      5格            6格            5格
提示       有(7条)        有(7条)        无
最高combo  x3             x5             x8
```

Levels are designed with: opening flat run → first obstacle → gap to jump → star cluster → harder obstacles → finish line.

## Collision (no Box2D — simple rectangle math)

```java
// Player vs Star
if (rectOverlap(player.x, player.y, player.w, player.h, star.x, star.y, star.w, star.h))
    collectStar()

// Player vs Obstacle
if (rectOverlap(player, obstacle))
    triggerDeath()

// Player vs Floor
for each floorTile:
    if (player standing on top of tile)
        player.isGrounded = true

// Player fell off
if (player.y > mapHeight) triggerDeath()

// Player reached end
if (player.x > mapWidth * tileSize) triggerVictory()
```

## Voice Calibration (Polish Feature 1)

**Problem:** Fixed dB thresholds (80, 82, 85) from original 2016 code don't work for everyone. A quiet person might never reach level 1; a loud person might always trigger level 3.

**Solution:** On first launch, show a one-time calibration screen before the main menu.

```
Calibration flow:
  1. "Please speak normally for 3 seconds..." → record min/max dB
  2. "Now YELL as loud as you can!" → record peak dB
  3. Store: quietDb (min), loudDb (max)
  4. Map thresholds proportionally:
       range = loudDb - quietDb
       level 1 threshold = quietDb + range * 0.3
       level 2 threshold = quietDb + range * 0.6
       level 3 threshold = quietDb + range * 0.85
```

**Implementation:**
- `SoundMeter.java` stores `quietDb` and `loudDb` in `SharedPreferences`
- If `SharedPreferences` has no calibration data → show `CalibrationScreen` state
- After calibration, `getJumpLevel()` uses proportionally mapped thresholds instead of fixed numbers
- "Recalibrate" button in Settings resets the stored values

**Defense value:** Solves a real UX problem. Shows you thought about accessibility and edge cases, not just the happy path.

## Combo Scoring (Polish Feature 2)

**Problem:** Collecting stars gives flat points. No incentive to play well consistently.

**Solution:** Consecutive stars collected without missing builds a combo multiplier. One missed star resets it.

```
combo = 0
comboTimer = 2.0 (seconds)

Each frame: comboTimer -= dt
Each star collected:
    combo++
    comboTimer = 2.0
    score += starBaseValue * combo
    show "x{combo}" floating text

If comboTimer reaches 0:
    combo = 0
    comboTimer = 2.0
```

**Implementation:**
- Two new fields in `GameView`: `int comboCount`, `float comboTimer`
- `update()` decrements `comboTimer`; if <= 0, reset `comboCount`
- On star collect: `comboCount++`, `comboTimer = 2.0f`, `score += 100 * comboCount`
- `HUD.draw()` renders "x2" / "x3" near the score when comboCount > 1
- Floating combo text: a small `ComboText` sprite that fades out and floats up over 0.5s

**Defense value:** Turns star collection from a checklist into a skill-based system. Combo + voice control = the player must maintain vocal consistency to maximize score.

## Screen Shake (Polish Feature 3)

**Problem:** The game has no visceral feedback. A max-level yell should feel powerful.

**Solution:** When the player yells at level 3, the entire game canvas shifts by a small random offset for a few frames, then settles back to center.

```
shakeDuration = 0   (frames remaining, 0 = idle)
shakeIntensity = 5  (pixels, max offset)

When jumpLevel == 3 on takeoff:
    shakeDuration = 8   (8 frames ≈ 130ms at 60fps)

Each frame in draw():
    if shakeDuration > 0:
        offsetX = random(-shakeIntensity, +shakeIntensity)
        offsetY = random(-shakeIntensity, +shakeIntensity)
        canvas.translate(offsetX, offsetY)
        shakeDuration--
```

**Implementation:**
- Two new fields in `GameView`: `int shakeFrames`, `float shakePower`
- Set `shakeFrames = 8` when a level-3 jump triggers
- In `draw()`, before rendering the world, apply `canvas.translate()` with random offset if `shakeFrames > 0`
- Intensity tapers: `currentPower = shakePower * (shakeFrames / 8.0f)` for a smooth decay

**Defense value:** Shows you understand game feel / juice. Trivial code, huge perception difference. The teacher may mention "polish" or "attention to detail" in feedback.

## Sprite Animation

Following the experiment guide's `CharacterSprite` pattern:
- Sprite sheet split into frames using `Bitmap.createBitmap()`
- Animation cycles through frames at fixed rate (e.g., 1/12f per frame)
- `CharacterSprite.update()` handles position + velocity
- `CharacterSprite.draw(Canvas)` draws current frame

## Persistence

- SQLite database for high scores
- Table: `scores(_id, level, score, date)`
- Simple CRUD via `ScoreDBHelper extends SQLiteOpenHelper`
