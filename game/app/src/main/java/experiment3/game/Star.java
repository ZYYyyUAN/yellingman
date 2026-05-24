package experiment3.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

public class Star {

    public float x, y;
    public int width, height;
    public boolean collected;

    private Bitmap sprite;
    private Bitmap[] frames;
    private int frameCount;
    private float animTimer;
    private int currentFrame;

    public Star(Bitmap spriteSheet, int col, int rowOffset) {
        // stars.png is a spritesheet with frames horizontally
        int frameW = 24;
        int frameH = 24;
        this.sprite = spriteSheet;
        int cols = spriteSheet.getWidth() / frameW;
        frameCount = cols;
        frames = new Bitmap[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = Bitmap.createBitmap(spriteSheet, i * frameW, 0, frameW, frameH);
        }
        width = frameW;
        height = frameH;
        collected = false;

        x = col * LevelData.TILE_SIZE + (LevelData.TILE_SIZE - width) / 2f;
        y = LevelData.FLOOR_Y + rowOffset * LevelData.TILE_SIZE;
    }

    public Rect getBounds() {
        return new Rect((int) x, (int) y, (int) (x + width), (int) (y + height));
    }

    public void update(float dt) {
        if (collected) return;
        animTimer += dt;
        if (animTimer >= 0.12f) {
            animTimer -= 0.12f;
            currentFrame = (currentFrame + 1) % frameCount;
        }
    }

    public void draw(Canvas canvas, int offsetX, int offsetY) {
        if (collected) return;
        int drawX = (int) x - offsetX;
        int drawY = (int) y - offsetY;
        canvas.drawBitmap(frames[currentFrame], drawX, drawY, null);
    }
}
