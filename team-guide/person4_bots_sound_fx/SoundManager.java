// =====================================================================================
// [คนที่ 4 - บอท เสียง และเอฟเฟกต์]  ไฟล์: SoundManager.java  (251 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/SoundManager.java
// ไฟล์เสียงอยู่ที่: app/src/main/res/raw/  (sfx_*.ogg)
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   ศูนย์รวมเสียงของทั้งเกม (final class + constructor private + ทุกอย่าง static = ไม่ต้องสร้าง object เรียกได้จากทุกที่)
//     เสียงเอฟเฟกต์ (สั้น เล่นซ้อนกันได้)  -> SoundPool
//     เพลงพื้นหลัง (ยาว วนลูป)             -> MediaPlayer
//   จำค่าเปิด/ปิดเสียงไว้ใน SharedPreferences "GamePrefs" คีย์ sound_enabled (หน้าตั้งค่าเป็นคนสลับ)
//
// [วิธีใช้จากไฟล์อื่น - 4 บรรทัดที่ใช้บ่อย]
//   SoundManager.init(this);                       // ครั้งเดียวต้น Activity (เรียกซ้ำได้ ไม่โหลดซ้ำ)
//   SoundManager.play(SoundManager.Sfx.BUTTON);    // เล่นเสียงเอฟเฟกต์
//   SoundManager.playMusic(this, "bgm_battle");    // ใน onResume (ชื่อไฟล์ใน res/raw ไม่ต้องใส่นามสกุล)
//   SoundManager.pauseMusic();                     // ใน onPause
//
// [จะเพิ่มเสียงใหม่ยังไง]
//   1) ใส่ไฟล์ .ogg/.wav/.mp3 ชื่อตัวพิมพ์เล็กและขีดล่างใน app/src/main/res/raw/ เช่น sfx_boom.ogg
//   2) เพิ่มค่าใน enum Sfx : BOOM("sfx_boom")
//   3) เรียก SoundManager.play(SoundManager.Sfx.BOOM) ตรงที่อยากให้ดัง
// [จะแก้ความดัง/ความถี่]
//   SFX_VOLUME เสียงเอฟเฟกต์ , MUSIC_VOLUME เพลง , MAX_STREAMS เล่นซ้อนได้กี่เสียง , MIN_REPEAT_MS กันเสียงเดียวกันถี่เกิน
// [ข้อสังเกต] ใน res/raw ตอนนี้ "ยังไม่มีไฟล์เพลง bgm_menu และ bgm_battle" โค้ดจึงเงียบเฉย ๆ (ไม่ crash) ถ้าต้องการเพลงให้ใส่ไฟล์สองชื่อนี้
// =====================================================================================
package com.example.finfury;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.SystemClock;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// [คอมเมนต์คลาส - โดยเจ้าของไฟล์เดิม]
/**
 * จัดการเสียงทั้งเกมที่เดียว
 *   - เสียงเอฟเฟกต์ (สั้น เล่นซ้อนกันได้)  -> SoundPool
 *   - เพลงพื้นหลัง (ยาว วนลูป)             -> MediaPlayer
 *
 * วิธีใช้:
 *   SoundManager.init(this);                       // เรียกครั้งเดียวใน MainActivity.onCreate
 *   SoundManager.play(SoundManager.Sfx.SKILL1);    // เล่นเสียงเอฟเฟกต์
 *   SoundManager.playMusic(this, "bgm_battle");    // ใน onResume ของ Activity (ชื่อไฟล์ใน res/raw ไม่ต้องใส่นามสกุล)
 *   SoundManager.pauseMusic();                     // ใน onPause ของ Activity
 *
 * ไฟล์เสียงอยู่ใน res/raw/ ถ้าไฟล์ไหนไม่มี เสียงนั้นจะเงียบเฉยๆ เกมไม่ crash
 */
public final class SoundManager {

    // [enum Sfx] รายชื่อเสียงเอฟเฟกต์ทั้งหมด ในวงเล็บ = ชื่อไฟล์ใน res/raw (ไม่ต้องมีนามสกุล) resName เก็บชื่อไว้ให้ init ไปค้นหา id
    //   SKILL1/SKILL2/ULTIMATE (สกิล) , HIT_ENEMY/ENEMY_DIE/ENEMY_WARN (ศัตรู) , PLAYER_HURT/WIN/LOSE (ผู้เล่น/ผลลัพธ์)
    //   QUIZ_SHOW/CORRECT/WRONG/TICK (โจทย์) , BUTTON (ปุ่มเมนู) , ULT_READY (ปลดล็อก ULT)
    /** เสียงเอฟเฟกต์ทั้งหมด ค่าในวงเล็บ = ชื่อไฟล์ใน res/raw (ไม่มีนามสกุล) */
    public enum Sfx {
        SKILL1("sfx_skill1"),
        SKILL2("sfx_skill2"),
        ULTIMATE("sfx_ultimate"),
        HIT_ENEMY("sfx_hit_enemy"),
        PLAYER_HURT("sfx_player_hurt"),
        ENEMY_DIE("sfx_enemy_die"),
        ENEMY_WARN("sfx_enemy_warn"),
        QUIZ_SHOW("sfx_quiz_show"),
        QUIZ_CORRECT("sfx_quiz_correct"),
        QUIZ_WRONG("sfx_quiz_wrong"),
        QUIZ_TICK("sfx_quiz_tick"),
        BUTTON("sfx_button"),
        ULT_READY("sfx_ult_ready"),
        WIN("sfx_win"),
        LOSE("sfx_lose");

        final String resName;

        Sfx(String resName) {
            this.resName = resName;
        }
    }

    // [ค่าที่ปรับได้] PREFS_NAME/KEY_SOUND_ENABLED = ที่เก็บค่าเปิดปิดเสียง | MAX_STREAMS | SFX_VOLUME 0-1 | MUSIC_VOLUME เบากว่า | MIN_REPEAT_MS
    // ---------- ค่าที่ปรับได้ ----------
    private static final String PREFS_NAME = "GamePrefs";      // ไฟล์เดียวกับ unlocked_stage
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final int MAX_STREAMS = 8;                   // เล่นเสียงเอฟเฟกต์ซ้อนกันได้สูงสุดกี่เสียง
    private static final float SFX_VOLUME = 0.9f;               // 0.0 - 1.0
    private static final float MUSIC_VOLUME = 0.45f;            // เพลงเบากว่าเอฟเฟกต์ จะได้ไม่กลบ
    private static final long MIN_REPEAT_MS = 50;               // เสียงเดียวกันเล่นซ้ำถี่สุดทุกกี่ ms

    // [ตัวแปรภายใน - static ตลอดอายุแอป]
    //   appContext (ใช้ application context กัน Activity รั่ว) | soundPool | soundIds = Sfx -> id ที่โหลดแล้ว | lastPlayedAt = เวลาที่เล่นล่าสุดต่อเสียง
    //   loadedIds = เสียงที่โหลดเสร็จแล้ว | musicPlayer, currentMusicName = เพลงที่เล่นอยู่ | soundEnabled
    private static Context appContext;
    private static SoundPool soundPool;
    private static final Map<Sfx, Integer> soundIds = new EnumMap<>(Sfx.class);
    private static final Map<Sfx, Long> lastPlayedAt = new EnumMap<>(Sfx.class);
    private static final Set<Integer> loadedIds = new HashSet<>();

    private static MediaPlayer musicPlayer;
    private static String currentMusicName;

    private static boolean soundEnabled = true;

    private SoundManager() {}

    // [init] ทำครั้งเดียว: เก็บ application context , อ่านค่าเปิด/ปิดเสียง , สร้าง SoundPool (ชนิดเสียงเกม) ,
    //   ตั้งตัวฟังว่าโหลดเสร็จ (SoundPool โหลดแบบ async เสียงที่ยังไม่เสร็จเล่นไม่ได้) แล้วโหลดทุกเสียงใน enum (ไฟล์ไหนไม่มี ข้ามไป ไม่ crash)
    // =========================================================
    // เริ่มต้น / ปิด
    // =========================================================

    /** เรียกครั้งเดียวตอนเปิดเกม (เรียกซ้ำได้ ไม่โหลดซ้ำ) */
    public static void init(Context context) {
        if (soundPool != null) return;

        // ใช้ application context กัน Activity รั่ว เพราะ class นี้เป็น static อยู่ตลอดอายุแอป
        appContext = context.getApplicationContext();

        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attrs)
                .build();

        // SoundPool โหลดไฟล์แบบ async เสียงที่ยังโหลดไม่เสร็จเล่นไม่ได้ จึงจดไว้ว่าตัวไหนพร้อมแล้ว
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (status == 0) loadedIds.add(sampleId);
        });

        for (Sfx sfx : Sfx.values()) {
            int resId = rawId(sfx.resName);
            if (resId != 0) {
                soundIds.put(sfx, soundPool.load(appContext, resId, 1));
            }
        }
    }

    // [release] คืนหน่วยความจำทั้งหมด (ปกติไม่ต้องเรียก ระบบคืนตอนแอปปิด)
    /** คืนหน่วยความจำเสียงทั้งหมด (ปกติไม่จำเป็นต้องเรียก ระบบคืนให้เองตอนแอปปิด) */
    public static void release() {
        stopMusic();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundIds.clear();
        loadedIds.clear();
        lastPlayedAt.clear();
    }

    // [play] เล่นเสียงเอฟเฟกต์ (มี 2 แบบ: ปกติ และกำหนด rate ความสูงเสียง 0.5-2.0)
    //   ไม่เล่นถ้า: ปิดเสียงอยู่ / ยังไม่ init / เสียงยังโหลดไม่เสร็จ / เล่นเสียงเดิมซ้ำเร็วกว่า MIN_REPEAT_MS (กันดังแตกเมื่อสกิลโดนศัตรู 5 ตัวพร้อมกัน)
    // =========================================================
    // เสียงเอฟเฟกต์
    // =========================================================

    public static void play(Sfx sfx) {
        play(sfx, 1f);
    }

    /** @param rate ความเร็ว/ระดับเสียง 0.5 - 2.0 (1 = ปกติ, มากกว่า 1 = เสียงสูงขึ้น) */
    public static void play(Sfx sfx, float rate) {
        if (!soundEnabled || soundPool == null || sfx == null) return;

        Integer id = soundIds.get(sfx);
        if (id == null || !loadedIds.contains(id)) return;

        // กันเสียงเดียวกันเล่นซ้อนในเฟรมเดียว (เช่น สกิลโดนศัตรู 5 ตัวพร้อมกัน) ซึ่งจะดังแตก
        long now = SystemClock.uptimeMillis();
        Long last = lastPlayedAt.get(sfx);
        if (last != null && now - last < MIN_REPEAT_MS) return;
        lastPlayedAt.put(sfx, now);

        float r = Math.max(0.5f, Math.min(2f, rate));
        soundPool.play(id, SFX_VOLUME, SFX_VOLUME, 1, 0, r);
    }

    // [playMusic] เริ่มเพลงวนลูป ถ้าเป็นเพลงเดิมที่เล่นอยู่แล้วให้เล่นต่อ ไม่เริ่มใหม่
    //   เปลี่ยนเพลง: ปล่อยตัวเล่นเก่า -> จำชื่อ -> (ถ้าปิดเสียงหรือไม่มีไฟล์ = เงียบ) -> MediaPlayer.create ตั้งวนลูป ตั้งความดัง แล้ว start
    // =========================================================
    // เพลงพื้นหลัง
    // =========================================================

    /**
     * เริ่มเพลงวนลูป ถ้าเพลงเดียวกันเล่นอยู่แล้วจะไม่เริ่มใหม่
     * @param rawName ชื่อไฟล์ใน res/raw ไม่ต้องใส่นามสกุล เช่น "bgm_battle"
     */
    public static void playMusic(Context context, String rawName) {
        if (appContext == null) init(context);

        if (rawName != null && rawName.equals(currentMusicName) && musicPlayer != null) {
            resumeMusic();
            return;
        }

        releaseMusicPlayer();
        currentMusicName = rawName;
        if (rawName == null || !soundEnabled) return;

        int resId = rawId(rawName);
        if (resId == 0) return; // ยังไม่มีไฟล์เพลง -> เงียบ

        musicPlayer = MediaPlayer.create(appContext, resId);
        if (musicPlayer == null) return;

        musicPlayer.setLooping(true);
        musicPlayer.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
        musicPlayer.start();
    }

    // [pauseMusic] หยุดเพลงชั่วคราว (ต้องเรียกใน onPause ไม่งั้นเพลงดังต่อหลังพับจอ)
    /** เรียกใน onPause ของ Activity ไม่งั้นเพลงจะดังต่อหลังพับจอ/สลับแอป */
    public static void pauseMusic() {
        if (musicPlayer != null && musicPlayer.isPlaying()) {
            musicPlayer.pause();
        }
    }

    // [resumeMusic] เล่นต่อจากที่หยุด ถ้าตัวเล่นถูกปล่อยไปแล้ว (เพราะเคยปิดเสียง) แต่ยังจำชื่อเพลงไว้ ให้สร้างใหม่แล้วเล่น
    /** เล่นเพลงเดิมต่อจากที่หยุดไว้ (playMusic ด้วยชื่อเดิมก็ให้ผลเหมือนกัน) */
    public static void resumeMusic() {
        if (!soundEnabled) return;

        if (musicPlayer != null) {
            if (!musicPlayer.isPlaying()) musicPlayer.start();
        } else if (currentMusicName != null && appContext != null) {
            // เคยขอเพลงไว้ตอนปิดเสียงอยู่ พอเปิดเสียงแล้วให้เริ่มเล่น
            String name = currentMusicName;
            currentMusicName = null;
            playMusic(appContext, name);
        }
    }

    // [stopMusic] หยุดและลืมเพลง (resumeMusic จะไม่เล่นต่อ ต้อง playMusic ใหม่) | releaseMusicPlayer = ปล่อยทรัพยากรอย่างปลอดภัย (กัน IllegalStateException)
    /** หยุดเพลงและลืมเพลงปัจจุบัน (resumeMusic จะไม่เล่นต่อ ต้องเรียก playMusic ใหม่) */
    public static void stopMusic() {
        releaseMusicPlayer();
        currentMusicName = null;
    }

    private static void releaseMusicPlayer() {
        if (musicPlayer != null) {
            try {
                if (musicPlayer.isPlaying()) musicPlayer.stop();
            } catch (IllegalStateException ignored) {
                // player อยู่ในสถานะที่ stop ไม่ได้ ปล่อยไป release ต่อ
            }
            musicPlayer.release();
            musicPlayer = null;
        }
    }

    // [isSoundEnabled / setSoundEnabled] ใช้กับสวิตช์ในหน้าตั้งค่า : บันทึกค่า ถ้าเปิด = เล่นเพลงต่อ ถ้าปิด = ปล่อยตัวเล่นเพลงแต่จำชื่อไว้เล่นต่อเมื่อเปิดอีก
    // =========================================================
    // เปิด/ปิดเสียง (ใช้กับสวิตช์ในหน้าตั้งค่า)
    // =========================================================

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    /** เปิด/ปิดเสียงทั้งเกม และบันทึกค่าไว้ให้จำข้ามการเปิดแอป */
    public static void setSoundEnabled(Context context, boolean enabled) {
        if (appContext == null) init(context);
        soundEnabled = enabled;

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();

        if (enabled) {
            resumeMusic();
        } else {
            // ปล่อยตัวเล่นเพลง แต่จำชื่อเพลงไว้ จะได้เล่นต่อเมื่อเปิดเสียงอีกครั้ง
            releaseMusicPlayer();
        }
    }

    // =========================================================

    // [rawId] หา id ของไฟล์ใน res/raw จากชื่อ (getIdentifier) คืน 0 ถ้าไม่มีไฟล์ -> ผู้เรียกจะข้ามเงียบ ๆ
    /** หา id ของไฟล์ใน res/raw จากชื่อ คืน 0 ถ้าไม่มีไฟล์นั้น */
    private static int rawId(String name) {
        if (appContext == null || name == null) return 0;
        return appContext.getResources().getIdentifier(name, "raw", appContext.getPackageName());
    }
}
