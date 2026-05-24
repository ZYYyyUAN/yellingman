package experiment3.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

public class Obstacle {

    public static final int TYPE_GROUND = 0;
    public static final int TYPE_FLOATING = 1;

    public float x, y;
    public int width, height;
    public int type;

    private Bitmap sprite;
    private float bobTimer;
    private float bobOffset;

    public Obstacle(Bitmap spriteSheet, int col, int type) {
        // s_patch.png is a 32x32 obstacle sprite
        this.sprite = spriteSheet;
        this.type = type;
        width = 32;
        height = 32;
        bobTimer = 0;

        x = col * LevelData.TILE_SIZE + (LevelData.TILE_SIZE - width) / 2f;

        if (type == TYPE_GROUND) {
            y = LevelData.FLOOR_Y - height;
        } else {
            // floating obstacle: 3-4 tiles above floor
            y = LevelData.FLOOR_Y - 3 * LevelData.TILE_SIZE;
        }
    }

    public Rect getBounds() {
        return new Rect((int) x + 4, (int) y + 4,
                (int) (x + width) - 4, (int) (y + height) - 4);
    }

    public void update(float dt) {
        if (type == TYPE_FLOATING) {
            bobTimer += dt;
            bobOffset = (float) Math.sin(bobTimer * 3) * 10;
        }
    }

    public void draw(Canvas canvas, int offsetX, int offsetY) {
        int drawX = (int) x - offsetX;
        int drawY = (int) (y + bobOffset) - offsetY;
        canvas.drawBitmap(sprite, drawX, drawY, null);
    }
}
