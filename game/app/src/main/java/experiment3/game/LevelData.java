package experiment3.game;

import java.util.ArrayList;
import java.util.List;

public class LevelData {

    public static final int TILE_SIZE = 64;
    public static final int LEVEL_HEIGHT = 720;
    public static final int FLOOR_Y = 560;

    public int mapWidthTiles;
    public int tileSize;
    public int floorY;
    public boolean[] floor;
    public int[][] stars;       // [n][2] → [col, rowOffsetAboveFloor]
    public int[][] obstacles;   // [n][2] → [col, type] 0=ground 1=floating
    public int finishTile;
    public String[] hints;
    public int[] hintTiles;

    private static final int G = 0;   // ground obstacle
    private static final int F = 1;   // floating obstacle

    private LevelData() {}

    // ── Level 1: First Steps ──────────────────────────────────────
    public static LevelData level1() {
        LevelData l = new LevelData();
        l.mapWidthTiles = 120;
        l.tileSize = TILE_SIZE;
        l.floorY = FLOOR_Y;
        l.finishTile = 115;

        // gaps: [start, end) — floor absent in these ranges
        int[][] gaps = {{22, 24}, {34, 38}, {56, 61}, {101, 104}};
        l.floor = buildFloor(l.mapWidthTiles, gaps);

        l.stars = new int[][]{
            {6, -3}, {16, -3},
            {24, -5},
            {36, -5}, {38, -5},
            {43, -3}, {45, -3}, {47, -3},
            {62, -5},
            {91, -3}, {94, -3},
        };

        l.obstacles = new int[][]{
            {30, G}, {68, G}, {76, G}, {88, G},
        };

        l.hints = new String[]{
            "触碰星星收集分数",
            "小声说话让小跳，跳过坑！",
            "遇到障碍物！大声点跳过去！",
            "喊大声一点！跳得更远！",
            "连续收集星星获得 Combo 加分！",
            "用最大声喊叫！飞跃大坑！",
            "冲向终点！",
        };
        l.hintTiles = new int[]{3, 20, 27, 32, 41, 53, 100};

        return l;
    }

    // ── Level 2: Voice Control ────────────────────────────────────
    public static LevelData level2() {
        LevelData l = new LevelData();
        l.mapWidthTiles = 160;
        l.tileSize = TILE_SIZE;
        l.floorY = FLOOR_Y;
        l.finishTile = 155;

        int[][] gaps = {
            {17, 19},          // small gap
            {22, 25}, {26, 28},// consecutive gaps
            {32, 36},          // triple gap
            {38, 42}, {43, 46},
            {56, 58},          // wide gap for floating
            {62, 68},          // long gap (skill check)
            {86, 91},          // floating + gap combo
        };
        l.floor = buildFloor(l.mapWidthTiles, gaps);

        l.stars = new int[][]{
            {4, -3}, {12, -3},
            {20, -5}, {24, -4},
            {30, -5},
            {36, -5}, {40, -5}, {44, -3},
            {50, -3}, {52, -3}, {54, -3},
            {63, -6},           // above obstacle for risk-reward
            {74, -3}, {76, -3}, {78, -3}, {80, -3},
            {96, -3}, {100, -3},
            {113, -3}, {117, -3},
        };

        l.obstacles = new int[][]{
            {28, F},            // floating obstacle
            {56, F}, {59, F},   // double floating
            {63, G},            // ground with star above
            {86, F},            // floating in gap
            {108, F}, {112, F}, // final floating
        };

        l.hints = new String[]{
            "准备好了吗？声音要稳！",
            "试试控制音量跳不同距离",
            "空中也有障碍！控制跳跃高度！",
            "连续三个坑！调整你的声音！",
            "两个浮空障碍！精准控制！",
            "障碍物上方有星星！敢拿吗？",
            "大声跳过大坑后立刻连击！",
            "最后的挑战！浮空障碍加大坑！",
        };
        l.hintTiles = new int[]{5, 16, 26, 35, 54, 61, 72, 90};

        return l;
    }

    // ── Level 3: Ultimate Challenge ───────────────────────────────
    public static LevelData level3() {
        LevelData l = new LevelData();
        l.mapWidthTiles = 200;
        l.tileSize = TILE_SIZE;
        l.floorY = FLOOR_Y;
        l.finishTile = 190;

        int[][] gaps = {
            {16, 18}, {20, 22}, {24, 26},   // triple gap set
            {28, 33},                        // wide combo gap
            {38, 41},
            {47, 52}, {53, 57},             // double big gap
            {59, 62},
            {102, 104}, {106, 108},          // X^ corridor
            {110, 112}, {114, 116},
            {118, 121}, {124, 126},
            {128, 133},
            {156, 158}, {160, 162},
        };
        l.floor = buildFloor(l.mapWidthTiles, gaps);

        l.stars = new int[][]{
            {2, -3}, {4, -3}, {6, -3},
            {10, -3}, {12, -3}, {14, -3},
            {18, -5}, {22, -5}, {26, -5},
            {30, -5},
            {36, -3},
            {42, -3},
            {50, -5},
            {66, -3}, {68, -3},
            {84, -3}, {86, -3}, {88, -3}, {90, -3},
            {92, -3}, {94, -3}, {96, -3}, {98, -3},
            {118, -5},
            {132, -3}, {136, -3}, {140, -3},
            {146, -3}, {148, -3},
            {154, -3}, {156, -3}, {158, -3}, {160, -3},
            {164, -3}, {168, -3}, {172, -5},
            {178, -3}, {182, -3}, {186, -3},
        };

        l.obstacles = new int[][]{
            {28, F}, {30, G}, {32, F},
            {38, G}, {40, F},
            {59, F}, {61, F},
            {101, G}, {103, F}, {105, G}, {107, F}, {109, G},
            {134, G}, {138, G},
            {163, G}, {167, F}, {170, G},
        };

        l.hints = new String[]{};   // no hints in level 3
        l.hintTiles = new int[]{};

        return l;
    }

    // ── Utility: build floor boolean array from gap ranges ────────
    private static boolean[] buildFloor(int width, int[][] gaps) {
        boolean[] floor = new boolean[width];
        for (int x = 0; x < width; x++) {
            floor[x] = true;
        }
        for (int[] gap : gaps) {
            for (int x = gap[0]; x < gap[1] && x < width; x++) {
                floor[x] = false;
            }
        }
        return floor;
    }
}
