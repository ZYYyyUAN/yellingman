package experiment3.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

public class Player {

    public static final float GRAVITY = 1600f;
    public static final float RUN_SPEED = 250f;
    public static final float[] JUMP_VELOCITY = {0, -420, -620, -820};

    private Bitmap spriteSheet;
    private Bitmap[] frames;
    private int frameCount;

    public float x, y;
    public float vx, vy;
    public int width, height;
    public boolean isGrounded;

    private float animTimer;
    private int currentFrame;
    private static final float FRAME_DURATION = 0.10f;

    public boolean isDead;
    public float deathTimer;

    public Player(Bitmap spriteSheet) {
        this.spriteSheet = spriteSheet;
        int frameW = 60;
        int frameH = 60;
        frameCount = spriteSheet.getWidth() / frameW;
        frames = new Bitmap[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = Bitmap.createBitmap(spriteSheet, i * frameW, 0, frameW, frameH);
        }
        width = frameW;
        height = frameH;

        // start position
        x = 100;
        y = LevelData.FLOOR_Y - height;
        vx = RUN_SPEED;
        vy = 0;
        isGrounded = false;
        isDead = false;
    }

    public void jump(int level) {
        if (!isGrounded) return;
        if (level < 0) level = 0;
        if (level > 3) level = 3;
        vy = JUMP_VELOCITY[level];
        isGrounded = false;
    }

    public void update(float dt) {
        if (isDead) {
            deathTimer += dt;
            return;
        }

        // physics
        if (!isGrounded) {
            vy += GRAVITY * dt;
        }
        x += vx * dt;
        y += vy * dt;

        // animation
        animTimer += dt;
        if (animTimer >= FRAME_DURATION) {
            animTimer -= FRAME_DURATION;
            currentFrame = (currentFrame + 1) % frameCount;
        }
    }

    public void landOnFloor(float floorY) {
        y = floorY - height;
        vy = 0;
        isGrounded = true;
    }

    public void die() {
        isDead = true;
        vy = -300;
        vx = 0;
        deathTimer = 0;
    }

    public Rect getBounds() {
        return new Rect((int) x, (int) y, (int) (x + width), (int) (y + height));
    }

    public void draw(Canvas canvas, int offsetX, int offsetY) {
        int drawX = (int) x - offsetX;
        int drawY = (int) y - offsetY;
        canvas.drawBitmap(frames[currentFrame], drawX, drawY, null);
    }
}
