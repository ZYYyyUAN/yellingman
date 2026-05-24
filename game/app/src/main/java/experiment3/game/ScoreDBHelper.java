package experiment3.game;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScoreDBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "yellingman_scores.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "scores";

    private static ScoreDBHelper instance;

    public static synchronized ScoreDBHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new ScoreDBHelper(ctx.getApplicationContext());
        }
        return instance;
    }

    private ScoreDBHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "level INTEGER NOT NULL, "
                + "score INTEGER NOT NULL, "
                + "date TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    // ── CRUD ──────────────────────────────────────────────────────

    public void insertScore(int level, int score) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("level", level);
        cv.put("score", score);
        cv.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        db.insert(TABLE, null, cv);
        db.close();
    }

    public int getBestScore(int level) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT MAX(score) FROM " + TABLE + " WHERE level=?",
                new String[]{String.valueOf(level)});
        int best = 0;
        if (c.moveToFirst()) best = c.getInt(0);
        c.close();
        db.close();
        return best;
    }

    public String[][] getTopScores(int level, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT score, date FROM " + TABLE
                        + " WHERE level=? ORDER BY score DESC LIMIT ?",
                new String[]{String.valueOf(level), String.valueOf(limit)});
        String[][] result = new String[c.getCount()][2];
        int i = 0;
        while (c.moveToNext()) {
            result[i][0] = String.valueOf(c.getInt(0));
            result[i][1] = c.getString(1);
            i++;
        }
        c.close();
        db.close();
        return result;
    }
}
