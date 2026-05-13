package com.aykut.skystrike2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends View {

    private static final String PREFS = "SkyStrikePrefs";

    // ── Google Play Games ─────────────────────────────────────────────────────
    private Object mainActivity = null;
    private String playerName   = "";

    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void setPlayerName(String name) {
        this.playerName = (name != null) ? name : "";
    }

    public void applyCloudSave(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            org.json.JSONObject j = new org.json.JSONObject(json);
            if (j.optInt("ts", 0) < totalStarsEver) return;
            highScore        = j.optInt("hs",           highScore);
            totalCoins       = j.optInt("tc",           totalCoins);
            totalStarsEver   = j.optInt("ts",           totalStarsEver);
            unlockedWaves    = j.optInt("uw",           unlockedWaves);
            unlockedMapCount = j.optInt("unlockedMaps", unlockedMapCount);
            permGunLevel     = j.optInt("permGun",      permGunLevel);
            permHealth       = j.optInt("permHP",       permHealth);
            permShield       = j.optInt("permShield",   permShield);
            permMagnet       = j.optInt("permMagnet",   permMagnet);
            difficulty       = j.optInt("diff",         difficulty);
            saveProfile();
            saveStars();
        } catch (Exception e) {
            android.util.Log.w("SkyStrike", "applyCloudSave error", e);
        }
    }

    private void syncToCloud() {
        if (mainActivity == null) return;
        try {
            org.json.JSONObject j = new org.json.JSONObject();
            j.put("hs",           highScore);
            j.put("tc",           totalCoins);
            j.put("ts",           totalStarsEver);
            j.put("uw",           unlockedWaves);
            j.put("unlockedMaps", unlockedMapCount);
            j.put("permGun",      permGunLevel);
            j.put("permHP",       permHealth);
            j.put("permShield",   permShield);
            j.put("permMagnet",   permMagnet);
            j.put("diff",         difficulty);
            mainActivity.getClass()
                    .getMethod("saveToCloud", String.class)
                    .invoke(mainActivity, j.toString());
        } catch (Exception e) {
            android.util.Log.w("SkyStrike", "syncToCloud error", e);
        }
    }
    private static final int MAX_PLAYER_HP = 100;  // now percentage-based
    private static final int BOSS_MAX_HP   = 450;  // was 120

    // Boss weapon timers (frames between each weapon firing) — 40% slower than original
    private static final int BOSS_SPREAD_INTERVAL  = 116;  // 50% slower
    private static final int BOSS_LASER_INTERVAL   = 588;  // 50% slower
    private static final int BOSS_SPIRAL_INTERVAL  = 378;  // 50% slower
    private static final int BOSS_MINE_INTERVAL    = 672;  // 50% slower
    private static final int BOSS_BARRAGE_INTERVAL = 200;  // 50% slower
    private static final int POWER_UP_DURATION = 600;
    private static final int MAX_PROGRESS  = 1000;
    private static final float PLAYER_W    = 120f;
    private static final float PLAYER_H    = 62f;

    private static final int SKY_TOP_BLUE  = Color.parseColor("#1a0535");
    private static final int SKY_MID_BLUE  = Color.parseColor("#2d0a55");
    private static final int SKY_LOW_BLUE  = Color.parseColor("#3d1270");
    private static final int HORIZON_BLUE  = Color.parseColor("#4a1a80");
    private static final int SKY_TOP_DARK  = Color.parseColor("#0d0020");
    private static final int SKY_MID_DARK  = Color.parseColor("#1a0035");
    private static final int SKY_LOW_DARK  = Color.parseColor("#2a0050");
    private static final int HORIZON_DARK  = Color.parseColor("#3a0070");
    private static final int PANEL         = Color.argb(150, 5, 12, 20);

    // ===================== SOUND =====================
    private SoundPool soundPool;
    private boolean   soundPoolReady = false;

    // Sound IDs — set to -1 until loaded
    private int sndGunShoot    = -1;
    private int sndSuperShoot  = -1;
    private int sndCannon      = -1;
    private int sndExplosionSm = -1;
    private int sndExplosionMd = -1;
    private int sndExplosionLg = -1;
    private int sndPlayerHit   = -1;
    private int sndShieldUp    = -1;
    private int sndShieldBlock = -1;
    private int sndCoinPickup  = -1;
    private int sndGunUpgrade  = -1;
    private int sndSuperPickup = -1;
    private int sndBossAppear  = -1;
    private int sndBossDeath   = -1;
    private int sndWaveStart   = -1;
    private int sndKamikaze    = -1;
    private int sndEnemyShoot  = -1;
    private int sndJetEngine   = -1;
    private int sndMissileFire = -1;
    private int sndLowHealth   = -1;
    private int sndBossLaser   = -1;
    private int sndBossSpiral  = -1;
    private int sndDroneEngine = -1;
    private int sndDroneShoot  = -1;
    private int sndJetFire     = -1;
    private int sndBomberFire  = -1;
    private int sndExplosion   = -1;  // explosion.wav — plays on every enemy death
    // ── Uploaded sounds ──────────────────────────────────────────────────
    private int sndArachnidEngine = -1;  // arachnid_engine.wav — idle hum while locked
    private int sndBossBarrage    = -1;  // boss_barrage.wav    — boss phase-3 rapid fire
    private int sndFighterEngine  = -1;  // engine_fighter.wav  — fighter spawn fly-in
    private int sndFighterGun     = -1;  // fighter_gun.wav     — fighter shooting
    private int sndHeliEngine     = -1;  // heli_engie.wav      — gunship idle rotor
    private int sndHeliMissile    = -1;  // heli_missile.wav    — gunship missile launch
    private int lowHealthSirenTimer = 0;
    private int sndVictory     = -1;
    private int sndGameOver    = -1;

    // Throttle high-frequency sounds so they don't spam every frame
    private int gunSoundThrottle    = 0;
    private int enemySoundThrottle  = 0;

    private enum GameState {
        HOME, HANGAR, UPGRADE_SHOP, SETTINGS, PLAYING, PAUSED, GAME_OVER, VICTORY
    }

    // ── Music (MediaPlayer — separate from SoundPool) ─────────────────────
    private android.media.MediaPlayer musicCurrent  = null;
    private String   musicCurrentTrack = "";
    private float    musicCurrentVol   = 0f;   // actual volume applied (for ducking)
    private float    musicTargetVol    = 0f;   // target volume (ducking drives this)
    private Context  appContext        = null;
    // Per-map track names — space/desert/ocean each get their own file
    private static final String TRACK_MENU   = "menu";
    private static final String TRACK_BOSS   = "boss";
    private static final String TRACK_SPACE  = "space";   // music_space.mp3
    private static final String TRACK_DESERT = "desert";  // music_desert.mp3
    private static final String TRACK_OCEAN  = "ocean";   // music_ocean.mp3

    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path spikePath = new Path();
    private final PorterDuffXfermode addMode = new PorterDuffXfermode(PorterDuff.Mode.SCREEN);

    private enum AirType {
        FIGHTER, BOMBER, JET, KAMIKAZE, DRONE, ARACHNID, GUNSHIP, STARSPARROW, SWITCHBLADE
    }

    private enum PowerUpType { SHIELD, SUPER, GUN_UPGRADE, MAGNET }

    private final Random          random        = new Random();
    private final Paint           paint         = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Dedicated paint for bitmap draws — never has color filters, always correct alpha blending
    private final Paint           bitmapPaint   = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final SharedPreferences prefs;

    private int   screenWidth, screenHeight;
    private float uiScale = 1f;  // computed from screen vs 1080×1920 design base
    private float planeX, planeY;
    private boolean isDragging;
    private int   hpPopupTimer = 0;     // frames to show HP popup after hit
    private int   hpShakeTimer = 0;     // frames of HP bar shake after damage
    private int   releaseSlowTimer = 0; // frames of slow-down after releasing player
    private float   dragOffsetX, dragOffsetY;
    private int     dragPointerId = -1;

    private final List<PlayerBullet>   bullets       = new ArrayList<>();
    private final List<EnemyBullet>    enemyBullets  = new ArrayList<>();
    private final List<AirEnemy>       airEnemies    = new ArrayList<>();
    private final List<ExplosionFx>    explosions    = new ArrayList<>();
    private final List<FloatingTextFx> floatingTexts = new ArrayList<>();
    private final List<CoinPickup>     coins         = new ArrayList<>();
    private final List<CoinParticle>   coinParticles = new ArrayList<>();
    private final List<PowerUpPickup>  powerUps      = new ArrayList<>();
    private final List<BossMine>       bossMines     = new ArrayList<>();
    private final List<HealthPickup>   healthPickups = new ArrayList<>();
    private int healthPickupsSpawned = 0;
    private final List<Asteroid>       asteroids     = new ArrayList<>();
    private final List<AstronautRescue> astronauts   = new ArrayList<>();
    private final List<StarPickup>       starPickups   = new ArrayList<>();  // collectible stars from meteors
    private final List<OceanPropObj>     oceanPropObjs = new ArrayList<>();  // ambient ice/fossil props
    private final List<DesertPropObj>    desertPropObjs = new ArrayList<>(); // ambient desert props
    private int meteorShowerStarsCollected = 0; // stars collected this meteor wave
    private int   astronautsSaved   = 0;   // total saved this run
    private int   astronautsSpawned = 0;   // how many placed on map so far
    private static final int MAX_ASTRONAUTS = 10;

    private BossEnemy boss;
    private boolean   bossDefeated;

    private int     shootCooldown, superShootCooldown;
    private int     cannonCooldown = 540;
    // Burst shooting (Sky Force style — 2-3 shot bursts with rhythm)
    private int     burstShotsLeft   = 0;   // shots remaining in current burst
    private int     burstShotTimer   = 0;   // delay between shots within burst
    private static final int BURST_BETWEEN = 12;   // frames between shots in burst
    private static final int MAX_GUN_LEVEL = 3;   // max 3 upgrades
    // Challenge tracking
    private int runTotalEnemiesSpawned = 0;  // every enemy that spawns this run
    private boolean hasShield;
    private int     shieldTimer;
    private int     gunPower     = 1;
    private int     coinsForNext = 5;
    private int     coinsProgress = 0;

    private int superFireTimeLeft = 0;
    private int magnetTimer       = 0;
    private int magnetSpawned     = 0;
    private int eagleSpawnTimer   = 1800;

    private float screenFlashAlpha = 0f;
    private int   screenFlashColor = Color.WHITE;
    private float skyDarkness      = 0f;
    private float shakeIntensity   = 0f;
    private Bitmap playerSprite      = null;
    private Bitmap spriteFighter     = null;
    private Bitmap spriteFighter2    = null;
    private Bitmap spriteAsteroid    = null;
    private Bitmap spriteKamikazeL   = null;
    private Bitmap spriteKamikazeR   = null;
    private final Bitmap[] kamikazeDirs = new Bitmap[8];
    private Bitmap spriteBomber      = null;
    private Bitmap spriteJet         = null;
    private Bitmap spriteGunPickup   = null;
    private Bitmap spriteGunship     = null;
    private Bitmap spriteAstronaut   = null;  // rescue astronaut character
    private Bitmap spritePlanet5     = null;
    private Bitmap spritePlanet6     = null;
    private Bitmap spritePlanet7     = null;
    private Bitmap spritePlanet8     = null;
    private Bitmap spritePlanet9     = null;
    private Bitmap spritePlanet10    = null;
    private Bitmap spritePlanet12    = null;
    private Bitmap spritePlanet13    = null;
    private Bitmap spritePlanet14    = null;
    private Bitmap spritePlanet15    = null;
    private Bitmap spritePlanet26    = null;
    private float  planetScrollY     = 0f;
    private Bitmap bgNormal          = null;
    private Bitmap bgSuperfire       = null;
    // ── Boss part sprites ──────────────────────────────────────────────
    private Bitmap bossLeftArmSprite  = null;
    private Bitmap bossRightArmSprite = null;
    private Bitmap bossHeadSprite     = null;
    private Bitmap bossBodySprite     = null;
    // Desert boss sprites
    private Bitmap desertBossBody        = null;  // main body block
    private Bitmap desertBossHead        = null;  // spiked head on top
    private Bitmap desertBossBottom      = null;  // tank tracks / bottom chassis
    private Bitmap desertBossLeftTurret  = null;  // left cannon turret
    private Bitmap desertBossRightTurret = null;  // right cannon turret
    // ── Desert infinite strip — 4 top-down aerial images ─────────────────
    private final Bitmap[] desertStrip   = new Bitmap[2];
    private final float[]  dsStrip_Y     = new float[2]; // current Y of each tile
    private Paint          dsStripPaint  = null;         // for edge blending
    // ── Ocean infinite strip — 6 top-down aerial images ──────────────────
    private final Bitmap[] oceanStrip    = new Bitmap[2];
    private final float[]  ocStrip_Y     = new float[2];
    private Paint          ocStripPaint  = null;
    // Ocean boss sprites
    private Bitmap oceanBossHead   = null;  // serpent head (left)
    private Bitmap oceanBossBody   = null;  // mid body segment
    private Bitmap oceanBossTail   = null;  // tail segment (right)
    private Bitmap oceanBossTurret = null;  // independent turret (2 drawn separately)
    // Ocean ambient props — 25 ice/fossil objects spawned as scenery
    private static final int OCEAN_PROP_COUNT = 25;
    private final Bitmap[] oceanProps = new Bitmap[OCEAN_PROP_COUNT];
    // Desert ambient props — 18 cactus/skull/bone/rock objects spawned as scenery
    private static final int DESERT_PROP_COUNT = 18;
    private final Bitmap[] desertProps = new Bitmap[DESERT_PROP_COUNT];
    private float  bgScrollY         = 0f;
    private final Bitmap[] arachnidDirs   = new Bitmap[8];
    private final Bitmap[] crossbowDirs   = new Bitmap[8];
    private final Bitmap[] sciFighterDirs   = new Bitmap[8]; // SciFighter_Top_dir1-8
    private final Bitmap[] starSparrowDirs   = new Bitmap[8]; // StarSparrow_Top_dir1-8
    private final Bitmap[] switchBladeDirs   = new Bitmap[8]; // SwitchBlade_Top_dir1-8

    private int score, highScore, coinCount, totalCoins;
    private int[] mapHighScore = new int[3]; // best score per map: [space, desert, ocean]
    private int playerHP   = MAX_PLAYER_HP;
    private int frameCount, mapProgress;
    private int pauseAnimFrame  = 0;   // runs during pause for animations
    private int gameStartFrame  = 0;   // frameCount at game start, for time-survived calc
    private int difficulty = 1;
    private static final int MAP_SPACE  = 0;
    private static final int MAP_DESERT = 1;
    private static final int MAP_OCEAN  = 2;
    private int currentMap       = MAP_SPACE;
    private int unlockedMapCount = 1;  // 1=Space only, 2=+Desert, 3=+Ocean

    // ── Adaptive difficulty ───────────────────────────────────────────────
    private float performancePressure = 1.0f;  // 0.6 (easy) → 1.5 (hard) — scales enemy count
    private int   recentKills         = 0;     // kills in last 300 frames
    private int   recentKillTimer     = 0;     // frame counter for kill window
    private int   recentHits          = 0;     // times player was hit recently
    private int   recentHitTimer      = 0;

    // ── Hit freeze ────────────────────────────────────────────────────────
    private int   hitFreezeFrames  = 0;        // frames of 1-2 frame pause remaining

    // ── Slow motion (powerful pickup) ─────────────────────────────────────
    private int   slowMoFrames    = 0;         // frames of slow-motion remaining
    private float slowMoScale     = 1.0f;      // 1.0 = normal, 0.35 = slow

    // ── Bullet trails ─────────────────────────────────────────────────────
    private final List<BulletTrail> bulletTrails = new ArrayList<>();

    // ── Typed death particles ─────────────────────────────────────────────
    private final List<DeathParticle> deathParticles = new ArrayList<>();

    private int runKills        = 0;
    private int runBulletsShot  = 0;
    private int runBulletsHit   = 0;
    private int runStartHP      = MAX_PLAYER_HP;

    private int   comboCount    = 0;
    private int   comboTimer    = 0;
    private float comboMult     = 1f;
    private static final int COMBO_TIMEOUT = 180;

    private int currentWaveType = -1;
    private int  currentWave          = 0;
    private int  totalWavesCompleted  = 0;
    private int  waveEnemiesRemaining = 0;
    private boolean waveInProgress    = false;
    private boolean waveSpawnFinished  = false;  // all enemies spawned but screen not yet clear
    private int  waveClearTimeout     = 0;       // countdown after spawn: if >0 forces retreat
    private int  waveCooldown         = 240;
    private int  waveEnemySpawnTimer  = 0;
    // ── DEBUG: set true to skip waves and fight boss immediately ─────────
    private static final boolean DEBUG_BOSS = false;

    private static final int BOSS_AFTER_WAVES = 15;
    private static final int[] WAVE_SEQUENCE = {1, 0, 3, 6, 9, 2, 5, 7, 9, 4, 3, 6, 5, 4, 8};

    // ── Sky Force medal system ─────────────────────────────────────────────
    // Each wave awards 0–3 stars based on performance
    // Stars gate which wave you can replay / how hard next wave is
    private final int[] waveStars = new int[BOSS_AFTER_WAVES + 1]; // stars earned per wave (0-3)
    private int   runWaveStars    = 0;   // stars earned in current wave run
    private int   totalStarsEver  = 0;   // sum across all waves (loaded from prefs)

    // In-wave performance tracking (reset each wave)
    private int   waveKillsNeeded    = 0;   // total enemies spawned this wave
    private int   waveKillsGot       = 0;   // enemies killed this wave
    private int   waveEnemiesAlive   = 0;   // still alive on screen (spawned - killed - escaped)
    private int   waveStartHP        = 0;   // HP at wave start
    private boolean waveAstroSpawned = false; // whether an astronaut appeared this wave
    private boolean waveAstroSaved   = false; // whether it was rescued

    // Between-wave upgrade shop

    // Wave select — player can replay any unlocked wave
    private boolean waveSelectOpen  = false;
    private float   waveSelectAnim  = 0f;
    private int     unlockedWaves   = 1;    // how many waves are unlocked (persisted)

    private float scrollSpeed = 2f;
    private float skyWaveOffset;
    private int   flameFrame;

    private RectF btnEasy, btnMedium, btnHard;
    private RectF pauseBtn, resumeBtn, btnPauseRestart, btnPauseHome;
    private RectF btnPlayAgain = null;  // defeat/victory screen play again button
    private RectF btnMainPage  = null;  // defeat/victory screen main page button
    // Menu press state — for button press animation
    private float menuTouchX = -1, menuTouchY = -1;
    private boolean menuTouchDown = false;
    private float settingsScrollY      = 0f;
    private float settingsContentH     = 0f;
    private float settingsLastTouchY   = 0f;
    private boolean settingsDragging   = false;
    // Transition delay — brief pause before switching state for polish
    private int menuTransitionTimer = 0;
    private GameState menuTransitionTarget = null;
    // Start screen button
    private RectF btnStart;
    // Difficulty popup — shown as overlay on start screen
    private boolean difficultyPopupOpen = false;
    private RectF   btnDifficultySelect;   // "SELECT DIFFICULTY" button on start screen
    private RectF   btnMapSelect;           // map toggle button on start screen

    // ── Permanent upgrades (saved to prefs) ──────────────────────────────
    private int permGunLevel  = 0;  // max 3 extra levels
    private int permFireRate  = 0;  // max 5
    private int permHealth    = 0;  // max 5
    private int permShield    = 0;  // max 5
    private int permMagnet    = 0;  // max 5

    // ── Settings ──────────────────────────────────────────────────────────
    private boolean sfxEnabled          = true;
    private android.os.Vibrator vibrator = null;
    private boolean vibrationEnabled    = true;
    private float   dragSensitivity     = 1.0f;  // 0.7=slow / 1.0=normal / 1.4=fast
    private boolean screenShakeEnabled  = true;
    private boolean damageNumbersEnabled= true;
    private int     effectsQuality      = 1;      // 0=low 1=med 2=high

    // ── Home screen nav buttons ───────────────────────────────────────────
    private RectF btnHomeStart, btnHomeUpgrades, btnHomeSettings, btnHomeHangar;
    // ── Upgrade shop buttons ──────────────────────────────────────────────
    private RectF btnShopBack;
    private RectF[] btnBuyUpgrade = new RectF[5];
    // ── Settings buttons ─────────────────────────────────────────────────
    private RectF btnSettingsBack;
    private RectF btnSfxToggle, btnVibToggle, btnSensLow, btnSensMed, btnSensHigh;
    private RectF btnSettingsDiff0, btnSettingsDiff1, btnSettingsDiff2;
    private RectF btnSettingsMap0, btnSettingsMap1, btnSettingsMap2;
    private RectF btnShakeToggle, btnDmgNumToggle;
    private RectF btnEffLow, btnEffMed, btnEffHigh;
    private float   difficultyPopupAnim = 0f; // 0→1 open animation

    private GameState gameState = GameState.HOME;

    private LinearGradient skyGradient;
    private RadialGradient  sunGlow;

    // -------------------------------------------------------
    public GameView(Context context) {
        this(context, PREFS);  // default prefs for local testing
    }

    public GameView(Context context, String prefsName) {
        super(context);
        appContext = context;
        prefs      = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        highScore  = prefs.getInt("hs", 0);
        mapHighScore[0] = prefs.getInt("hs_space",  0);
        mapHighScore[1] = prefs.getInt("hs_desert", 0);
        mapHighScore[2] = prefs.getInt("hs_ocean",  0);
        totalCoins = prefs.getInt("tc", 0);
        totalStarsEver = prefs.getInt("ts", 0);
        unlockedWaves  = prefs.getInt("uw", 1);
        for (int i = 0; i <= BOSS_AFTER_WAVES; i++) {
            waveStars[i] = prefs.getInt("ws" + i, 0);
        }
        loadProfile();
        vibrator = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        setFocusable(true);
        initSoundPool(context);
    }

    private void loadProfile() {
        permGunLevel = prefs.getInt("permGun",    0);
        permFireRate = prefs.getInt("permFire",   0);
        permHealth   = prefs.getInt("permHP",     0);
        permShield   = prefs.getInt("permShield", 0);
        permMagnet   = prefs.getInt("permMagnet", 0);
        sfxEnabled       = prefs.getBoolean("sfx", true);
        currentMap       = prefs.getInt("map", MAP_SPACE);
        unlockedMapCount = prefs.getInt("unlockedMaps", 1);
        if (unlockedMapCount < 1) unlockedMapCount = 1;
        if (unlockedMapCount > 3) unlockedMapCount = 3;
        if (currentMap >= unlockedMapCount) currentMap = unlockedMapCount - 1;
        vibrationEnabled = prefs.getBoolean("vib", true);
        dragSensitivity  = prefs.getFloat("drag", 1.0f);
        difficulty       = prefs.getInt("diff", 1);
        screenShakeEnabled   = prefs.getBoolean("shake", true);
        damageNumbersEnabled = prefs.getBoolean("dmgnum", true);
        effectsQuality       = prefs.getInt("fxq", 1);
        // Restore purchased upgrades — overrides saved int levels with billing truth
        loadUpgrades();
    }

    private void saveProfile() {
        prefs.edit()
                .putInt("permGun",    permGunLevel)
                .putInt("permFire",   permFireRate)
                .putInt("permHP",     permHealth)
                .putInt("permShield", permShield)
                .putInt("permMagnet", permMagnet)
                .putBoolean("sfx",    sfxEnabled)
                .putBoolean("vib",    vibrationEnabled)
                .putFloat("drag",     dragSensitivity)
                .putInt("diff",       difficulty)
                .putInt("map",        currentMap)
                .putInt("unlockedMaps", unlockedMapCount)
                .putBoolean("shake",  screenShakeEnabled)
                .putBoolean("dmgnum", damageNumbersEnabled)
                .putInt("fxq",        effectsQuality)
                .apply();
    }

    // ── IAP Product IDs — one per upgrade slot + level ────────────────────
    // Format: "upgrade_<slot>_<level>" e.g. "upgrade_gun_1", "upgrade_gun_2"
    private static final String[] UPGRADE_KEYS = {
            "gun", "fire", "health", "shield", "magnet"
    };

    /**
     * Called when player taps the buy button for upgrade slot [upgradeIndex].
     *
     * Integrating Google Play Billing:
     *   1. Add billing dependency in build.gradle:
     *      implementation 'com.android.billingclient:billing:6.0.0'
     *   2. In MainActivity, implement PurchasesUpdatedListener and pass a
     *      callback here. On successful purchase call: applyUpgrade(upgradeIndex)
     *   3. Replace the direct applyUpgrade() call below with:
     *      ((MainActivity) getContext()).launchBillingFlow(productId);
     *
     * Until billing is integrated this grants the upgrade directly (debug behaviour).
     */
    private void purchaseUpgrade(int upgradeIndex) {
        int[] levels = getPermLevels();
        if (upgradeIndex < 0 || upgradeIndex >= 5) return;
        if (levels[upgradeIndex] >= UPGRADE_MAX[upgradeIndex]) return;

        String productId = "upgrade_" + UPGRADE_KEYS[upgradeIndex]
                + "_" + (levels[upgradeIndex] + 1);

        // ── Check if already purchased (restore purchases support) ────────
        if (prefs.getBoolean("purchased_" + productId, false)) {
            // Already paid for — apply without re-charging
            applyUpgrade(upgradeIndex, productId);
            return;
        }

        // ── TODO: replace with real billing flow ──────────────────────────
        // ((MainActivity) getContext()).launchBillingFlow(productId);
        //
        // For now: grant directly and mark as purchased in SharedPreferences
        applyUpgrade(upgradeIndex, productId);
    }

    /**
     * Called after a successful purchase confirmation (from billing callback or
     * restore purchases). Marks purchased in SharedPreferences and applies stat.
     */
    public void applyUpgrade(int upgradeIndex, String productId) {
        // Mark purchased so restore works across re-installs
        prefs.edit().putBoolean("purchased_" + productId, true).apply();

        // Apply the stat boost
        switch (upgradeIndex) {
            case 0: permGunLevel = Math.min(permGunLevel + 1, UPGRADE_MAX[0]); break;
            case 1: permFireRate = Math.min(permFireRate + 1, UPGRADE_MAX[1]); break;
            case 2: permHealth   = Math.min(permHealth   + 1, UPGRADE_MAX[2]); break;
            case 3: permShield   = Math.min(permShield   + 1, UPGRADE_MAX[3]); break;
            case 4: permMagnet   = Math.min(permMagnet   + 1, UPGRADE_MAX[4]); break;
        }

        saveProfile();
        loadUpgrades(); // re-apply to live game state immediately

        playSound(sndGunUpgrade, 1.0f, 1.1f);
        screenFlashAlpha = 35f;
        screenFlashColor = Color.parseColor("#00D4FF");
        addFloatingBig(screenWidth/2f, screenHeight*0.45f,
                UPGRADE_NAMES[upgradeIndex] + " UPGRADED!", Color.parseColor("#00D4FF"));
    }

    /**
     * Scans SharedPreferences for all "purchased_*" keys and re-derives perm
     * levels. Call this on app start (after loadProfile) and after any purchase
     * to keep game state in sync with billing records.
     *
     * This is the "restore purchases" equivalent for non-consumables.
     */
    public void loadUpgrades() {
        // Re-derive each level from purchased_ flags (authoritative source)
        // Levels are strictly sequential: must own level N to have level N+1
        for (int slot = 0; slot < 5; slot++) {
            int maxOwned = 0;
            for (int lv = 1; lv <= UPGRADE_MAX[slot]; lv++) {
                String key = "purchased_upgrade_" + UPGRADE_KEYS[slot] + "_" + lv;
                if (prefs.getBoolean(key, false)) maxOwned = lv;
                else break; // sequential — stop at first missing level
            }
            switch (slot) {
                case 0: permGunLevel = maxOwned; break;
                case 1: permFireRate = maxOwned; break;
                case 2: permHealth   = maxOwned; break;
                case 3: permShield   = maxOwned; break;
                case 4: permMagnet   = maxOwned; break;
            }
        }
        // Apply HP boost immediately to live game if mid-session
        if (gameState == GameState.PLAYING) {
            playerHP = Math.min(playerHP + 10, MAX_PLAYER_HP);
        }
    }



    private String getMapName() {
        switch (currentMap) {
            case MAP_DESERT: return "DESERT";
            case MAP_OCEAN:  return "OCEAN";
            default:         return "DEEP SPACE";
        }
    }

    private String getMapEmoji() {
        switch (currentMap) {
            case MAP_DESERT: return "🏜";
            case MAP_OCEAN:  return "🌊";
            default:         return "🌌";
        }
    }

    private int getMapColor() {
        switch (currentMap) {
            case MAP_DESERT: return Color.argb(130, 255, 210, 90);
            case MAP_OCEAN:  return Color.argb(130, 80, 220, 255);
            default:         return Color.argb(130, 100, 180, 255);
        }
    }

    private float getMapHpMultiplier() {
        switch (currentMap) {
            case MAP_DESERT: return 1.08f;
            case MAP_OCEAN:  return 0.95f;
            default:         return 1.00f;
        }
    }

    private float getMapSpeedMultiplier() {
        switch (currentMap) {
            case MAP_DESERT: return 1.10f;
            case MAP_OCEAN:  return 0.92f;
            default:         return 1.00f;
        }
    }

    private float getMapCoinMultiplier() {
        switch (currentMap) {
            case MAP_OCEAN:  return 1.20f;
            case MAP_DESERT: return 1.10f;
            default:         return 1.00f;
        }
    }

    private float getMapBulletSpeedMultiplier() {
        switch (currentMap) {
            case MAP_DESERT: return 1.08f;
            case MAP_OCEAN:  return 0.94f;
            default:         return 1.00f;
        }
    }

    private int getMapAsteroidInterval() {
        switch (currentMap) {
            case MAP_DESERT: return 160;  // more hazard pressure
            case MAP_OCEAN:  return 310;  // fewer asteroids on ocean
            default:         return 240;
        }
    }

    private void updateMapGameplayFx() {
        if (currentMap == MAP_DESERT) {
            // dsr_gustTimer managed inside drawSkyDesert already
        }
        // Ocean foam pulse is computed inline in drawOceanFoam
    }

    private String getStageProgressText() {
        switch (unlockedMapCount) {
            case 1: return "Complete Deep Space to unlock Desert";
            case 2: return "Complete Desert to unlock Ocean";
            default: return "All stages unlocked!";
        }
    }

    private void unlockNextMapIfNeeded() {
        int nextMap = currentMap + 1;
        if (nextMap < 3 && unlockedMapCount < nextMap + 1) {
            unlockedMapCount = nextMap + 1;
            currentMap = nextMap;
            String name = nextMap == MAP_DESERT ? "🏜 DESERT UNLOCKED!" : "🌊 OCEAN UNLOCKED!";
            addFloatingBig(screenWidth/2f, screenHeight*0.30f, "NEW STAGE UNLOCKED!", Color.parseColor("#FFD700"));
            addFloatingBig(screenWidth/2f, screenHeight*0.40f, name,
                    nextMap == MAP_DESERT ? Color.parseColor("#ffcc66") : Color.parseColor("#66ddff"));
        }
        prefs.edit().putInt("map", currentMap).putInt("unlockedMaps", unlockedMapCount).apply();
    }

    private int getUpgradeCost(int type, int level) {
        // Flat 50 coins per upgrade (0.5$ equivalent)
        return 50;
    }

    private void applyPermanentUpgrades() {
        playerHP  = MAX_PLAYER_HP;
        gunPower  = Math.min(MAX_GUN_LEVEL, 1 + permGunLevel);
    }

    private void initSoundPool(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(10)
                    .setAudioAttributes(aa)
                    .build();
        } else {
            soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        }

        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) soundPoolReady = true;
        });

        try {
            sndGunShoot    = SoundGenerator.loadSound(soundPool, context, "gun_shoot");
            sndSuperShoot  = SoundGenerator.loadSound(soundPool, context, "super_shoot");
            sndCannon      = SoundGenerator.loadSound(soundPool, context, "cannon_boom");
            sndExplosionSm = SoundGenerator.loadSound(soundPool, context, "explosion_sm");
            sndExplosionMd = SoundGenerator.loadSound(soundPool, context, "explosion_md");
            sndExplosionLg = SoundGenerator.loadSound(soundPool, context, "explosion_lg");
            sndPlayerHit   = SoundGenerator.loadSound(soundPool, context, "player_hit");
            sndShieldUp    = SoundGenerator.loadSound(soundPool, context, "shield_up");
            sndShieldBlock = SoundGenerator.loadSound(soundPool, context, "shield_block");
            sndCoinPickup  = SoundGenerator.loadSound(soundPool, context, "coin_pickup");
            sndGunUpgrade  = SoundGenerator.loadSound(soundPool, context, "gun_upgrade");
            sndSuperPickup = SoundGenerator.loadSound(soundPool, context, "super_pickup");
            sndBossAppear  = SoundGenerator.loadSound(soundPool, context, "boss_appear");
            sndBossDeath   = SoundGenerator.loadSound(soundPool, context, "boss_death");
            sndWaveStart   = SoundGenerator.loadSound(soundPool, context, "wave_start");
            sndKamikaze    = SoundGenerator.loadSound(soundPool, context, "kamikaze");
            sndEnemyShoot  = SoundGenerator.loadSound(soundPool, context, "enemy_shoot");
            sndJetEngine   = SoundGenerator.loadSound(soundPool, context, "jet_engine");
            sndMissileFire = SoundGenerator.loadSound(soundPool, context, "missile_fire");
            sndLowHealth   = SoundGenerator.loadSound(soundPool, context, "low_health");
            sndBossLaser   = loadRawSound(soundPool, context, "boss_laser");
            sndBossSpiral  = loadRawSound(soundPool, context, "boss_spiral");
            sndDroneEngine = loadRawSound(soundPool, context, "drone_engine");
            sndDroneShoot  = loadRawSound(soundPool, context, "drone_laser");
            sndJetFire     = loadRawSound(soundPool, context, "jet_fire");
            sndKamikaze    = loadRawSound(soundPool, context, "kamikaze");
            sndJetEngine   = loadRawSound(soundPool, context, "jet_engine");
            sndBomberFire  = loadRawSound(soundPool, context, "bomber_fire");
            sndExplosion   = loadRawSound(soundPool, context, "explosion");
            sndArachnidEngine = loadRawSound(soundPool, context, "arachnid_engine");
            sndBossBarrage    = loadRawSound(soundPool, context, "boss_barrage");
            sndFighterEngine  = loadRawSound(soundPool, context, "engine_fighter");
            sndFighterGun     = loadRawSound(soundPool, context, "fighter_gun");
            sndHeliEngine     = loadRawSound(soundPool, context, "heli_engie");
            sndHeliMissile    = loadRawSound(soundPool, context, "heli_missile");
            sndVictory     = SoundGenerator.loadSound(soundPool, context, "victory");
            sndGameOver    = SoundGenerator.loadSound(soundPool, context, "game_over");
        } catch (Exception e) {
            // Missing sound — game still runs silently
        }
    }

    private int loadRawSound(android.media.SoundPool pool, Context ctx, String name) {
        try {
            int resId = ctx.getResources().getIdentifier(name, "raw", ctx.getPackageName());
            if (resId == 0) return -1;
            return pool.load(ctx, resId, 1);
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Music system ──────────────────────────────────────────────────────

    /** Switch BGM track. Call every frame — safe, only acts on track change. */
    private void playMusic(String track) {
        if (track.equals(musicCurrentTrack)) {
            updateMusicDucking();
            return;
        }
        // Fade out and release old player on background thread
        final android.media.MediaPlayer old = musicCurrent;
        musicCurrent = null;          // clear immediately to stop ducking/volume writes to old
        musicCurrentTrack = track;
        musicCurrentVol = 0f;

        if (old != null) {
            new Thread(() -> {
                try {
                    for (float v = 0.3f; v >= 0f; v -= 0.04f) {
                        old.setVolume(v, v);
                        Thread.sleep(18);
                    }
                    old.pause(); old.release();
                } catch (Exception ignored) {}
            }).start();
        }

        if (track.isEmpty() || appContext == null) return;

        try {
            int resId = appContext.getResources().getIdentifier(
                    "music_" + track, "raw", appContext.getPackageName());
            if (resId == 0) return; // file missing — silent, no crash

            android.media.MediaPlayer mp = android.media.MediaPlayer.create(appContext, resId);
            if (mp == null) return;
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.start();
            musicCurrent = mp;

            // Fade in — snapshot mp so thread holds correct reference even if track switches again
            final android.media.MediaPlayer mpSnap = mp;
            final String trackSnap = track;
            new Thread(() -> {
                try {
                    float targetV = baseVolForTrack(trackSnap);
                    for (float v = 0f; v <= targetV; v += 0.025f) {
                        if (musicCurrent != mpSnap) return; // track already changed, stop fade
                        mpSnap.setVolume(v, v);
                        musicCurrentVol = v;
                        Thread.sleep(18);
                    }
                    if (musicCurrent == mpSnap) {
                        mpSnap.setVolume(targetV, targetV);
                        musicCurrentVol = targetV;
                    }
                } catch (Exception ignored) {}
            }).start();

        } catch (Exception ignored) {}
    }

    /** Base volume for each track — boss louder, ocean softer */
    private float baseVolForTrack(String track) {
        float base = musicVolume * 0.32f;
        if (track.equals(TRACK_BOSS))   return base * 1.15f; // slightly louder during boss
        if (track.equals(TRACK_OCEAN))  return base * 0.88f; // softer, calmer feel
        if (track.equals(TRACK_DESERT)) return base * 1.05f; // slightly tense
        return base;
    }

    /** Dynamic ducking — lower music during heavy action, restore during calm */
    private void updateMusicDucking() {
        if (musicCurrent == null) return;
        float base = baseVolForTrack(musicCurrentTrack);
        // Duck when many explosions on screen or boss is firing
        boolean heavyAction = explosions.size() > 4
                || (boss != null && boss.laserFiring)
                || superFireTimeLeft > 0;
        musicTargetVol = heavyAction ? base * 0.65f : base;
        // Smooth lerp toward target
        musicCurrentVol += (musicTargetVol - musicCurrentVol) * 0.04f;
        try { musicCurrent.setVolume(musicCurrentVol, musicCurrentVol); }
        catch (Exception ignored) {}
    }

    /** Update music volume from settings slider in real time */
    private void applyMusicVolume() {
        if (musicCurrent == null) return;
        float vol = baseVolForTrack(musicCurrentTrack);
        musicCurrentVol = vol;
        try { musicCurrent.setVolume(vol, vol); } catch (Exception ignored) {}
    }

    public void pauseMusic()  { try { if (musicCurrent != null && musicCurrent.isPlaying())  musicCurrent.pause(); } catch (Exception ignored) {} }
    public void resumeMusic() { try { if (musicCurrent != null && !musicCurrent.isPlaying()) musicCurrent.start(); } catch (Exception ignored) {} }
    public void releaseMusic() {
        try { if (musicCurrent != null) { musicCurrent.pause(); musicCurrent.release(); musicCurrent = null; } } catch (Exception ignored) {}
        musicCurrentTrack = "";
    }


    private void playSound(int soundId, float volume, float pitch) {
        if (!soundPoolReady || soundPool == null || soundId < 0) return;
        if (!sfxEnabled) return;
        float vol = volume * sfxVolume; // apply user volume setting
        soundPool.play(soundId, vol, vol, 1, 0, pitch);
    }

    private void vibrate(int ms) {
        if (!vibrationEnabled || vibrator == null) return;
        try {
            post(() -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(ms,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(ms);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    public void releaseSound() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        screenWidth  = w;
        screenHeight = h;
        // Scale relative to 1080×1920 design base — use smaller axis so nothing clips
        uiScale = Math.min(w / 1080f, h / 1920f);
        planeX = screenWidth / 2f - PLAYER_W / 2f;
        planeY = screenHeight * 0.82f;

        try {
            int resId = getResources().getIdentifier("skyjet_player","drawable",getContext().getPackageName());
            if (resId != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), resId);
                playerSprite = Bitmap.createScaledBitmap(raw, 240, 240, true);
                if (raw != playerSprite) raw.recycle(); }
        } catch (Exception e) { playerSprite = null; }

        spriteFighter   = loadSprite("enemy_fighter",        110, 110);
        spriteFighter2  = loadSprite("enemy_fighter2",       210, 210);
        spriteAsteroid  = loadSprite("enemy_asteroid",       140, 140);
        for (int d = 0; d < 8; d++) {
            kamikazeDirs[d] = loadSprite("kamikaze_dir" + (d+1), 210, 210);
        }
        spriteBomber    = loadSprite("enemy_bomber",         215, 215);
        spriteJet       = loadSprite("enemy_jet",            160, 160);
        spriteGunPickup = loadSprite("pickup_gun",            80,  80);
        spriteGunship   = loadSprite("enemy_heli",           210, 210);
        spriteAstronaut = loadSprite("astronaut",             75,  75);
        spritePlanet5   = loadSprite("planet_5",              180, 180);
        spritePlanet6   = loadSprite("planet_6",              180, 180);
        spritePlanet7   = loadSprite("planet_7",              160, 160);
        spritePlanet8   = loadSprite("planet_8",              150, 150);
        spritePlanet9   = loadSprite("planet_9",              170, 170);
        spritePlanet10  = loadSprite("planet_10",             190, 190);
        spritePlanet12  = loadSprite("planet_12",             165, 165);
        spritePlanet13  = loadSprite("planet_13",             175, 175);
        spritePlanet14  = loadSprite("planet_14",             185, 185);
        spritePlanet15  = loadSprite("planet_15",             170, 170);
        spritePlanet26  = loadSprite("planet_26",             160, 160);

        bgNormal    = loadSprite("bg_normal",    screenWidth, screenHeight);
        bgSuperfire = loadSprite("bg_superfire", screenWidth, screenHeight);

        // Boss part sprites
        bossLeftArmSprite  = loadSprite("boss_left_arm",  240, 240);
        bossRightArmSprite = loadSprite("boss_right_arm", 240, 240);
        bossHeadSprite     = loadSprite("boss_head",      200, 200);
        bossBodySprite     = loadSprite("boss_body",      280, 280);
        // Desert boss sprites
        desertBossBody        = loadSprite("desert_body",         360, 290);
        desertBossHead        = loadSprite("desert_head",         380, 200);
        desertBossBottom      = loadSprite("desert_bottom",       420, 140);
        desertBossLeftTurret  = loadSprite("desert_left_turret",  200, 200);
        desertBossRightTurret = loadSprite("desert_right_turret", 200, 200);

        // ── Desert infinite strip — center-crop each image to screen size ─
        String[] stripNames = {"desert_strip_1", "desert_strip_2"};
        for (int si = 0; si < 2; si++) {
            try {
                int rid = getResources().getIdentifier(stripNames[si], "drawable", getContext().getPackageName());
                if (rid != 0) {
                    android.graphics.Bitmap raw = android.graphics.BitmapFactory.decodeResource(getResources(), rid);
                    if (raw != null) {
                        desertStrip[si] = getCenterCroppedBitmap(raw, screenWidth, screenHeight);
                        raw.recycle();
                    }
                }
            } catch (Exception ignored) {}
            // Start tiles: one above screen, rest below (screen always covered)
            dsStrip_Y[si] = (si - 1) * (float)screenHeight;
        }
        dsStripPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        dsStripPaint.setAlpha(160); // 43% opacity — background subtle, characters clearly visible

        // ── Ocean infinite strip — 6 aerial tiles ────────────────────────
        String[] oceanStripNames = {"ocean_strip_new_1","ocean_strip_new_2"};
        for (int si = 0; si < 2; si++) {
            try {
                int rid = getResources().getIdentifier(oceanStripNames[si], "drawable", getContext().getPackageName());
                if (rid != 0) {
                    android.graphics.Bitmap raw = android.graphics.BitmapFactory.decodeResource(getResources(), rid);
                    if (raw != null) {
                        oceanStrip[si] = getCenterCroppedBitmap(raw, screenWidth, screenHeight);
                        raw.recycle();
                    }
                }
            } catch (Exception ignored) {}
            ocStrip_Y[si] = (si - 1) * (float)screenHeight;
        }
        ocStripPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        ocStripPaint.setAlpha(148); // 43% opacity — background subtle, characters clearly visible
        // Ocean boss sprites
        oceanBossHead   = loadSprite("ocean_head",   472, 388);  // 630×517 scaled
        oceanBossBody   = loadSprite("ocean_body",   572, 220);  // 868×335 scaled
        oceanBossTail   = loadSprite("ocean_tail",   510, 211);  // 890×368 scaled
        oceanBossTurret = loadSprite("ocean_turret", 280, 160);

        // Load ocean ambient prop sprites (ocean_prop_01..25)
        for (int p = 0; p < OCEAN_PROP_COUNT; p++) {
            oceanProps[p] = loadSprite(String.format("ocean_prop_%02d", p + 1), 160, 80);
        }
        // Load desert ambient prop sprites (desert_prop_01..18)
        for (int p = 0; p < DESERT_PROP_COUNT; p++) {
            desertProps[p] = loadSprite(String.format("desert_prop_%02d", p + 1), 160, 80);
        }

        for (int d = 0; d < 8; d++) {
            arachnidDirs[d] = loadSprite("arachnid_dir" + (d+1), 210, 210);
        }
        for (int d = 0; d < 8; d++) {
            crossbowDirs[d] = loadSprite("crossbow_dir" + (d+1), 170, 170);
        }
        for (int d = 0; d < 8; d++) {
            sciFighterDirs[d] = loadSprite("scifighter_dir" + (d+1), 200, 200);
            starSparrowDirs[d] = loadSprite("starsparrow_dir" + (d+1), 200, 200);
            switchBladeDirs[d] = loadSprite("switchblade_dir" + (d+1), 200, 200);
        }

        sunGlow = new RadialGradient(
                screenWidth * 0.8f, screenHeight * 0.13f, 120f,
                new int[]{Color.argb(120,255,250,220),
                        Color.argb(20,255,255,255),
                        Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);

        pauseBtn       = new RectF(screenWidth - 90, 10, screenWidth - 10, 90);
        resumeBtn      = new RectF(screenWidth/2f - 160, screenHeight/2f - 60,
                screenWidth/2f + 160, screenHeight/2f - 60 + 80);
        btnPauseRestart = new RectF(screenWidth/2f - 160, screenHeight/2f + 40,
                screenWidth/2f + 160, screenHeight/2f + 40 + 80);
        btnPauseHome    = new RectF(screenWidth/2f - 160, screenHeight/2f + 140,
                screenWidth/2f + 160, screenHeight/2f + 140 + 80);

        int dbW = screenWidth / 3 - 20;
        int dbY = (int)(screenHeight * 0.55f);
        btnEasy   = new RectF(10,       dbY, dbW,          dbY + 90);
        btnMedium = new RectF(dbW + 20, dbY, dbW * 2 + 10, dbY + 90);
        btnHard   = new RectF(dbW*2+20, dbY, screenWidth-10, dbY + 90);
        btnStart  = new RectF(screenWidth/2f - 180, screenHeight * 0.62f,
                screenWidth/2f + 180, screenHeight * 0.62f + 100);
        btnDifficultySelect = new RectF(screenWidth/2f - 200, screenHeight * 0.755f,
                screenWidth/2f + 200, screenHeight * 0.755f + 80);
        btnMapSelect        = new RectF(screenWidth/2f - 200, screenHeight * 0.855f,
                screenWidth/2f + 200, screenHeight * 0.855f + 70);

        // Home screen nav buttons
        float hw = screenWidth / 2f;
        btnHomeStart    = new RectF(hw-200, screenHeight*0.65f, hw+200, screenHeight*0.65f+100);
        btnHomeUpgrades = new RectF(12,      screenHeight*0.79f, hw-8,   screenHeight*0.79f+68);
        btnHomeSettings = new RectF(hw+8,    screenHeight*0.79f, screenWidth-12, screenHeight*0.79f+68);
        btnHomeHangar   = new RectF(hw-200,  screenHeight*0.90f, hw+200, screenHeight*0.90f+50);

        // Shop back button
        btnShopBack     = new RectF(20, 20, 160, 90);
        btnSettingsBack = new RectF(20, 20, 160, 90);
        // Shop buy buttons (5 upgrades)
        for (int u = 0; u < 5; u++) {
            float by = screenHeight * 0.22f + u * (screenHeight * 0.13f);
            btnBuyUpgrade[u] = new RectF(screenWidth - 220, by + 14, screenWidth - 20, by + 70);
        }
        // Settings toggles
        float sy = screenHeight * 0.22f;
        float sg = screenHeight * 0.10f;
        btnSfxToggle  = new RectF(screenWidth*0.55f, sy,       screenWidth-30, sy+60);
        btnVibToggle  = new RectF(screenWidth*0.55f, sy+sg,    screenWidth-30, sy+sg+60);
        btnSensLow    = new RectF(screenWidth*0.30f, sy+sg*2,  screenWidth*0.47f, sy+sg*2+60);
        btnSensMed    = new RectF(screenWidth*0.50f, sy+sg*2,  screenWidth*0.67f, sy+sg*2+60);
        btnSensHigh   = new RectF(screenWidth*0.70f, sy+sg*2,  screenWidth*0.87f, sy+sg*2+60);
        btnSettingsDiff0 = new RectF(screenWidth*0.12f, sy+sg*3.3f, screenWidth*0.39f, sy+sg*3.3f+64);
        btnSettingsDiff1 = new RectF(screenWidth*0.42f, sy+sg*3.3f, screenWidth*0.69f, sy+sg*3.3f+64);
        btnSettingsDiff2 = new RectF(screenWidth*0.72f, sy+sg*3.3f, screenWidth*0.99f, sy+sg*3.3f+64);
        btnSettingsMap0  = new RectF(screenWidth*0.05f, sy+sg*4.8f, screenWidth*0.37f, sy+sg*4.8f+64);
        btnSettingsMap1  = new RectF(screenWidth*0.40f, sy+sg*4.8f, screenWidth*0.72f, sy+sg*4.8f+64);
        btnSettingsMap2  = new RectF(screenWidth*0.75f, sy+sg*4.8f, screenWidth*0.99f, sy+sg*4.8f+64);
    }

    private void rebuildSkyGradient() {
        if (screenHeight == 0) return;
        int top   = lerpColor(SKY_TOP_BLUE, SKY_TOP_DARK, skyDarkness);
        int mid   = lerpColor(SKY_MID_BLUE, SKY_MID_DARK, skyDarkness);
        int low   = lerpColor(SKY_LOW_BLUE, SKY_LOW_DARK, skyDarkness);
        int horiz = lerpColor(HORIZON_BLUE, HORIZON_DARK, skyDarkness);
        skyGradient = new LinearGradient(0, 0, 0, screenHeight,
                new int[]{top, mid, low, horiz},
                new float[]{0f, 0.3f, 0.75f, 1f},
                Shader.TileMode.CLAMP);
    }

    private int lerpColor(int a, int b, float t) {
        return Color.rgb(
                (int)(Color.red(a)   + (Color.red(b)   - Color.red(a))   * t),
                (int)(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int)(Color.blue(a)  + (Color.blue(b)  - Color.blue(a))  * t));
    }

    // ===================== DRAW DISPATCH =====================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Transition delay — count down then switch state
        if (menuTransitionTimer > 0) {
            menuTransitionTimer--;
            if (menuTransitionTimer == 0 && menuTransitionTarget != null) {
                gameState = menuTransitionTarget;
                menuTransitionTarget = null;
            }
        }

        switch (gameState) {
            case HOME:
                playMusic("menu");
                drawHomeScreen(canvas);
                postInvalidateOnAnimation(); return;
            case UPGRADE_SHOP:
                playMusic("menu");
                drawUpgradeShop(canvas);
                postInvalidateOnAnimation(); return;
            case SETTINGS:
                playMusic("menu");
                drawSettingsScreen(canvas);
                postInvalidateOnAnimation(); return;
            case PAUSED:
                pauseAnimFrame++;
                drawPausedScreen(canvas);
                postInvalidateOnAnimation(); return;
            case GAME_OVER:
                playMusic("");
                drawGameOverScreen(canvas);
                postInvalidateOnAnimation(); return;
            case VICTORY:
                playMusic("");
                drawVictoryScreen(canvas);
                postInvalidateOnAnimation(); return;
            case PLAYING:
                updateAndDrawGame(canvas);
                postInvalidateOnAnimation();
        }
    }

    // ===================== GAME LOOP =====================
    private void updateAndDrawGame(Canvas canvas) {
        // Per-map music — boss overrides all
        if (boss != null && !bossDefeated) {
            playMusic(TRACK_BOSS);
        } else {
            switch (currentMap) {
                case MAP_DESERT: playMusic(TRACK_DESERT); break;
                case MAP_OCEAN:  playMusic(TRACK_OCEAN);  break;
                default:         playMusic(TRACK_SPACE);  break;
            }
        }

        // ── Hit freeze — skip all logic for 1-2 frames ────────────────────
        if (hitFreezeFrames > 0) {
            hitFreezeFrames--;
            // Still draw but don't update — gives that "punch" pause
            drawSky(canvas);
            drawPlayerPlane(canvas);
            drawHUD(canvas);
            drawProgressBar(canvas);
            drawPauseButton(canvas);
            postInvalidateOnAnimation();
            return;
        }

        // ── Slow motion time scale ─────────────────────────────────────────
        // slowMoFrames: reserved for special events (kill streak, boss phase)
        // Normal gameplay: slow when not touching, full speed when dragging
        float targetScale;
        if (slowMoFrames > 0) {
            // Special event slow-mo (kill burst, boss hit) — always overrides
            slowMoFrames--;
            targetScale = 0.35f;
        } else {
            // Sky Force feel: normal speed while touching, 0.65x when released
            targetScale = isDragging ? 1.0f : 0.70f;
        }
        // Smooth interpolation — no snapping, feels cinematic
        slowMoScale += (targetScale - slowMoScale) * 0.08f;
        // Clamp to avoid floating point drift
        if (Math.abs(slowMoScale - targetScale) < 0.005f) slowMoScale = targetScale;

        frameCount++;
        flameFrame++;
        skyWaveOffset += 0.04f * slowMoScale;

        if (totalWavesCompleted < BOSS_AFTER_WAVES) {
            mapProgress = Math.min(MAX_PROGRESS, mapProgress + 1);
        }

        // ── Adaptive pressure tracking ─────────────────────────────────────
        recentKillTimer++;
        recentHitTimer++;
        if (recentKillTimer >= 300) { recentKills = 0; recentKillTimer = 0; }
        if (recentHitTimer  >= 300) { recentHits  = 0; recentHitTimer  = 0; }
        // Pressure: high kills + low hits = player is crushing it → ramp up
        // Low kills + high hits = player is struggling → ease back
        float killRate = recentKills / 5f;   // normalised — 5 kills/5s = 1.0
        float hitRate  = recentHits  / 3f;   // 3 hits/5s = 1.0 = struggling
        float targetPressure = 1.0f + killRate * 0.3f - hitRate * 0.25f;
        targetPressure = Math.max(0.6f, Math.min(1.6f, targetPressure));
        performancePressure += (targetPressure - performancePressure) * 0.005f; // smooth

        planeX = clamp(planeX, -10f, screenWidth - PLAYER_W + 10f);
        planeY = clamp(planeY, 80, screenHeight - 120);

        scrollSpeed = (1.0f + mapProgress * 0.001f) * slowMoScale;
        updateMapGameplayFx();

        boolean shaking = shakeIntensity > 0;
        if (shaking) {
            float sx = (random.nextFloat() - 0.5f) * shakeIntensity;
            float sy = (random.nextFloat() - 0.5f) * shakeIntensity;
            canvas.save();
            canvas.translate(sx, sy);
            shakeIntensity *= 0.72f;
            if (shakeIntensity < 0.5f) shakeIntensity = 0f;
        }

        drawSky(canvas);
        spawnEntities();
        updateTimers();
        handleShooting();

        RectF planeRect = getPlayerRect();
        updateAirEnemies(canvas, planeRect);
        updateBoss(canvas, planeRect);
        updateBullets(canvas);
        updateEnemyBullets(canvas, planeRect);
        updateCoins(canvas, planeRect);
        updatePowerUps(canvas, planeRect);
        updateHealthPickups(canvas, planeRect);
        updateAstronauts(canvas, planeRect);
        updateAsteroids(canvas, planeRect);
        updateStarPickups(canvas, planeRect);
        updateExplosions(canvas);
        updateDeathParticles(canvas);   // ← new typed particles
        updateBulletTrails(canvas);     // ← new bullet trails
        updateCoinParticles(canvas);    // ← coin collection burst
        updateFloatingTexts(canvas);

        if (shaking) canvas.restore();

        // Props drawn AFTER shake restore — never affected by shake translate
        updateDesertProps(canvas);
        updateOceanProps(canvas);

        drawPlayerPlane(canvas);
        drawHUD(canvas);

        // ── Continuous electric sparks around player during high combo ────
        if (comboCount >= 10 && frameCount % 4 == 0) {
            float pcx = planeX + 120f, pcy = planeY + 120f;
            float angle = (float)(random.nextDouble() * Math.PI * 2);
            int col = comboCount >= 15
                    ? Color.argb(180, 255, 80, 20)
                    : Color.argb(160, 80, 180, 255);
            deathParticles.add(new DeathParticle(
                    pcx + (float)Math.cos(angle) * (60f + random.nextFloat() * 50f),
                    pcy + (float)Math.sin(angle) * (60f + random.nextFloat() * 50f),
                    (float)Math.cos(angle) * 1.5f, (float)Math.sin(angle) * 1.5f,
                    col, 2f + random.nextFloat() * 2f));
        }
        drawProgressBar(canvas);
        drawPauseButton(canvas);


        // Slow-mo vignette removed — no color change desired

        if (screenFlashAlpha > 0) {
            paint.setColor(Color.argb((int)screenFlashAlpha,
                    Color.red(screenFlashColor),
                    Color.green(screenFlashColor),
                    Color.blue(screenFlashColor)));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);
            screenFlashAlpha = Math.max(0, screenFlashAlpha - 10f);
        }

        if (score > highScore) {
            highScore = score;
            prefs.edit().putInt("hs", highScore).apply();
        }
        // Update per-map best
        if (currentMap >= 0 && currentMap < 3 && score > mapHighScore[currentMap]) {
            mapHighScore[currentMap] = score;
            String[] keys = {"hs_space","hs_desert","hs_ocean"};
            prefs.edit().putInt(keys[currentMap], score).apply();
        }
    }

    // ===================== SPAWN =====================
    private void spawnEntities() {
        // Random asteroids — Space only (desert has wind debris, ocean has no rocks)
        if (currentMap == MAP_SPACE) {
            int asteroidInterval = getMapAsteroidInterval();
            if (frameCount % asteroidInterval == 0) {
                float ax = 40 + random.nextFloat() * (screenWidth - 80);
                float aspd = 0.8f + random.nextFloat() * 1.0f;
                float rot = random.nextFloat() * 360f;
                float rotSpd = (random.nextFloat() - 0.5f) * 3f;
                asteroids.add(new Asteroid(ax, -50, aspd, rot, rotSpd));
            }
        }
        // Ocean drops more coins
        int coinInterval = currentMap == MAP_OCEAN
                ? 80 + totalWavesCompleted * 8
                : 110 + totalWavesCompleted * 12;
        if (frameCount % coinInterval == 0)
            coins.add(new CoinPickup(
                    30 + random.nextFloat() * (screenWidth - 60), -30));

        // ── Desert ambient props — cacti/skulls/rocks scroll across map ───
        // ── Desert props — maintain constant screen coverage ──────────────
        if (currentMap == MAP_DESERT) {
            // Target: ~12 props visible at all times spread across the screen
            int target = 12;
            if (desertPropObjs.size() < target) {
                int[] dCounts = new int[DESERT_PROP_COUNT];
                for (DesertPropObj p : desertPropObjs) dCounts[p.spriteIdx]++;
                // Spawn a batch to reach target
                int toSpawn = target - desertPropObjs.size();
                for (int s = 0; s < toSpawn; s++) {
                    int tries = 0;
                    while (tries++ < 15) {
                        int idx = random.nextInt(DESERT_PROP_COUNT);
                        if (dCounts[idx] < 3 && desertProps[idx] != null) {
                            float sx = 30 + random.nextFloat() * (screenWidth - 60);
                            float scale = 0.45f + random.nextFloat() * 0.55f;
                            // Distribute spawn Y across off-screen top band
                            float sy = -30 - random.nextFloat() * screenHeight;
                            desertPropObjs.add(new DesertPropObj(sx, sy, 0, idx, scale));
                            dCounts[idx]++;
                            break;
                        }
                    }
                }
            }
        }

        // ── Ocean props — maintain constant screen coverage ───────────────
        if (currentMap == MAP_OCEAN) {
            int target = 10;
            if (oceanPropObjs.size() < target) {
                int[] propCounts = new int[OCEAN_PROP_COUNT];
                for (OceanPropObj p : oceanPropObjs) propCounts[p.spriteIdx]++;
                int toSpawn = target - oceanPropObjs.size();
                float riverL = screenWidth * 0.40f;
                float riverR = screenWidth * 0.66f;
                for (int s = 0; s < toSpawn; s++) {
                    int tries = 0;
                    while (tries++ < 15) {
                        int idx = random.nextInt(OCEAN_PROP_COUNT);
                        if (propCounts[idx] < 3 && oceanProps[idx] != null) {
                            // Left bank (0-35%) or right bank (70-100%), skip river channel
                            float sx = random.nextBoolean()
                                    ? 20 + random.nextFloat() * (riverL - 40)
                                    : riverR + 20 + random.nextFloat() * (screenWidth - riverR - 40);
                            float scale = 0.5f + random.nextFloat() * 0.6f;
                            float sy = -30 - random.nextFloat() * screenHeight;
                            oceanPropObjs.add(new OceanPropObj(sx, sy, 0, idx, scale));
                            propCounts[idx]++;
                            break;
                        }
                    }
                }
            }
        }

        // Spawn up to 10 astronauts — max 2 visible at once, spaced far apart
        if (currentMap == MAP_SPACE && astronautsSpawned < MAX_ASTRONAUTS && boss == null && !bossDefeated) {
            long liveAstronauts = astronauts.size();
            if (liveAstronauts < 2) {
                int spawnThreshold = (astronautsSpawned + 1) * (MAX_PROGRESS / (MAX_ASTRONAUTS + 1));
                // Extra gap: only spawn if last one is already past 40% down screen
                boolean lastOneDeep = astronauts.isEmpty() ||
                        astronauts.get(astronauts.size()-1).y > screenHeight * 0.4f;
                if (mapProgress >= spawnThreshold && lastOneDeep) {
                    float ax = 60 + random.nextFloat() * (screenWidth - 120);
                    astronauts.add(new AstronautRescue(ax, -60));
                    astronautsSpawned++;
                    waveAstroSpawned = true;
                }
            }
        }

        if (totalWavesCompleted >= BOSS_AFTER_WAVES && boss == null
                && !bossDefeated && !waveInProgress && airEnemies.isEmpty()) {
            spawnBoss();
            return;
        }

        // ── Between waves: count down then start next ──────────────────────
        if (!waveInProgress && boss == null && !bossDefeated) {
            waveCooldown = Math.max(0, (int)(waveCooldown - slowMoScale));
            if (waveCooldown <= 0 && totalWavesCompleted < BOSS_AFTER_WAVES) {
                startNextWave();
            }
            return;
        }

        // ── Phase 1: spawn enemies ─────────────────────────────────────────
        if (waveInProgress && !waveSpawnFinished) {
            int waveType = currentWaveType;

            if (waveType == 3) {
                int maxJets = totalWavesCompleted > 9 ? 2 : 4;
                waveEnemySpawnTimer--;
                int liveJets = 0;
                for (AirEnemy e : airEnemies) {
                    if (e.type == AirType.JET) liveJets++;
                }
                if (liveJets <= maxJets - 2 && waveEnemySpawnTimer <= 0 && waveEnemiesRemaining > 0) {
                    int toSpawn = Math.min(2, waveEnemiesRemaining);
                    for (int s = 0; s < toSpawn; s++) {
                        spawnWaveEnemy(waveType);
                        waveEnemiesRemaining--;
                    }
                    waveEnemySpawnTimer = 60;
                }
            } else {
                waveEnemySpawnTimer--;
                if (waveEnemySpawnTimer <= 0 && waveEnemiesRemaining > 0) {
                    spawnWaveEnemy(waveType);
                    waveEnemiesRemaining--;
                    waveEnemySpawnTimer = getWaveSpawnInterval(waveType);
                }
            }

            if (waveEnemiesRemaining <= 0) {
                waveSpawnFinished  = true;
                waveClearTimeout   = 600; // 10 seconds max to clear screen
            }
        }

        // ── Phase 2: wait for screen clear ────────────────────────────────
        if (waveInProgress && waveSpawnFinished) {
            if (waveClearTimeout > 0) waveClearTimeout = Math.max(0, (int)(waveClearTimeout - slowMoScale));

            // Timeout: force ALL non-persistent enemies off screen
            if (waveClearTimeout == 1) {
                for (AirEnemy e : airEnemies) {
                    if (e.type == AirType.ARACHNID) continue;
                    if (e.type == AirType.STARSPARROW) { e.kamiState = 4; e.velY = -4f; e.waveAmt = 0; continue; }
                    if (e.type == AirType.SWITCHBLADE) { e.kamiState = 3; e.velY = -6f; e.velX *= 0.5f; continue; }
                    if (e.type == AirType.FIGHTER)     { e.kamiState = 2; e.velX = e.kamiEntryX > 0 ? 6.5f : -6.5f; e.velY = 5f; continue; }
                    if (e.type == AirType.JET)         { e.escapeTimer = 10; continue; }
                    if (e.escapeTimer < 0) e.escapeTimer = random.nextInt(60) + 5;
                    if (e.type == AirType.GUNSHIP && e.escapeTimer < 0) e.escapeTimer = 30;
                }
            }

            // ── Simple clear: wave done when all spawned enemies are gone ──
            if (waveEnemiesAlive <= 0) {
                waveInProgress    = false;
                waveSpawnFinished = false;
                waveClearTimeout  = 0;
                waveCooldown      = 180;
                totalWavesCompleted++;
                onWaveComplete();
            }
        }
    }

    /** Called when all enemies in a wave are spawned — wait for screen clear then open shop */
    private void onWaveComplete() {
        int waveIdx = totalWavesCompleted - 1; // just completed wave index
        int stars   = calcWaveStars();
        runWaveStars = stars;

        // Update best stars for this wave
        if (waveIdx >= 0 && waveIdx < waveStars.length) {
            if (stars > waveStars[waveIdx]) {
                waveStars[waveIdx] = stars;
                totalStarsEver = 0;
                for (int s : waveStars) totalStarsEver += s;
                saveStars();
            }
        }

        // Unlock next wave
        if (totalWavesCompleted > unlockedWaves) {
            unlockedWaves = totalWavesCompleted;
            prefs.edit().putInt("uw", unlockedWaves).apply();
        }

        // Show star popup + coins bonus
        int coinBonus = stars * 8;
        coinCount  += coinBonus;
        totalCoins += coinBonus;
        score      += stars * 50;
        prefs.edit().putInt("tc", totalCoins).apply();

        waveCooldown = 150;

        // For meteor shower wave — announce how many stars were collected
        if (currentWaveType == 4 && meteorShowerStarsCollected > 0) {
            addFloatingBig(screenWidth / 2f, screenHeight * 0.28f,
                    "★ " + meteorShowerStarsCollected + " STARS COLLECTED!", Color.parseColor("#FFD700"));
            meteorShowerStarsCollected = 0;
        }
        // Clear uncollected star pickups between waves
        starPickups.clear();

        // Star announcement
        String starStr = stars == 3 ? "★★★ PERFECT!" : stars == 2 ? "★★☆ GREAT!" : stars == 1 ? "★☆☆ GOOD" : "☆☆☆ TRY AGAIN";
        addFloatingBig(screenWidth / 2f, screenHeight * 0.35f, starStr,
                stars == 3 ? Color.parseColor("#FFD700") :
                        stars == 2 ? Color.parseColor("#88ffaa") :
                                stars == 1 ? Color.parseColor("#aaccff") : Color.parseColor("#aaaaaa"));
    }

    private int calcWaveStars() {
        int stars = 0;
        // Star 1: killed at least 70% of enemies
        if (waveKillsNeeded > 0 && waveKillsGot >= (int)(waveKillsNeeded * 0.70f)) stars++;
        // Star 2: survived with at least 50% HP remaining
        if (playerHP >= MAX_PLAYER_HP / 2) stars++;
        // Star 3: rescued the astronaut if one spawned, OR kept 80%+ HP if none appeared
        if (waveAstroSpawned) {
            if (waveAstroSaved) stars++;
        } else {
            if (playerHP >= (int)(MAX_PLAYER_HP * 0.80f)) stars++;
        }
        return Math.min(3, stars);
    }

    private void saveStars() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt("ts", totalStarsEver);
        for (int i = 0; i < waveStars.length; i++) {
            ed.putInt("ws" + i, waveStars[i]);
        }
        ed.apply();
    }


    private String getWaveTransitionMessage(int wavesCompleted) {
        switch (wavesCompleted) {
            case 2:  return "⚡ JETS INCOMING — GET READY!";
            case 4:  return "☄ METEOR SHOWER! Collect the stars!";
            case 7:  return "💀 WAVES GET HARDER FROM HERE!";
            case 8:  return "🚀 HEADS UP — GUNSHIP IS BACK!";
            case 9:  return "⚡ JETS AGAIN — EVEN FASTER!";
            case 12: return "⚠ BOSS APPROACHES — FINAL WAVES!";
            case 14: return "💥 LAST WAVE — BOSS IS WAITING!";
            default: return null;
        }
    }

    private void startNextWave() {
        waveInProgress    = true;
        waveSpawnFinished = false;
        waveClearTimeout  = 0;
        int waveType = WAVE_SEQUENCE[currentWave % WAVE_SEQUENCE.length];
        // Meteor shower (type 4) and astronaut-tied waves only exist on Deep Space
        if (currentMap != MAP_SPACE && waveType == 4) {
            waveType = 5; // replace meteor shower with a fighter wave on Desert/Ocean
        }
        mapProgress = Math.min(MAX_PROGRESS,
                (totalWavesCompleted * MAX_PROGRESS) / (BOSS_AFTER_WAVES + 1));

        int waveNum   = totalWavesCompleted + 1;
        int lateBonus = Math.max(0, (waveNum - 6) * 3);

        // Base counts scaled by adaptive performancePressure (0.6–1.6)
        int base;
        switch (waveType) {
            case 0: base = 22 + lateBonus;     break;
            case 1: base = 13 + lateBonus;     break;
            case 2: base =  7 + lateBonus;     break;
            case 3: base = totalWavesCompleted > 9 ? 14 + lateBonus : 8 + lateBonus; break;
            case 4: base = 20 + lateBonus;     break;  // meteor shower — count = meteors
            case 5: base =  9 + lateBonus;     break;
            case 6: base = 10 + lateBonus;     break;
            case 7: base = 38 + lateBonus;     break;
            case 8: base = 64;                  break;
            case 9: base = 2;                   break;
            default: base = 10;
        }
        // Apply adaptive pressure — gunship always fixed at 2, kamikaze3 fixed at 64
        if (waveType != 9 && waveType != 8) {
            waveEnemiesRemaining = Math.max(3, (int)(base * performancePressure));
        } else {
            waveEnemiesRemaining = base;
        }

        waveEnemySpawnTimer = 180;
        screenFlashAlpha = 35f;
        screenFlashColor = Color.WHITE;

        // Reset per-wave medal tracking
        waveKillsGot     = 0;
        waveKillsNeeded  = waveEnemiesRemaining;
        waveEnemiesAlive = 0;  // will increment as enemies spawn
        waveStartHP      = playerHP;
        waveAstroSpawned = false;
        waveAstroSaved   = false;



        currentWaveType = waveType;
        currentWave++;
        playSound(sndWaveStart, 0.8f, 1.0f);
        // Meteor shower special announcement
        if (waveType == 4) {
            screenFlashAlpha = 50f;
            screenFlashColor = Color.parseColor("#ff8800");
            addFloatingBig(screenWidth / 2f, screenHeight * 0.4f,
                    "☄ METEOR SHOWER!", Color.parseColor("#ff8800"));
            addFloating(screenWidth / 2f, screenHeight * 0.48f,
                    "Shoot meteors to collect ★ stars!", Color.parseColor("#FFD700"));
        }
    }

    private int getWaveSpawnInterval(int waveType) {
        int base;
        switch (waveType) {
            case 0: base = 45;  break;
            case 1: base = 115; break;
            case 2: base = 165; break;
            case 3: base = 95;  break;
            case 4: base = 35;  break;  // meteor shower — faster spawn for rain feel
            case 5: base = 125; break;
            case 6: base = 90;  break;
            case 7: base = 28;  break;
            case 8: base = 20;  break;
            case 9: base = 180; break;
            default: base = 100;
        }
        int waveNum = totalWavesCompleted + 1;
        int reduction = waveNum <= 6
                ? waveNum
                : 6 + (waveNum - 6) * 4;
        int interval = Math.max(22, base - reduction);
        // Add slight randomness ±20% so spawns feel organic not mechanical
        int jitter = (int)(interval * 0.2f);
        return interval + (jitter > 0 ? random.nextInt(jitter * 2) - jitter : 0);
    }

    private void spawnWaveEnemy(int waveType) {
        float diff = getDifficultyScale();

        int maxEnemies = 6 + (int)(diff * 2);
        if (airEnemies.size() >= maxEnemies) return;

        float x = 40 + random.nextFloat() * (screenWidth - 80);

        int gpTier = Math.max(0, gunPower - 1);

        // Exponential wave strength — feels slow early, spikes hard late (Sky Force feel)
        float progressFactor = (float)mapProgress / MAX_PROGRESS;
        float waveStrength   = 1.0f + progressFactor * progressFactor * 2.5f; // 1.0 → 3.5

        int   hpBonus          = (int)(gpTier * 1.5f * (0.8f + diff * 0.4f));
        int   baseShootTimer   = (int)Math.max(90, (390 - gpTier * 33) / (0.9f + diff * 0.2f));
        float damageMultiplier = (1.45f + gpTier * 0.22f) * (0.9f + diff * 0.25f);
        // Scale enemy HP with exponential strength
        float mapMult = getMapHpMultiplier();
        float hpScale = waveStrength * (difficulty == 0 ? 0.7f : difficulty == 2 ? 1.4f : 1.0f) * mapMult;

        switch (waveType) {
            case 0: {
                // Step 5 — prevent kamikaze hell at high difficulty
                if (diff > 2.5f && random.nextFloat() < 0.3f) return;
                int totalInWave = 14 + (currentWave / WAVE_SEQUENCE.length) * 2;
                int lineIndex   = totalInWave - waveEnemiesRemaining;
                boolean fromRight = lineIndex < (totalInWave / 2);
                float entryX = fromRight
                        ? screenWidth + 50 + lineIndex * 15f
                        : -50 - (lineIndex - totalInWave/2) * 15f;
                float entryY = -60 - lineIndex * 15f;

                AirEnemy kamikaze = new AirEnemy(entryX, entryY,
                        AirType.KAMIKAZE,
                        (int)((1 + hpBonus / 2) * hpScale),
                        0, 0, 9999, baseShootTimer,
                        10f * damageMultiplier * hpScale * 0.4f
                );
                kamikaze.kamiSweepRight = !fromRight;
                kamikaze.kamiTargetY   = screenHeight * 0.60f;
                kamikaze.kamiTargetX   = fromRight ? screenWidth*0.12f : screenWidth*0.88f;
                kamikaze.kamiEntryX    = entryX;
                kamikaze.kamiState     = 0;
                runTotalEnemiesSpawned++; airEnemies.add(kamikaze); waveEnemiesAlive++;
                if (lineIndex % 3 == 0)
                    playSound(sndKamikaze, 0.7f, 0.9f + random.nextFloat() * 0.2f);
                break;
            }
            case 1: {
                int waveNum2  = totalWavesCompleted + 1;
                int lateBonus = Math.max(0, (waveNum2 - 6) * 3);
                int total    = 13 + lateBonus;
                int lineIdx  = total - waveEnemiesRemaining;
                int group    = lineIdx / 3;
                int posInGrp = lineIdx % 3;

                float spawnX;
                float spawnY = -50 - posInGrp * 30f;
                float initVX, initVY;

                switch (group % 4) {
                    case 0:
                        spawnX = screenWidth * 0.25f + posInGrp * screenWidth * 0.25f;
                        initVX = 0f; initVY = 2.5f;
                        break;
                    case 1:
                        spawnX = -40 - posInGrp * 40f;
                        spawnY = screenHeight * 0.05f + posInGrp * 50f;
                        initVX = 3.5f; initVY = 1.8f;
                        break;
                    case 2:
                        spawnX = screenWidth + 40 + posInGrp * 40f;
                        spawnY = screenHeight * 0.05f + posInGrp * 50f;
                        initVX = -3.5f; initVY = 1.8f;
                        break;
                    default:
                        spawnX = screenWidth * 0.4f + posInGrp * screenWidth * 0.1f;
                        initVX = (posInGrp - 1) * 1.5f; initVY = 3.0f;
                        break;
                }

                AirEnemy drone = new AirEnemy(spawnX, spawnY,
                        AirType.DRONE,
                        (int)((1 + hpBonus / 2) * hpScale),
                        initVX, initVY, 9999, baseShootTimer,
                        5f * damageMultiplier * hpScale * 0.4f);
                drone.waveAmt = posInGrp * 2.1f;
                drone.waveSpd = 0.045f + random.nextFloat() * 0.02f;
                drone.waveAmp = 18f + random.nextFloat() * 12f;
                runTotalEnemiesSpawned++; airEnemies.add(drone); waveEnemiesAlive++;
                playSound(sndDroneEngine, 0.1f, 0.15f + random.nextFloat() * 0.1f);
                break;
            }
            case 2: {
                AirEnemy bomber = new AirEnemy(x, -80,
                        AirType.BOMBER,
                        (int)((5 + hpBonus * 2) * hpScale),
                        random.nextBoolean() ? 1.2f : -1.2f, 0.7f,
                        baseShootTimer, baseShootTimer,
                        25f * damageMultiplier * hpScale * 0.35f
                );
                runTotalEnemiesSpawned++; airEnemies.add(bomber); waveEnemiesAlive++;
                break;
            }
            case 3: {
                boolean fromLeft = (waveEnemiesRemaining % 2 == 0);
                float spd = ((totalWavesCompleted > 9 ? 5.4f : 4.2f) + gpTier * 0.36f)
                        * (0.9f + diff * 0.15f) * getMapSpeedMultiplier();
                float startX = fromLeft ? -80 : screenWidth + 80;
                float startY = 60 + random.nextFloat() * 60f;
                AirEnemy jet = new AirEnemy(
                        startX, startY,
                        AirType.JET,
                        (int)((2 + hpBonus) * hpScale),
                        fromLeft ? spd : -spd, 0f,
                        baseShootTimer - 10, baseShootTimer - 10,
                        18f * damageMultiplier
                );
                jet.velX = fromLeft ? spd : -spd;
                jet.velY = 1f;
                jet.waveAmt = fromLeft ? (float)Math.PI : 0f;
                jet.waveSpd = spd;
                jet.kamiTargetX = screenWidth / 2f;
                jet.kamiTargetY = screenHeight * 0.28f;
                jet.waveAmp = screenHeight * 0.30f;
                jet.kamiSweepRight = fromLeft;
                jet.kamiState = 0;
                runTotalEnemiesSpawned++; airEnemies.add(jet); waveEnemiesAlive++;
                playSound(sndJetEngine, 0.55f, 0.9f + random.nextFloat() * 0.2f);
                playSound(sndFighterEngine, 0.4f, 0.85f + random.nextFloat() * 0.15f);
                break;
            }
            case 4: {
                // ── Meteor Shower ──────────────────────────────────────────────
                // Meteors fall fast, spin fast. Shoot them for star pickups.
                // They don't shoot back — pure collect/dodge challenge.
                float mx = 30 + random.nextFloat() * (screenWidth - 60);
                float mSpeed = 1.5f + random.nextFloat() * 2.5f + totalWavesCompleted * 0.08f;
                float mRot   = random.nextFloat() * 360f;
                float mRotSpd = (random.nextFloat() - 0.5f) * 8f;
                // Give them some diagonal drift too
                float mVx = (random.nextFloat() - 0.5f) * 1.8f;
                // Meteor HP: 1-3 hits depending on size (random)
                int mSize = random.nextInt(3); // 0=small, 1=med, 2=large
                int mHp   = mSize + 1;
                // Encode size in waveAmt field (reusing Asteroid class)
                Asteroid meteor = new Asteroid(mx, -60, mSpeed, mRot, mRotSpd);
                meteor.hp = mHp;
                meteor.speed = mSpeed;
                // Store vx in rotSpeed temporarily — we handle it in updateAsteroids
                // Actually use a special spawn pattern: cluster every 5th spawn
                if (waveEnemiesRemaining % 5 == 0) {
                    // Cluster burst — 3 close meteors
                    for (int cl = -1; cl <= 1; cl++) {
                        float cx2 = Math.max(40, Math.min(screenWidth-40, mx + cl * 60f));
                        Asteroid cm = new Asteroid(cx2, -60 - Math.abs(cl) * 25f,
                                mSpeed * (0.9f + random.nextFloat() * 0.2f),
                                random.nextFloat() * 360f,
                                (random.nextFloat() - 0.5f) * 8f);
                        cm.hp = 1 + random.nextInt(2);
                        asteroids.add(cm);
                        waveKillsNeeded++;  // count each in the cluster
                    }
                } else {
                    asteroids.add(meteor);
                }
                // Meteors count as wave enemies — waveKillsNeeded already set to waveEnemiesRemaining
                // No runTotalEnemiesSpawned increment — these aren't kill-for-score enemies
                break;
            }
            case 7:
            case 8: {
                // Step 5 — prevent kamikaze hell at high difficulty
                if (diff > 2.5f && random.nextFloat() < 0.3f) return;
                if (waveType == 8 && waveEnemiesRemaining % 14 == 0) {
                    screenFlashAlpha = 60f;
                    screenFlashColor = Color.parseColor("#ff2200");
                }
                int totalInWave = waveType == 8 ? 56 : 22;
                int lineIndex   = totalInWave - waveEnemiesRemaining;
                boolean fromRight = lineIndex % 2 == 0;
                float entryX = fromRight ? screenWidth + 10 : -10;
                float entryY = -20 - (lineIndex % 7) * 12f;
                float targetX = screenWidth * 0.15f + (lineIndex % 8) * (screenWidth * 0.7f / 8f);
                float targetY = screenHeight * 0.55f + (lineIndex % 3) * 30f;
                AirEnemy kamikaze = new AirEnemy(entryX, entryY,
                        AirType.KAMIKAZE,
                        1 + hpBonus / 2,
                        0, 0, 9999, baseShootTimer,
                        10f * damageMultiplier);
                kamikaze.kamiSweepRight = !fromRight;
                kamikaze.kamiTargetY   = targetY;
                kamikaze.kamiTargetX   = targetX;
                kamikaze.kamiEntryX    = entryX;
                kamikaze.kamiState     = 0;
                kamikaze.velX = fromRight ? -4f : 4f;
                kamikaze.velY = 2.5f;
                airEnemies.add(kamikaze);
                if (lineIndex % 3 == 0)
                    playSound(sndKamikaze, 0.7f, 0.9f + random.nextFloat() * 0.2f);
                break;
            }
            case 5: {
                if (currentMap == MAP_OCEAN) {
                    // ── SWITCHBLADE: Ocean Fast Assassin ──
                    boolean fromLeft = random.nextBoolean();
                    // Spawn near centre so it's clearly visible on screen immediately
                    float spawnX2 = screenWidth * (fromLeft ? 0.25f : 0.75f);
                    float initVX  = fromLeft ? 2.5f : -2.5f; // noticeable diagonal
                    float initVY  = 5.5f;                     // enters faster
                    int   hp2     = (int)((3 + hpBonus) * hpScale);
                    float dmg2    = 13f * damageMultiplier;
                    AirEnemy blade = new AirEnemy(spawnX2, -80,
                            AirType.SWITCHBLADE, hp2, 0f, 0f,
                            baseShootTimer + 10, baseShootTimer + 10, dmg2);
                    blade.kamiState = 0;
                    blade.waveAmt   = 0f;
                    blade.velX = initVX;
                    blade.velY = initVY;
                    runTotalEnemiesSpawned++; airEnemies.add(blade); waveEnemiesAlive++;
                    playSound(sndJetEngine, 0.5f, 0.9f + random.nextFloat() * 0.15f);
                } else if (currentMap == MAP_DESERT) {
                    // ── STAR SPARROW: Desert Heavy Tactical Unit ──
                    // Enters straight down, then positions in a lane and hovers
                    float spawnX = screenWidth * (0.15f + (waveEnemiesRemaining % 5) * 0.175f);
                    int   lane   = waveEnemiesRemaining % 4; // 0-3 screen lanes
                    int   hp     = (int)((5 + hpBonus) * hpScale);
                    float dmg    = 14f * damageMultiplier;
                    AirEnemy sparrow = new AirEnemy(spawnX, -80,
                            AirType.STARSPARROW, hp, 0f, 2.2f,
                            baseShootTimer + 30, baseShootTimer + 30, dmg);
                    sparrow.kamiState   = 0;
                    sparrow.kamiTargetX = lane;   // lane 0-3, resolved in movement
                    sparrow.kamiEntryX  = random.nextFloat() * (float)Math.PI * 2f; // strafe phase offset
                    sparrow.velX = 0f;
                    sparrow.velY = 2.2f;
                    runTotalEnemiesSpawned++; airEnemies.add(sparrow); waveEnemiesAlive++;
                    playSound(sndFighterEngine, 0.5f, 0.85f + random.nextFloat() * 0.15f);
                } else {
                    // Normal fighter on Deep Space — diagonal entry, precision assault
                    boolean fromLeft = (waveEnemiesRemaining % 2 == 0);
                    float startX = fromLeft ? -120 : screenWidth + 120;
                    float startY = 60 + (waveEnemiesRemaining % 3) * 40f;
                    int   hp     = (int)((3 + hpBonus) * hpScale);
                    float dmg    = 12f * damageMultiplier;
                    AirEnemy enemy = new AirEnemy(startX, startY,
                            AirType.FIGHTER, hp, 0f, 0f,
                            baseShootTimer, baseShootTimer, dmg);
                    enemy.velX       = fromLeft ?  3.8f : -3.8f;
                    enemy.velY       = 4.5f;
                    enemy.kamiState  = 0;
                    enemy.kamiEntryX = fromLeft ? 1f : -1f; // encode side for exit bank
                    runTotalEnemiesSpawned++; airEnemies.add(enemy); waveEnemiesAlive++;
                }
                break;
            }
            case 9: {
                boolean isLeft = (waveEnemiesRemaining == 2);
                float gx = isLeft ? screenWidth * 0.28f : screenWidth * 0.72f;
                AirEnemy gunship = new AirEnemy(gx, -120,
                        AirType.GUNSHIP,
                        (int)((14 + hpBonus * 2) * hpScale),
                        0f, 1.2f,
                        baseShootTimer - 20, baseShootTimer - 20,
                        20f * damageMultiplier);
                gunship.waveAmt  = isLeft ? 0f : (float)Math.PI;
                gunship.waveSpd  = 0.008f;
                gunship.waveAmp  = screenWidth * 0.18f;
                runTotalEnemiesSpawned++; airEnemies.add(gunship); waveEnemiesAlive++;
                playSound(sndHeliEngine, 0.7f, 0.6f + random.nextFloat() * 0.1f);
                addFloating(screenWidth/2f, screenHeight/3f,
                        "GUNSHIP INCOMING!", Color.parseColor("#ff4400"));
                break;
            }
            case 6: {
                // Arachnids hold fixed positions — max 3 RIGHT side, max 3 LEFT side
                int rightCount = 0, leftCount = 0;
                for (AirEnemy ae : airEnemies) {
                    if (ae.type == AirType.ARACHNID) {
                        if (ae.waveAmt >= 0.5f) rightCount++; // waveAmt=1 → right screen (leftSide slot)
                        else                     leftCount++;  // waveAmt=0 → left screen
                    }
                }
                if (rightCount >= 3 && leftCount >= 3) return; // both sides full

                // Fill right side first (slots 0-2, leftSide=true), then left side
                boolean leftSide;
                int pos;
                if (rightCount < 3) {
                    leftSide = true;   // leftSide slot → RIGHT screen
                    pos = rightCount;  // 0=top 1=mid 2=low
                } else {
                    leftSide = false;  // rightSide slot → LEFT screen
                    pos = leftCount;
                }

                float targetX = leftSide
                        ? screenWidth * (0.92f - pos * 0.06f)   // RIGHT side of screen
                        : screenWidth * (0.08f + pos * 0.06f);  // LEFT side of screen
                float targetY = screenHeight * (0.12f + pos * 0.14f);

                AirEnemy arachnid = new AirEnemy(targetX, -120,
                        AirType.ARACHNID,
                        (int)((18 + hpBonus * 3) * hpScale),
                        0f, 3.5f,
                        130, 130,
                        18f * damageMultiplier);
                arachnid.waveAmp  = targetX;
                arachnid.waveSpd  = targetY;
                arachnid.waveAmt  = leftSide ? 1f : 0f;
                arachnid.kamiState = 0;
                runTotalEnemiesSpawned++; airEnemies.add(arachnid);
                break;
            }
        }
    }

    private void spawnBoss() {
        boss = new BossEnemy(screenWidth / 2f, -220, BOSS_MAX_HP);
        boss.bossType = currentMap; // 0=Space, 1=Desert, 2=Ocean
        // Desert/Ocean use their own part system — disable space multi-part
        if (currentMap == MAP_DESERT || currentMap == MAP_OCEAN) {
            boss.leftArmAlive = false; boss.rightArmAlive = false; boss.headAlive = false;
            boss.bodyHP = currentMap == MAP_DESERT ? 200 : 220;
        }
        // Ocean: turrets spawn at sides, descend independently
        if (currentMap == MAP_OCEAN) {
            boss.leftTurretX  = screenWidth * 0.18f;
            boss.leftTurretY  = -200f;
            boss.rightTurretX = screenWidth * 0.82f;
            boss.rightTurretY = -200f;
        }
        screenFlashAlpha = 80f;
        screenFlashColor = currentMap == MAP_DESERT ? Color.parseColor("#cc6600")
                : currentMap == MAP_OCEAN  ? Color.parseColor("#0066cc")
                : Color.parseColor("#ff2200");
        String bossName = currentMap == MAP_DESERT ? "⚠ SAND DESTROYER APPROACHES ⚠"
                : currentMap == MAP_OCEAN   ? "⚠ SEA SERPENT RISING ⚠"
                : "!!! BOSS INCOMING !!!";
        addFloatingBig(screenWidth/2f, screenHeight/3f, bossName, screenFlashColor);
        playSound(sndBossAppear, 1.0f, currentMap == MAP_DESERT ? 0.85f : currentMap == MAP_OCEAN ? 1.1f : 1.0f);
    }

    // ===================== TIMERS / SHOOTING =====================
    private void updateTimers() {
        if (shieldTimer > 0 && --shieldTimer <= 0) hasShield = false;
        if (shootCooldown > 0) shootCooldown = Math.max(0, (int)(shootCooldown - slowMoScale));
        if (superShootCooldown > 0) superShootCooldown = Math.max(0, (int)(superShootCooldown - slowMoScale));
        if (cannonCooldown > 0) cannonCooldown = Math.max(0, (int)(cannonCooldown - slowMoScale));
        if (gunSoundThrottle > 0) gunSoundThrottle--;
        if (enemySoundThrottle > 0) enemySoundThrottle--;
        if (magnetTimer > 0) magnetTimer--;
        if (comboTimer > 0) {
            comboTimer--;
            if (comboTimer <= 0 && comboCount > 0) {
                comboCount = 0;
                comboMult  = 1f;
            }
        }
        if (superFireTimeLeft > 0) {
            superFireTimeLeft = Math.max(0, (int)(superFireTimeLeft - slowMoScale));
        }
    }

    private void handleShooting() {
        // ── Burst tick — fire one shot of the current burst ───────────────
        if (burstShotsLeft > 0) {
            burstShotTimer = Math.max(0, (int)(burstShotTimer - slowMoScale));
            if (burstShotTimer <= 0) {
                shootNormal();
                burstShotsLeft--;
                burstShotTimer = BURST_BETWEEN;
            }
        }

        // ── Start a new burst when cooldown expires ────────────────────────
        // Burst count and cooldown scale with gun level (Sky Force feel)
        if (shootCooldown > 0) { shootCooldown = Math.max(0, (int)(shootCooldown - slowMoScale)); }
        else if (burstShotsLeft <= 0) {
            // Lv1: single shot  Lv2: double burst  Lv3-4: triple burst
            burstShotsLeft = gunPower <= 1 ? 1 : gunPower == 2 ? 2 : 3;
            burstShotTimer = 0; // fire first shot immediately
            // Cooldown between bursts: shorter at higher levels
            shootCooldown = Math.max(12, (gunPower <= 1 ? 42 : gunPower == 2 ? 36 : 30) - permFireRate * 3);
        }

        if (superFireTimeLeft > 0 && superShootCooldown <= 0) {
            shootSuper();
            superShootCooldown = 14;
        }
        if (cannonCooldown <= 0) {
            shootCannon();
            cannonCooldown = 360;
        }
    }

    private void shootNormal() {
        float bx = planeX + 120f, by = planeY + 20f;
        // Bullet speed grows slightly with level
        float spd = 13f + gunPower * 1.0f;

        // ── Sky Force 4-level gun design ───────────────────────────────────
        // Lv1: single centre shot
        // Lv2: twin shots (left + right cannon)
        // Lv3: twin + angled side shots (V-spread)
        // Lv4: twin + wide V + a fast centre tracer
        switch (gunPower) {
            case 1:
                // Single centre — clean, punchy
                bullets.add(new PlayerBullet(bx, by, spd, 0));
                break;

            case 2:
                // Twin barrels — parallel left & right
                bullets.add(new PlayerBullet(bx - 18, by, spd, 0));
                bullets.add(new PlayerBullet(bx + 18, by, spd, 0));
                break;

            case 3:
                // Twin + angled side shots — V-shape spread
                bullets.add(new PlayerBullet(bx - 16, by, spd, 0));
                bullets.add(new PlayerBullet(bx + 16, by, spd, 0));
                bullets.add(new PlayerBullet(bx - 38, by, spd * 0.92f,  0.14f * spd * 0.92f, 0));
                bullets.add(new PlayerBullet(bx + 38, by, spd * 0.92f, -0.14f * spd * 0.92f, 0));
                break;

            default: // Lv4 — wide V + fast tracer up the middle
                bullets.add(new PlayerBullet(bx - 16, by, spd, 0));
                bullets.add(new PlayerBullet(bx + 16, by, spd, 0));
                bullets.add(new PlayerBullet(bx - 40, by, spd * 0.90f,  0.20f * spd * 0.90f, 0));
                bullets.add(new PlayerBullet(bx + 40, by, spd * 0.90f, -0.20f * spd * 0.90f, 0));
                // Fast centre tracer — narrower, brighter
                bullets.add(new PlayerBullet(bx, by - 10, spd * 1.3f, 0));
                break;
        }

        if (gunSoundThrottle <= 0) {
            // Pitch rises slightly with high combo — audio feedback for streaks
            float comboPitchBonus = comboCount >= 10 ? Math.min(0.25f, (comboCount - 10) * 0.012f) : 0f;
            float pitch = 0.88f + gunPower * 0.06f + comboPitchBonus;
            int shootSnd = sndJetFire != -1 ? sndJetFire : sndGunShoot;
            playSound(shootSnd, 0.55f, pitch);
            gunSoundThrottle = 6;
        }
        int shotCount = gunPower == 1 ? 1 : gunPower == 2 ? 2 : gunPower == 3 ? 4 : 5;
        runBulletsShot += shotCount;
    }

    private void shootSuper() {
        float bx = planeX + 120f, by = planeY;
        bullets.add(new PlayerBullet(bx-28, by, 26f, 1));
        bullets.add(new PlayerBullet(bx,    by, 26f, 1));
        bullets.add(new PlayerBullet(bx+28, by, 26f, 1));
        runBulletsShot += 3;
        playSound(sndSuperShoot, 0.8f, 1.0f);
    }

    private void shootCannon() {
        float bx = planeX + 120f, by = planeY + 10f;
        bullets.add(new PlayerBullet(bx-10, by, 28f, -0.04f, 2));
        bullets.add(new PlayerBullet(bx,    by, 30f, 0f,     2));
        bullets.add(new PlayerBullet(bx+10, by, 28f,  0.04f, 2));
        runBulletsShot += 3;
        screenFlashAlpha = 30f;
        screenFlashColor = Color.parseColor("#ff8800");
        addFloating(bx, by - 20, "BOOM!", Color.parseColor("#ff8800"));
        shake(8f);
        playSound(sndCannon, 1.0f, 0.9f + random.nextFloat() * 0.2f);
    }

    // ===================== HELPERS =====================
    private float getDiffMult() {
        return difficulty == 0 ? 0.65f : difficulty == 2 ? 1.5f : 1f;
    }

    // Step 1 — adaptive difficulty scale (wave progress × HP performance)
    private float getDifficultyScale() {
        float waveFactor  = totalWavesCompleted * 0.12f;
        float hpFactor    = (playerHP / (float)MAX_PLAYER_HP);
        float performance = 0.5f + hpFactor * 0.5f;
        float mapBonus = getMapHpMultiplier() - 1.0f;
        return 1f + waveFactor * performance + mapBonus;
    }

    private RectF getPlayerRect() {
        return new RectF(planeX+10, planeY+5,
                planeX+230, planeY+34);
    }

    private boolean circlesIntersect(float x1,float y1,float r1,
                                     float x2,float y2,float r2) {
        float dx=x1-x2, dy=y1-y2, rr=r1+r2;
        return dx*dx+dy*dy <= rr*rr;
    }

    private float clamp(float v, float mn, float mx) {
        return Math.max(mn, Math.min(v, mx));
    }

    private void damagePlayer(float dmg) {
        if (hasShield) {
            hasShield = false; shieldTimer = 0;
            playSound(sndShieldBlock, 1.0f, 1.0f);
            // Shield block — short freeze punch
            hitFreezeFrames = 3;  // 3-frame (~50ms) hit stop
            return;
        }
        int intDmg = Math.max(1, (int)dmg);
        playerHP -= intDmg;
        playerHP  = Math.max(0, playerHP);
        shake(14f);
        hitFreezeFrames = 3;  // 3-frame (~50ms) hit stop on real hit
        hpShakeTimer    = 18;  // HP bar shakes for 18 frames (0.3s)
        recentHits++;
        comboCount = 0; comboTimer = 0; comboMult = 1f;
        screenFlashAlpha = 60f;
        screenFlashColor = Color.parseColor("#ff0000");
        playSound(sndPlayerHit, 1.0f, 0.9f + random.nextFloat() * 0.2f);
        hpPopupTimer = 120; // show HP for 2 seconds
        vibrate(40);
        if (playerHP <= 15 && lowHealthSirenTimer <= 0) {
            playSound(sndLowHealth, 0.95f, 1.0f);
            lowHealthSirenTimer = 180;
        }
        if (playerHP <= 0) {
            playerHP = 0;
            gameState = GameState.GAME_OVER;
            playSound(sndGameOver, 1.0f, 1.0f);
        }
    }

    private void collectCoin(float x, float y) {
        coinCount++;
        totalCoins++;
        score += 3;
        prefs.edit().putInt("tc", totalCoins).apply();
        addFloating(x, y - 22, "+$", Color.parseColor("#FFD700"));
        playSound(sndCoinPickup, 0.7f, 0.95f + random.nextFloat() * 0.1f);
        // Burst of gold particles — varied sizes
        int count = 8 + random.nextInt(6);
        for (int i = 0; i < count; i++) {
            float angle = (float)(Math.PI * 2 * i / count) + random.nextFloat() * 0.5f;
            float spd   = 2.5f + random.nextFloat() * 5f;
            float size  = 3f + random.nextFloat() * 7f;  // varied: small 3px to large 10px
            // Alternate gold, yellow, white-gold
            int col = i % 3 == 0 ? Color.parseColor("#FFD700")
                    : i % 3 == 1 ? Color.parseColor("#fff080")
                    :              Color.parseColor("#ffaa00");
            coinParticles.add(new CoinParticle(x, y,
                    (float)Math.cos(angle) * spd,
                    (float)Math.sin(angle) * spd - 1f,
                    col, size));
        }
    }

    private void collectGunUpgrade(float x, float y) {
        if (gunPower < MAX_GUN_LEVEL) {
            gunPower++;
            addFloating(x, y - 55, "GUN LV" + gunPower + "!", Color.parseColor("#00ffcc"));
            screenFlashAlpha = 55f;
            screenFlashColor = Color.parseColor("#00ffcc");
            playSound(sndGunUpgrade, 1.0f, 1.0f);
            if (gunPower == 2) {
                addFloating(screenWidth/2f, screenHeight/2f - 40,
                        "TWIN CANNONS!", Color.parseColor("#44ffaa"));
            } else if (gunPower == 3) {
                addFloating(screenWidth/2f, screenHeight/2f - 40,
                        "V-SPREAD! ENEMIES STRONGER!", Color.parseColor("#ff8800"));
                screenFlashAlpha = 50f;
                screenFlashColor = Color.parseColor("#ff8800");
            } else if (gunPower == MAX_GUN_LEVEL) {
                addFloating(screenWidth/2f, screenHeight/2f - 40,
                        "MAX POWER! ★★★", Color.parseColor("#FFD700"));
                screenFlashAlpha = 90f;
                screenFlashColor = Color.parseColor("#FFD700");
            }
        } else {
            score += 100;
            addFloating(x, y - 55, "MAX! +100", Color.parseColor("#FFD700"));
            playSound(sndGunUpgrade, 0.8f, 1.3f);
        }
    }

    private void giveReward(int pts, float x, float y) {
        comboCount++;
        comboTimer = COMBO_TIMEOUT;
        runKills++;
        recentKills++;
        waveKillsGot++;  // track per-wave kills for star rating
        waveEnemiesAlive = Math.max(0, waveEnemiesAlive - 1); // enemy eliminated

        float newMult = comboCount >= 15 ? 5f
                : comboCount >= 10 ? 3f
                : comboCount >= 6  ? 2f
                : comboCount >= 3  ? 1.5f
                :                    1f;

        if (newMult > comboMult) {
            comboMult = newMult;
            // Cinematic combo label with punch-in scale
            String tag;
            int tagColor;
            float tagSize;
            if (comboCount >= 15) {
                tag = "★ LEGENDARY! ★";
                tagColor = Color.parseColor("#ff2200");
                tagSize  = 58f;
                screenFlashAlpha = 45f; screenFlashColor = Color.parseColor("#ff4400");
            } else if (comboCount >= 10) {
                tag = "🔥 UNSTOPPABLE!";
                tagColor = Color.parseColor("#ff6600");
                tagSize  = 50f;
            } else if (comboCount >= 6) {
                tag = "⚡ RAMPAGE!";
                tagColor = Color.parseColor("#ffcc00");
                tagSize  = 44f;
            } else {
                tag = "COMBO!";
                tagColor = Color.parseColor("#ffffff");
                tagSize  = 36f;
            }
            // Big punch-in text at screen centre
            floatingTexts.add(new FloatingTextFx(
                    screenWidth / 2f, screenHeight * 0.38f,
                    tag, tagColor, 255f, tagSize, 3.5f, 1.35f));
            // Multiplier badge
            String multBadge = "×" + (comboMult >= 5f ? "5" : comboMult >= 3f ? "3" : comboMult >= 2f ? "2" : "1.5");
            floatingTexts.add(new FloatingTextFx(
                    screenWidth / 2f, screenHeight * 0.38f + tagSize + 8,
                    multBadge, tagColor, 255f, tagSize * 0.7f, 2.5f, 1.2f));
            shake(8f);
        }

        // ── Electric sparks around player on high combo ─────────────────
        if (comboCount >= 10) {
            float pcx = planeX + 120f, pcy = planeY + 120f;
            int sparkCount = comboCount >= 15 ? 5 : 3;
            int sparkCol = comboCount >= 15
                    ? Color.argb(220, 255, 80, 0)   // red-orange for legendary
                    : Color.argb(200, 100, 200, 255); // electric blue for unstoppable
            for (int s = 0; s < sparkCount; s++) {
                float angle = (float)(random.nextDouble() * Math.PI * 2);
                float radius = 80f + random.nextFloat() * 60f;
                float spd = 2f + random.nextFloat() * 3f;
                float svx = (float)Math.cos(angle) * spd;
                float svy = (float)Math.sin(angle) * spd;
                deathParticles.add(new DeathParticle(
                        pcx + (float)Math.cos(angle) * 40,
                        pcy + (float)Math.sin(angle) * 40,
                        svx, svy, sparkCol, 3f + random.nextFloat() * 3f));
            }
        }

        int earned = (int)(pts * comboMult);
        score += earned;
        String label = comboMult > 1f ? "+" + earned + " x" + (int)comboMult
                : "+" + earned;
        addFloating(x, y - 20, label, comboMult >= 3f ? Color.parseColor("#ff4400")
                : comboMult >= 2f ? Color.parseColor("#ff8800")
                : Color.parseColor("#FFD700"));
    }

    /**
     * Scales src to fill targetWidth×targetHeight while preserving aspect ratio,
     * then crops the centre — no stretching, no black bars.
     */
    private Bitmap getCenterCroppedBitmap(Bitmap src, int targetWidth, int targetHeight) {
        float srcAspect    = (float) src.getWidth()  / src.getHeight();
        float targetAspect = (float) targetWidth / targetHeight;

        int scaledW, scaledH;
        if (srcAspect > targetAspect) {
            // Source is wider — fit to height, crop sides
            scaledH = targetHeight;
            scaledW = (int)(targetHeight * srcAspect);
        } else {
            // Source is taller — fit to width, crop top/bottom
            scaledW = targetWidth;
            scaledH = (int)(targetWidth / srcAspect);
        }

        Bitmap scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true);
        int xOff = (scaledW - targetWidth)  / 2;
        int yOff = (scaledH - targetHeight) / 2;
        Bitmap cropped = Bitmap.createBitmap(scaled, xOff, yOff, targetWidth, targetHeight);
        if (scaled != cropped) scaled.recycle();
        return cropped;
    }

    private Bitmap loadSprite(String name, int w, int h) {
        try {
            int rid = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
            if (rid == 0) {
                rid = getResources().getIdentifier(
                        getContext().getPackageName() + ":drawable/" + name, null, null);
            }
            android.util.Log.d("SPRITE_LOAD", name + " -> rid=" + rid);
            if (rid == 0) return null;
            Bitmap raw = BitmapFactory.decodeResource(getResources(), rid);
            if (raw == null) return null;
            Bitmap scaled = Bitmap.createScaledBitmap(raw, w, h, true);
            if (raw != scaled) raw.recycle();
            return scaled;
        } catch (Exception e) {
            android.util.Log.e("SPRITE_LOAD", name + " EXCEPTION: " + e.getMessage());
            return null;
        }
    }

    private void shake(float intensity) {
        if (!screenShakeEnabled) return;
        shakeIntensity = Math.max(shakeIntensity, intensity);
    }

    private void addExplosion(float x, float y, float r) {
        int type = r >= 150 ? 2 : r >= 60 ? 1 : 0;
        // Core fireball
        explosions.add(new ExplosionFx(x, y, Math.min(8, r*0.12f), 255, r, type));
        // Secondary delayed flash (offset timing)
        if (r >= 60) {
            ExplosionFx delayed = new ExplosionFx(x + random.nextFloat()*20-10,
                    y + random.nextFloat()*20-10, 4f, 220, r*0.6f, type);
            delayed.frame = -4; // starts a bit later
            explosions.add(delayed);
        }
        // Shockwave ring for large explosions
        if (r >= 60) {
            ExplosionFx ring = new ExplosionFx(x, y, r * 0.2f, 180, r * 1.4f, 3);
            explosions.add(ring);
        }

        if (r >= 150) {
            screenFlashAlpha = 70f;
            screenFlashColor = Color.parseColor("#ff6600");
            shake(18f);
            playSound(sndExplosionLg, 1.0f, 0.85f + random.nextFloat() * 0.15f);
        } else if (r >= 60) {
            shake(10f);
            playSound(sndExplosionMd, 0.9f, 0.9f + random.nextFloat() * 0.2f);
        } else {
            shake(4f);
            playSound(sndExplosionSm, 0.7f, 1.0f + random.nextFloat() * 0.3f);
        }
    }

    private void addFloating(float x, float y, String text, int color) {
        // Damage numbers (starting with -) respect the damageNumbersEnabled setting
        if (!damageNumbersEnabled && text.startsWith("-")) return;
        floatingTexts.add(new FloatingTextFx(x, y, text, color, 255));
    }

    private void addFloatingBig(float x, float y, String text, int color) {
        floatingTexts.add(new FloatingTextFx(x, y, text, color, 255, 48f, 4f, 1.25f));
    }

    /** Spawn coloured sparks on enemy death — colour/count varies by type */
    private void spawnDeathParticles(float x, float y, AirType type) {
        int particleColor;
        int count;
        float sizeBase;
        switch (type) {
            case BOMBER:      particleColor = Color.parseColor("#ff6600"); count=18; sizeBase=9f;  break;
            case GUNSHIP:     particleColor = Color.parseColor("#ff3300"); count=22; sizeBase=10f; break;
            case JET:         particleColor = Color.parseColor("#4488ff"); count=14; sizeBase=7f;  break;
            case DRONE:       particleColor = Color.parseColor("#00eeff"); count=10; sizeBase=5f;  break;
            case ARACHNID:    particleColor = Color.parseColor("#00ff55"); count=16; sizeBase=7f;  break;
            case KAMIKAZE:    particleColor = Color.parseColor("#ffcc00"); count=12; sizeBase=6f;  break;
            case STARSPARROW: particleColor = Color.parseColor("#ff6633"); count=14; sizeBase=7f;  break;
            case SWITCHBLADE: particleColor = Color.parseColor("#00ccff"); count=16; sizeBase=8f;  break;
            default:          particleColor = Color.parseColor("#ffaa00"); count=12; sizeBase=6f;  break;
        }
        // Scale particle count by effects quality setting
        if (effectsQuality == 0) count = Math.max(4, count / 3);       // LOW  — 33%
        else if (effectsQuality == 1) count = Math.max(6, count * 2/3); // MED  — 66%
        // HIGH (2) = full count

        for (int i = 0; i < count; i++) {
            float angle = (float)(Math.PI * 2 * i / count) + random.nextFloat() * 0.4f;
            float spd   = 2.5f + random.nextFloat() * 5f;
            float vx    = (float)Math.cos(angle) * spd;
            float vy    = (float)Math.sin(angle) * spd;
            float size  = sizeBase * (0.6f + random.nextFloat() * 0.8f);
            deathParticles.add(new DeathParticle(x, y, vx, vy, particleColor, size));
        }
        // Core flash — skip on low quality
        if (effectsQuality > 0)
            deathParticles.add(new DeathParticle(x, y, 0, 0, Color.WHITE, sizeBase * 2.5f));
    }

    /** Celebratory burst of coloured sparks when a pickup is collected */
    private void spawnPickupBurst(float x, float y, int color) {
        int count = effectsQuality == 0 ? 8 : effectsQuality == 1 ? 14 : 20;
        for (int i = 0; i < count; i++) {
            float angle = (float)(Math.PI * 2 * i / count);
            float spd   = 3f + random.nextFloat() * 6f;
            deathParticles.add(new DeathParticle(x, y,
                    (float)Math.cos(angle)*spd, (float)Math.sin(angle)*spd - 2f,
                    color, 7f + random.nextFloat() * 5f));
        }
        if (effectsQuality > 0) {
            for (int i = 0; i < 8; i++) {
                float angle = (float)(Math.PI * 2 * i / 8);
                deathParticles.add(new DeathParticle(x, y,
                        (float)Math.cos(angle)*8f, (float)Math.sin(angle)*8f, Color.WHITE, 5f));
            }
        }
    }

    private void dropFromEnemy(float x, float y, AirType type) {
        int roll = random.nextInt(100);

        int gunChance, healthChance, shieldChance, magnetChance, superChance;
        switch (type) {
            case BOMBER:
                gunChance=22; healthChance=20; shieldChance=12; magnetChance=5; superChance=3; break;
            case GUNSHIP:
                gunChance=28; healthChance=25; shieldChance=15; magnetChance=8; superChance=5; break;
            case FIGHTER: case ARACHNID:
                gunChance=14; healthChance=12; shieldChance=8; magnetChance=3; superChance=1; break;
            case JET:
                gunChance=16; healthChance=10; shieldChance=6; magnetChance=3; superChance=1; break;
            default:
                gunChance=7; healthChance=8; shieldChance=4; magnetChance=1; superChance=0; break;
        }

        // Gate gun upgrades by wave number — you earn max power by wave 11
        // Lv2 unlocks at wave 3, Lv3 at wave 7, Lv4 at wave 11
        boolean gunUpgradeAllowed = gunPower < MAX_GUN_LEVEL
                && ((gunPower == 1 && totalWavesCompleted >= 3)
                ||  (gunPower == 2 && totalWavesCompleted >= 7));

        int threshold = 0;
        if (roll < (threshold += gunChance) && gunUpgradeAllowed) {
            powerUps.add(new PowerUpPickup(x, y, PowerUpType.GUN_UPGRADE));
        } else if (roll < (threshold += healthChance) && playerHP < MAX_PLAYER_HP) {
            healthPickups.add(new HealthPickup(x, y));
        } else if (roll < (threshold += shieldChance) && !hasShield) {
            powerUps.add(new PowerUpPickup(x, y, PowerUpType.SHIELD));
        } else if (roll < (threshold += magnetChance)) {
            powerUps.add(new PowerUpPickup(x, y, PowerUpType.MAGNET));
        } else if (roll < (threshold + superChance)) {
            powerUps.add(new PowerUpPickup(x, y, PowerUpType.SUPER));
        }
    }

    // ===================== SKY & CLOUDS =====================
    private void drawSky(Canvas canvas) {
        bgScrollY += 0.4f;
        if (bgScrollY > 10000f) bgScrollY -= 10000f;

        switch (currentMap) {
            case MAP_DESERT: drawSkyDesert(canvas); break;
            case MAP_OCEAN:  drawSkyOcean(canvas);  break;
            default:         drawSkyDeepSpace(canvas); break;
        }
    }

    // ── MAP 0: Deep Space — 3-layer parallax ──────────────────────────────
    private void drawSkyDeepSpace(Canvas canvas) {
        // Base gradient — near-black space
        paint.setShader(new LinearGradient(0, 0, 0, screenHeight,
                Color.parseColor("#020510"), Color.parseColor("#050d1a"), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, screenWidth, screenHeight, paint);
        paint.setShader(null);

        // ── LAYER 1: Distant stars — tiny, slow (speed 0.1f), grey tones ──
        for (int i = 0; i < 120; i++) {
            float sx = (i * 2531 + 173) % screenWidth;
            float sy = ((i * 1747 + 89) % screenHeight + bgScrollY * 0.1f) % screenHeight;
            int alpha = 60 + (i % 4) * 20;
            paint.setColor(Color.argb(alpha, 190, 200, 220));
            canvas.drawCircle(sx, sy, 0.7f, paint);
        }

        // ── LAYER 2: Nebula clouds + mid stars — semi-transparent, medium speed ──
        // Nebula ovals — soft purple/blue wisps
        paint.setColor(Color.argb(18, 60, 20, 140));
        float ny = (bgScrollY * 0.2f) % (screenHeight * 1.5f) - screenHeight * 0.25f;
        canvas.drawOval(new RectF(screenWidth*0.05f, ny, screenWidth*0.95f, ny + screenHeight*0.65f), paint);
        paint.setColor(Color.argb(14, 40, 10, 120));
        float ny2 = (bgScrollY * 0.13f + screenHeight * 0.55f) % (screenHeight * 1.5f) - screenHeight*0.25f;
        canvas.drawOval(new RectF(screenWidth*0.2f, ny2, screenWidth*1.1f, ny2 + screenHeight*0.75f), paint);
        // Mid-layer stars — medium size, twinkling
        for (int i = 0; i < 50; i++) {
            float sx = (i * 1913 + 457) % screenWidth;
            float sy = ((i * 2239 + 311) % screenHeight + bgScrollY * 0.4f) % screenHeight;
            int alpha = (frameCount * 3 + i * 47) % 2 == 0 ? 170 : 110;
            paint.setColor(Color.argb(alpha, 220, 225, 255));
            canvas.drawCircle(sx, sy, i % 5 == 0 ? 1.8f : 1.1f, paint);
        }

        // ── Planets — layer 2.5, slow parallax ────────────────────────────
        planetScrollY += 0.075f;
        float tileH = screenHeight * 5.5f;
        if (planetScrollY > tileH) planetScrollY -= tileH;
        Bitmap[] pSprites = {spritePlanet5,  spritePlanet6,  spritePlanet7,
                spritePlanet8,  spritePlanet9,  spritePlanet10,
                spritePlanet12, spritePlanet13, spritePlanet14,
                spritePlanet15, spritePlanet26};
        float[] pX     = {0.78f, 0.14f, 0.55f, 0.85f, 0.25f, 0.65f,
                0.10f, 0.70f, 0.42f, 0.20f, 0.58f};
        float[] pOff   = new float[11];
        for (int i = 0; i < 11; i++) pOff[i] = tileH * i / 11f;
        float[] pScale = {1.0f, 0.5f, 0.85f, 0.45f, 0.9f, 0.55f,
                0.80f, 0.48f, 0.95f, 0.52f, 0.75f};
        float[] pAlpha = {0.65f, 0.40f, 0.60f, 0.35f, 0.68f, 0.42f,
                0.62f, 0.38f, 0.70f, 0.40f, 0.58f};
        for (int p = 0; p < pSprites.length; p++) {
            if (pSprites[p] == null) continue;
            float cx = screenWidth * pX[p];
            float cy = ((pOff[p] + planetScrollY) % tileH) - screenHeight * 0.15f;
            float hw = pSprites[p].getWidth() * pScale[p] / 2f;
            float hh = pSprites[p].getHeight() * pScale[p] / 2f;
            if (cy + hh < -50 || cy - hh > screenHeight + 50) continue;
            bitmapPaint.setAlpha((int)(pAlpha[p] * 255));
            canvas.drawBitmap(pSprites[p], null,
                    new RectF(cx - hw, cy - hh, cx + hw, cy + hh), bitmapPaint);
            // Single-direction lighting: dark gradient on right side (sun from top-left)
            paint.setShader(new RadialGradient(cx + hw * 0.3f, cy - hh * 0.3f,
                    hw * 1.4f,
                    new int[]{Color.argb(0,0,0,0), Color.argb(90,0,0,0)},
                    new float[]{0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(cx-hw, cy-hh, cx+hw, cy+hh), paint);
            paint.setShader(null);
            bitmapPaint.setAlpha(255);
        }

        // ── LAYER 3: Bright foreground stars — fast (1.0f), sharp, cross-shaped ──
        for (int i = 0; i < 20; i++) {
            float sx = (i * 3371 + 621) % screenWidth;
            float sy = ((i * 2791 + 503) % screenHeight + bgScrollY * 1.0f) % screenHeight;
            paint.setColor(Color.argb(255, 255, 255, 255));
            canvas.drawCircle(sx, sy, 2.2f, paint);
            if (i % 4 == 0) {
                paint.setColor(Color.argb(100, 255, 220, 255));
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
                canvas.drawLine(sx-7, sy, sx+7, sy, paint);
                canvas.drawLine(sx, sy-7, sx, sy+7, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    // ── MAP 1: Nebula Storm — deep red/orange with thick gas clouds ────────
    private void drawSkyNebulaStorm(Canvas canvas) {
        // Dark crimson base gradient
        paint.setShader(new LinearGradient(0, 0, 0, screenHeight,
                Color.parseColor("#0f0005"), Color.parseColor("#1a0008"), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, screenWidth, screenHeight, paint);
        paint.setShader(null);

        planetScrollY += 0.09f;  // slightly faster scroll — more intense feel
        float tileH = screenHeight * 5.5f;
        if (planetScrollY > tileH) planetScrollY -= tileH;

        // Nebula cloud layers — large soft ovals in reds/oranges/purples
        float[] cloudX   = {0.15f, 0.7f,  0.4f,  0.85f, 0.25f, 0.6f};
        float[] cloudOff = {0f, tileH*0.17f, tileH*0.33f, tileH*0.5f, tileH*0.66f, tileH*0.83f};
        int[]   cloudClr = {
                Color.argb(28, 180, 20, 0),    // deep red
                Color.argb(22, 120, 0, 80),    // dark magenta
                Color.argb(25, 200, 50, 0),    // orange-red
                Color.argb(20, 80,  0, 120),   // purple
                Color.argb(30, 160, 30, 0),    // burnt orange
                Color.argb(18, 140, 0, 60),    // dark rose
        };
        float[] cloudW = {0.9f, 0.7f, 1.0f, 0.65f, 0.85f, 0.75f};
        float[] cloudH = {0.45f, 0.55f, 0.40f, 0.60f, 0.50f, 0.45f};

        for (int c = 0; c < 6; c++) {
            float cy = ((cloudOff[c] + planetScrollY * 0.6f) % tileH) - screenHeight * 0.2f;
            if (cy + screenHeight*cloudH[c] < -80 || cy > screenHeight + 80) continue;
            paint.setColor(cloudClr[c]);
            canvas.drawOval(new RectF(
                    screenWidth * (cloudX[c] - cloudW[c]*0.5f), cy,
                    screenWidth * (cloudX[c] + cloudW[c]*0.5f), cy + screenHeight * cloudH[c]
            ), paint);
        }

        // Reuse planet sprites but tinted warm for map 2 (just draw them with warm alpha)
        Bitmap[] pSprites = {spritePlanet8, spritePlanet9, spritePlanet12,
                spritePlanet13, spritePlanet15, spritePlanet26};
        float[] pX   = {0.82f, 0.12f, 0.55f, 0.78f, 0.20f, 0.60f};
        float[] pOff2 = new float[6];
        for (int i = 0; i < 6; i++) pOff2[i] = tileH * i / 6f;
        float[] pScale2 = {0.7f, 0.55f, 1.1f, 0.5f, 0.9f, 0.65f};
        float[] pAlpha2 = {0.45f, 0.38f, 0.55f, 0.35f, 0.50f, 0.40f};

        for (int p = 0; p < pSprites.length; p++) {
            if (pSprites[p] == null) continue;
            float cx = screenWidth * pX[p];
            float cy = ((pOff2[p] + planetScrollY) % tileH) - screenHeight * 0.15f;
            float hw = pSprites[p].getWidth() * pScale2[p] / 2f;
            float hh = pSprites[p].getHeight() * pScale2[p] / 2f;
            if (cy + hh < -50 || cy - hh > screenHeight + 50) continue;
            // Warm orange-red tint filter
            bitmapPaint.setAlpha((int)(pAlpha2[p] * 255));
            bitmapPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    Color.argb(80, 255, 60, 0), android.graphics.PorterDuff.Mode.SCREEN));
            canvas.drawBitmap(pSprites[p], null,
                    new RectF(cx - hw, cy - hh, cx + hw, cy + hh), bitmapPaint);
            bitmapPaint.setColorFilter(null);
            bitmapPaint.setAlpha(255);
        }

        // Denser star field — more twinkling, red-tinted
        for (int i = 0; i < 100; i++) {
            float sx = (i * 2531 + 173) % screenWidth;
            float sy = ((i * 1747 + 89) % screenHeight + bgScrollY * 0.3f) % screenHeight;
            int a = 60 + (i%3)*50;
            // Alternate warm/cool stars
            if (i % 3 == 0) paint.setColor(Color.argb(a, 255, 180, 150));
            else if (i % 3 == 1) paint.setColor(Color.argb(a, 255, 100, 80));
            else paint.setColor(Color.argb(a, 220, 200, 255));
            canvas.drawCircle(sx, sy, 0.9f, paint);
        }
        // Brighter twinkling stars
        for (int i = 0; i < 50; i++) {
            float sx = (i * 1913 + 457) % screenWidth;
            float sy = ((i * 2239 + 311) % screenHeight + bgScrollY * 0.6f) % screenHeight;
            int alpha = (frameCount * 4 + i * 53) % 2 == 0 ? 200 : 100;
            paint.setColor(Color.argb(alpha, 255, 200, 180));
            canvas.drawCircle(sx, sy, i % 5 == 0 ? 2.2f : 1.3f, paint);
        }
        // Bright sharp stars with cross flare — orange tint
        for (int i = 0; i < 20; i++) {
            float sx = (i * 3371 + 621) % screenWidth;
            float sy = ((i * 2791 + 503) % screenHeight + bgScrollY * 1.0f) % screenHeight;
            paint.setColor(Color.argb(255, 255, 220, 200));
            canvas.drawCircle(sx, sy, 2.4f, paint);
            if (i % 3 == 0) {
                paint.setColor(Color.argb(120, 255, 160, 80));
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
                canvas.drawLine(sx-8, sy, sx+8, sy, paint);
                canvas.drawLine(sx, sy-8, sx, sy+8, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        // Nebula gas streaks — thin diagonal bands drifting across
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        for (int i = 0; i < 8; i++) {
            float streakY = ((i * 400 + bgScrollY * 0.8f) % (screenHeight * 1.4f)) - screenHeight * 0.2f;
            int sa = 18 + i * 3;
            paint.setColor(Color.argb(sa, 200 - i*10, 30, i*8));
            canvas.drawLine(-40, streakY, screenWidth + 40, streakY + 60 + i*15, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    // ── MAP 1: Desert Front ───────────────────────────────────────────────
    // ── MAP 1: Desert Front — Dune Sea ────────────────────────────────────
    // Own scroll state — completely independent from Deep Space
    private float dsr_skyScroll = 0f;   // slow sky clouds
    private float dsr_duneFar   = 0f;   // far dune ridge
    private float dsr_duneMid   = 0f;   // mid dune row
    private float dsr_duneFront = 0f;   // close dune crest
    private float dsr_dustScroll= 0f;   // fast dust layer
    private int   dsr_gustTimer = 0;    // occasional gust flash
    private float dsr_wind      = 0f;   // current wind force (smoothed sine)
    private boolean dsr_sandstorm = false;
    private int   dsr_sandstormTimer = 0;

    private void drawSkyDesert(Canvas canvas) {
        // ── 1. Infinite desert strip — 4 top-down aerial images ─────────
        float sp = scrollSpeed * slowMoScale;

        // Update timers (wind, storm, scroll values still needed for overlays)
        dsr_skyScroll  = (dsr_skyScroll  + sp * 0.04f) % (screenHeight * 2);
        dsr_dustScroll = (dsr_dustScroll + sp * 1.20f) % screenHeight;
        if (dsr_gustTimer > 0) dsr_gustTimer--;
        if (frameCount % 220 == 0) dsr_gustTimer = 45;

        // Desert wind force (used by entities, not drawing)
        dsr_wind = (float)Math.sin(frameCount * 0.018f) * 1.1f
                + (float)Math.sin(frameCount * 0.041f) * 0.4f;

        // Sandstorm event every ~25 seconds
        if (dsr_sandstormTimer > 0) {
            dsr_sandstormTimer--;
            dsr_sandstorm = dsr_sandstormTimer > 0;
        }
        if (frameCount % 1500 == 800) { dsr_sandstormTimer = 180; dsr_sandstorm = true; }

        // ── Solid sand base — ensures alpha blends to warm sand, not black ──
        paint.setColor(Color.parseColor("#f0c060")); // warm sandy base
        canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

        if (desertStrip[0] != null) {
            float tileH = screenHeight;
            // Slow scroll 30% during desert boss for epic confrontation feel
            float bossSlowFactor = (boss != null && boss.bossType == MAP_DESERT) ? 0.70f : 1.0f;
            float stripSpeed = sp * 0.85f * bossSlowFactor;

            // Draw each tile stretched to fill exact screen size using Matrix
            // No canvas.scale() — avoids gaps at edges and recycling bugs
            android.graphics.Matrix tileMatrix = new android.graphics.Matrix();

            for (int si = 0; si < 2; si++) {
                dsStrip_Y[si] += stripSpeed;

                // Recycle: when tile exits bottom, jump above topmost tile
                if (dsStrip_Y[si] >= tileH) {
                    float minY = dsStrip_Y[0];
                    for (int k = 1; k < 2; k++) if (dsStrip_Y[k] < minY) minY = dsStrip_Y[k];
                    dsStrip_Y[si] = minY - tileH + stripSpeed;
                }

                if (dsStripPaint != null && desertStrip[si] != null) {
                    Bitmap bm = desertStrip[si];
                    // Scale bitmap to fill full screen width and tile height
                    float scaleX = (float) screenWidth  / bm.getWidth();
                    float scaleY = (float) tileH        / bm.getHeight();
                    tileMatrix.setScale(scaleX, scaleY);
                    tileMatrix.postTranslate(0, dsStrip_Y[si]);
                    canvas.drawBitmap(bm, tileMatrix, dsStripPaint);
                }
            }

            // ── Gradient seam mask — fade top and bottom of each tile ─────
            // Draws a transparent→sandy→transparent band over each tile edge
            // so transitions blend naturally regardless of image content
            Paint seamPaint = new Paint();
            int fadeH = (int)(screenHeight * 0.30f); // 30% of screen height
            int sandA = 130; // stronger fade at 30% zone
            for (int si = 0; si < 2; si++) {
                if (desertStrip[si] == null) continue;
                float tileTop = dsStrip_Y[si];
                float tileBot = dsStrip_Y[si] + tileH;

                // Top edge of this tile fades in from transparent
                if (tileTop > -fadeH && tileTop < screenHeight) {
                    seamPaint.setShader(new LinearGradient(
                            0, tileTop, 0, tileTop + fadeH,
                            Color.argb(sandA, 240, 184, 74),
                            Color.argb(0,     240, 184, 74),
                            Shader.TileMode.CLAMP));
                    canvas.drawRect(0, tileTop, screenWidth, tileTop + fadeH, seamPaint);
                }

                // Bottom edge of this tile fades out to transparent
                if (tileBot > 0 && tileBot < screenHeight + fadeH) {
                    seamPaint.setShader(new LinearGradient(
                            0, tileBot - fadeH, 0, tileBot,
                            Color.argb(0,     240, 184, 74),
                            Color.argb(sandA, 240, 184, 74),
                            Shader.TileMode.CLAMP));
                    canvas.drawRect(0, tileBot - fadeH, screenWidth, tileBot, seamPaint);
                }
                seamPaint.setShader(null);
            }
        } else {
            // Fallback gradient if images not loaded
            paint.setShader(new LinearGradient(0, 0, 0, screenHeight,
                    new int[]{Color.parseColor("#4a8fc4"), Color.parseColor("#a0c8e8"),
                            Color.parseColor("#d4a857"), Color.parseColor("#c28020")},
                    new float[]{0f, 0.38f, 0.72f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);
            paint.setShader(null);
        }

        // ── 2. Sun disc — floats in top-right, always visible ────────────
        float sunX = screenWidth * 0.84f, sunY = screenHeight * 0.08f;
        paint.setShader(new RadialGradient(sunX, sunY, screenWidth*0.10f,
                new int[]{Color.argb(220,255,252,200), Color.argb(140,255,220,80), Color.argb(0,255,180,0)},
                new float[]{0f,0.45f,1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(sunX, sunY, screenWidth*0.09f, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(35, 255, 230, 120));
        canvas.drawCircle(sunX, sunY, screenWidth*0.16f, paint);

        // ── 3. Warm atmospheric tint ──────────────────────────────────────
        paint.setColor(Color.argb(22, 255, 200, 80));
        canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

        // ── 4. Dust streaks ───────────────────────────────────────────────
        drawDesertDustStreaks(canvas);

        // ── 5. Heat shimmer bands ─────────────────────────────────────────
        paint.setColor(Color.argb(12, 255, 255, 200));
        for (int i = 0; i < 8; i++) {
            float hy = (float)(i * screenHeight / 8.0
                    + Math.sin((frameCount + i*19) * 0.048f) * 9f
                    + dsr_skyScroll * 0.25f) % screenHeight;
            canvas.drawRect(0, hy, screenWidth, hy + 11, paint);
        }

        // ── 6. Gust flash ──────────────────────────────────────────────────
        if (dsr_gustTimer > 0) {
            int ga = Math.min(45, dsr_gustTimer * 2);
            paint.setColor(Color.argb(ga, 255, 230, 160));
            canvas.drawRect(0, screenHeight*0.25f, screenWidth, screenHeight*0.92f, paint);
        }

        // ── 7. Sandstorm overlay ───────────────────────────────────────────
        if (dsr_sandstorm && dsr_sandstormTimer > 0) {
            float fade = dsr_sandstormTimer < 40 ? dsr_sandstormTimer / 40f
                    : dsr_sandstormTimer > 140 ? (180 - dsr_sandstormTimer) / 40f : 1f;
            int sa = (int)(90 * fade);
            paint.setColor(Color.argb(sa, 210, 170, 80));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);
            paint.setColor(Color.argb((int)(sa * 0.7f), 240, 200, 100));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
            for (int i = 0; i < 20; i++) {
                float sx2 = (i * 157 + (int)(dsr_dustScroll * 2.5f)) % screenWidth;
                float sy2 = (i * 83  + (int)(dsr_dustScroll * 1.8f)) % screenHeight;
                float len = 40 + (i % 5) * 20f;
                canvas.drawLine(sx2, sy2, sx2 + len, sy2 - len * 0.3f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        // ── 8. Desert boss effects ────────────────────────────────────────
        if (boss != null && boss.bossType == MAP_DESERT) {
            // Pulsing heat haze — flickering orange tint across screen
            float heatFlicker = 0.6f + (float)Math.sin(frameCount * 0.18f) * 0.4f;
            int heatAlpha = (int)(30 * heatFlicker);
            paint.setColor(Color.argb(heatAlpha, 255, 120, 20));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

            // Boss vignette — dark edge radial, focuses on centre
            Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            vignettePaint.setShader(new RadialGradient(
                    screenWidth / 2f, screenHeight / 2f, screenHeight / 1.1f,
                    new int[]{Color.TRANSPARENT, Color.argb(140, 60, 20, 0)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenWidth, screenHeight, vignettePaint);
        }
    }

    /** Draw a scrolling dune silhouette row using sine-wave bumps */
    private void drawDuneRow(Canvas canvas, float scroll, float baseY,
                             int[] colors, float minH, float maxH, int count, int seed1, int seed2) {
        float bY = screenHeight * baseY;
        float tileW = (float)screenWidth / count;
        paint.setShader(new LinearGradient(0, bY - screenHeight*maxH, 0, bY + screenHeight*0.08f,
                colors, null, Shader.TileMode.CLAMP));
        Path path = new Path();
        path.moveTo(-10, screenHeight + 10);

        // Two passes for seamless looping
        for (int pass = -1; pass <= 1; pass++) {
            for (int d = 0; d < count + 1; d++) {
                float x = pass * screenWidth + d * tileW;
                // Use deterministic seeds so dunes are always the same shape
                float h = screenHeight * (minH + ((seed1 * (d+1) + seed2) % 100) / 100f * (maxH - minH));
                float offsetX = ((seed2 * (d+3)) % 100) / 100f * tileW * 0.6f;
                // Scroll: shift vertically by scroll amount translated to Y
                float scrolledY = bY - h + (float)Math.sin(d * 0.9f) * h * 0.15f;
                // Each "dune" is a quadratic bezier bump
                if (d == 0 && pass == -1) {
                    path.moveTo(x - tileW*0.5f, scrolledY + h);
                }
                path.quadTo(x + offsetX, scrolledY - h * 0.1f, x + tileW, scrolledY + h * 0.6f);
            }
        }
        path.lineTo(screenWidth + 10, screenHeight + 10);
        path.close();
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    /** Desert ground clutter: ruins, outpost towers, crashed hulks */
    private void drawDesertProps(Canvas canvas) {
        float sp = dsr_duneFront;
        float tileH = screenHeight * 3f;
        float[] propX = {0.12f, 0.38f, 0.61f, 0.82f, 0.25f, 0.72f};
        float[] propOff = {0f, tileH*0.18f, tileH*0.37f, tileH*0.55f, tileH*0.73f, tileH*0.90f};
        int[]   propType = {0, 1, 2, 0, 1, 2};  // 0=ruin arch, 1=tower, 2=hulk
        for (int p = 0; p < 6; p++) {
            float py = ((propOff[p] + sp * 2.2f) % tileH) - 80;
            if (py > screenHeight + 80 || py < -150) continue;
            float px = screenWidth * propX[p];
            switch (propType[p]) {
                case 0: drawRuinArch(canvas, px, py); break;
                case 1: drawWatchTower(canvas, px, py); break;
                case 2: drawCrashedHulk(canvas, px, py); break;
            }
        }
    }

    private void drawRuinArch(Canvas canvas, float x, float y) {
        paint.setColor(Color.parseColor("#5a3010"));
        // Two pillars
        canvas.drawRect(x-28, y+10, x-14, y+70, paint);
        canvas.drawRect(x+14, y+10, x+28, y+70, paint);
        // Arch cap (broken)
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(8f);
        paint.setColor(Color.parseColor("#7a4820"));
        canvas.drawArc(new RectF(x-28, y-10, x+28, y+50), 180, 140, false, paint);
        paint.setStyle(Paint.Style.FILL);
    }
    private void drawWatchTower(Canvas canvas, float x, float y) {
        paint.setColor(Color.parseColor("#6a4015"));
        canvas.drawRect(x-10, y, x+10, y+80, paint);  // shaft
        canvas.drawRect(x-18, y-15, x+18, y+8, paint); // top box
        paint.setColor(Color.parseColor("#4a2808"));
        canvas.drawRect(x-14, y-12, x-10, y+8, paint); // window L
        canvas.drawRect(x+10, y-12, x+14, y+8, paint); // window R
    }
    private void drawCrashedHulk(Canvas canvas, float x, float y) {
        paint.setColor(Color.parseColor("#3a2408"));
        // Fuselage tilted
        canvas.save();
        canvas.rotate(18, x, y+30);
        canvas.drawRoundRect(new RectF(x-40, y+15, x+40, y+38), 8, 8, paint);
        canvas.drawRoundRect(new RectF(x-15, y, x+18, y+18), 4, 4, paint); // cockpit
        paint.setColor(Color.parseColor("#5a3810"));
        canvas.drawRect(x-35, y+28, x-22, y+40, paint); // wing stub L
        canvas.drawRect(x+22, y+28, x+38, y+40, paint); // wing stub R
        canvas.restore();
    }
    private void drawDesertDustStreaks(Canvas canvas) {
        float windShift = (float)Math.sin(frameCount * 0.016f) * 18f;
        paint.setColor(Color.argb(22, 230, 200, 130));
        for (int i = 0; i < 11; i++) {
            float dy = (dsr_dustScroll * 0.8f + i * screenHeight / 11f) % screenHeight;
            float xOff = windShift + (float)Math.sin((frameCount + i*23)*0.05f) * 12f;
            float len = 80 + (i % 4) * 40;
            canvas.drawRoundRect(new RectF(xOff + i*(screenWidth/11f) - len*0.5f, dy,
                    xOff + i*(screenWidth/11f) + len*0.5f, dy+7), 4, 4, paint);
        }
        // Large dust cloud blobs
        paint.setColor(Color.argb(12, 210, 170, 90));
        for (int i = 0; i < 5; i++) {
            float bx = (i * 431 + (int)(dsr_dustScroll * 0.4f) * 7) % screenWidth;
            float by = (dsr_dustScroll * 0.5f + i * screenHeight * 0.2f) % screenHeight;
            canvas.drawOval(new RectF(bx-55, by-20, bx+55, by+22), paint);
        }
    }

    // ── MAP 2: Ocean Strike — Naval Warfare ───────────────────────────────
    private float ocn_skyScroll  = 0f;   // cloud layer
    private float ocn_deep       = 0f;   // deep water body
    private float ocn_surface    = 0f;   // surface shimmer
    private float ocn_island     = 0f;   // island/reef parallax
    private float ocn_ships      = 0f;   // naval ships / carriers
    private float ocn_foam       = 0f;   // fast foam / wake
    private float ocn_wave       = 0f;   // live wave bob applied to entities

    private void drawSkyOcean(Canvas canvas) {
        // ── 1. Infinite ocean strip — 6 top-down ice/water tiles ─────────
        float sp = scrollSpeed * slowMoScale;

        // Update scroll offsets (still needed for overlay effects)
        ocn_skyScroll = (ocn_skyScroll + sp * 0.05f) % (screenHeight * 2);
        ocn_surface   = (ocn_surface   + sp * 0.20f) % screenHeight;
        ocn_foam      = (ocn_foam      + sp * 1.30f) % screenHeight;
        ocn_wave      = (float)Math.sin(frameCount * 0.028f) * 0.45f;

        // ── Solid ice-white base — alpha blends to pale blue, not black ──
        paint.setColor(Color.parseColor("#dce8f0")); // pale icy base
        canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

        if (oceanStrip[0] != null) {
            float tileH = screenHeight;
            // Slow scroll 30% during ocean boss
            float bossSlowFactor = (boss != null && boss.bossType == MAP_OCEAN) ? 0.70f : 1.0f;
            float stripSpeed = sp * 0.85f * bossSlowFactor;

            android.graphics.Matrix tileMatrix = new android.graphics.Matrix();

            for (int si = 0; si < 2; si++) {
                ocStrip_Y[si] += stripSpeed;

                // Recycle tile to top when it exits the bottom
                if (ocStrip_Y[si] >= tileH) {
                    float minY = ocStrip_Y[0];
                    for (int k = 1; k < 2; k++) if (ocStrip_Y[k] < minY) minY = ocStrip_Y[k];
                    ocStrip_Y[si] = minY - tileH + stripSpeed;
                }

                if (ocStripPaint != null && oceanStrip[si] != null) {
                    Bitmap bm = oceanStrip[si];
                    float scaleX = (float) screenWidth / bm.getWidth();
                    float scaleY = (float) tileH       / bm.getHeight();
                    // Ocean boss distortion: subtle horizontal shake using sin wave
                    float distortX = (boss != null && boss.bossType == MAP_OCEAN)
                            ? (float)Math.sin(frameCount * 0.07f + si * 1.4f) * 4f : 0f;
                    tileMatrix.setScale(scaleX, scaleY);
                    tileMatrix.postTranslate(distortX, ocStrip_Y[si]);
                    canvas.drawBitmap(bm, tileMatrix, ocStripPaint);
                }
            }

            // ── Gradient seam mask — top + bottom fade on each tile ──────
            Paint seamPaint = new Paint();
            int fadeH = (int)(screenHeight * 0.30f); // 30% of screen height
            int iceA  = 110; // stronger fade at 30% zone
            for (int si = 0; si < 2; si++) {
                if (oceanStrip[si] == null) continue;
                float tileTop = ocStrip_Y[si];
                float tileBot = ocStrip_Y[si] + tileH;

                if (tileTop > -fadeH && tileTop < screenHeight) {
                    seamPaint.setShader(new LinearGradient(
                            0, tileTop, 0, tileTop + fadeH,
                            Color.argb(iceA, 230, 240, 248),
                            Color.argb(0,    230, 240, 248),
                            Shader.TileMode.CLAMP));
                    canvas.drawRect(0, tileTop, screenWidth, tileTop + fadeH, seamPaint);
                }

                if (tileBot > 0 && tileBot < screenHeight + fadeH) {
                    seamPaint.setShader(new LinearGradient(
                            0, tileBot - fadeH, 0, tileBot,
                            Color.argb(0,    230, 240, 248),
                            Color.argb(iceA, 230, 240, 248),
                            Shader.TileMode.CLAMP));
                    canvas.drawRect(0, tileBot - fadeH, screenWidth, tileBot, seamPaint);
                }
                seamPaint.setShader(null);
            }
        } else {
            // Fallback gradient if images not loaded
            paint.setShader(new LinearGradient(0, 0, 0, screenHeight,
                    new int[]{Color.parseColor("#1a6fa8"), Color.parseColor("#70c0e8"),
                            Color.parseColor("#b8e8f8"), Color.parseColor("#2a7aaa"),
                            Color.parseColor("#0a3a5c")},
                    new float[]{0f, 0.28f, 0.45f, 0.55f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);
            paint.setShader(null);
        }

        // ── 2. Sine wave bands — animated water motion overlay ────────────
        Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setStyle(Paint.Style.STROKE); wavePaint.setStrokeWidth(2f);
        wavePaint.setColor(Color.argb(30, 100, 160, 220));
        for (int i = 0; i < 9; i++) {
            float wy = i * screenHeight / 9f + (ocn_surface * 0.3f) % (screenHeight / 9f);
            Path wp = new Path(); wp.moveTo(0, wy);
            for (int wx = 0; wx <= screenWidth; wx += 16) {
                float wyy = wy + (float)Math.sin((frameCount*0.04f) + wx*0.025f + i*0.9f) * 5f;
                wp.lineTo(wx, wyy);
            }
            canvas.drawPath(wp, wavePaint);
        }

        // ── 3. Foam streaks ───────────────────────────────────────────────
        drawOceanFoamStreaks(canvas);

        // ── 4. Cool sea tint (subtle, preserves image colours) ───────────
        paint.setColor(Color.argb(12, 30, 100, 160));
        canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

        // ── 5. Ocean boss effects ─────────────────────────────────────────
        if (boss != null && boss.bossType == MAP_OCEAN) {
            // Deep ocean tint — pulls colours toward dark navy
            float depthPulse = 0.7f + (float)Math.sin(frameCount * 0.05f) * 0.3f;
            paint.setColor(Color.argb((int)(35 * depthPulse), 0, 20, 80));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

            // Boss vignette — dark navy edge focuses on centre
            Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            vignettePaint.setShader(new RadialGradient(
                    screenWidth / 2f, screenHeight / 2f, screenHeight / 1.1f,
                    new int[]{Color.TRANSPARENT, Color.argb(150, 0, 10, 60)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenWidth, screenHeight, vignettePaint);
        }
    }

    private void drawOceanClouds(Canvas canvas) {
        float tileH = screenHeight * 3f;
        float[] cX = {0.10f, 0.40f, 0.68f, 0.25f, 0.80f};
        float[] cOff = {0f, tileH*0.22f, tileH*0.44f, tileH*0.66f, tileH*0.88f};
        float[] cW   = {120, 90, 150, 80, 110};
        for (int c = 0; c < 5; c++) {
            float cy = ((cOff[c] + ocn_skyScroll * 1.4f) % tileH) - 60;
            if (cy > screenHeight * 0.42f || cy < -80) continue;
            float cx2 = screenWidth * cX[c];
            paint.setColor(Color.argb(90, 255, 255, 255));
            canvas.drawOval(new RectF(cx2 - cW[c], cy - 18, cx2 + cW[c], cy + 18), paint);
            paint.setColor(Color.argb(50, 240, 248, 255));
            canvas.drawOval(new RectF(cx2 - cW[c]*0.7f, cy - 28, cx2 + cW[c]*0.6f, cy + 10), paint);
        }
    }

    private void drawOceanDeepWater(Canvas canvas) {
        // Alternating dark water bands creating depth illusion
        float tileH = screenHeight * 0.12f;
        for (int row = 0; row < 8; row++) {
            float wy = screenHeight * 0.44f + row * tileH + (ocn_deep * 0.5f) % tileH;
            int alpha = 30 + row * 8;
            paint.setColor(Color.argb(Math.min(90, alpha), 5, 40, 80));
            canvas.drawRect(0, wy, screenWidth, wy + tileH * 0.55f, paint);
        }
    }

    private void drawOceanIslands(Canvas canvas) {
        float tileH = screenHeight * 4f;
        float[] iX = {0.18f, 0.72f, 0.42f, 0.88f};
        float[] iOff = {0f, tileH*0.28f, tileH*0.56f, tileH*0.82f};
        float[] iW   = {70f, 50f, 90f, 45f};
        for (int is = 0; is < 4; is++) {
            float iy = ((iOff[is] + ocn_island * 2.5f) % tileH) - 40;
            if (iy > screenHeight || iy < -120) continue;
            float ix = screenWidth * iX[is];
            // Island body — dark green hill
            paint.setColor(Color.parseColor("#1a4a1a"));
            canvas.drawOval(new RectF(ix - iW[is], iy + 20, ix + iW[is], iy + 60), paint);
            paint.setColor(Color.parseColor("#2a6a20"));
            canvas.drawOval(new RectF(ix - iW[is]*0.65f, iy, ix + iW[is]*0.55f, iy + 36), paint);
            // Beach rim
            paint.setColor(Color.parseColor("#c8a050"));
            canvas.drawOval(new RectF(ix - iW[is]*1.1f, iy + 42, ix + iW[is]*1.1f, iy + 68), paint);
        }
    }

    private void drawNavalShips(Canvas canvas) {
        float tileH = screenHeight * 5f;
        float[] sX = {0.30f, 0.75f, 0.55f};
        float[] sOff = {0f, tileH*0.35f, tileH*0.70f};
        int[] sType = {0, 1, 0};  // 0=carrier, 1=destroyer
        for (int s = 0; s < 3; s++) {
            float sy = ((sOff[s] + ocn_ships * 3.5f) % tileH) - 30;
            if (sy > screenHeight || sy < -100) continue;
            float sx2 = screenWidth * sX[s];
            drawShip(canvas, sx2, sy, sType[s]);
        }
    }

    private void drawShip(Canvas canvas, float x, float y, int type) {
        if (type == 0) {
            // Aircraft carrier — long flat deck
            paint.setColor(Color.parseColor("#3a4055"));
            canvas.drawRoundRect(new RectF(x-90, y+20, x+90, y+40), 5, 5, paint);
            // Deck
            paint.setColor(Color.parseColor("#505870"));
            canvas.drawRect(x-80, y+14, x+88, y+22, paint);
            // Island superstructure
            paint.setColor(Color.parseColor("#303850"));
            canvas.drawRect(x+40, y, x+62, y+16, paint);
            // Wake
            paint.setColor(Color.argb(50, 180, 220, 240));
            canvas.drawOval(new RectF(x-100, y+35, x+100, y+55), paint);
        } else {
            // Destroyer — narrow hull
            paint.setColor(Color.parseColor("#2a3545"));
            canvas.drawRoundRect(new RectF(x-55, y+18, x+55, y+38), 8, 8, paint);
            // Bridge
            paint.setColor(Color.parseColor("#202a38"));
            canvas.drawRect(x-10, y+6, x+18, y+20, paint);
            // Gun turret
            paint.setColor(Color.parseColor("#1a2030"));
            canvas.drawRect(x-22, y+10, x-8, y+20, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4f);
            paint.setColor(Color.parseColor("#2a3a50"));
            canvas.drawLine(x-28, y+14, x-35, y+10, paint);
            paint.setStyle(Paint.Style.FILL);
            // Wake
            paint.setColor(Color.argb(40, 180, 220, 240));
            canvas.drawOval(new RectF(x-65, y+30, x+65, y+48), paint);
        }
    }

    private void drawOceanSurface(Canvas canvas) {
        // Specular highlight streaks
        for (int i = 0; i < 18; i++) {
            float sx2 = (i * 373 + (int)(ocn_surface * 2.5f) * 13) % screenWidth;
            float sy2 = screenHeight*0.46f + (i * 197 + (int)(ocn_foam * 1.2f) * 7) % (int)(screenHeight*0.54f);
            float sw = 20 + (i%5)*18;
            paint.setColor(Color.argb(20 + i%3*8, 200, 240, 255));
            canvas.drawRoundRect(new RectF(sx2, sy2, sx2+sw, sy2+4), 2, 2, paint);
        }
    }

    private void drawOceanFoamStreaks(Canvas canvas) {
        float waveShift = (float)Math.sin(frameCount * 0.035f) * 10f;
        paint.setColor(Color.argb(35, 255, 255, 255));
        for (int i = 0; i < 8; i++) {
            float fy = (ocn_foam * 0.9f + i * screenHeight * 0.125f) % screenHeight;
            float xOff = waveShift + (float)Math.sin((frameCount + i*29)*0.04f) * 14f;
            float len = 60 + (i%3)*50;
            canvas.drawRoundRect(new RectF(xOff + i*(screenWidth/8f) - len*0.5f, fy,
                    xOff + i*(screenWidth/8f) + len*0.5f, fy+5), 3, 3, paint);
        }
        // Sparkle dots on wave crests
        paint.setColor(Color.argb(50, 255, 255, 255));
        for (int i = 0; i < 20; i++) {
            float px2 = (i * 431 + (int)(ocn_foam * 3)) % screenWidth;
            float py2 = screenHeight*0.46f + (i * 307 + (int)(ocn_surface*2)) % (int)(screenHeight*0.54f);
            canvas.drawCircle(px2, py2, 1.5f + i%3, paint);
        }
    }

    private void drawSpritePlanet(Canvas canvas, Bitmap sprite, float cx, float cy, float alpha) {
        if (sprite == null) return;
        float hw = sprite.getWidth() / 2f;
        float hh = sprite.getHeight() / 2f;
        bitmapPaint.setAlpha((int)(alpha * 255));
        canvas.drawBitmap(sprite, null, new RectF(cx - hw, cy - hh, cx + hw, cy + hh), bitmapPaint);
        bitmapPaint.setAlpha(255);
    }

    // ===================== PLAYER PLANE =====================
    private void drawPlayerPlane(Canvas canvas) {
        float x  = planeX;
        float y  = planeY;
        float cx = x + 120f;

        // ── Z-axis depth shadow (space self-shadowing illusion) ───────────
        paint.setShader(new RadialGradient(cx, y + 150f, 110f,
                new int[]{Color.argb(60,0,0,0), Color.argb(0,0,0,0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawOval(new RectF(cx-110, y+80, cx+110, y+200), paint);
        paint.setShader(null);

        // ── Engine glow — pulsing, colour shifts with gun power ───────────
        float enginePulse = 0.75f + (float)Math.sin(frameCount * 0.22f) * 0.25f;
        int glowCol = superFireTimeLeft > 0 ? Color.argb(80, 120, 0, 255)
                : gunPower >= 3         ? Color.argb(70, 0, 200, 255)
                : gunPower >= 2         ? Color.argb(65, 0, 255, 160)
                :                         Color.argb(60, 255, 120, 0);
        float glowR = 38f * enginePulse;
        float engineY = y + PLAYER_W + 10f;
        paint.setShader(new RadialGradient(cx, engineY, glowR,
                new int[]{glowCol, Color.argb(0,0,0,0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, engineY, glowR, paint);
        paint.setShader(null);

        if (hasShield) {
            paint.setColor(Color.argb(
                    38+(int)(Math.sin(frameCount*0.12f)*18+18), 68,170,255));
            canvas.drawCircle(cx, y+120, 145, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
            paint.setColor(Color.argb(140,130,215,255));
            canvas.drawCircle(cx, y+120, 145, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float fh = 18 + (flameFrame % 8) * 2.8f;
        paint.setColor(Color.argb(180, 255,120,0));
        canvas.drawOval(new RectF(cx-28, y+PLAYER_W-10, cx-14, y+PLAYER_W-10+fh), paint);
        canvas.drawOval(new RectF(cx+14, y+PLAYER_W-10, cx+28, y+PLAYER_W-10+fh), paint);
        paint.setColor(Color.argb(220, 255,210,60));
        canvas.drawOval(new RectF(cx-25, y+PLAYER_W-7, cx-17, y+PLAYER_W-7+fh*0.5f), paint);
        canvas.drawOval(new RectF(cx+17, y+PLAYER_W-7, cx+25, y+PLAYER_W-7+fh*0.5f), paint);

        if (superFireTimeLeft > 0) {
            paint.setColor(Color.argb(170, 0,180,255));
            canvas.drawOval(new RectF(cx-32, y+PLAYER_W-16, cx-10, y+PLAYER_W-16+fh*1.6f), paint);
            canvas.drawOval(new RectF(cx+10, y+PLAYER_W-16, cx+32, y+PLAYER_W-16+fh*1.6f), paint);
        }

        if (playerSprite != null) {
            canvas.drawBitmap(playerSprite,
                    null,
                    new RectF(x, y, x + 240, y + 240),
                    bitmapPaint);

            int stripeColor = gunPower <= 1 ? Color.parseColor("#00ccff") :
                    gunPower <= 2 ? Color.parseColor("#44ffaa") :
                            gunPower <= 3 ? Color.parseColor("#ffee00") :
                                    Color.parseColor("#FFD700");
            if (superFireTimeLeft > 0) stripeColor = Color.parseColor("#8800ff");
            paint.setColor(stripeColor);
            canvas.drawRoundRect(new RectF(cx-14, y+PLAYER_W+2, cx+14, y+PLAYER_W+6), 2,2, paint);

            paint.setColor(Color.parseColor("#1a2230"));
            canvas.drawRoundRect(new RectF(cx-9, y+PLAYER_W+4, cx+9, y+PLAYER_W+22), 4,4, paint);
            paint.setColor(Color.parseColor("#2e3d50"));
            canvas.drawRoundRect(new RectF(cx-6, y+PLAYER_W+6, cx+6, y+PLAYER_W+24), 3,3, paint);
            boolean cannonReady = cannonCooldown <= 0;
            paint.setColor(cannonReady ? Color.argb(220,255,120,0) : Color.argb(80,80,80,80));
            canvas.drawCircle(cx, y+PLAYER_W+10, 4, paint);
            if (!cannonReady) {
                float progress = 1f - cannonCooldown / 360f;
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
                paint.setColor(Color.argb(180,255,140,0));
                canvas.drawArc(new RectF(cx-10, y+PLAYER_W, cx+10, y+PLAYER_W+20),
                        -90, 360*progress, false, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            return;
        }

        // Canvas fallback plane drawing (unchanged from original)
        paint.setColor(Color.parseColor("#252e38"));
        canvas.drawRoundRect(new RectF(cx-52, y+6, cx-22, y+38), 8,8, paint);
        paint.setColor(Color.parseColor("#2e3d4e"));
        canvas.drawRoundRect(new RectF(cx-50, y+8, cx-24, y+28), 6,6, paint);
        paint.setColor(Color.argb(210, 255,130,20));
        canvas.drawOval(new RectF(cx-50, y+2, cx-22, y+16), paint);
        paint.setColor(Color.argb(130, 255,210,80));
        canvas.drawOval(new RectF(cx-46, y+5, cx-26, y+13), paint);
        paint.setColor(Color.parseColor("#1a2530"));
        canvas.drawRoundRect(new RectF(cx-49, y+19, cx-23, y+37), 4,4, paint);
        paint.setColor(Color.argb(160, 40,160,255));
        canvas.drawRoundRect(new RectF(cx-47, y+21, cx-25, y+27), 3,3, paint);
        paint.setColor(Color.parseColor("#252e38"));
        canvas.drawRoundRect(new RectF(cx+22, y+6, cx+52, y+38), 8,8, paint);
        paint.setColor(Color.parseColor("#2e3d4e"));
        canvas.drawRoundRect(new RectF(cx+24, y+8, cx+50, y+28), 6,6, paint);
        paint.setColor(Color.argb(210, 255,130,20));
        canvas.drawOval(new RectF(cx+22, y+2, cx+50, y+16), paint);
        paint.setColor(Color.argb(130, 255,210,80));
        canvas.drawOval(new RectF(cx+26, y+5, cx+46, y+13), paint);
        paint.setColor(Color.parseColor("#1a2530"));
        canvas.drawRoundRect(new RectF(cx+23, y+19, cx+49, y+37), 4,4, paint);
        paint.setColor(Color.argb(160, 40,160,255));
        canvas.drawRoundRect(new RectF(cx+25, y+21, cx+47, y+27), 3,3, paint);
        paint.setColor(Color.parseColor("#3a4a5c"));
        Path body = new Path();
        body.moveTo(cx,    y+50); body.lineTo(cx+24, y+34);
        body.lineTo(cx+26, y+10); body.lineTo(cx+20, y-2);
        body.lineTo(cx-20, y-2); body.lineTo(cx-26, y+10);
        body.lineTo(cx-24, y+34); body.close();
        canvas.drawPath(body, paint);
        int stripeColorFb = gunPower <= 1 ? Color.parseColor("#00ccff") :
                gunPower <= 2 ? Color.parseColor("#44ffaa") :
                        gunPower <= 3 ? Color.parseColor("#ffee00") :
                                Color.parseColor("#FFD700");
        if (superFireTimeLeft > 0) stripeColorFb = Color.parseColor("#8800ff");
        paint.setColor(stripeColorFb);
        canvas.drawRoundRect(new RectF(cx-14, y+40, cx+14, y+44), 2,2, paint);
        paint.setColor(Color.parseColor("#1a2230"));
        canvas.drawRoundRect(new RectF(cx-9, y+44, cx+9, y+62), 4, 4, paint);
        paint.setColor(Color.parseColor("#2e3d50"));
        canvas.drawRoundRect(new RectF(cx-6, y+46, cx+6, y+64), 3, 3, paint);
        boolean cannonReadyFb = cannonCooldown <= 0;
        paint.setColor(cannonReadyFb ? Color.argb(220, 255, 120, 0) : Color.argb(80, 80, 80, 80));
        canvas.drawCircle(cx, y+50, 4, paint);
        if (!cannonReadyFb) {
            float progress = 1f - cannonCooldown / 360f;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
            paint.setColor(Color.argb(180, 255, 140, 0));
            canvas.drawArc(new RectF(cx-10, y+40, cx+10, y+60), -90, 360 * progress, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    // ===================== ENEMIES =====================
    private float getFireRateMultiplier() {
        float[] mults = {1.0f, 1.0f, 1.15f, 1.35f, 1.6f, 1.9f, 2.2f};
        int idx = Math.min(gunPower, mults.length - 1);
        return mults[idx];
    }

    private void updateAirEnemies(Canvas canvas, RectF planeRect) {
        // Map-based passive forces applied to all air enemies
        float mapWindX = 0f, mapBobY = 0f;
        if (currentMap == MAP_DESERT) mapWindX = dsr_wind;
        if (currentMap == MAP_OCEAN)  mapBobY  = ocn_wave;

        for (int i = airEnemies.size()-1; i >= 0; i--) {
            AirEnemy e = airEnemies.get(i);

            // ── Retreat state — enemy visibly flies out of screen ─────────
            if (e.escapeTimer > 0 && e.type != AirType.ARACHNID && e.type != AirType.GUNSHIP) {
                e.escapeTimer--;
                if (e.escapeTimer <= 0) {
                    // Timer expired → begin visible retreat: accelerate upward
                    e.speedY = -3f;
                }
                // Once retreating (speedY negative), keep accelerating
                if (e.speedY < 0) {
                    e.speedY -= 0.7f;
                    e.y += e.speedY;
                    e.x += e.speedX * 0.3f;
                    if (e.y < -150 || e.x < -200 || e.x > screenWidth + 200) {
                        airEnemies.remove(i); continue;
                    }
                    drawAirEnemy(canvas, e);
                    continue;
                }
            }

            if (e.type == AirType.KAMIKAZE) {
                float spd = 8.5f + totalWavesCompleted * 0.2f;

                if (e.kamiState == 0) {
                    float targetX = e.kamiSweepRight ? screenWidth * 0.85f : screenWidth * 0.15f;
                    float targetY = screenHeight * 0.45f;
                    float dx = targetX - e.x;
                    float dy = targetY - e.y;
                    float dist = (float)Math.sqrt(dx*dx + dy*dy);
                    if (dist > 2f) {
                        e.velX += (dx/dist * spd - e.velX) * 0.08f;
                        e.velY += (dy/dist * spd - e.velY) * 0.08f;
                    }
                    e.x += e.velX;
                    e.y += e.velY;
                    if (e.y > screenHeight * 0.4f &&
                            (e.kamiSweepRight ? e.x > screenWidth * 0.75f : e.x < screenWidth * 0.25f)) {
                        e.kamiState = 1;
                        e.velX = 0f;
                        e.velY = spd;
                    }
                } else if (e.kamiState == 1) {
                    e.velX *= 0.9f;
                    e.velY = spd * 1.1f;
                    e.x += e.velX;
                    e.y += e.velY;
                    if (e.y > screenHeight + 40) {
                        e.kamiState = 2;
                        e.x = e.kamiSweepRight ? screenWidth * 0.58f : screenWidth * 0.42f;
                        e.y = screenHeight + 20;
                        e.velX = 0f;
                        e.velY = -spd * 1.2f;
                    }
                } else if (e.kamiState == 2) {
                    e.velX *= 0.95f;
                    e.velY = -spd * 1.2f;
                    e.x += e.velX;
                    e.y += e.velY;
                    if (e.y < -150) e.kamiState = 3;
                } else {
                    e.y -= spd * 2f;
                }

            } else {
                if (e.type == AirType.JET) {
                    float spd = e.waveSpd;
                    float ovalCX = e.kamiTargetX;
                    float ovalCY = e.kamiTargetY;
                    float ovalRY = e.waveAmp;
                    float ovalRX = screenWidth * 0.42f;

                    if (e.kamiState == 0) {
                        float entryX = ovalCX + (float)Math.cos(e.waveAmt) * ovalRX;
                        float entryY = ovalCY + (float)Math.sin(e.waveAmt) * ovalRY;
                        float dx = entryX - e.x;
                        float dy = entryY - e.y;
                        float dist = (float)Math.sqrt(dx*dx + dy*dy);
                        if (dist < spd * 2.5f) {
                            e.kamiState = 1;
                            e.x = entryX; e.y = entryY;
                        } else {
                            e.velX += (dx/dist * spd - e.velX) * 0.12f;
                            e.velY += (dy/dist * spd - e.velY) * 0.12f;
                            e.x += e.velX; e.y += e.velY;
                        }
                    } else if (e.kamiState == 1) {
                        float angularSpd = spd / ((ovalRX + ovalRY) / 2f);
                        if (e.kamiSweepRight) {
                            e.waveAmt -= angularSpd;
                        } else {
                            e.waveAmt += angularSpd;
                        }
                        float prevX = e.x, prevY = e.y;
                        e.x = ovalCX + (float)Math.cos(e.waveAmt) * ovalRX;
                        e.y = ovalCY + (float)Math.sin(e.waveAmt) * ovalRY;
                        e.velX = e.x - prevX;
                        e.velY = e.y - prevY;
                        if (e.kamiSweepRight) {
                            if (e.waveAmt < -3.0f * (float)Math.PI) e.kamiState = 2;
                        } else {
                            if (e.waveAmt > 3.0f * (float)Math.PI) e.kamiState = 2;
                        }
                    } else {
                        // Exit phase — slow down and glide out, don't rocket away
                        e.velX *= 0.98f;
                        e.velY *= 0.98f;
                        e.x += e.velX; e.y += e.velY;
                    }
                    float targetAngle = (float)Math.toDegrees(Math.atan2(e.velX, -e.velY)) * 0.4f;
                    e.angle += (targetAngle - e.angle) * 0.15f;
                } else {
                    e.x += e.speedX;
                    e.y += e.speedY;
                    e.velX += (e.speedX - e.velX) * 0.15f;
                    e.velY += (e.speedY - e.velY) * 0.15f;
                    float targetAngle = e.speedX * 4f;
                    e.angle += (targetAngle - e.angle) * 0.08f;

                    // ── S-curve: horizontal sine based on Y position ──────
                    // Creates natural flowing S-path as enemy descends
                    if (e.type == AirType.BOMBER || e.type == AirType.ARACHNID) {
                        float sCurve = (float)Math.sin(e.y / 200.0) * 2.5f;
                        e.x += sCurve;
                        e.x = Math.max(30, Math.min(screenWidth - 30, e.x));
                    }

                    // ── Player-proximity dodge: push away if within 200px ─
                    float pDist = (float)Math.sqrt(
                            Math.pow(e.x - (planeX + 120f), 2) +
                                    Math.pow(e.y - (planeY + 120f), 2));
                    if (pDist < 200f && e.type != AirType.FIGHTER) {
                        float pushDir = e.x < (planeX + 120f) ? -1.8f : 1.8f;
                        e.speedX += pushDir;
                        e.speedX = Math.max(-5f, Math.min(5f, e.speedX));
                    }

                    if (e.type == AirType.DRONE) {
                        e.waveAmt += e.waveSpd;
                        e.speedY = Math.min(e.speedY + 0.015f, 2.0f);
                        float weave = (float)Math.sin(e.waveAmt) * e.waveAmp * 0.08f;
                        e.velX = e.speedX * 0.8f + weave;
                        e.velY = e.speedY;
                        e.x += e.velX;
                        e.y += e.velY;
                        if (e.x < 40)              { e.x = 40;              e.speedX = Math.abs(e.speedX) * 0.6f; }
                        if (e.x > screenWidth - 40) { e.x = screenWidth-40; e.speedX = -Math.abs(e.speedX) * 0.6f; }
                    }
                    if (e.type == AirType.FIGHTER) {
                        // Phase 0: glide in from side to assigned X position
                        // Phase 1: hover at top zone with gentle drift
                        // Phase 2: slowly descend across screen
                        if (e.kamiState == 0) {
                            // Fly to assigned hover X (stored in kamiTargetX)
                            float targetHoverX = e.kamiTargetX > 0 ? e.kamiTargetX
                                    : screenWidth * (0.2f + (e.x / screenWidth) * 0.6f);
                            if (e.kamiTargetX <= 0) e.kamiTargetX = targetHoverX;
                            float dx2 = targetHoverX - e.x;
                            float dy2 = screenHeight * 0.12f - e.y;
                            e.velX = dx2 * 0.06f;
                            e.velY = Math.max(0f, dy2 * 0.06f + 1.5f);
                            e.x += e.velX;
                            e.y += e.velY;
                            // Once near hover position, lock into hover
                            if (Math.abs(dx2) < 20f && e.y <= screenHeight * 0.16f) {
                                e.kamiState = 1;
                                e.waveAmt = random.nextFloat() * (float)Math.PI * 2f;
                                e.kamiEntryX = e.x;
                            }
                        } else if (e.kamiState == 1) {
                            // Hover phase — drift side to side at top of screen
                            e.waveAmt += 0.022f;
                            float hoverRange = screenWidth * 0.18f;
                            float targetX = e.kamiEntryX + (float)Math.sin(e.waveAmt) * hoverRange;
                            e.velX = (targetX - e.x) * 0.08f;
                            e.velY = 0.15f; // very slow downward creep
                            e.x += e.velX;
                            e.y += e.velY;
                            // After drifting below 25% screen, start descent
                            if (e.y > screenHeight * 0.25f) {
                                e.kamiState = 2;
                            }
                        } else {
                            // Descent phase — sweep diagonally down across screen
                            e.waveAmt += 0.028f;
                            e.velX = (float)Math.sin(e.waveAmt) * 6f;
                            e.velY = 0.8f + Math.abs((float)Math.sin(e.waveAmt)) * 0.5f;
                            e.x += e.velX;
                            e.y += e.velY;
                            e.x = Math.max(40, Math.min(screenWidth - 40, e.x));
                        }
                    }
                    if (e.type == AirType.BOMBER) {
                        // Lazy S-curve — wide sweep, slows near centre to take aim
                        e.waveAmt += e.waveSpd > 0 ? e.waveSpd : 0.018f;
                        float sweep = (float)Math.sin(e.waveAmt) * (screenWidth * 0.28f);
                        float centreOffset = Math.abs(e.x - screenWidth / 2f);
                        // Slow down when near centre (aiming behaviour)
                        float ySpeed = centreOffset < screenWidth * 0.15f ? 0.25f : 0.55f;
                        e.x += sweep * 0.06f;
                        e.y += ySpeed;
                        e.velX = sweep * 0.06f;
                        e.velY = ySpeed;
                        e.x = Math.max(60, Math.min(screenWidth - 60, e.x));
                    }
                    if (e.type == AirType.ARACHNID) {
                        float anchorX = e.waveAmp;  // stored anchor X
                        float anchorY = e.waveSpd;  // stored anchor Y
                        if (e.kamiState == 0) {
                            // Fly to anchor position
                            float dx2 = anchorX - e.x;
                            float dy2 = anchorY - e.y;
                            float dist2 = (float)Math.sqrt(dx2*dx2 + dy2*dy2);
                            if (dist2 < 8f) {
                                e.x = anchorX; e.y = anchorY;
                                e.kamiState = 1; // locked
                                e.velX = 0; e.velY = 0;
                                playSound(sndArachnidEngine, 0.6f, 0.5f + random.nextFloat() * 0.1f);
                            } else {
                                float spd2 = Math.min(dist2, 7f);
                                e.x += dx2/dist2 * spd2;
                                e.y += dy2/dist2 * spd2;
                                e.velX = dx2/dist2 * spd2;
                                e.velY = dy2/dist2 * spd2;
                            }
                        } else {
                            // Locked — hover with tiny oscillation, no drift
                            e.x = anchorX + (float)Math.sin(frameCount * 0.04f + e.waveAmt * 3) * 4f;
                            e.y = anchorY + (float)Math.sin(frameCount * 0.03f + e.waveAmt * 2) * 3f;
                            e.velX = 0; e.velY = 0;
                        }
                        e.speedY = 0; // never drift downward
                    }
                    if (e.type == AirType.GUNSHIP) {
                        e.waveAmt += e.waveSpd;
                        float targetY = screenHeight * 0.28f;
                        if (e.y < targetY) {
                            e.y = Math.min(e.y + 2.5f, targetY);
                        } else {
                            e.y = targetY;
                            e.speedY = 0f;
                        }
                        e.x += (float)Math.cos(e.waveAmt) * e.waveAmp * e.waveSpd * 60f;
                        e.x = Math.max(80, Math.min(screenWidth - 80, e.x));
                        e.velX = (float)Math.cos(e.waveAmt);
                        e.velY = 0f;
                    }
                }
            }

            if (e.type == AirType.JET) {
                // Only start escape timer after all enemies in this wave have spawned
                if (e.kamiState == 1 && e.escapeTimer < 0 && waveSpawnFinished) {
                    e.escapeTimer = 1800; // 30 sec after spawn finishes before retreating
                }
                if (e.escapeTimer > 0 && e.escapeTimer != 999) e.escapeTimer--;
                if (e.escapeTimer == 0) {
                    e.kamiState = 2;
                    e.speedY = -2f;
                    e.escapeTimer = 999; // sentinel
                }
                // Remove only when fully off-screen in exit phase
                if (e.kamiState == 2 &&
                        (e.x < -400 || e.x > screenWidth+400 || e.y < -400 || e.y > screenHeight+400)) {
                    airEnemies.remove(i); continue;
                }
            } else if (e.type == AirType.GUNSHIP) {
                // Gunships get 20 seconds at position before retreating upward
                if (e.kamiState == 0 && e.y >= screenHeight * 0.28f - 5f && e.escapeTimer < 0) {
                    e.escapeTimer = 1200; // 20 seconds
                }
                if (e.escapeTimer > 0 && e.escapeTimer != 999) e.escapeTimer--;
                if (e.escapeTimer == 0) {
                    // Visible retreat — fly upward
                    e.speedY = -2f;
                    addFloating(e.x, e.y, "GUNSHIP RETREATING", Color.parseColor("#aaaaaa"));
                    e.escapeTimer = 999; // sentinel
                }
                // Remove when fully off top
                if (e.escapeTimer == 999 && e.y < -150) {
                    airEnemies.remove(i); continue;
                }
            } else if (e.type == AirType.STARSPARROW) {
                // ── StarSparrow: Heavy Tactical Unit ─────────────────────
                // kamiState: 0=entry  1=positioning  2=hover+strafe  3=attack  4=exit
                // velX/velY = current velocity, kamiTargetX = target lane, waveAmt = phase timer
                float spMult = getMapSpeedMultiplier();

                if (e.kamiState == 0) {
                    // Phase 0: straight down entry
                    e.velX = 0;
                    e.velY = 2.2f * spMult;
                    e.y += e.velY;
                    if (e.y > screenHeight * 0.28f) {
                        // Assign a hover lane
                        int lane = (int)(e.kamiTargetX) % 4; // encoded at spawn
                        e.kamiTargetX = screenWidth * (0.18f + lane * 0.21f);
                        e.kamiState = 1;
                        e.waveAmt = 0;
                    }
                } else if (e.kamiState == 1) {
                    // Phase 1: slide to lane
                    float dx = e.kamiTargetX - e.x;
                    e.velX = dx * 0.06f;
                    e.velY = 0.8f * spMult;
                    e.x += e.velX;
                    e.y += e.velY;
                    e.waveAmt++;
                    if (Math.abs(dx) < 12f || e.waveAmt > 80) {
                        e.y = Math.min(e.y, screenHeight * 0.35f);
                        e.kamiState = 2;
                        e.waveAmt = 0;
                    }
                } else if (e.kamiState == 2) {
                    // Phase 2: hover + slow strafe left-right
                    float strafe = (float)Math.sin(frameCount * 0.028f + e.kamiEntryX) * 2.0f * spMult;
                    e.velX = strafe;
                    e.velY = (float)Math.sin(frameCount * 0.015f) * 0.4f; // gentle bob
                    e.x += e.velX;
                    e.y += e.velY;
                    e.x = Math.max(60, Math.min(screenWidth - 60, e.x));
                    e.waveAmt++;
                    if (e.waveAmt > 220) { // hover ~3.6s then escalate
                        e.kamiState = 3;
                        e.waveAmt = 0;
                    }
                } else if (e.kamiState == 3) {
                    // Phase 3: aggressive strafe — faster, aimed
                    float strafe = (float)Math.sin(frameCount * 0.055f + e.kamiEntryX) * 3.5f * spMult;
                    float dxP = (planeX + 120f) - e.x;
                    e.velX = strafe + dxP * 0.012f; // slight player tracking
                    e.velY = (float)Math.sin(frameCount * 0.02f) * 0.6f;
                    e.x += e.velX;
                    e.y += e.velY;
                    e.x = Math.max(50, Math.min(screenWidth - 50, e.x));
                    e.waveAmt++;
                    if (e.waveAmt > 160) { // retreat after ~2.7s
                        e.kamiState = 4;
                        e.waveAmt = 0;
                        e.velY = -3.5f;
                    }
                } else {
                    // Phase 4: exit upward
                    e.velY -= 0.18f; // accelerate upward
                    e.y += e.velY;
                    if (e.y < -120) { airEnemies.remove(i); continue; }
                }
            } else if (e.type == AirType.SWITCHBLADE) {
                // ── SwitchBlade: Fast Assassin ────────────────────────────
                // kamiState: 0=diagonal entry  1=target lock  2=dash attack  3=overshoot+exit
                float spMult = getMapSpeedMultiplier();
                float px = planeX + 120f, py = planeY + 120f;

                if (e.kamiState == 0) {
                    // Phase 0: diagonal entry from random side
                    e.x += e.velX * spMult;
                    e.y += e.velY * spMult;
                    // Enter at ~15% screen height then lock on
                    if (e.y > screenHeight * 0.15f) {
                        e.kamiState = 1;
                        e.waveAmt = 0;
                    }
                } else if (e.kamiState == 1) {
                    // Phase 1: target lock — smoothly aim toward player
                    float dx = px - e.x, dy = py - e.y;
                    float len = (float)Math.sqrt(dx*dx + dy*dy);
                    if (len > 0.1f) {
                        float targetVx = dx / len * 5.5f * spMult;
                        float targetVy = dy / len * 5.5f * spMult;
                        e.velX += (targetVx - e.velX) * 0.08f;
                        e.velY += (targetVy - e.velY) * 0.08f;
                    }
                    e.x += e.velX;
                    e.y += e.velY;
                    // Keep on screen during lock-on
                    e.x = Math.max(40, Math.min(screenWidth - 40, e.x));
                    e.waveAmt++;
                    // After 1.5s of tracking, dash
                    if (e.waveAmt > 90) {
                        e.kamiState = 2;
                        e.waveAmt = 0;
                        // Snapshot direction to player and double speed
                        float dx2 = px - e.x, dy2 = py - e.y;
                        float len2 = (float)Math.sqrt(dx2*dx2+dy2*dy2);
                        if (len2 > 0.1f) {
                            e.velX = dx2 / len2 * 11f * spMult;
                            e.velY = dy2 / len2 * 11f * spMult;
                        }
                    }
                } else if (e.kamiState == 2) {
                    // Phase 2: dash — fixed direction, high speed
                    e.x += e.velX;
                    e.y += e.velY;
                    e.waveAmt++;
                    if (e.waveAmt > 35) { // short dash window
                        e.kamiState = 3;
                    }
                } else {
                    // Phase 3: overshoot — decelerate, gravity curve, exit off-screen
                    e.velX *= 0.93f;
                    e.velY += 0.25f; // gravity arc
                    e.x += e.velX;
                    e.y += e.velY;
                    if (e.y > screenHeight + 150 || e.x < -300 || e.x > screenWidth + 300) {
                        airEnemies.remove(i); continue;
                    }
                }
            } else if (e.type == AirType.FIGHTER) {
                // ── SciFighter: Precision Assault Unit ───────────────────
                // kamiState: 0=diagonal entry  1=attack pass  2=lateral exit
                // velX/velY = current velocity, kamiTargetX = locked player X

                if (e.kamiState == 0) {
                    // Phase 0: diagonal entry from side
                    e.x += e.velX;
                    e.y += e.velY;
                    if (e.y > screenHeight * 0.25f) {
                        e.kamiState = 1;
                        e.kamiTargetX = planeX + 120f; // lock player position
                        e.shootTimer = Math.max(e.shootTimer, 25); // allow fire soon
                    }
                } else if (e.kamiState == 1) {
                    // Phase 1: attack pass — smooth alignment + slow descent
                    float dx = e.kamiTargetX - e.x;
                    e.velX += dx * 0.022f;
                    e.velX = Math.max(-7f, Math.min(7f, e.velX)); // cap lateral speed
                    e.velY = 2.5f;
                    e.x += e.velX;
                    e.y += e.velY;
                    if (e.y > screenHeight * 0.52f) {
                        e.kamiState = 2;
                        // Bank hard away from the side it came from
                        e.velX = e.kamiEntryX > 0 ? 6.5f : -6.5f; // kamiEntryX = fromLeft flag
                        e.velY = 4f;
                    }
                } else {
                    // Phase 2: lateral exit — banks away fast
                    e.x += e.velX;
                    e.y += e.velY;
                    if (e.x < -220 || e.x > screenWidth + 220 || e.y > screenHeight + 200) {
                        airEnemies.remove(i); continue;
                    }
                }
                // Update velX/velY for directional sprite
                e.angle = (float)Math.toDegrees(Math.atan2(e.velY, e.velX));
            } else if (e.type != AirType.KAMIKAZE
                    && e.type != AirType.ARACHNID && e.type != AirType.DRONE) {
                if (e.x < 35 || e.x > screenWidth-35) e.speedX *= -1f;
            }

            // ── Wander + Bullet-dodge reaction ────────────────────────────────
            // Only for enemies that use speedX/speedY movement (not special state machines)
            if (e.type == AirType.BOMBER || e.type == AirType.DRONE
                    || e.type == AirType.GUNSHIP) {

                // Wander — gentle sine oscillation so enemies aren't robotically straight
                // Each enemy has a unique phase via waveAmt seed baked at spawn
                float wanderAmt = (float)Math.cos(frameCount * 0.022f + e.kamiEntryX * 3.7f) * 0.55f;
                e.speedX += (wanderAmt - e.speedX) * 0.04f; // smooth toward wander target
                e.speedX = Math.max(-2.5f, Math.min(2.5f, e.speedX));
            }

            // Bullet reaction — ALL non-kamikaze, non-arachnid enemies dodge incoming bullets
            // Shifts speedX strongly away from bullet — creates "trying to escape" illusion
            if (e.type != AirType.KAMIKAZE && e.type != AirType.ARACHNID
                    && e.type != AirType.JET   // jet has its own orbital path
                    && e.type != AirType.STARSPARROW && e.type != AirType.SWITCHBLADE) {
                for (PlayerBullet b : bullets) {
                    float bdx = b.x - e.x, bdy = b.y - e.y;
                    float distSq = bdx*bdx + bdy*bdy;
                    if (distSq < 70f*70f && bdy < 0) { // bullet within 70px, heading upward
                        // Shift speedX 50 units in opposite direction of bullet's X offset
                        float dodgeDir = bdx > 0 ? -1f : 1f;
                        e.speedX += dodgeDir * 1.8f;
                        e.speedX = Math.max(-4f, Math.min(4f, e.speedX));
                        // Also briefly boost upward to enhance dodge feel
                        e.speedY = Math.max(e.speedY - 0.4f, -1.5f);
                        break;
                    }
                }
                // Decay dodge: gradually return speedX toward 0 so enemy doesn't fly off screen
                if (Math.abs(e.speedX) > 0.8f) e.speedX *= 0.94f;
            }

            if (e.type == AirType.DRONE && e.shootTimer == 9999 && e.y > 80) {
                e.shootTimer = 30 + random.nextInt(40);
            }

            if (e.type != AirType.KAMIKAZE) {
                e.shootTimer--;
                if (e.shootTimer <= 0) {
                    fireEnemyShot(e);
                }
            }

            float er = e.type == AirType.GUNSHIP ? 55f : e.type == AirType.BOMBER ? 45f : 28f;
            if (RectF.intersects(planeRect,
                    new RectF(e.x-er, e.y-er, e.x+er, e.y+er))) {
                damagePlayer(e.type == AirType.KAMIKAZE ? 20f : e.damage * 0.5f);
                screenFlashAlpha = 55f;
                screenFlashColor = Color.parseColor("#ff2200");
                addExplosion(e.x, e.y, e.type==AirType.KAMIKAZE ? 90 : 45);
                playSound(sndExplosion, 0.9f, e.type==AirType.KAMIKAZE ? 0.75f : 1.0f);
                if (e.type == AirType.KAMIKAZE) {
                    airEnemies.remove(i); continue;
                } else if (e.type == AirType.ARACHNID) {
                    // Arachnids are anchored — don't bounce them
                } else {
                    e.y -= 40f;
                    if (e.type == AirType.GUNSHIP) e.y = Math.min(e.y, screenHeight * 0.28f);
                }
            }
            if (e.type == AirType.KAMIKAZE && e.kamiState == 3) {
                airEnemies.remove(i); continue;
            }
            if (e.y > screenHeight + 140
                    && e.type != AirType.GUNSHIP
                    && e.type != AirType.ARACHNID
                    && e.type != AirType.FIGHTER
                    && e.type != AirType.JET) {
                waveEnemiesAlive = Math.max(0, waveEnemiesAlive - 1); // escaped — count as gone
                airEnemies.remove(i); continue;
            }
            // Apply map passive forces — not to retreating or already-handled types
            if (e.escapeTimer <= 0 || e.escapeTimer == -1) {
                if (mapWindX != 0f && e.type != AirType.ARACHNID) {
                    e.x += mapWindX;
                    e.x = Math.max(20, Math.min(screenWidth - 20, e.x)); // keep on screen
                }
                if (mapBobY != 0f && e.type != AirType.ARACHNID && e.type != AirType.GUNSHIP) {
                    e.y += mapBobY;
                }
            }
            drawAirEnemy(canvas, e);
        }
    }

    // ===================== ENEMY FIRING =====================
    private void fireEnemyShot(AirEnemy e) {
        float mult = getFireRateMultiplier();

        if (enemySoundThrottle <= 0) {
            int snd;
            float vol = 0.5f;
            float pitch = 0.8f + random.nextFloat() * 0.4f;
            switch (e.type) {
                case DRONE:    snd = sndDroneShoot;   vol = 0.6f;                       break;
                case JET:      snd = sndJetFire;      vol = 0.55f;                      break;
                case BOMBER:   snd = sndBomberFire;   vol = 0.85f;                      break;
                case FIGHTER:     snd = sndFighterGun;   vol = 0.65f; pitch = 0.9f + random.nextFloat()*0.2f; break;
                case STARSPARROW: snd = sndFighterGun;   vol = 0.7f;  pitch = 1.05f + random.nextFloat()*0.15f; break;
                case SWITCHBLADE:  snd = sndJetFire;    vol = 0.75f; pitch = 1.1f  + random.nextFloat()*0.1f;  break;
                case GUNSHIP:  snd = sndHeliMissile;  vol = 0.80f; pitch = 0.85f + random.nextFloat()*0.15f; break;
                case ARACHNID: snd = sndBossSpiral;   vol = 0.75f; pitch = 0.28f + random.nextFloat()*0.08f; break;
                default:       snd = sndEnemyShoot;   break;
            }
            playSound(snd, vol, pitch);
            enemySoundThrottle = 12;
        }

        switch (e.type) {
            case SWITCHBLADE: {
                // Fast single teal plasma bolt aimed at player
                e.shootTimer = Math.max(45, (int)(90 / mult));
                float dx3 = (planeX+120f) - e.x, dy3 = (planeY+120f) - e.y;
                float d3  = (float)Math.sqrt(dx3*dx3+dy3*dy3);
                if (d3 > 0.001f) {
                    float spd3 = 9f * getMapBulletSpeedMultiplier();
                    enemyBullets.add(new EnemyBullet(e.x, e.y+10,
                            dx3/d3*spd3, dy3/d3*spd3, 9, e.damage));
                }
                break;
            }
            case STARSPARROW: {
                // Twin amber shots aimed at player, slight spread
                e.shootTimer = Math.max(55, (int)(110 / mult));
                float dx2 = (planeX+120f) - e.x, dy2 = (planeY+120f) - e.y;
                float d2  = (float)Math.sqrt(dx2*dx2+dy2*dy2);
                if (d2 > 0.001f) {
                    float spd2 = 7.5f * getMapBulletSpeedMultiplier();
                    // type 11 = amber (fighter bullet colour — reuse)
                    enemyBullets.add(new EnemyBullet(e.x-14, e.y+10,
                            dx2/d2*spd2 - 0.5f, dy2/d2*spd2, 11, e.damage));
                    enemyBullets.add(new EnemyBullet(e.x+14, e.y+10,
                            dx2/d2*spd2 + 0.5f, dy2/d2*spd2, 11, e.damage));
                }
                break;
            }
            case BOMBER: {
                e.shootTimer = Math.max(80, (int)(160 / mult));
                // type 8 = bomber — deep red heavy shells
                enemyBullets.add(new EnemyBullet(e.x-18, e.y+24, -0.3f, 4f, 8, 36f));
                enemyBullets.add(new EnemyBullet(e.x+18, e.y+24,  0.3f, 4f, 8, 36f));
                break;
            }
            case JET: {
                boolean isSecondJetWave = totalWavesCompleted > 9;
                float jetMult = isSecondJetWave ? mult * 1.4f : mult * 0.7f;
                e.shootTimer = Math.max(34, (int)(70 / jetMult));
                float dx = (planeX+120f) - e.x;
                float dy = (planeY+120f) - e.y;
                float d  = (float)Math.sqrt(dx*dx+dy*dy);
                if (d > 0.001f) {
                    float bulletDmg = isSecondJetWave ? 21f : 14f;
                    float bulletSpd = isSecondJetWave ? 10f : 8f;
                    // type 9 = jet — electric blue plasma
                    enemyBullets.add(new EnemyBullet(e.x, e.y+10,
                            dx/d*bulletSpd, dy/d*bulletSpd, 9, bulletDmg));
                }
                break;
            }
            case DRONE: {
                e.shootTimer = Math.max(140, (int)(260 / mult));
                float dx = (planeX+120f) - e.x;
                float dy = (planeY+120f) - e.y;
                float d  = (float)Math.sqrt(dx*dx+dy*dy);
                if (d > 0.001f)
                    // type 1 = drone — cyan laser dot (keep existing)
                    enemyBullets.add(new EnemyBullet(e.x, e.y+10,
                            dx/d*6f, dy/d*6f, 1, 8f));
                break;
            }
            case ARACHNID: {
                e.shootTimer = Math.max(140, (int)(280 / mult));
                // Only fire when locked in position
                if (e.kamiState != 1) break;
                // Fire in nose direction — left side nose points SE (bottom-right), right side SW (bottom-left)
                // leftSide: waveAmt < 0.5 → rotation=-45 → nose points SE
                // rightSide: waveAmt >= 0.5 → rotation=+45 → nose points SW
                boolean leftSide2 = e.waveAmt < 0.5f;
                // SE = 135° from N = atan2(+x,+y). SW = 225° from N = atan2(-x,+y)
                // In standard math angles: SE = 45° below horizontal right = Math.PI/4 below +x
                float baseAngle = leftSide2
                        ? (float)(Math.PI * 0.25f)    // SE: dx=+, dy=+ at 45°
                        : (float)(Math.PI * 0.75f);   // SW: dx=-, dy=+ at 135°
                float spd = 6f;
                // 3 bullets in a line perpendicular to firing direction
                float perpAngle = baseAngle + (float)(Math.PI * 0.5f);
                float gap = 24f;
                for (int s = -1; s <= 1; s++) {
                    float ox = (float)Math.cos(perpAngle) * s * gap;
                    float oy = (float)Math.sin(perpAngle) * s * gap;
                    enemyBullets.add(new EnemyBullet(
                            e.x + ox, e.y + oy,
                            (float)Math.cos(baseAngle) * spd,
                            (float)Math.sin(baseAngle) * spd,
                            12, 16f));
                }
                playSound(sndBossSpiral, 0.7f, 0.3f + random.nextFloat() * 0.1f); // low ultrasonic
                break;
            }
            case GUNSHIP: {
                e.shootTimer = Math.max(170, (int)(340 / mult));
                float dx = (planeX+120f) - e.x;
                float dy = (planeY+120f) - e.y;
                float d  = (float)Math.sqrt(dx*dx+dy*dy);
                if (d > 0.001f) {
                    for (int m = -1; m <= 1; m += 2) {
                        float spread = m * 0.15f;
                        float ang = (float)Math.atan2(dy, dx) + spread;
                        // type 7 = gunship guided missile (keep existing rocket shape)
                        enemyBullets.add(new EnemyBullet(
                                e.x + m * 20, e.y + 30,
                                (float)Math.cos(ang) * 3.5f,
                                (float)Math.sin(ang) * 3.5f,
                                7, 31f));
                    }
                    playSound(sndHeliMissile, 0.7f, 0.9f + random.nextFloat() * 0.2f);
                }
                break;
            }
            default: {  // FIGHTER — 3-shot spread burst, only during attack pass
                if (e.type == AirType.FIGHTER && e.kamiState != 1) {
                    // Don't fire during entry or exit
                    e.shootTimer = Math.max(e.shootTimer, 30);
                    break;
                }
                e.shootTimer = Math.max(38, (int)(75 / mult));
                float bspd = 6.5f * getMapBulletSpeedMultiplier();
                // 3-shot spread straight down — clean precision burst
                enemyBullets.add(new EnemyBullet(e.x,      e.y + 12, 0f,    bspd, 11, e.damage));
                enemyBullets.add(new EnemyBullet(e.x - 14, e.y + 12, -1.5f, bspd, 11, e.damage));
                enemyBullets.add(new EnemyBullet(e.x + 14, e.y + 12,  1.5f, bspd, 11, e.damage));
                break;
            }
        }
    }

    private void drawAirEnemy(Canvas canvas, AirEnemy e) {
        if (e.hitFlash > 0) e.hitFlash--;

        // ── White hit flash via SRC_IN — paints enemy fully white on hit ──
        if (e.hitFlash > 4) { // first few frames = full white
            bitmapPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
        }

        switch (e.type) {
            case FIGHTER:  drawFighter(canvas, e.x, e.y, e.hp, e.maxHp);  break;
            case BOMBER:   drawBomber(canvas, e.x, e.y, e.hp, e.maxHp);   break;
            case JET:      drawJet(canvas, e.x, e.y);                      break;
            case KAMIKAZE: drawKamikaze(canvas, e.x, e.y);                 break;
            case DRONE:    drawDrone(canvas, e.x, e.y);                    break;
            case ARACHNID: drawArachnid(canvas, e.x, e.y, e.hp, e.maxHp); break;
            case GUNSHIP:     drawGunship(canvas, e.x, e.y, e.hp, e.maxHp);     break;
            case STARSPARROW:
                e.dirVelX += (e.velX - e.dirVelX) * 0.12f;
                e.dirVelY += (e.velY - e.dirVelY) * 0.12f;
                drawStarSparrow(canvas, e.x, e.y, e.dirVelX, e.dirVelY); break;
            case SWITCHBLADE:
                e.dirVelX += (e.velX - e.dirVelX) * 0.14f;
                e.dirVelY += (e.velY - e.dirVelY) * 0.14f;
                drawSwitchBlade(canvas, e.x, e.y, e.dirVelX, e.dirVelY); break;
            default:
                paint.setColor(e.hitFlash > 4
                        ? Color.WHITE
                        : Color.argb(200, 255, 100, 0));
                canvas.drawCircle(e.x, e.y, 30, paint);
                break;
        }

        // Clear filter immediately after drawing — never leaks to other draws
        bitmapPaint.setColorFilter(null);
    }

    private Bitmap getArachnidFrame(float velX, float velY) {
        if (arachnidDirs[0] == null) return null;
        double angleDeg = Math.toDegrees(Math.atan2(velX, -velY));
        if (angleDeg < 0) angleDeg += 360;
        int sector = (int)((angleDeg + 22.5) / 45.0) % 8;
        int[] map = {4, 5, 6, 7, 0, 1, 2, 3};
        return arachnidDirs[map[sector]];
    }

    // SciFighter direction mapping — same convention as Arachnid/Crossbow:
    // dir1=S(down), dir2=SW, dir3=W, dir4=NW, dir5=N(up), dir6=NE, dir7=E, dir8=SE
    private Bitmap getFighterFrame(float velX, float velY) {
        if (sciFighterDirs[0] == null) return null;
        // dir1=S(down), dir2=SW, dir3=W(left), dir4=NW, dir5=N(up), dir6=NE, dir7=E(right), dir8=SE
        // atan2(velX, -velY): angle from North, clockwise
        double angleDeg = Math.toDegrees(Math.atan2(velX, -velY));
        if (angleDeg < 0) angleDeg += 360;
        // Offset +22.5 to centre each 45° sector, then map to dir index
        int sector = (int)((angleDeg + 22.5) / 45.0) % 8;
        // sector 0=N→dir5(idx4), 1=NE→dir6(idx5), 2=E→dir7(idx6), 3=SE→dir8(idx7)
        //        4=S→dir1(idx0), 5=SW→dir2(idx1), 6=W→dir3(idx2), 7=NW→dir4(idx3)
        int[] map = {4, 5, 6, 7, 0, 1, 2, 3};
        return sciFighterDirs[map[sector]];
    }

    private void drawFighter(Canvas canvas, float x, float y, int hp, int maxHp) {
        // Find this enemy to get velocity for directional sprite
        AirEnemy thisEnemy = null;
        for (AirEnemy e : airEnemies) {
            if (e.type == AirType.FIGHTER && Math.abs(e.x-x) < 12f && Math.abs(e.y-y) < 12f) { thisEnemy = e; break; }
        }
        float vx = thisEnemy != null ? thisEnemy.velX : 0f;
        float vy = thisEnemy != null ? thisEnemy.velY : 4f;
        Bitmap frame = getFighterFrame(vx, vy);
        if (frame == null && sciFighterDirs[0] != null) frame = sciFighterDirs[0];
        if (frame != null) {
            float hw = frame.getWidth()/2f, hh = frame.getHeight()/2f;
            canvas.drawBitmap(frame, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            if (hp < maxHp) drawHpBar(canvas, x-hw, y+hh+4, hw*2, 4, hp, maxHp);
            return;
        }
        // Fallback canvas shape
        float s = 1.4f;
        paint.setColor(Color.parseColor("#3a3d4a"));
        Path body = new Path();
        body.moveTo(x, y+36*s); body.lineTo(x+10*s, y+14*s);
        body.lineTo(x+8*s, y-2*s); body.lineTo(x, y+4*s);
        body.lineTo(x-8*s, y-2*s); body.lineTo(x-10*s, y+14*s);
        body.close(); canvas.drawPath(body, paint);
        if (hp < maxHp) drawHpBar(canvas, x-20*s, y+42*s, 40*s, 4, hp, maxHp);
    }

    private void drawBomber(Canvas canvas, float x, float y, int hp, int maxHp) {
        if (spriteBomber != null) {
            float hw = spriteBomber.getWidth()/2f, hh = spriteBomber.getHeight()/2f;
            float angle = 0f;
            for (AirEnemy e : airEnemies) { if (e.type==AirType.BOMBER && Math.abs(e.x-x)<2f) { angle=e.angle; break; } }
            canvas.save(); canvas.rotate(angle, x, y);
            canvas.drawBitmap(spriteBomber, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            canvas.restore();
            if (hp < maxHp) drawHpBar(canvas, x-hw, y+hh+4, hw*2, 5, hp, maxHp);
            return;
        }
        float s = 1.4f;
        paint.setColor(Color.parseColor("#3a4455"));
        Path body = new Path();
        body.moveTo(x,y+38*s); body.lineTo(x+15*s,y+10*s);
        body.lineTo(x+13*s,y-10*s); body.lineTo(x,y-2*s);
        body.lineTo(x-13*s,y-10*s); body.lineTo(x-15*s,y+10*s);
        body.close(); canvas.drawPath(body,paint);
        if (hp < maxHp) drawHpBar(canvas, x-36*s, y+42*s, 72*s, 5, hp, maxHp);
    }

    private Bitmap getCrossbowFrame(float velX, float velY) {
        if (crossbowDirs[0] == null) return null;
        double angleDeg = Math.toDegrees(Math.atan2(velX, -velY));
        if (angleDeg < 0) angleDeg += 360;
        int sector = (int)((angleDeg + 22.5) / 45.0) % 8;
        int[] map = {4, 5, 6, 7, 0, 1, 2, 3};
        return crossbowDirs[map[sector]];
    }

    private void drawJet(Canvas canvas, float x, float y) {
        AirEnemy thisEnemy = null;
        for (AirEnemy e : airEnemies) {
            if (e.type == AirType.JET && Math.abs(e.x - x) < 2f) { thisEnemy = e; break; }
        }
        Bitmap frame = (thisEnemy != null) ? getCrossbowFrame(thisEnemy.velX, thisEnemy.velY) : null;
        if (frame != null) {
            float hw = frame.getWidth()/2f, hh = frame.getHeight()/2f;
            canvas.drawBitmap(frame, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            return;
        }
        if (spriteJet != null) {
            float hw = spriteJet.getWidth()/2f, hh = spriteJet.getHeight()/2f;
            float angle = thisEnemy != null ? thisEnemy.angle : 0f;
            canvas.save(); canvas.rotate(angle, x, y);
            canvas.drawBitmap(spriteJet, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            canvas.restore();
            return;
        }
        float s = 1.4f;
        paint.setColor(Color.parseColor("#1a1a2e"));
        Path body = new Path();
        body.moveTo(x,y+52*s); body.lineTo(x+7*s,y+20*s);
        body.lineTo(x+6*s,y-4*s); body.lineTo(x,y+2*s);
        body.lineTo(x-6*s,y-4*s); body.lineTo(x-7*s,y+20*s);
        body.close(); canvas.drawPath(body,paint);
    }

    private Bitmap getKamikazeFrame(float velX, float velY) {
        if (kamikazeDirs[0] == null) return null;
        double angleDeg = Math.toDegrees(Math.atan2(velX, -velY));
        if (angleDeg < 0) angleDeg += 360;
        int sector = (int)((angleDeg + 22.5) / 45.0) % 8;
        return kamikazeDirs[sector];
    }

    /** Get the correct dir sprite based on velocity angle (same mapping as kamikaze) */
    private Bitmap getStarSparrowFrame(float velX, float velY) {
        if (starSparrowDirs[0] == null) return null;
        // dir1=up-left, dir2=up, dir3=up-right, dir4=right
        // dir5=down-right, dir6=down, dir7=down-left, dir8=left
        double angle = Math.toDegrees(Math.atan2(velY, velX));
        if (angle < 0) angle += 360;
        int sector = (int)((angle + 22.5) / 45.0) % 8;
        // sector: 0=E, 1=SE, 2=S, 3=SW, 4=W, 5=NW, 6=N, 7=NE
        int[] map = {3, 4, 5, 6, 7, 0, 1, 2}; // → dir4,dir5,dir6,dir7,dir8,dir1,dir2,dir3
        return starSparrowDirs[map[sector]];
    }

    private Bitmap getSwitchBladeFrame(float velX, float velY) {
        if (switchBladeDirs[0] == null) return null;
        // Same direction layout as StarSparrow
        double angle = Math.toDegrees(Math.atan2(velY, velX));
        if (angle < 0) angle += 360;
        int sector = (int)((angle + 22.5) / 45.0) % 8;
        int[] map = {3, 4, 5, 6, 7, 0, 1, 2};
        return switchBladeDirs[map[sector]];
    }

    private void drawSwitchBlade(Canvas canvas, float x, float y, float velX, float velY) {
        Bitmap frame = getSwitchBladeFrame(velX, velY);
        if (frame == null) {
            paint.setColor(Color.argb(220, 0, 200, 255));
            canvas.drawCircle(x, y, 38, paint);
            return;
        }
        float hw = frame.getWidth() * 0.5f, hh = frame.getHeight() * 0.5f;
        canvas.drawBitmap(frame, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
    }

    private void drawStarSparrow(Canvas canvas, float x, float y, float velX, float velY) {
        Bitmap frame = getStarSparrowFrame(velX, velY);
        if (frame == null) {
            // Fallback: orange circle
            paint.setColor(Color.argb(220, 255, 80, 20));
            canvas.drawCircle(x, y, 38, paint);
            return;
        }
        float hw = frame.getWidth() * 0.5f, hh = frame.getHeight() * 0.5f;
        canvas.drawBitmap(frame, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
    }

    private void drawKamikaze(Canvas canvas, float x, float y) {
        AirEnemy thisEnemy = null;
        for (AirEnemy e : airEnemies) {
            if (e.type == AirType.KAMIKAZE && Math.abs(e.x - x) < 2f) { thisEnemy = e; break; }
        }
        float vx = thisEnemy != null ? thisEnemy.velX : 0f;
        float vy = thisEnemy != null ? thisEnemy.velY : 2f;
        Bitmap frame = getKamikazeFrame(vx, vy);
        if (frame != null) {
            float hw = frame.getWidth()/2f, hh = frame.getHeight()/2f;
            canvas.drawBitmap(frame, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            return;
        }
        Bitmap ks = (thisEnemy != null && !thisEnemy.kamiSweepRight) ? spriteKamikazeR : spriteKamikazeL;
        if (ks != null) {
            float hw = ks.getWidth()/2f, hh = ks.getHeight()/2f;
            canvas.drawBitmap(ks, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            return;
        }
        float s = 1.3f;
        paint.setColor(Color.parseColor("#4a5a38"));
        Path body = new Path();
        body.moveTo(x, y + 38*s); body.lineTo(x + 7*s, y + 20*s);
        body.lineTo(x + 8*s, y - 2*s); body.lineTo(x + 5*s, y - 18*s);
        body.lineTo(x, y - 22*s); body.lineTo(x - 5*s, y - 18*s);
        body.lineTo(x - 8*s, y - 2*s); body.lineTo(x - 7*s, y + 20*s);
        body.close();
        canvas.drawPath(body, paint);
    }

    private void drawGunship(Canvas canvas, float x, float y, int hp, int maxHp) {
        if (spriteGunship != null) {
            float hw = spriteGunship.getWidth()/2f, hh = spriteGunship.getHeight()/2f;
            canvas.drawBitmap(spriteGunship, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            paint.setColor(Color.argb(200, 255, 100, 0));
            canvas.drawCircle(x - hw * 0.6f, y + hh * 0.3f, 8, paint);
            canvas.drawCircle(x + hw * 0.6f, y + hh * 0.3f, 8, paint);
            paint.setColor(Color.argb(255, 255, 200, 0));
            canvas.drawCircle(x - hw * 0.6f, y + hh * 0.3f, 4, paint);
            canvas.drawCircle(x + hw * 0.6f, y + hh * 0.3f, 4, paint);
            drawHpBar(canvas, x - hw, y + hh + 6, hw * 2, 8, hp, maxHp);
            return;
        }
        paint.setColor(Color.parseColor("#334455"));
        canvas.drawRoundRect(new RectF(x-55, y-30, x+55, y+30), 8, 8, paint);
        paint.setColor(Color.argb(200, 255, 100, 0));
        canvas.drawCircle(x-40, y+10, 10, paint);
        canvas.drawCircle(x+40, y+10, 10, paint);
        drawHpBar(canvas, x-55, y+36, 110, 8, hp, maxHp);
    }

    private void drawArachnid(Canvas canvas, float x, float y, int hp, int maxHp) {
        AirEnemy thisEnemy = null;
        for (AirEnemy e : airEnemies) {
            if (e.type == AirType.ARACHNID && Math.abs(e.x - x) < 4f) { thisEnemy = e; break; }
        }
        // Left side → rotated 45° (NW→SE nose), Right side → rotated -45° (NE→SW nose)
        boolean leftSide = thisEnemy == null || thisEnemy.waveAmt < 0.5f;
        float rotation = leftSide ? -45f : 45f;  // dir2↔dir8 swapped: left→SE nose, right→SW nose

        Bitmap frame = arachnidDirs[0] != null ? arachnidDirs[0] : null;
        if (frame != null) {
            float hw = frame.getWidth()/2f, hh = frame.getHeight()/2f;
            canvas.save();
            canvas.rotate(rotation, x, y);
            canvas.drawBitmap(frame, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            canvas.restore();
            if (hp < maxHp) drawHpBar(canvas, x-hw, y+hh+4, hw*2, 4, hp, maxHp);
            return;
        }
        // Canvas fallback — draw as rotated cross
        canvas.save();
        canvas.rotate(rotation, x, y);
        paint.setColor(Color.parseColor("#8800cc"));
        canvas.drawCircle(x, y, 35, paint);
        paint.setColor(Color.parseColor("#cc44ff"));
        canvas.drawCircle(x, y, 18, paint);
        canvas.restore();
    }

    private void drawDrone(Canvas canvas, float x, float y) {
        if (spriteFighter != null) {
            float hw = spriteFighter.getWidth()/2f, hh = spriteFighter.getHeight()/2f;
            canvas.drawBitmap(spriteFighter, null, new RectF(x-hw, y-hh, x+hw, y+hh), bitmapPaint);
            return;
        }
        paint.setColor(Color.argb(220, 0, 200, 255));
        canvas.drawCircle(x, y, 40, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, 20, paint);
    }

    // ── Boss part hitboxes ────────────────────────────────────────────
    private RectF getBossLeftArm(BossEnemy b)  { return new RectF(b.x-260, b.y-50,  b.x-60,  b.y+90); }
    private RectF getBossRightArm(BossEnemy b) { return new RectF(b.x+60,  b.y-50,  b.x+260, b.y+90); }
    private RectF getBossHead(BossEnemy b)     { return new RectF(b.x-50,  b.y-190, b.x+50,  b.y-60); }
    private RectF getBossBody(BossEnemy b)     { return new RectF(b.x-100, b.y-60,  b.x+100, b.y+100); }

    /** Shared segmented HP bar used by all three bosses */
    private void drawBossPartBar(Canvas canvas, float bx, float by, float bw, float bh,
                                 float[] maxes, int[] curHPs, int[] colors) {
        float totalMax = 0; for (float m : maxes) totalMax += m;
        float segX = bx;
        for (int s = 0; s < maxes.length; s++) {
            float segW = bw * maxes[s] / totalMax;
            float fillRatio = Math.max(0, curHPs[s] / maxes[s]);
            paint.setColor(Color.argb(100, 20, 0, 0));
            canvas.drawRect(segX+1, by, segX+segW-1, by+bh, paint);
            if (fillRatio > 0) {
                paint.setColor(colors[s]);
                canvas.drawRect(segX+1, by, segX+1+fillRatio*(segW-2), by+bh, paint);
            }
            if (s < maxes.length-1) {
                paint.setColor(Color.argb(120, 255, 255, 255));
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
                canvas.drawLine(segX+segW, by, segX+segW, by+bh, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            segX += segW;
        }
    }

    // ── DESERT BOSS: Sand Destroyer ───────────────────────────────────────
    private void updateDesertBoss(Canvas canvas, RectF planeRect) {
        float targetY = screenHeight * 0.15f;

        // Intro descent
        if (!boss.introDone) {
            boss.y = Math.min(boss.y + 2.5f, targetY);
            if (boss.y >= targetY) {
                boss.introDone = true;
                boss.cannonTimer = 90; boss.sandWaveTimer = 180; boss.spreadTimer = 60;
            }
            drawDesertBoss(canvas); drawBossHpBar(canvas); return;
        }

        // Phase transitions — driven by part destruction, not raw HP%
        if (boss.phase == 0 && !boss.leftCannonAlive && !boss.rightCannonAlive) {
            boss.phase = 1; boss.phaseFlashTimer = 40;
            screenFlashAlpha = 90f; screenFlashColor = Color.parseColor("#cc6600");
            addFloatingBig(screenWidth/2f, screenHeight/3f, "CANNONS DESTROYED — ATTACK ARMOR!", Color.parseColor("#ff8800"));
        }
        if (boss.phase == 1 && !boss.armorAlive) {
            boss.phase = 2; boss.phaseFlashTimer = 60;
            screenFlashAlpha = 120f; screenFlashColor = Color.parseColor("#ff4400");
            addFloatingBig(screenWidth/2f, screenHeight/3f, "⚠ CORE EXPOSED — FINISH IT! ⚠", Color.parseColor("#ff4400"));
        }
        if (boss.bodyHP <= 0) { killBoss(); return; }

        // Movement — Phase 2+ adds zigzag
        float baseSpeed = boss.phase == 0 ? 1.2f : boss.phase == 1 ? 1.8f : 2.4f;
        boss.zigzagOffset = boss.phase == 2
                ? (float)Math.sin(frameCount * 0.09f) * 4.5f
                : (float)Math.sin(frameCount * 0.035f) * 1.5f;
        boss.x += boss.moveDir * baseSpeed + boss.zigzagOffset;
        boss.y = targetY + (float)Math.sin(frameCount * 0.04f) * 8f;
        if (boss.x > screenWidth - 110) boss.moveDir = -1f;
        if (boss.x < 110)               boss.moveDir =  1f;
        boss.x = clamp(boss.x, 110, screenWidth - 110);

        // Phase 1: Side cannon diagonal spread — fire straight down with slight outward angle
        boss.cannonTimer = Math.max(0, (int)(boss.cannonTimer - slowMoScale));
        if (boss.cannonTimer <= 0) {
            boss.cannonTimer = boss.phase == 0 ? 75 : boss.phase == 1 ? 55 : 38;
            float bspd = 6.5f + boss.phase * 0.8f;
            float dmg = 12f + boss.phase * 4f;
            // Left cannon: fires down-left  (~200° = slightly left of straight down)
            // Right cannon: fires down-right (~340° = slightly right of straight down)
            // In screen coords: 90° = straight down, so:
            //   left cannon  = ~120°–150° (down-left spread)
            //   right cannon = ~30°–60°   (down-right spread)
            double angL = Math.toRadians(130); // down-left
            double angR = Math.toRadians(50);  // down-right
            for (int s = -1; s <= 1; s++) {
                float spreadRad = s * 0.10f;
                enemyBullets.add(new EnemyBullet(boss.x - 80, boss.y + 20,
                        (float)Math.cos(angL + spreadRad)*bspd, (float)Math.sin(angL + spreadRad)*bspd, 8, dmg));
                enemyBullets.add(new EnemyBullet(boss.x + 80, boss.y + 20,
                        (float)Math.cos(angR - spreadRad)*bspd, (float)Math.sin(angR - spreadRad)*bspd, 8, dmg));
            }
            // Center aimed shot straight down toward player
            if (boss.phase >= 1) {
                float dx = (planeX+120f)-boss.x, dy = (planeY+120f)-(boss.y+30);
                float d = (float)Math.sqrt(dx*dx+dy*dy);
                if (d > 0.1f)
                    enemyBullets.add(new EnemyBullet(boss.x, boss.y+30, dx/d*bspd, dy/d*bspd, 5, dmg*1.4f));
            }
            playSound(sndEnemyShoot, 0.8f, 0.8f + random.nextFloat()*0.15f);
        }

        // Sand wave — horizontal wall of bullets
        boss.sandWaveTimer = Math.max(0, (int)(boss.sandWaveTimer - slowMoScale));
        if (boss.sandWaveTimer <= 0) {
            boss.sandWaveTimer = boss.phase == 0 ? 220 : boss.phase == 1 ? 160 : 110;
            float wy = boss.y + 60 + random.nextFloat() * screenHeight * 0.15f;
            int count = 10 + boss.phase * 4;
            float gap = screenWidth / (float)count;
            for (int s = 0; s < count; s++) {
                float bx = s * gap + gap * 0.5f;
                // Alternate gaps for player to dodge through
                if (s % 3 == 1) continue;
                enemyBullets.add(new EnemyBullet(bx, wy - 40,
                        dsr_wind * 0.5f, 4.5f + boss.phase * 0.8f, 9, 10f + boss.phase * 3f));
            }
            addFloating(boss.x, boss.y + 40, "SAND WAVE!", Color.parseColor("#cc8800"));
            playSound(sndBossBarrage, 0.8f, 0.9f);
        }

        // Wind-influenced bullet modifier (applied in updateEnemyBullets already via bulletWindX)
        // Hit detection handled centrally in updatePlayerBullets()

        drawDesertBoss(canvas); drawBossHpBar(canvas);
    }

    // ── OCEAN BOSS: Sea Serpent ───────────────────────────────────────────
    private void updateOceanBoss(Canvas canvas, RectF planeRect) {
        float targetY = screenHeight * 0.18f;
        float turretTargetY = screenHeight * 0.28f; // turrets hover lower than head

        // Update independent turret positions — always descend and hover
        boss.leftTurretY  = Math.min(boss.leftTurretY + 2.2f, turretTargetY);
        boss.rightTurretY = Math.min(boss.rightTurretY + 2.2f, turretTargetY);
        // Turrets patrol left/right independently with sine waves
        boss.leftTurretPhase  += 0.025f;
        boss.rightTurretPhase += 0.025f;
        boss.leftTurretX  = screenWidth * 0.18f + (float)Math.sin(boss.leftTurretPhase)  * screenWidth * 0.12f;
        boss.rightTurretX = screenWidth * 0.82f + (float)Math.sin(boss.rightTurretPhase) * screenWidth * 0.12f;
        boss.leftTurretX  = Math.max(60, Math.min(screenWidth * 0.45f, boss.leftTurretX));
        boss.rightTurretX = Math.max(screenWidth * 0.55f, Math.min(screenWidth - 60, boss.rightTurretX));
        if (boss.leftTurretFlash2 > 0)  boss.leftTurretFlash2--;
        if (boss.rightTurretFlash2 > 0) boss.rightTurretFlash2--;

        // Intro descent
        if (!boss.introDone) {
            boss.y = Math.min(boss.y + 2.5f, targetY);
            if (boss.y >= targetY) {
                boss.introDone = true;
                boss.waveAttackTimer = 120; boss.serpentTimer = 90; boss.spreadTimer = 80;
            }
            drawOceanBoss(canvas); drawBossHpBar(canvas); return;
        }

        // Phase transitions — driven by part destruction
        if (boss.phase == 0 && !boss.frontFinAlive && !boss.backFinAlive) {
            boss.phase = 1; boss.phaseFlashTimer = 40;
            screenFlashAlpha = 90f; screenFlashColor = Color.parseColor("#0088cc");
            addFloatingBig(screenWidth/2f, screenHeight/3f, "TURRETS DESTROYED — TARGET THE HEAD!", Color.parseColor("#00aaff"));
        }
        if (boss.phase == 1 && !boss.serpentHeadAlive) {
            boss.phase = 2; boss.phaseFlashTimer = 60;
            screenFlashAlpha = 120f; screenFlashColor = Color.parseColor("#0044aa");
            addFloatingBig(screenWidth/2f, screenHeight/3f, "⚠ CORE EXPOSED — DIVE ATTACK! ⚠", Color.parseColor("#00ccff"));
        }
        if (boss.bodyHP <= 0) { killBoss(); return; }

        // Dive mechanic (phase 2)
        if (boss.phase == 2) {
            if (!boss.diveActive) {
                boss.diveTimer = Math.max(0, (int)(boss.diveTimer - slowMoScale));
                if (boss.diveTimer <= 0) {
                    boss.diveActive = true;
                    boss.diveTargetX = 60 + random.nextFloat() * (screenWidth - 120);
                    boss.diveTimer = 180 + random.nextInt(60);
                    addFloating(boss.x, boss.y, "DIVING!", Color.parseColor("#00ccff"));
                    playSound(sndBossAppear, 0.6f, 1.3f);
                }
            } else {
                // Dive down and reappear
                boss.y += 8f * slowMoScale;
                if (boss.y > screenHeight + 100) {
                    // Teleport
                    boss.x = boss.diveTargetX;
                    boss.y = -100;
                    boss.diveActive = false;
                    addFloating(boss.x, screenHeight*0.3f, "REAPPEARING!", Color.parseColor("#00ffcc"));
                    shake(10f);
                    // Burst of bullets on reappear
                    for (int a = 0; a < 12; a++) {
                        float angle = (float)(a * Math.PI * 2 / 12);
                        enemyBullets.add(new EnemyBullet(boss.x, targetY,
                                (float)Math.cos(angle)*5f, (float)Math.sin(angle)*5f, 9, 14f));
                        // Only downward bullets
                        if (Math.sin(angle) < -0.3f) enemyBullets.remove(enemyBullets.size()-1);
                    }
                }
                drawOceanBoss(canvas); drawBossHpBar(canvas); return;
            }
        }

        // Smooth serpent movement — figure-8 style
        float moveSpd = boss.phase == 0 ? 1.4f : boss.phase == 1 ? 2.0f : 2.6f;
        boss.x += boss.moveDir * moveSpd;
        // Ocean bob + figure-8 y motion
        float bobAmt = boss.phase == 0 ? 14f : boss.phase == 1 ? 22f : 30f;
        boss.y = targetY + (float)Math.sin(frameCount * 0.038f) * bobAmt
                + (float)Math.sin(frameCount * 0.019f) * bobAmt * 0.5f;
        if (boss.x > screenWidth - 110) boss.moveDir = -1f;
        if (boss.x < 110)               boss.moveDir =  1f;
        boss.x = clamp(boss.x, 110, screenWidth - 110);

        // Spread shots (aimed)
        boss.spreadTimer = Math.max(0, (int)(boss.spreadTimer - slowMoScale));
        if (boss.spreadTimer <= 0) {
            boss.spreadTimer = boss.phase == 0 ? 90 : boss.phase == 1 ? 65 : 45;
            float dx = (planeX+120f)-boss.x, dy = (planeY+120f)-(boss.y+40);
            float d = (float)Math.sqrt(dx*dx+dy*dy);
            float spd = 5.5f + boss.phase * 0.8f;
            float dmg = 12f + boss.phase * 3f;
            int shots = boss.phase == 0 ? 3 : boss.phase == 1 ? 5 : 7;
            float baseAng = (float)Math.atan2(dy, dx);
            for (int a = 0; a < shots; a++) {
                float angle = baseAng + (a - shots/2f) * 0.22f;
                enemyBullets.add(new EnemyBullet(boss.x, boss.y+40,
                        (float)Math.cos(angle)*spd, (float)Math.sin(angle)*spd, 9, dmg));
            }
            playSound(sndEnemyShoot, 0.7f, 1.1f + random.nextFloat()*0.1f);
        }

        // Wave bullets — sine curve pattern (phase 1+)
        boss.waveAttackTimer = Math.max(0, (int)(boss.waveAttackTimer - slowMoScale));
        if (boss.phase >= 1 && boss.waveAttackTimer <= 0) {
            boss.waveAttackTimer = boss.phase == 1 ? 200 : 140;
            int count = 14;
            for (int s = 0; s < count; s++) {
                float bx = s * screenWidth / (float)count;
                float vy = 4f + boss.phase * 0.5f;
                float vx = (float)Math.sin(s * 0.7f) * 2.5f; // sine wave spread
                enemyBullets.add(new EnemyBullet(bx, boss.y + 50,
                        vx + ocn_wave * 0.5f, vy, 9, 11f + boss.phase * 2f));
            }
            addFloating(boss.x, boss.y + 40, "WAVE ATTACK!", Color.parseColor("#0088ff"));
            playSound(sndBossBarrage, 0.7f, 1.1f);
        }

        // Hit detection handled centrally in updatePlayerBullets()
        // Player collision
        float pdx2 = (planeX+120f)-boss.x, pdy2 = (planeY+120f)-boss.y;
        if (!boss.diveActive && pdx2*pdx2+pdy2*pdy2 < 110f*110f) damagePlayer(16f);

        drawOceanBoss(canvas); drawBossHpBar(canvas);
    }

    /** Draw the Sand Destroyer using new sprites — head/body/turrets/bottom */
    private void drawDesertBoss(Canvas canvas) {
        if (boss == null) return;
        float x = boss.x, y = boss.y;
        if (boss.bodyFlash > 0) boss.bodyFlash--;
        if (boss.armorFlash > 0) boss.armorFlash--;
        if (boss.leftCannonFlash > 0) boss.leftCannonFlash--;
        if (boss.rightCannonFlash > 0) boss.rightCannonFlash--;

        // ── Part animations — all based on same bodyBob so they stay connected ──
        float t = frameCount * 0.038f;
        float bodyBob   = (float)Math.sin(t) * 6f;          // master bob — all parts follow this
        float leftBob   = bodyBob + (float)Math.sin(t + 0.4f) * 2f;   // tiny extra wiggle on turrets
        float rightBob  = bodyBob + (float)Math.sin(t - 0.4f) * 2f;
        float bottomBob = bodyBob + (float)Math.sin(t + 0.8f) * 1.5f;

        // ── Ground shadow (large oval under boss, moves with boss.x) ─────
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float shadowW = 380f, shadowH = 55f;
        float shadowY = y + 180f;  // on the ground below boss
        // Shadow shifts slightly opposite to boss X position (parallax)
        float shadowX = x + (x - screenWidth/2f) * 0.12f;
        shadowPaint.setShader(new RadialGradient(shadowX, shadowY, shadowW * 0.5f,
                new int[]{Color.argb(90, 0, 0, 0), Color.argb(0, 0, 0, 0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawOval(new RectF(shadowX - shadowW/2f, shadowY - shadowH/2f,
                shadowX + shadowW/2f, shadowY + shadowH/2f), shadowPaint);

        // ── Sand dust particles — boss kicks up ground dust ───────────────
        if (frameCount % 3 == 0) {
            // Spawn sand particles below boss spreading outward
            for (int d = 0; d < 2; d++) {
                float px = x + (random.nextFloat() - 0.5f) * 300f;
                float py = y + 140f + random.nextFloat() * 40f;
                float vx = (px - x) * 0.04f + (random.nextFloat() - 0.5f) * 1.5f;
                float vy = random.nextFloat() * 1.5f + 0.5f;
                int sandColor = Color.argb(
                        60 + random.nextInt(60),
                        200 + random.nextInt(55),
                        150 + random.nextInt(50),
                        50 + random.nextInt(50));
                deathParticles.add(new DeathParticle(px, py, vx, vy, sandColor,
                        4f + random.nextFloat() * 6f));
            }
        }

        // ── Bottom chassis / tracks ───────────────────────────────────────
        Bitmap bottom = desertBossBottom;
        if (bottom != null) {
            float hw = bottom.getWidth()/2f, hh = bottom.getHeight()/2f;
            canvas.drawBitmap(bottom, null,
                    new RectF(x-hw, y+40+bottomBob-hh, x+hw, y+40+bottomBob+hh), bitmapPaint);
        }

        // ── Left turret ───────────────────────────────────────────────────
        float ltX = x - 165f, ltY = y + 10f + leftBob;
        if (boss.leftCannonAlive) {
            Bitmap lt = desertBossLeftTurret;
            if (lt != null) {
                if (boss.leftCannonFlash > 0) bitmapPaint.setColorFilter(
                        new android.graphics.PorterDuffColorFilter(
                                Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
                float hw = lt.getWidth()/2f, hh = lt.getHeight()/2f;
                canvas.drawBitmap(lt, null, new RectF(ltX-hw, ltY-hh, ltX+hw, ltY+hh), bitmapPaint);
                bitmapPaint.setColorFilter(null);
            }
        } else {
            if (frameCount % 4 == 0) spawnDeathParticles(ltX, ltY, AirType.KAMIKAZE);
        }

        // ── Right turret ──────────────────────────────────────────────────
        float rtX = x + 165f, rtY = y + 10f + rightBob;
        if (boss.rightCannonAlive) {
            Bitmap rt = desertBossRightTurret;
            if (rt != null) {
                if (boss.rightCannonFlash > 0) bitmapPaint.setColorFilter(
                        new android.graphics.PorterDuffColorFilter(
                                Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
                float hw = rt.getWidth()/2f, hh = rt.getHeight()/2f;
                canvas.drawBitmap(rt, null, new RectF(rtX-hw, rtY-hh, rtX+hw, rtY+hh), bitmapPaint);
                bitmapPaint.setColorFilter(null);
            }
        } else {
            if (frameCount % 4 == 0) spawnDeathParticles(rtX, rtY, AirType.KAMIKAZE);
        }

        // ── Main body (centre) — drawn over turret overlap ────────────────
        Bitmap body = desertBossBody;
        if (body != null) {
            if (boss.bodyFlash > 0) bitmapPaint.setColorFilter(
                    new android.graphics.PorterDuffColorFilter(
                            Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
            float hw = body.getWidth()/2f, hh = body.getHeight()/2f;
            // 14px wider than natural on each side — covers turret joint seams
            canvas.drawBitmap(body, null,
                    new RectF(x-hw-14, y-hh+10+bodyBob, x+hw+14, y+hh+10+bodyBob), bitmapPaint);
            bitmapPaint.setColorFilter(null);
        } else {
            paint.setColor(Color.parseColor("#6a3808"));
            canvas.drawRoundRect(new RectF(x-90, y-58+bodyBob, x+90, y+58+bodyBob), 14, 14, paint);
        }

        // ── Head / crown — drawn last on top, 14px overlap into body ─────
        Bitmap head = desertBossHead;
        if (head != null) {
            float scale = !boss.armorAlive
                    ? 1.0f + (float)Math.sin(frameCount * 0.15f) * 0.04f : 1.0f;
            float hw = head.getWidth()/2f * scale;
            float hh = head.getHeight()/2f * scale;
            // Push head 14px down into body so the joint is fully hidden
            float headY = y - (body != null ? body.getHeight()/2f : 60) - hh + 54 + bodyBob;
            if (boss.armorFlash > 0) bitmapPaint.setColorFilter(
                    new android.graphics.PorterDuffColorFilter(
                            Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(head, null, new RectF(x-hw, headY-hh, x+hw, headY+hh), bitmapPaint);
            bitmapPaint.setColorFilter(null);
        }

        // ── Engine heat glows — orange blobs at motor exhaust points ──────
        Paint heatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float heatPulse = 0.6f + (float)Math.sin(frameCount * 0.22f) * 0.4f;
        // Left engine glow
        float lEngX = x - 130f, lEngY = y + 55f + bodyBob;
        heatPaint.setShader(new RadialGradient(lEngX, lEngY, 28f * heatPulse,
                new int[]{Color.argb(120, 255, 140, 20), Color.argb(0, 255, 80, 0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(lEngX, lEngY, 28f * heatPulse, heatPaint);
        // Right engine glow
        float rEngX = x + 130f, rEngY = y + 55f + bodyBob;
        heatPaint.setShader(new RadialGradient(rEngX, rEngY, 28f * heatPulse,
                new int[]{Color.argb(120, 255, 140, 20), Color.argb(0, 255, 80, 0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(rEngX, rEngY, 28f * heatPulse, heatPaint);
        // Centre exhaust — larger, hotter
        float cEngPulse = 0.7f + (float)Math.sin(frameCount * 0.18f + 1.2f) * 0.3f;
        heatPaint.setShader(new RadialGradient(x, y + 65f + bodyBob, 38f * cEngPulse,
                new int[]{Color.argb(100, 255, 200, 60), Color.argb(0, 255, 100, 0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y + 65f + bodyBob, 38f * cEngPulse, heatPaint);

        // ── Boss label ─────────────────────────────────────────────────────
        float labelY = y - (body != null ? body.getHeight()/2f + 20 : 90)
                - (head != null ? head.getHeight() * 0.8f : 0) + bodyBob;
        paint.setColor(Color.parseColor("#cc8800")); paint.setTextSize(17);
        paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true);
        canvas.drawText("SAND DESTROYER", x, labelY, paint);
        paint.setFakeBoldText(false); paint.setTextAlign(Paint.Align.LEFT);
    }


    /** Draw the Sea Serpent — joint-based positioning, wave bob, head rotation */
    // Move these to your class level (GameView members)
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode screenMode = new PorterDuffXfermode(PorterDuff.Mode.SCREEN);

    private void drawOceanBoss(Canvas canvas) {
        if (boss == null) return;
        if (boss.bodyFlash > 0) boss.bodyFlash--;
        if (boss.serpentHeadFlash > 0) boss.serpentHeadFlash--;
        if (boss.diveActive && boss.y > screenHeight - 50) return;

        Bitmap head = oceanBossHead;
        Bitmap body = oceanBossBody;
        Bitmap tail = oceanBossTail;

        // ── Scale factor — derive from actual loaded sprite widths ──────────
        float rawTotal = (head != null ? head.getWidth() : 630)
                + (body != null ? body.getWidth() : 868)
                + (tail != null ? tail.getWidth() : 890);
        float sc = (screenWidth * 0.88f) / rawTotal;

        float headW = (head != null ? head.getWidth()  : 630) * sc;
        float headH = (head != null ? head.getHeight() : 517) * sc;
        float bodyW = (body != null ? body.getWidth()  : 868) * sc;
        float bodyH = (body != null ? body.getHeight() : 335) * sc;
        float tailW = (tail != null ? tail.getWidth()  : 890) * sc;
        float tailH = (tail != null ? tail.getHeight() : 368) * sc;

        // ── Joint coordinates — re-measured from padded sprites ───────────
        //   head_rear  = (497, 291) in 630×517
        //   body_front = (118, 166) in 868×335
        //   body_rear  = (778, 173) in 868×335
        //   tail_front = (265, 170) in 890×368
        float headRearX  = 497f * sc, headRearY  = 291f * sc;
        float bodyFrontX = 118f * sc, bodyFrontY = 166f * sc;
        float bodyRearX  = 778f * sc, bodyRearY  = 173f * sc;
        float tailFrontX = 265f * sc, tailFrontY = 170f * sc;

        // ── Wave bob — single baseline so all joints stay aligned ─────────
        float t       = frameCount * 0.035f;
        float masterY = boss.y + (float)Math.sin(t) * 14f;

        // ── Head: rear joint anchored at boss.x / masterY ─────────────────
        float jointX = boss.x;
        float jointY = masterY;
        float headX  = jointX - headRearX;
        float headY  = jointY - headRearY;

        // ── Body: front joint meets head rear joint ───────────────────────
        float bodyX = jointX - bodyFrontX - 4f;
        float bodyY = jointY - bodyFrontY;

        // ── Tail: front joint meets body rear joint ───────────────────────
        float jRearX = bodyX + bodyRearX;
        float jRearY = bodyY + bodyRearY;
        float tailX  = jRearX - tailFrontX;
        float tailY  = jRearY - tailFrontY;

        // ── Shadow ─────────────────────────────────────────────────────────
        float shadowCX = (headX + tailX + tailW) / 2f;
        float shadowRx = (headW + bodyW + tailW) * 0.30f;
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setShader(new RadialGradient(
                shadowCX, masterY + headH * 0.5f, shadowRx,
                new int[]{Color.argb(55, 0, 0, 30), Color.argb(0, 0, 0, 0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawOval(new RectF(
                shadowCX - shadowRx, masterY + headH * 0.3f,
                shadowCX + shadowRx, masterY + headH * 0.7f), shadowPaint);

        boolean anyFlash = boss.bodyFlash > 0 || boss.serpentHeadFlash > 0;

        // ── Draw order: tail → body → head ────────────────────────────────

        // TAIL
        if (tail != null) {
            canvas.drawBitmap(tail, null,
                    new RectF(tailX, tailY, tailX + tailW, tailY + tailH), bitmapPaint);
        }

        // BODY
        if (body != null) {
            if (boss.bodyFlash > 0) {
                bitmapPaint.setColorFilter(
                        new android.graphics.PorterDuffColorFilter(
                                Color.WHITE,
                                android.graphics.PorterDuff.Mode.SRC_IN
                        )
                );
            }

            canvas.drawBitmap(body, null,
                    new RectF(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH), bitmapPaint);
            bitmapPaint.setColorFilter(null);
        }

        // HEAD — rotates around its rear joint (which stays pinned at jointX/jointY)
        if (head != null) {
            if (anyFlash) {
                bitmapPaint.setColorFilter(
                        new android.graphics.PorterDuffColorFilter(
                                Color.WHITE,
                                android.graphics.PorterDuff.Mode.SRC_IN
                        )
                );
            }

            float targetAngle = boss.moveDir * -12f + 12f;
            boss.segAngle[0] += (targetAngle - boss.segAngle[0]) * 0.06f;

            canvas.save();
            canvas.rotate(boss.segAngle[0], jointX, jointY);
            canvas.drawBitmap(head, null,
                    new RectF(headX, headY, headX + headW, headY + headH), bitmapPaint);
            canvas.restore();

            bitmapPaint.setColorFilter(null);
        }

        // ── Rolling energy connection effect ───────────────────────────────
        float time = frameCount;

        float energySize1 = bodyH * 0.28f;
        float energySize2 = bodyH * 0.26f;

        if (boss.laserFiring) {
            energySize1 *= 1.30f;
            energySize2 *= 1.25f;
        }

        // head ↔ body joint
        float headOffsetX = bodyH * 0.3f;
        float headOffsetY = bodyH * 0.26f;
        drawRollingEnergy(canvas,
                jointX - headOffsetX,
                jointY - headOffsetY,
                energySize1,
                time);
        // body ↔ tail joint
        drawRollingEnergy(canvas,
                jRearX - bodyH * 1.10f,
                jRearY - bodyH * 0.24f,
                energySize2,
                time + 15f);
        // ── Independent turrets ───────────────────────────────────────────
        if (boss.frontFinAlive) {
            Bitmap turret = oceanBossTurret;
            if (turret != null) {
                if (boss.leftTurretFlash2 > 0) {
                    bitmapPaint.setColorFilter(
                            new android.graphics.PorterDuffColorFilter(
                                    Color.WHITE,
                                    android.graphics.PorterDuff.Mode.SRC_IN
                            )
                    );
                }

                float hw = turret.getWidth() / 2f * 0.75f;
                float hh = turret.getHeight() / 2f * 0.75f;

                canvas.drawBitmap(turret, null,
                        new RectF(
                                boss.leftTurretX - hw, boss.leftTurretY - hh,
                                boss.leftTurretX + hw, boss.leftTurretY + hh
                        ),
                        bitmapPaint);

                bitmapPaint.setColorFilter(null);
            } else {
                paint.setColor(Color.parseColor("#0a5080"));
                canvas.drawCircle(boss.leftTurretX, boss.leftTurretY, 40, paint);
            }
        } else {
            if (frameCount % 4 == 0) {
                spawnDeathParticles(boss.leftTurretX, boss.leftTurretY, AirType.DRONE);
            }
        }

        if (boss.backFinAlive) {
            Bitmap turret = oceanBossTurret;
            if (turret != null) {
                if (boss.rightTurretFlash2 > 0) {
                    bitmapPaint.setColorFilter(
                            new android.graphics.PorterDuffColorFilter(
                                    Color.WHITE,
                                    android.graphics.PorterDuff.Mode.SRC_IN
                            )
                    );
                }

                float hw = turret.getWidth() / 2f * 0.75f;
                float hh = turret.getHeight() / 2f * 0.75f;

                canvas.save();
                canvas.scale(-1f, 1f, boss.rightTurretX, boss.rightTurretY);
                canvas.drawBitmap(turret, null,
                        new RectF(
                                boss.rightTurretX - hw, boss.rightTurretY - hh,
                                boss.rightTurretX + hw, boss.rightTurretY + hh
                        ),
                        bitmapPaint);
                canvas.restore();

                bitmapPaint.setColorFilter(null);
            } else {
                paint.setColor(Color.parseColor("#0a5080"));
                canvas.drawCircle(boss.rightTurretX, boss.rightTurretY, 40, paint);
            }
        } else {
            if (frameCount % 4 == 0) {
                spawnDeathParticles(boss.rightTurretX, boss.rightTurretY, AirType.DRONE);
            }
        }

        // ── Label ─────────────────────────────────────────────────────────
        if (boss.diveActive) {
            paint.setColor(Color.argb(200, 0, 212, 255));
            paint.setTextSize(22);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("DIVING ▼", boss.x, headY - 20, paint);
        }

        float labelY2 = headY - 14;
        paint.setColor(Color.parseColor("#0088cc"));
        paint.setTextSize(17);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText("SEA SERPENT", boss.x, labelY2, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawRollingEnergy(Canvas canvas, float cx, float cy, float size, float time) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Helix size
        float helixWidth = size * 1.3f;
        float helixHeight = size * 1.55f;
        float coreRadius = size * 0.75f;

        // Additive glow
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

        // Soft center haze
        p.setShader(new RadialGradient(
                cx, cy, size * 2.0f,
                new int[]{
                        Color.argb(90, 90, 220, 255),
                        Color.argb(40, 60, 140, 255),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, size * 1.7f, p);
        p.setShader(null);

        // Two strands of helix
        for (int strand = 0; strand < 2; strand++) {
            float strandOffset = strand == 0 ? 0f : (float) Math.PI;

            for (int i = 14; i >= 0; i--) {
                float delay = i * 0.18f;
                float t = time * 0.18f - delay + strandOffset;

                float x = cx + (float) Math.cos(t) * helixWidth;
                float y = cy + (float) Math.sin(t) * helixHeight;

                // Depth illusion
                float depth = (float) Math.sin(t);
                float scale = depth < 0 ? 0.58f : 1.0f;

                float alphaFactor = 1f - (i / 14f);
                int alpha = (int) (210 * alphaFactor * (depth < 0 ? 0.35f : 1.0f));

                if (strand == 0) {
                    p.setColor(Color.argb(alpha, 110, 235, 255));
                } else {
                    p.setColor(Color.argb(alpha, 180, 120, 255));
                }

                canvas.drawCircle(x, y, size * 0.34f * scale * alphaFactor, p);

                // Bright leading head
                if (i == 0) {
                    p.setColor(Color.argb(255, 255, 255, 255));
                    canvas.drawCircle(x, y, size * 0.16f, p);
                }
            }
        }

        // --- START FANCY ELLIPTIC CORE ---
        float corePulseX = 1f + 0.04f * (float) Math.sin(time * 0.22f);
        float corePulseY = 1f + 0.12f * (float) Math.sin(time * 0.22f + 1.1f);

        // Elliptic core sizes
        float coreW = coreRadius * 0.75f * corePulseX;
        float coreH = coreRadius * 1.45f * corePulseY;

        // 1. Outer Bloom (Aura) — vertical/elliptic look
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(
                cx, cy, coreRadius * 2.5f,
                new int[]{Color.argb(140, 100, 200, 255), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP
        ));
        canvas.save();
        canvas.scale(0.85f, 1.35f, cx, cy);
        canvas.drawCircle(cx, cy, coreRadius * 2.5f, p);
        canvas.restore();
        p.setShader(null);

        // 2. Rotating Prismatic Diamond (Energy Flare)
        canvas.save();
        canvas.rotate(time * -15f, cx, cy);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(180, 200, 255, 255));

        float flareW = coreW * (1.35f + 0.10f * corePulseX);
        float flareH = coreH * (0.32f + 0.08f * corePulseY);

        RectF flareRect = new RectF(
                cx - flareW,
                cy - flareH,
                cx + flareW,
                cy + flareH
        );
        canvas.drawOval(flareRect, p);
        canvas.rotate(90, cx, cy);
        canvas.drawOval(flareRect, p);
        canvas.restore();

        // 3. Containment Ring — elliptic
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.12f);
        p.setColor(Color.argb(130, 180, 150, 255));

        float ringW = coreW * 1.45f;
        float ringH = coreH * 0.92f;

        canvas.drawOval(new RectF(
                cx - ringW,
                cy - ringH,
                cx + ringW,
                cy + ringH
        ), p);

        // 4. Hot Inner Seed — all oval
        p.setStyle(Paint.Style.FILL);

        // Layer A
        p.setColor(Color.argb(255, 0, 190, 255));
        canvas.drawOval(new RectF(
                cx - coreW * 0.95f,
                cy - coreH * 0.62f,
                cx + coreW * 0.95f,
                cy + coreH * 0.62f
        ), p);

        // Layer B
        p.setColor(Color.WHITE);
        canvas.drawOval(new RectF(
                cx - coreW * 0.52f,
                cy - coreH * 0.34f,
                cx + coreW * 0.52f,
                cy + coreH * 0.34f
        ), p);

        // Layer C
        p.setColor(Color.argb(255, 200, 255, 255));
        canvas.drawOval(new RectF(
                cx - coreW * 0.20f,
                cy - coreH * 0.40f,
                cx + coreW * 0.20f,
                cy - coreH * 0.08f
        ), p);
        // --- END FANCY ELLIPTIC CORE ---

        p.setXfermode(null);
    }    private void killBoss() {
        // Chain explosions across the whole boss
        for (int i = 0; i < 10; i++) {
            float ex = boss.x + random.nextFloat()*260 - 130;
            float ey = boss.y + random.nextFloat()*200 - 80;
            addExplosion(ex, ey, 80 + random.nextFloat()*80);
        }
        addExplosion(boss.x, boss.y, 220);
        playSound(sndExplosion, 1.0f, 0.4f);
        playSound(sndExplosionLg, 1.0f, 0.6f);
        screenFlashAlpha = 120f; screenFlashColor = Color.parseColor("#ff6600");
        shake(24f);
        score += 500;
        bossDefeated = true;
        boss = null;
        unlockNextMapIfNeeded();
        gameState = GameState.VICTORY;
        playSound(sndBossDeath, 1.0f, 1.0f);
        playSound(sndVictory,   1.0f, 1.0f);
    }

    private void drawBossHpBar(Canvas canvas) {
        if (boss == null) return;
        float bw = screenWidth * 0.70f, bx = screenWidth * 0.15f;
        float by = dp(178), bh = dp(18);

        // Background
        paint.setColor(Color.parseColor("#1a0000"));
        canvas.drawRoundRect(new RectF(bx, by, bx+bw, by+bh), 6, 6, paint);

        if (boss.bossType == MAP_DESERT) {
            // Desert: Left Cannon | Right Cannon | Armor | Core
            float[] maxes = {80f, 80f, 120f, 200f};
            int[] curHPs = {
                    boss.leftCannonAlive  ? boss.leftCannonHP  : 0,
                    boss.rightCannonAlive ? boss.rightCannonHP : 0,
                    boss.armorAlive       ? boss.armorHP       : 0,
                    boss.bodyHP
            };
            int[] colors = {
                    Color.parseColor("#ff8800"), Color.parseColor("#ff8800"),
                    Color.parseColor("#cc4400"), Color.parseColor("#ff2200"),
            };
            drawBossPartBar(canvas, bx, by, bw, bh, maxes, curHPs, colors);
            String[] labels = {"DESTROY CANNONS", "DESTROY THE ARMOR", "⚠ CORE EXPOSED — FINISH IT!"};
            String lbl = !boss.introDone ? "INCOMING" : labels[Math.min(boss.phase, 2)];
            paint.setColor(colors[Math.min(boss.phase*2, 3)]); paint.setTextSize(19);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(lbl, screenWidth/2f, by+bh+16, paint);

        } else if (boss.bossType == MAP_OCEAN) {
            // Ocean: Front Fin | Back Fin | Head | Core
            float[] maxes = {80f, 80f, 120f, 220f};
            int[] curHPs = {
                    boss.frontFinAlive    ? boss.frontFinHP    : 0,
                    boss.backFinAlive     ? boss.backFinHP     : 0,
                    boss.serpentHeadAlive ? boss.serpentHeadHP : 0,
                    boss.bodyHP
            };
            int[] colors = {
                    Color.parseColor("#0088ff"), Color.parseColor("#0088ff"),
                    Color.parseColor("#0044cc"), Color.parseColor("#0022aa"),
            };
            drawBossPartBar(canvas, bx, by, bw, bh, maxes, curHPs, colors);
            String[] labels = {"DESTROY THE FINS", "DESTROY THE HEAD", "⚠ CORE EXPOSED — DIVE ATTACK!"};
            String lbl = !boss.introDone ? "INCOMING" : labels[Math.min(boss.phase, 2)];            paint.setColor(colors[Math.min(boss.phase*2, 3)]); paint.setTextSize(19);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(lbl, screenWidth/2f, by+bh+16, paint);

        } else {
            // Space boss — segmented multi-part bar
            float totalMax = 80+80+120+200f;
            float[] maxes  = {80f, 80f, 120f, 200f};
            int[]   curHPs = {
                    boss.leftArmAlive  ? boss.leftArmHP  : 0,
                    boss.rightArmAlive ? boss.rightArmHP : 0,
                    boss.headAlive     ? boss.headHP     : 0,
                    boss.bodyHP
            };
            int[] colors = {
                    Color.parseColor("#4488ff"),
                    Color.parseColor("#4488ff"),
                    Color.parseColor("#ffaa00"),
                    Color.parseColor("#cc2200"),
            };
            float segX = bx;
            for (int s = 0; s < 4; s++) {
                float segW = bw * maxes[s] / totalMax;
                float fillRatio = curHPs[s] / maxes[s];
                paint.setColor(Color.argb(100, 20, 0, 0));
                canvas.drawRect(segX+1, by, segX+segW-1, by+bh, paint);
                if (fillRatio > 0) {
                    paint.setColor(colors[s]);
                    canvas.drawRect(segX+1, by, segX+1+fillRatio*(segW-2), by+bh, paint);
                }
                if (s < 3) {
                    paint.setColor(Color.argb(120, 255, 255, 255));
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
                    canvas.drawLine(segX+segW, by, segX+segW, by+bh, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                segX += segW;
            }
            String phaseLabel = !boss.introDone ? "INCOMING" :
                    boss.phase == 0 ? "DESTROY THE ARMS" :
                            boss.phase == 1 ? "TARGET THE HEAD!" : "⚠ DESTROY THE CORE!";
            int phaseColor = boss.phase == 0 ? Color.parseColor("#4488ff")
                    : boss.phase == 1 ? Color.parseColor("#ffaa00") : Color.parseColor("#ff2200");
            paint.setColor(phaseColor); paint.setTextSize(19);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(phaseLabel, screenWidth/2f, by+bh+16, paint);
        }
        // Border
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        paint.setColor(Color.argb(100, 255, 255, 255));
        canvas.drawRoundRect(new RectF(bx, by, bx+bw, by+bh), 6, 6, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void updateBoss(Canvas canvas, RectF planeRect) {
        if (boss == null) return;
        if (boss.phaseFlashTimer > 0) boss.phaseFlashTimer--;

        // Route to map-specific boss
        if (boss.bossType == MAP_DESERT) { updateDesertBoss(canvas, planeRect); return; }
        if (boss.bossType == MAP_OCEAN)  { updateOceanBoss(canvas, planeRect);  return; }

        // ── SPACE BOSS (original) ────────────────────────────────────────
        float targetY = screenHeight * 0.12f;

        // ── INTRO: descend, no attacks ────────────────────────────────────
        if (!boss.introDone) {
            boss.y = Math.min(boss.y + 2.5f, targetY);
            boss.x += boss.moveDir * 0.5f;
            if (boss.x > screenWidth - 130) boss.moveDir = -1f;
            if (boss.x < 130) boss.moveDir = 1f;
            if (boss.y >= targetY) {
                boss.introDone    = true;
                boss.spreadTimer  = 90;
                boss.spiralTimer  = 180;
                boss.laserTimer   = 240;
                boss.mineTimer    = BOSS_MINE_INTERVAL;
                boss.barrageTimer = BOSS_BARRAGE_INTERVAL;
            }
            drawBoss(canvas);
            drawBossHpBar(canvas);
            return;
        }

        // ── PHASE TRANSITIONS ─────────────────────────────────────────────
        if (boss.phase == 0 && boss.hp <= BOSS_MAX_HP * 2/3) {
            boss.phase = 1; boss.phaseFlashTimer = 40;
            screenFlashAlpha = 90f; screenFlashColor = Color.parseColor("#ff4400");
            addFloating(screenWidth/2f, screenHeight/3f, "PHASE 2 — ENRAGED!", Color.parseColor("#ff4400"));
            vibrate(80); playSound(sndBossAppear, 0.8f, 0.9f);
        }
        if (boss.phase == 1 && boss.hp <= BOSS_MAX_HP / 3) {
            boss.phase = 2; boss.phaseFlashTimer = 60; boss.dashTimer = 60;
            screenFlashAlpha = 120f; screenFlashColor = Color.parseColor("#ff0000");
            addFloating(screenWidth/2f, screenHeight/3f, "\u26a0 FINAL PHASE \u26a0", Color.parseColor("#ff0000"));
            vibrate(120); playSound(sndBossAppear, 1.0f, 0.7f);
        }

        // ── MOVEMENT ──────────────────────────────────────────────────────
        if (boss.phase == 0) {
            boss.x += boss.moveDir * 1.0f;
            boss.y  = targetY + (float)Math.sin(frameCount * 0.03f) * 10f;
        } else if (boss.phase == 1) {
            boss.x += boss.moveDir * 1.5f;
            boss.y  = targetY + (float)Math.sin(frameCount * 0.045f) * 18f;
        } else {
            if (boss.dashTimer > 0) {
                boss.dashTimer--; boss.dashVelX *= 0.88f;
            } else {
                float targetX = clamp(planeX + 60f, 130, screenWidth - 130);
                boss.dashVelX = (targetX - boss.x) * 0.04f;
                boss.dashTimer = 50 + random.nextInt(40);
            }
            boss.x += boss.dashVelX;
            boss.y  = targetY + (float)Math.sin(frameCount * 0.06f) * 22f;
        }
        if (boss.x > screenWidth - 130) boss.moveDir = -1f;
        if (boss.x < 130)               boss.moveDir =  1f;
        boss.x = clamp(boss.x, 130, screenWidth - 130);

        // ── SPREAD — paused while laser firing ────────────────────────────
        if (!boss.laserFiring) {
            boss.spreadTimer--;
            if (boss.spreadTimer <= 0) {
                int interval = boss.phase == 0 ? BOSS_SPREAD_INTERVAL : boss.phase == 1 ? 105 : 66;
                boss.spreadTimer = interval;
                int shots = boss.phase == 0 ? 5 : boss.phase == 1 ? 7 : 9;
                float spd = (boss.phase == 0 ? 6f : boss.phase == 1 ? 7f : 8f);  // boss speed unchanged
                float dx = (planeX+120f)-boss.x, dy = (planeY+120f)-(boss.y+60);
                float baseAngle = (float)Math.atan2(dy, dx);
                float spread = boss.phase == 0 ? 0.30f : boss.phase == 1 ? 0.25f : 0.20f;
                for (int a = 0; a < shots; a++) {
                    float angle = baseAngle + (a - shots/2f) * spread;
                    float ox = boss.x + (a - shots/2f) * 24f;
                    enemyBullets.add(new EnemyBullet(ox, boss.y+60,
                            (float)Math.cos(angle)*spd, (float)Math.sin(angle)*spd, 5,
                            boss.phase==0?11f:boss.phase==1?17f:22f));
                }
                playSound(sndEnemyShoot, 0.7f, 0.75f + random.nextFloat()*0.15f);
            }
        }

        // ── SPIRAL (phase 1+) ─────────────────────────────────────────────
        if (boss.phase >= 1) {
            boss.spiralTimer--;
            if (boss.spiralTimer <= 0) {
                boss.spiralTimer = boss.phase==1 ? BOSS_SPIRAL_INTERVAL : 225;
                int count = boss.phase==1 ? 12 : 16;
                float spd = boss.phase==1 ? 5f : 6.5f;
                for (int a = 0; a < count; a++) {
                    float angle = boss.spiralAngle + (float)(a*Math.PI*2/count);
                    float vy = (float)Math.sin(angle)*spd;
                    if (vy < -4f) continue;
                    enemyBullets.add(new EnemyBullet(boss.x, boss.y+60,
                            (float)Math.cos(angle)*spd, vy, 4, boss.phase==1?14f:20f));
                }
                boss.spiralAngle += 0.55f;
                playSound(sndBossSpiral, 0.9f, boss.phase==1?1.0f:1.2f);
            }
        }

        // ── LASER — aims at player position when starting ─────────────────
        if (boss.phase >= 1) {
            if (!boss.laserFiring) {
                boss.laserTimer--;
                if (boss.laserTimer <= 0) {
                    boss.laserFiring   = true;
                    boss.laserDuration = boss.phase==1 ? 110 : 160;
                    float dx = (planeX+120f)-boss.x, dy = (planeY+120f)-(boss.y-120f);
                    boss.laserAngle = (float)Math.atan2(dy,dx) + 0.65f;
                    boss.laserTimer = boss.phase==1 ? BOSS_LASER_INTERVAL : 450;
                    playSound(sndBossLaser, 1.0f, 1.0f);
                }
            } else {
                float sweepSpd = boss.phase==1 ? 0.022f : 0.035f;
                boss.laserAngle -= sweepSpd;
                boss.laserDuration--;
                if (boss.laserDuration % 10 == 0) {
                    float lx2 = boss.x+(float)Math.cos(boss.laserAngle)*screenHeight;
                    float ly2 = boss.y-120+(float)Math.sin(boss.laserAngle)*screenHeight;
                    if (lineCircleIntersect(boss.x,boss.y-120,lx2,ly2,planeX+120f,planeY+120f,40f))
                        damagePlayer(boss.phase==1?14f:21f);
                }
                if (boss.laserDuration <= 0) boss.laserFiring = false;
            }
        }

        // ── MINES (phase 2) ───────────────────────────────────────────────
        if (boss.phase >= 2) {
            boss.mineTimer--;
            if (boss.mineTimer <= 0) {
                boss.mineTimer = BOSS_MINE_INTERVAL;
                for (int m = 0; m < 3; m++) {
                    float mx = boss.x+(m-1)*80f+(random.nextFloat()-0.5f)*40f;
                    bossMines.add(new BossMine(mx, boss.y+60f,
                            (random.nextFloat()-0.5f)*2.5f, 1.5f+random.nextFloat()*1.5f));
                }
                addFloating(boss.x, boss.y-30, "MINE!", Color.parseColor("#ff4400"));
                playSound(sndCannon, 0.7f, 1.3f);
            }
        }

        // ── BARRAGE (phase 2) — only when laser NOT firing ────────────────
        if (boss.phase >= 2 && !boss.laserFiring) {
            boss.barrageTimer--;
            if (boss.barrageTimer <= 0) {
                boss.barrageTimer = BOSS_BARRAGE_INTERVAL;
                float dx = (planeX+120f)-boss.x, dy = (planeY+120f)-(boss.y+20);
                float dist = (float)Math.sqrt(dx*dx+dy*dy);
                if (dist > 0.001f) {
                    float bspd = 8f;
                    for (int b = -1; b <= 1; b++) {
                        float sp = b*0.10f;
                        float vx = (dx/dist*bspd)*(float)Math.cos(sp)-(dy/dist*bspd)*(float)Math.sin(sp);
                        float vy = (dx/dist*bspd)*(float)Math.sin(sp)+(dy/dist*bspd)*(float)Math.cos(sp);
                        enemyBullets.add(new EnemyBullet(boss.x+b*25f,boss.y+40,vx,vy,6,28f));
                    }
                }
                playSound(sndBossBarrage, 0.9f, 0.9f+random.nextFloat()*0.2f);
            }
        }

        updateBossMines(canvas, planeRect);

        // Tighter contact hitbox
        if (boss.y > 0 && RectF.intersects(planeRect,
                new RectF(boss.x-80, boss.y-45, boss.x+80, boss.y+45)))
            damagePlayer(25f);

        drawBoss(canvas);
        drawBossHpBar(canvas);
    }

    private boolean lineCircleIntersect(float x1, float y1, float x2, float y2,
                                        float cx, float cy, float r) {
        float dx = x2 - x1, dy = y2 - y1;
        float fx = x1 - cx, fy = y1 - cy;
        float a = dx*dx + dy*dy;
        float b = 2*(fx*dx + fy*dy);
        float c = fx*fx + fy*fy - r*r;
        float disc = b*b - 4*a*c;
        if (disc < 0) return false;
        disc = (float)Math.sqrt(disc);
        float t1 = (-b - disc) / (2*a);
        float t2 = (-b + disc) / (2*a);
        return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1);
    }

    private void updateBossMines(Canvas canvas, RectF planeRect) {
        for (int i = bossMines.size()-1; i >= 0; i--) {
            BossMine m = bossMines.get(i);
            m.rotAngle += 0.06f;
            m.life--;
            if (m.armTimer > 0) m.armTimer--;

            if (m.armTimer <= 0) {
                float dx = (planeX + 120f) - m.x;
                float dy = (planeY + 120f) - m.y;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist > 1f) {
                    float homingStr = 0.028f;
                    m.vx += (dx/dist) * homingStr;
                    m.vy += (dy/dist) * homingStr;
                    float spd = (float)Math.sqrt(m.vx*m.vx + m.vy*m.vy);
                    if (spd > 2.8f) { m.vx = m.vx/spd*2.8f; m.vy = m.vy/spd*2.8f; }
                }
            }

            m.x += m.vx; m.y += m.vy;

            boolean hitPlayer = m.armTimer <= 0 && RectF.intersects(planeRect,
                    new RectF(m.x-22, m.y-22, m.x+22, m.y+22));
            boolean expired = m.life <= 0;
            boolean offScreen = m.y > screenHeight + 50 || m.x < -50 || m.x > screenWidth+50;

            if (hitPlayer) { damagePlayer(28f); addExplosion(m.x, m.y, 75); }  // was 20f
            if (hitPlayer || expired) { addExplosion(m.x, m.y, 65); bossMines.remove(i); continue; }
            if (offScreen) { bossMines.remove(i); continue; }

            boolean warn = m.life < 90 && (m.life / 8) % 2 == 0;
            drawBossMine(canvas, m, warn);
        }
    }

    private void drawBossMine(Canvas canvas, BossMine m, boolean warn) {
        float s = 1.0f;
        paint.setColor(warn ? Color.argb(120, 255, 50, 0) : Color.argb(m.armTimer > 0 ? 60 : 90, 255, 180, 0));
        canvas.drawCircle(m.x, m.y, 26*s, paint);
        paint.setColor(warn ? Color.parseColor("#cc1100") : Color.parseColor("#2a1a3a"));
        Path hex = new Path();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i*60) + m.rotAngle;
            float hx = m.x + (float)Math.cos(a)*18*s;
            float hy = m.y + (float)Math.sin(a)*18*s;
            if (i==0) hex.moveTo(hx,hy); else hex.lineTo(hx,hy);
        }
        hex.close(); canvas.drawPath(hex, paint);
        paint.setColor(warn ? Color.parseColor("#ff2200") : Color.parseColor("#aa3300"));
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i*60 + 30) + m.rotAngle;
            float sx1 = m.x + (float)Math.cos(a)*18*s;
            float sy1 = m.y + (float)Math.sin(a)*18*s;
            float sx2 = m.x + (float)Math.cos(a)*26*s;
            float sy2 = m.y + (float)Math.sin(a)*26*s;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
            canvas.drawLine(sx1, sy1, sx2, sy2, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        int coreAlpha = m.armTimer > 0 ? 80 : (warn ? 255 : 200);
        paint.setColor(Color.argb(coreAlpha, 255, warn ? 30 : 140, 0));
        canvas.drawCircle(m.x, m.y, 8*s, paint);
        if (m.armTimer <= 0) {
            paint.setColor(Color.argb((int)(150+100*Math.sin(frameCount*0.25f)), 255, 50, 0));
            canvas.drawCircle(m.x, m.y, 4, paint);
        }
    }

    private void drawBoss(Canvas canvas) {
        float x = boss.x, y = boss.y;

        // Phase flash overlay
        if (boss.phaseFlashTimer > 0) {
            int fa = (int)(boss.phaseFlashTimer * 4.5f);
            paint.setColor(Color.argb(Math.min(180, fa), 255, 60, 0));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);
        }

        // Subtle pulsing aura under the whole boss
        float auraR = 180 + (float)Math.sin(frameCount * 0.06f) * 20f;
        int auraColor = boss.phase == 0 ? Color.argb(25, 60, 120, 255)
                : boss.phase == 1 ? Color.argb(30, 255, 140, 0)
                :                   Color.argb(35, 255, 30, 0);
        paint.setColor(auraColor);
        canvas.drawCircle(x, y, auraR, paint);

        // ── LEFT ARM ──────────────────────────────────────────────────
        float armOffX = 160f, armOffY = 20f;
        if (boss.leftArmAlive) {
            float lax = x - armOffX, lay = y + armOffY;
            if (bossLeftArmSprite != null) {
                // Slight bob independent from right arm
                float lbob = (float)Math.sin(frameCount * 0.05f) * 6f;
                drawBossPartSprite(canvas, bossLeftArmSprite, lax, lay + lbob, 1.0f,
                        boss.leftArmFlash > 0);
                if (boss.leftArmFlash > 0) boss.leftArmFlash--;
            } else {
                drawBossArmFallback(canvas, lax, lay, -1, boss.phase);
            }
        } else {
            drawPartWreckage(canvas, x - armOffX, y + armOffY, 0xFF884422);
        }

        // ── RIGHT ARM ─────────────────────────────────────────────────
        if (boss.rightArmAlive) {
            float rax = x + armOffX, ray = y + armOffY;
            if (bossRightArmSprite != null) {
                float rbob = (float)Math.sin(frameCount * 0.05f + 1.2f) * 6f;
                drawBossPartSprite(canvas, bossRightArmSprite, rax, ray + rbob, 1.0f,
                        boss.rightArmFlash > 0);
                if (boss.rightArmFlash > 0) boss.rightArmFlash--;
            } else {
                drawBossArmFallback(canvas, rax, ray, 1, boss.phase);
            }
        } else {
            drawPartWreckage(canvas, x + armOffX, y + armOffY, 0xFF884422);
        }

        // ── BODY CORE ─────────────────────────────────────────────────
        float bodyPulse = (float)Math.sin(frameCount * 0.12f) * 4f;
        if (bossBodySprite != null) {
            float bodyScale = !boss.headAlive ? 1.05f + (float)Math.sin(frameCount * 0.18f) * 0.03f : 1.0f;
            drawBossPartSprite(canvas, bossBodySprite, x, y + bodyPulse, bodyScale,
                    boss.bodyFlash > 0);
            if (boss.bodyFlash > 0) boss.bodyFlash--;
        } else {
            // Fallback body
            paint.setColor(boss.phase == 0 ? Color.parseColor("#1a2438")
                    : boss.phase == 1 ? Color.parseColor("#2a1200") : Color.parseColor("#2a0000"));
            canvas.drawCircle(x, y + bodyPulse, 80, paint);
            if (!boss.headAlive) {
                float coreGlow = 0.5f + (float)Math.sin(frameCount * 0.2f) * 0.5f;
                paint.setColor(Color.argb((int)(200 * coreGlow), 255, 40, 0));
                canvas.drawCircle(x, y + bodyPulse, 40, paint);
            }
        }

        // ── HEAD ──────────────────────────────────────────────────────
        float headOffY = -120f;
        if (boss.headAlive) {
            float hbob = (float)Math.sin(frameCount * 0.07f + 0.5f) * 8f;
            if (bossHeadSprite != null) {
                drawBossPartSprite(canvas, bossHeadSprite, x, y + headOffY + hbob, 1.0f,
                        boss.headFlash > 0);
                if (boss.headFlash > 0) boss.headFlash--;
            } else {
                // Fallback head
                paint.setColor(Color.parseColor("#0e1828"));
                canvas.drawCircle(x, y + headOffY + hbob, 40, paint);
                paint.setColor(Color.argb(200, 20, 160, 255));
                canvas.drawCircle(x, y + headOffY + hbob, 18, paint);
            }
        } else {
            drawPartWreckage(canvas, x, y + headOffY, 0xFF552200);
        }

        // ── LASER (from head position) ────────────────────────────────
        if (boss.laserFiring && boss.headAlive) {
            int iR = boss.phase == 0 ? 10  : boss.phase == 1 ? 210 : 255;
            int iG = boss.phase == 0 ? 160 : boss.phase == 1 ? 60  : 0;
            int iB = boss.phase == 0 ? 255 : boss.phase == 1 ? 0   : 0;
            float lx = boss.x, ly = boss.y + headOffY;
            float laserEndX = lx + (float)Math.cos(boss.laserAngle) * screenHeight * 1.5f;
            float laserEndY = ly + (float)Math.sin(boss.laserAngle) * screenHeight * 1.5f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.argb(50, iR, iG, iB));  paint.setStrokeWidth(40f);
            canvas.drawLine(lx, ly, laserEndX, laserEndY, paint);
            paint.setColor(Color.argb(200, iR, iG, iB)); paint.setStrokeWidth(18f);
            canvas.drawLine(lx, ly, laserEndX, laserEndY, paint);
            paint.setColor(Color.argb(255, 255, 245, 220)); paint.setStrokeWidth(5f);
            canvas.drawLine(lx, ly, laserEndX, laserEndY, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(230, iR, iG, iB));
            canvas.drawCircle(lx, ly, 18, paint);
        }
    }

    /** Draw a boss part sprite centred at (cx, cy) with optional hit flash */
    private void drawBossPartSprite(Canvas canvas, Bitmap sprite, float cx, float cy,
                                    float scale, boolean flash) {
        if (sprite == null) return;
        float hw = sprite.getWidth()  * 0.5f * scale;
        float hh = sprite.getHeight() * 0.5f * scale;
        if (flash) {
            // White tint overlay
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setScale(1f, 1f, 1f, 1f);
            bitmapPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    Color.argb(140, 255, 255, 255), android.graphics.PorterDuff.Mode.SRC_ATOP));
        } else {
            bitmapPaint.setColorFilter(null);
        }
        canvas.drawBitmap(sprite, null, new RectF(cx-hw, cy-hh, cx+hw, cy+hh), bitmapPaint);
        bitmapPaint.setColorFilter(null);
    }

    /** Smoke + sparks for a destroyed boss part */
    private void drawPartWreckage(Canvas canvas, float x, float y, int baseColor) {
        // Smoke blobs
        for (int i = 0; i < 4; i++) {
            float sx = x + (float)Math.sin(frameCount * 0.07f + i * 1.5f) * 18f;
            float sy = y - i * 12 - (frameCount * 0.4f + i * 7) % 60;
            int alpha = Math.max(0, 80 - i * 18);
            paint.setColor(Color.argb(alpha, 60, 50, 45));
            canvas.drawCircle(sx, sy, 14 - i * 2, paint);
        }
        // Ember sparks
        for (int i = 0; i < 3; i++) {
            if ((frameCount + i * 7) % 12 < 6) {
                float ex = x + (float)Math.sin(frameCount * 0.3f + i * 2.1f) * 22f;
                float ey = y + (float)Math.cos(frameCount * 0.25f + i) * 15f;
                paint.setColor(Color.argb(180, 255, 120 + i*30, 0));
                canvas.drawCircle(ex, ey, 4f, paint);
            }
        }
        // Blackened hull remnant
        paint.setColor(Color.argb(100, 20, 10, 5));
        canvas.drawCircle(x, y, 28, paint);
    }

    /** Canvas fallback arm if sprite missing */
    private void drawBossArmFallback(Canvas canvas, float x, float y, int side, int phase) {
        paint.setColor(phase == 0 ? Color.parseColor("#1a2438")
                : phase == 1 ? Color.parseColor("#381c00") : Color.parseColor("#380000"));
        canvas.drawCircle(x, y, 40, paint);
        int glow = 90 + (int)(Math.sin(frameCount * 0.11f) * 80 + 80);
        int rR = phase == 0 ? 20 : 255, rG = phase == 0 ? 140 : 80, rB = phase == 0 ? 255 : 0;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5f);
        paint.setColor(Color.argb(Math.min(255, glow), rR, rG, rB));
        canvas.drawCircle(x, y, 36, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ===================== BULLETS =====================
    private void updateBullets(Canvas canvas) {
        for (int i = bullets.size()-1; i >= 0; i--) {
            PlayerBullet b = bullets.get(i);
            b.prevX = b.x; b.prevY = b.y; // snapshot before move
            b.x += b.vx;
            b.y -= b.speed;
            // Spawn tracer trail from previous to current position
            if (frameCount % 2 == 0) { // every other frame — keeps list small
                bulletTrails.add(new BulletTrail(b.x, b.y, b.prevX, b.prevY, b.type));
            }
            if (b.y < -20) { bullets.remove(i); continue; }

            boolean hit = false;
            for (int j = enemyBullets.size()-1; j >= 0; j--) {
                EnemyBullet eb = enemyBullets.get(j);
                float dx = b.x-eb.x, dy = b.y-eb.y;
                if (dx*dx+dy*dy <= 22*22) {
                    addExplosion(eb.x, eb.y, 18);
                    enemyBullets.remove(j);
                    hit = true; break;
                }
            }
            if (hit) { bullets.remove(i); continue; }

            for (int j = airEnemies.size()-1; j >= 0; j--) {
                AirEnemy e = airEnemies.get(j);
                float r = e.type == AirType.BOMBER  ? 80f :
                        e.type == AirType.GUNSHIP  ? 80f :
                                e.type == AirType.STARSPARROW ? 80f :
                                        e.type == AirType.SWITCHBLADE  ? 75f :
                                                e.type == AirType.FIGHTER  ? 75f :
                                                        e.type == AirType.ARACHNID ? 75f :
                                                                e.type == AirType.KAMIKAZE ? 75f :
                                                                        e.type == AirType.JET      ? 55f :
                                                                                e.type == AirType.DRONE    ? 40f : 55f;
                float hitR = b.type == 2 ? r + 14f : r * 0.6f;
                if (circlesIntersect(b.x,b.y,hitR,e.x,e.y,r)) {
                    int dmg = b.type == 2 ? 3 : 1;
                    e.hp -= dmg;
                    e.hitFlash = 8;  // 8 frames: first 4 = white SRC_IN, next 4 = normal flash
                    shake(b.type == 2 ? 5f : 2f);
                    runBulletsHit++;

                    // Impact particles — 4 small sparks at hit point
                    for (int p = 0; p < 4; p++) {
                        float angle = random.nextFloat() * (float)(Math.PI * 2);
                        float spd   = 1.5f + random.nextFloat() * 3f;
                        deathParticles.add(new DeathParticle(
                                b.x, b.y,
                                (float)Math.cos(angle) * spd,
                                (float)Math.sin(angle) * spd,
                                Color.argb(220, 255, 220, 100), 4f));
                    }
                    // Hit sound — quiet impact tick
                    playSound(sndExplosionSm, 0.25f, 1.3f + random.nextFloat() * 0.3f);

                    if (e.hp <= 0) {
                        addExplosion(e.x, e.y, b.type == 2 ? 90 : 65);
                        spawnDeathParticles(e.x, e.y, e.type);
                        // Explosion sound — pitch varies by enemy type
                        float exPitch = e.type==AirType.BOMBER  ? 0.7f
                                : e.type==AirType.GUNSHIP ? 0.65f
                                : e.type==AirType.JET     ? 1.1f
                                : e.type==AirType.DRONE   ? 1.3f
                                : 1.0f;
                        playSound(sndExplosion, 1.0f, exPitch + random.nextFloat() * 0.1f);
                        // Screen flash on strong enemy kills
                        if (e.type == AirType.BOMBER || e.type == AirType.GUNSHIP) {
                            screenFlashAlpha = 45f;
                            screenFlashColor = Color.parseColor("#ff8800");
                        } else if (e.type == AirType.JET || e.type == AirType.ARACHNID) {
                            screenFlashAlpha = 25f;
                            screenFlashColor = Color.WHITE;
                        }
                        int pts = e.type==AirType.BOMBER?14:6;
                        // Hit stop — heavier enemies = longer freeze punch
                        hitFreezeFrames = (e.type == AirType.BOMBER || e.type == AirType.GUNSHIP
                                || e.type == AirType.ARACHNID) ? 2 : 1;
                        giveReward(pts, e.x, e.y);
                        // Map coin bonus — extra coins on Desert/Ocean
                        if (random.nextFloat() < getMapCoinMultiplier() - 1.0f) coins.add(new CoinPickup(e.x + 10, e.y));
                        coins.add(new CoinPickup(e.x, e.y));
                        dropFromEnemy(e.x, e.y, e.type);
                        airEnemies.remove(j);
                    }
                    hit = true; break;
                }
            }
            if (hit) { bullets.remove(i); continue; }

            if (boss != null) {
                int dmg = b.type == 2 ? 5 : 1;
                boolean bossHit = false;

                // Spawn impact sparks helper
                int sparkCol = boss.phase == 0 ? Color.argb(220,20,160,255)
                        : boss.phase == 1 ? Color.argb(220,255,140,0) : Color.argb(220,255,30,0);

                // ══════════════════════════════════════════════════════════
                // DESERT BOSS — Sand Destroyer partial damage
                // Phase 0: Destroy cannons  Phase 1: Destroy armor  Phase 2: Core
                // ══════════════════════════════════════════════════════════
                if (boss.bossType == MAP_DESERT) {
                    sparkCol = Color.argb(220, 255, 160, 40);
                    // Phase 2: Core exposed
                    if (!boss.armorAlive && !boss.leftCannonAlive && !boss.rightCannonAlive) {
                        float cdx = b.x - boss.x, cdy = b.y - boss.y;
                        if (cdx*cdx + cdy*cdy < 90f*90f) {
                            boss.bodyHP -= dmg; boss.bodyFlash = 8; bossHit = true;
                            addFloating(boss.x+(random.nextFloat()-.5f)*50,
                                    boss.y+(random.nextFloat()-.5f)*40, "-"+dmg, Color.parseColor("#ff6600"));
                            if (boss.bodyHP <= 0) { killBoss(); bullets.remove(i); continue; }
                        }
                        // Phase 1: Armor shell — cannons gone
                    } else if (!boss.leftCannonAlive && !boss.rightCannonAlive && boss.armorAlive) {
                        RectF armorBox = new RectF(boss.x-95, boss.y-60, boss.x+95, boss.y+60);
                        if (armorBox.contains(b.x, b.y)) {
                            boss.armorHP -= dmg; boss.armorFlash = 8; bossHit = true;
                            if (boss.armorHP <= 0) {
                                boss.armorAlive = false;
                                addExplosion(boss.x, boss.y, 90);
                                addFloatingBig(boss.x, boss.y-40, "ARMOR DESTROYED!", Color.parseColor("#ff8800"));
                                shake(14f); vibrate(70);
                            }
                        }
                        // Phase 0: Destroy cannons first
                    } else {
                        RectF leftCannon  = new RectF(boss.x-155, boss.y-24, boss.x-75, boss.y+24);
                        RectF rightCannon = new RectF(boss.x+75,  boss.y-24, boss.x+155, boss.y+24);
                        if (boss.leftCannonAlive && leftCannon.contains(b.x, b.y)) {
                            boss.leftCannonHP -= dmg; boss.leftCannonFlash = 8; bossHit = true;
                            if (boss.leftCannonHP <= 0) {
                                boss.leftCannonAlive = false;
                                addExplosion(boss.x-115, boss.y, 65);
                                addFloatingBig(boss.x-115, boss.y-20, "LEFT CANNON DOWN!", Color.parseColor("#ff8800"));
                                shake(10f); vibrate(50);
                            }
                        } else if (boss.rightCannonAlive && rightCannon.contains(b.x, b.y)) {
                            boss.rightCannonHP -= dmg; boss.rightCannonFlash = 8; bossHit = true;
                            if (boss.rightCannonHP <= 0) {
                                boss.rightCannonAlive = false;
                                addExplosion(boss.x+115, boss.y, 65);
                                addFloatingBig(boss.x+115, boss.y-20, "RIGHT CANNON DOWN!", Color.parseColor("#ff8800"));
                                shake(10f); vibrate(50);
                            }
                        }
                    }
                    // ══════════════════════════════════════════════════════════
                    // OCEAN BOSS — Sea Serpent partial damage
                    // Phase 0: Destroy fins  Phase 1: Destroy head  Phase 2: Core
                    // ══════════════════════════════════════════════════════════
                } else if (boss.bossType == MAP_OCEAN && !boss.diveActive) {
                    sparkCol = Color.argb(220, 40, 180, 255);

                    // Compute actual drawn positions (same math as drawOceanBoss)
                    float _rawW = 630f + 868f + 890f;
                    float _sc   = (screenWidth * 0.88f) / _rawW;
                    float _hrx  = 497f * _sc, _hry = 291f * _sc;
                    float _bfx  = 118f * _sc, _bfy = 166f * _sc;
                    float _brx  = 778f * _sc, _bry = 173f * _sc;
                    float _tfx  =  84f * _sc;
                    float _headW= 630f * _sc, _headH = 517f * _sc;
                    float _bodyW= 868f * _sc, _bodyH = 335f * _sc;
                    float _tailW= 890f * _sc;
                    float _t    = frameCount * 0.035f;
                    float _mY   = boss.y + (float)Math.sin(_t) * 14f;
                    float _jX   = boss.x, _jY = _mY;
                    float _headX= _jX - _hrx, _headY = _jY - _hry;
                    float _bodyX= _jX - _bfx - 4f;
                    float _bodyY= _jY - _bfy;
                    float _jRX  = _bodyX + _brx, _jRY = _bodyY + _bry;
                    float _tailX= _jRX - _tfx - 4f;
                    // Actual centres
                    float headCX = _headX + _headW * 0.4f; // visual centre of head (slightly left of bitmap centre)
                    float headCY = _headY + _headH * 0.5f;
                    float bodyCX = _bodyX + _bodyW * 0.5f;
                    float bodyCY = _bodyY + _bodyH * 0.5f;
                    float tailCX2= _tailX + _tailW * 0.5f;

                    // Phase 2: Core exposed
                    if (!boss.serpentHeadAlive && !boss.frontFinAlive && !boss.backFinAlive) {
                        float cdx = b.x - bodyCX, cdy = b.y - bodyCY;
                        if (cdx*cdx + cdy*cdy < 110f*110f) {
                            boss.bodyHP -= dmg; boss.bodyFlash = 8; bossHit = true;
                            spawnDeathParticles(b.x, b.y, AirType.DRONE);
                            addFloating(bodyCX+(random.nextFloat()-.5f)*50,
                                    bodyCY+(random.nextFloat()-.5f)*40, "-"+dmg, Color.parseColor("#00ccff"));
                            if (boss.bodyHP <= 0) { killBoss(); bullets.remove(i); continue; }
                        }
                        // Phase 1: Head — fins gone
                    } else if (!boss.frontFinAlive && !boss.backFinAlive && boss.serpentHeadAlive) {
                        float cdx = b.x - headCX, cdy = b.y - headCY;
                        if (cdx*cdx + cdy*cdy < 85f*85f) {
                            boss.serpentHeadHP -= dmg; boss.serpentHeadFlash = 8; bossHit = true;
                            if (boss.serpentHeadHP <= 0) {
                                boss.serpentHeadAlive = false;
                                addExplosion(headCX, headCY-30, 90);
                                addFloatingBig(headCX, headCY-50, "HEAD DESTROYED!", Color.parseColor("#00aaff"));
                                shake(16f); vibrate(80);
                            }
                        }
                        // Phase 0: Destroy turrets (fins)
                    } else {
                        // Turrets are at their own positions, not relative to boss x/y
                        float tR = 70f; // turret hit radius
                        float ldx = b.x - boss.leftTurretX,  ldy = b.y - boss.leftTurretY;
                        float rdx = b.x - boss.rightTurretX, rdy = b.y - boss.rightTurretY;
                        if (boss.frontFinAlive && ldx*ldx + ldy*ldy < tR*tR) {
                            boss.frontFinHP -= dmg; boss.leftTurretFlash2 = 8; bossHit = true;
                            if (boss.frontFinHP <= 0) {
                                boss.frontFinAlive = false;
                                addExplosion(boss.leftTurretX, boss.leftTurretY, 65);
                                addFloatingBig(boss.leftTurretX, boss.leftTurretY-30, "LEFT TURRET DOWN!", Color.parseColor("#00aaff"));
                                shake(10f); vibrate(50);
                            }
                        } else if (boss.backFinAlive && rdx*rdx + rdy*rdy < tR*tR) {
                            boss.backFinHP -= dmg; boss.rightTurretFlash2 = 8; bossHit = true;
                            if (boss.backFinHP <= 0) {
                                boss.backFinAlive = false;
                                addExplosion(boss.rightTurretX, boss.rightTurretY, 65);
                                addFloatingBig(boss.rightTurretX, boss.rightTurretY-30, "RIGHT TURRET DOWN!", Color.parseColor("#00aaff"));
                                shake(10f); vibrate(50);
                            }
                        }
                    }
                    // ══════════════════════════════════════════════════════════
                    // SPACE BOSS — original multi-part logic
                    // ══════════════════════════════════════════════════════════
                } else if (boss.bossType == MAP_SPACE) {
                    // Any bullet near the boss centre hits the core — generous 130px radius
                    float cdx = b.x - boss.x, cdy = b.y - boss.y;
                    if (cdx*cdx + cdy*cdy < 130f*130f) {
                        boss.bodyHP -= dmg; boss.bodyFlash = 8;
                        bossHit = true;
                        if (boss.bodyHP % 25 == 0) {
                            screenFlashAlpha = 25f; screenFlashColor = Color.parseColor("#ff4400");
                            addFloating(boss.x + (random.nextFloat()-0.5f)*60,
                                    boss.y + (random.nextFloat()-0.5f)*60,
                                    "-" + dmg, Color.parseColor("#ff6622"));
                        }
                        if (boss.bodyHP <= 0) {
                            killBoss();
                            bullets.remove(i); continue;
                        }
                    }
                    // ── Phase 1: Head exposed — arms gone ──────────────────────────────
                } else if (!boss.leftArmAlive && !boss.rightArmAlive && boss.headAlive) {
                    RectF headBox = new RectF(boss.x-70, boss.y-210, boss.x+70, boss.y-40);
                    if (headBox.contains(b.x, b.y)) {
                        boss.headHP -= dmg; boss.headFlash = 8;
                        bossHit = true;
                        if (boss.headHP <= 0) {
                            boss.headAlive = false;
                            addExplosion(boss.x, boss.y - 60, 90);
                            addFloatingBig(boss.x, boss.y - 80, "HEAD DESTROYED!", Color.parseColor("#ffaa00"));
                            shake(16f); vibrate(80);
                        }
                    }
                    // ── Phase 0: Destroy arms ──────────────────────────────────────────
                } else {
                    if (boss.leftArmAlive && getBossLeftArm(boss).contains(b.x, b.y)) {
                        boss.leftArmHP  -= dmg; boss.leftArmFlash = 8;
                        bossHit = true;
                        if (boss.leftArmHP <= 0) {
                            boss.leftArmAlive = false;
                            addExplosion(boss.x - 115, boss.y + 20, 75);
                            addFloatingBig(boss.x - 115, boss.y - 20, "ARM DESTROYED!", Color.parseColor("#4488ff"));
                            shake(12f); vibrate(60);
                        }
                    } else if (boss.rightArmAlive && getBossRightArm(boss).contains(b.x, b.y)) {
                        boss.rightArmHP -= dmg; boss.rightArmFlash = 8;
                        bossHit = true;
                        if (boss.rightArmHP <= 0) {
                            boss.rightArmAlive = false;
                            addExplosion(boss.x + 115, boss.y + 20, 75);
                            addFloatingBig(boss.x + 115, boss.y - 20, "ARM DESTROYED!", Color.parseColor("#4488ff"));
                            shake(12f); vibrate(60);
                        }
                    }
                } // end space boss hit detection

                if (bossHit) {
                    // Impact sparks
                    for (int p = 0; p < 4; p++) {
                        float angle = random.nextFloat() * (float)(Math.PI*2);
                        float spd2  = 2f + random.nextFloat()*4f;
                        deathParticles.add(new DeathParticle(b.x, b.y,
                                (float)Math.cos(angle)*spd2, (float)Math.sin(angle)*spd2, sparkCol, 5f));
                    }
                    playSound(sndExplosionSm, 0.3f, 1.1f + random.nextFloat()*0.2f);
                    bullets.remove(i); continue;
                }
            }
            drawBullet(canvas, b);
        }
    }

    private void updateEnemyBullets(Canvas canvas, RectF planeRect) {
        float bulletWindX = (currentMap == MAP_DESERT) ? dsr_wind * 0.35f : 0f;
        float bulletBobY  = (currentMap == MAP_OCEAN)  ? ocn_wave * 0.5f  : 0f;

        for (int i = enemyBullets.size()-1; i >= 0; i--) {
            EnemyBullet b = enemyBullets.get(i);
            if (b.type == 7) {
                float dx = (planeX+120f) - b.x;
                float dy = (planeY+120f) - b.y;
                float d  = (float)Math.sqrt(dx*dx+dy*dy);
                if (d > 1f) {
                    b.dx += (dx/d * 0.4f);
                    b.dy += (dy/d * 0.4f);
                    float spd = (float)Math.sqrt(b.dx*b.dx+b.dy*b.dy);
                    if (spd > 6f)  { b.dx = b.dx/spd*6f;  b.dy = b.dy/spd*6f;  }
                }
            }
            b.x += (b.dx + bulletWindX) * slowMoScale;
            b.y += (b.dy + bulletBobY) * slowMoScale;
            if (b.x<-20||b.x>screenWidth+20||b.y<-20||b.y>screenHeight+20) {
                enemyBullets.remove(i); continue;
            }
            if (planeRect.contains(b.x, b.y)) {
                damagePlayer(b.damage);
                addExplosion(planeX+120f, planeY+22, 38);
                enemyBullets.remove(i); continue;
            }
            drawEnemyBullet(canvas, b);
        }
    }

    private void drawBullet(Canvas canvas, PlayerBullet b) {
        if (b.type == 0) {
            // Bullet colour depends on map theme
            int r, g, bl;
            if (currentMap == MAP_DESERT) {
                // Blueish — electric/crystal energy in desert heat
                r = 60; g = 160; bl = 255;
            } else if (currentMap == MAP_OCEAN) {
                // Reddish — hot plasma contrasting cold ocean
                r = 255; g = 60; bl = 60;
            } else {
                // Space default — yellow-green
                r = gunPower <= 3 ? 255 : gunPower <= MAX_GUN_LEVEL ? 100 : 255;
                g = gunPower <= 3 ? 240 : gunPower <= MAX_GUN_LEVEL ? 255 : 180;
                bl = gunPower <= 3 ? 80  : gunPower <= MAX_GUN_LEVEL ? 80  : 0;
            }
            paint.setColor(Color.argb(60,r,g,bl));
            canvas.drawCircle(b.x,b.y,10,paint);
            paint.setColor(Color.rgb(r,g,bl));
            canvas.drawCircle(b.x,b.y,6,paint);
            paint.setColor(Color.argb(200,255,255,220));
            canvas.drawCircle(b.x-1,b.y-1,3,paint);
        } else if (b.type == 1) {
            paint.setColor(Color.argb(70,255,120,0));
            canvas.drawCircle(b.x,b.y,18,paint);
            paint.setColor(Color.parseColor("#ff8800"));
            canvas.drawCircle(b.x,b.y,12,paint);
            paint.setColor(Color.parseColor("#ffcc44"));
            canvas.drawCircle(b.x,b.y,7,paint);
        } else {
            paint.setColor(Color.argb(80, 255, 80, 0));
            canvas.drawCircle(b.x, b.y, 26, paint);
            paint.setColor(Color.parseColor("#ff4400"));
            canvas.drawCircle(b.x, b.y, 18, paint);
            paint.setColor(Color.parseColor("#ff8800"));
            canvas.drawCircle(b.x, b.y, 12, paint);
            paint.setColor(Color.parseColor("#ffdd00"));
            canvas.drawCircle(b.x, b.y, 6, paint);
            paint.setColor(Color.argb(220, 255, 255, 200));
            canvas.drawCircle(b.x, b.y, 3, paint);
        }
    }

    private void drawEnemyBullet(Canvas canvas, EnemyBullet b) {

        // ── Type 7: Gunship guided missile — rocket shape ─────────────────
        if (b.type == 7) {
            float spd = (float)Math.sqrt(b.dx*b.dx+b.dy*b.dy);
            float angle = spd > 0.1f ? (float)Math.toDegrees(Math.atan2(b.dy, b.dx)) : 90f;
            canvas.save();
            canvas.rotate(angle + 90f, b.x, b.y);
            paint.setColor(Color.argb(140, 255, 120, 0));
            canvas.drawOval(new RectF(b.x-4, b.y+8, b.x+4, b.y+22), paint);
            paint.setColor(Color.parseColor("#cc2200"));
            canvas.drawRoundRect(new RectF(b.x-4, b.y-14, b.x+4, b.y+8), 3, 3, paint);
            paint.setColor(Color.parseColor("#ff4400"));
            Path nose = new Path();
            nose.moveTo(b.x, b.y-18); nose.lineTo(b.x-4, b.y-10); nose.lineTo(b.x+4, b.y-10); nose.close();
            canvas.drawPath(nose, paint);
            canvas.restore();
            return;
        }

        // ── Type 12: Arachnid — purple-white plasma ball (big, in-line salvo) ──
        if (b.type == 12) {
            float pulse = (float)(0.85f + Math.sin(frameCount * 0.3f) * 0.15f);
            float R = 18f * pulse;
            // Outer purple glow
            paint.setColor(Color.argb(60, 180, 0, 255));
            canvas.drawCircle(b.x, b.y, R + 10f, paint);
            // Mid ring
            paint.setColor(Color.argb(140, 200, 50, 255));
            canvas.drawCircle(b.x, b.y, R + 4f, paint);
            // Core purple
            paint.setColor(Color.parseColor("#cc00ff"));
            canvas.drawCircle(b.x, b.y, R, paint);
            // White inner core
            paint.setColor(Color.argb(220, 255, 200, 255));
            canvas.drawCircle(b.x, b.y, R * 0.45f, paint);
            // Bright white centre
            paint.setColor(Color.argb(255, 255, 255, 255));
            canvas.drawCircle(b.x, b.y, R * 0.18f, paint);
            // Spin ring decoration
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
            paint.setColor(Color.argb(100, 220, 100, 255));
            float spin = frameCount * 0.12f;
            canvas.drawArc(new RectF(b.x-R*0.7f, b.y-R*0.7f, b.x+R*0.7f, b.y+R*0.7f),
                    (float)Math.toDegrees(spin), 200, false, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }

        // ── Type 10: Arachnid — green lightsaber beam ─────────────────────
        if (b.type == 10) {
            float spd = (float)Math.sqrt(b.dx*b.dx+b.dy*b.dy);
            float angle = spd > 0.1f ? (float)Math.toDegrees(Math.atan2(b.dy, b.dx)) : 90f;
            canvas.save();
            canvas.rotate(angle + 90f, b.x, b.y);
            // Outer soft glow
            paint.setColor(Color.argb(55, 0, 255, 80));
            canvas.drawOval(new RectF(b.x-10, b.y-28, b.x+10, b.y+28), paint);
            // Mid blade glow
            paint.setColor(Color.argb(160, 0, 230, 60));
            canvas.drawOval(new RectF(b.x-5, b.y-24, b.x+5, b.y+24), paint);
            // Core blade — bright white-green
            paint.setColor(Color.argb(240, 140, 255, 160));
            canvas.drawRoundRect(new RectF(b.x-2.5f, b.y-22, b.x+2.5f, b.y+22), 2, 2, paint);
            // Bright core line
            paint.setColor(Color.argb(255, 220, 255, 220));
            canvas.drawRoundRect(new RectF(b.x-1f, b.y-20, b.x+1f, b.y+20), 1, 1, paint);
            canvas.restore();
            return;
        }

        // ── Type 8: Bomber — red & black cannonball ───────────────────────
        if (b.type == 8) {
            // Direction-aligned trail smoke
            float spd = (float)Math.sqrt(b.dx*b.dx + b.dy*b.dy);
            float angle = spd > 0.1f ? (float)Math.toDegrees(Math.atan2(b.dy, b.dx)) : 90f;
            canvas.save();
            canvas.rotate(angle + 90f, b.x, b.y);
            // Smoke trail behind ball
            paint.setColor(Color.argb(50, 40, 40, 40));
            canvas.drawOval(new RectF(b.x-9, b.y+14, b.x+9, b.y+38), paint);
            paint.setColor(Color.argb(30, 60, 60, 60));
            canvas.drawOval(new RectF(b.x-6, b.y+30, b.x+6, b.y+52), paint);
            canvas.restore();
            // Outer red glow
            int glowPulse = (int)(60 + Math.sin(frameCount * 0.25f) * 30 + 30);
            paint.setColor(Color.argb(glowPulse, 180, 0, 0));
            canvas.drawCircle(b.x, b.y, 26f, paint);
            // Black iron body
            paint.setColor(Color.parseColor("#1a0000"));
            canvas.drawCircle(b.x, b.y, 20f, paint);
            // Deep red band
            paint.setColor(Color.parseColor("#880000"));
            canvas.drawCircle(b.x, b.y, 16f, paint);
            // Dark core
            paint.setColor(Color.parseColor("#330000"));
            canvas.drawCircle(b.x, b.y, 10f, paint);
            // Hot ember glint — offset top-left like a spinning ball
            paint.setColor(Color.argb(200, 255, 60, 0));
            canvas.drawCircle(b.x - 5f, b.y - 5f, 4f, paint);
            paint.setColor(Color.argb(140, 255, 180, 80));
            canvas.drawCircle(b.x - 7f, b.y - 7f, 2f, paint);
            // Rim crack lines
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
            paint.setColor(Color.argb(100, 255, 40, 0));
            canvas.drawArc(new RectF(b.x-14, b.y-14, b.x+14, b.y+14),
                    frameCount * 4f, 80f, false, paint);
            canvas.drawArc(new RectF(b.x-12, b.y-12, b.x+12, b.y+12),
                    frameCount * 4f + 160f, 60f, false, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }

        // ── All other bullet types — unique color per unit ─────────────────
        // type 1 = drone       cyan
        // type 8 = bomber      deep crimson-red heavy shells
        // type 9 = jet         electric blue plasma
        // type 11 = fighter    orange-yellow burst
        // type 4 = boss spiral gold
        // type 5 = boss spread hot pink
        // type 6 = boss barrage bright red
        // others  default orange
        int c;
        float outerR, innerR, coreR;
        switch (b.type) {
            case 1:  c = Color.parseColor("#00eeff"); outerR=13; innerR=8;  coreR=4; break; // drone — cyan
            case 8:  c = Color.parseColor("#cc0022"); outerR=18; innerR=12; coreR=6; break; // bomber — dark red, large
            case 9:  c = Color.parseColor("#4488ff"); outerR=14; innerR=9;  coreR=4; break; // jet — electric blue
            case 11: c = Color.parseColor("#ffaa00"); outerR=13; innerR=8;  coreR=4; break; // fighter — amber
            case 4:  c = Color.parseColor("#ffdd00"); outerR=16; innerR=10; coreR=5; break; // boss spiral — gold
            case 5:  c = Color.parseColor("#ff0088"); outerR=16; innerR=10; coreR=5; break; // boss spread — pink
            case 6:  c = Color.parseColor("#ff0000"); outerR=16; innerR=10; coreR=5; break; // boss barrage — red
            default: c = Color.parseColor("#ff4400"); outerR=13; innerR=8;  coreR=4; break;
        }

        // Soft outer glow
        paint.setColor(Color.argb(70, Color.red(c), Color.green(c), Color.blue(c)));
        canvas.drawCircle(b.x, b.y, outerR, paint);
        // Main body
        paint.setColor(c);
        canvas.drawCircle(b.x, b.y, innerR, paint);
        // Bright core
        paint.setColor(Color.argb(230, 255, 255, 255));
        canvas.drawCircle(b.x, b.y, coreR, paint);

        // Extra spinning cross on boss spiral bullets for visual distinction
        if (b.type == 4) {
            paint.setColor(Color.argb(180, 255, 200, 0));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
            float cr = frameCount * 0.15f;
            canvas.drawLine(b.x + (float)Math.cos(cr)*12, b.y + (float)Math.sin(cr)*12,
                    b.x - (float)Math.cos(cr)*12, b.y - (float)Math.sin(cr)*12, paint);
            canvas.drawLine(b.x + (float)Math.cos(cr+1.57f)*12, b.y + (float)Math.sin(cr+1.57f)*12,
                    b.x - (float)Math.cos(cr+1.57f)*12, b.y - (float)Math.sin(cr+1.57f)*12, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    // ===================== COLLECTIBLES =====================
    private void updateCoins(Canvas canvas, RectF planeRect) {
        float planeCx = planeX + 120f;
        float planeCy = planeY + 120f;
        float magnetRange    = magnetTimer > 0 ? screenWidth * 0.85f : 80f + permMagnet * 30f;
        float magnetStrength = magnetTimer > 0 ? 7f : 2f + permMagnet * 0.5f;
        float coinBobY = (currentMap == MAP_OCEAN) ? ocn_wave * 0.6f : 0f;
        float coinWindX= (currentMap == MAP_DESERT) ? dsr_wind * 0.5f : 0f;

        for (int i = coins.size()-1; i >= 0; i--) {
            CoinPickup c = coins.get(i);
            float dx = planeCx - c.x;
            float dy = planeCy - c.y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);

            if (dist < magnetRange && dist > 1f) {
                c.x += dx/dist * magnetStrength;
                c.y += dy/dist * magnetStrength;
            } else {
                c.y += (scrollSpeed * getDiffMult() + coinBobY) * slowMoScale;
                c.x += coinWindX * slowMoScale;
            }

            drawCoin(canvas, c.x, c.y);

            float collectR = magnetTimer > 0 ? 160f : 120f;
            if (dist < collectR) {
                collectCoin(c.x, c.y);
                coins.remove(i); continue;
            }
            if (c.y > screenHeight + 50) coins.remove(i);
        }
    }

    private void updatePowerUps(Canvas canvas, RectF planeRect) {
        for (int i = powerUps.size()-1; i >= 0; i--) {
            PowerUpPickup pu = powerUps.get(i);
            pu.y += scrollSpeed * getDiffMult() * 0.4f * slowMoScale;
            drawPowerUp(canvas, pu.x, pu.y, pu.type);
            float hitR = pu.type == PowerUpType.GUN_UPGRADE ? 40 : 32;
            if (RectF.intersects(planeRect, new RectF(pu.x-hitR, pu.y-hitR, pu.x+hitR, pu.y+hitR))) {
                if (pu.type == PowerUpType.SHIELD) {
                    hasShield = true; shieldTimer = Math.min(600, POWER_UP_DURATION + permShield * 90);
                    addFloatingBig(pu.x, pu.y-40, "SHIELD!", Color.parseColor("#44aaff"));
                    playSound(sndShieldUp, 1.0f, 1.0f);
                    // Shield — medium slow-mo + particle burst
                    slowMoFrames = 18;
                    spawnPickupBurst(pu.x, pu.y, Color.parseColor("#44aaff"));
                } else if (pu.type == PowerUpType.GUN_UPGRADE) {
                    collectGunUpgrade(pu.x, pu.y);
                    // Gun upgrade — quick pulse slowdown
                    slowMoFrames = 12;
                    spawnPickupBurst(pu.x, pu.y, Color.parseColor("#00ffcc"));
                } else if (pu.type == PowerUpType.MAGNET) {
                    magnetTimer = 420;
                    addFloatingBig(pu.x, pu.y-40, "MAGNET! 7s", Color.parseColor("#aa44ff"));
                    playSound(sndShieldUp, 1.0f, 0.8f);
                    spawnPickupBurst(pu.x, pu.y, Color.parseColor("#aa44ff"));
                } else { // SUPER FIRE
                    superFireTimeLeft += 360;
                    addFloatingBig(pu.x, pu.y-40, "SUPER FIRE!", Color.parseColor("#ff6600"));
                    screenFlashAlpha = 55f;
                    screenFlashColor = Color.parseColor("#ff6600");
                    playSound(sndSuperPickup, 1.0f, 1.0f);
                    // Super — longest slow-mo + biggest burst
                    slowMoFrames = 28;
                    spawnPickupBurst(pu.x, pu.y, Color.parseColor("#ff8800"));
                }
                powerUps.remove(i); continue;
            }
            if (pu.y > screenHeight + 60) powerUps.remove(i);
        }
    }

    private void updateHealthPickups(Canvas canvas, RectF planeRect) {
        for (int i = healthPickups.size()-1; i >= 0; i--) {
            HealthPickup h = healthPickups.get(i);
            h.y += scrollSpeed * getDiffMult() * 0.35f * slowMoScale;
            drawHeartPickup(canvas, h.x, h.y);
            if (RectF.intersects(planeRect, new RectF(h.x-28, h.y-28, h.x+28, h.y+28))) {
                playerHP = Math.min(MAX_PLAYER_HP, playerHP + 30);
                addFloatingBig(h.x, h.y-40, "+30% HP", Color.parseColor("#ff4466"));
                screenFlashAlpha = 40f;
                screenFlashColor = Color.parseColor("#ff2244");
                playSound(sndShieldUp, 1.0f, 1.2f);
                slowMoFrames = 15;
                spawnPickupBurst(h.x, h.y, Color.parseColor("#ff4466"));
                healthPickups.remove(i); continue;
            }
            if (h.y > screenHeight + 60) healthPickups.remove(i);
        }
    }

    private static final int RESCUE_FRAMES = 300; // 5 seconds at 60fps
    private static final float RESCUE_RADIUS = 90f; // how close player must stay

    private void updateAstronauts(Canvas canvas, RectF planeRect) {
        float planeCx = planeX + 120f;
        float planeCy = planeY + 120f;

        for (int i = astronauts.size()-1; i >= 0; i--) {
            AstronautRescue a = astronauts.get(i);

            // Drift slowly down
            a.y += scrollSpeed * 0.18f * slowMoScale;  // very slow drift — like floating in space
            if (a.y > screenHeight + 80) { astronauts.remove(i); continue; }

            float dx = planeCx - a.x;
            float dy = planeCy - a.y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);

            boolean inRange = dist < RESCUE_RADIUS;

            if (inRange) {
                a.rescueProgress++;
                if (a.rescueProgress >= RESCUE_FRAMES) {
                    // Rescued!
                    astronautsSaved++;
                    waveAstroSaved = true;
                    score += 150;
                    addFloatingBig(a.x, a.y - 50, "ASTRONAUT RESCUED! +150", Color.parseColor("#00ffcc"));
                    screenFlashAlpha = 60f;
                    screenFlashColor = Color.parseColor("#00ffcc");
                    spawnPickupBurst(a.x, a.y, Color.parseColor("#00ffcc"));
                    slowMoFrames = 20;
                    playSound(sndShieldUp, 1.0f, 1.3f);
                    playSound(sndCoinPickup, 0.8f, 1.1f);
                    astronauts.remove(i);
                    continue;
                }
            } else {
                // Decay slowly when player moves away — but not instant reset
                if (a.rescueProgress > 0) a.rescueProgress -= 2;
                if (a.rescueProgress < 0) a.rescueProgress = 0;
            }

            drawAstronaut(canvas, a, inRange);
        }
    }

    private void drawAstronaut(Canvas canvas, AstronautRescue a, boolean inRange) {
        float x = a.x, y = a.y;
        float bobY = (float)Math.sin(frameCount * 0.06f + a.x * 0.01f) * 5f; // gentle float

        // Outer attraction glow — pulses faster when in range
        float glowSpeed = inRange ? 0.22f : 0.08f;
        int glowAlpha = inRange ? 90 : 40;
        float glowR = 52 + (float)Math.sin(frameCount * glowSpeed) * 8f;
        paint.setColor(Color.argb(glowAlpha, 0, 255, 200));
        canvas.drawCircle(x, y + bobY, glowR, paint);

        // Second glow ring
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
        paint.setColor(Color.argb(inRange ? 160 : 60, 0, 220, 180));
        canvas.drawCircle(x, y + bobY, glowR + 12, paint);
        paint.setStyle(Paint.Style.FILL);

        // Draw astronaut sprite if loaded, otherwise canvas fallback
        if (spriteAstronaut != null) {
            float hw = spriteAstronaut.getWidth() / 2f;
            float hh = spriteAstronaut.getHeight() / 2f;
            canvas.drawBitmap(spriteAstronaut, null,
                    new RectF(x-hw, y+bobY-hh, x+hw, y+bobY+hh), bitmapPaint);
        } else {
            drawCanvasAstronaut(canvas, x, y + bobY);
        }

        // Rescue progress arc around astronaut
        if (a.rescueProgress > 0) {
            float prog = a.rescueProgress / (float)RESCUE_FRAMES;
            // Background track
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(7f);
            paint.setColor(Color.argb(80, 0, 200, 160));
            canvas.drawCircle(x, y + bobY, 50f, paint);
            // Progress arc
            paint.setColor(Color.argb(220, 0, 255, 180));
            canvas.drawArc(new RectF(x-50, y+bobY-50, x+50, y+bobY+50),
                    -90, 360 * prog, false, paint);
            paint.setStyle(Paint.Style.FILL);

            // Beam effect — particle line from player to astronaut when rescuing
            if (a.rescueProgress > 10) {
                float planeCx = planeX + 120f, planeCy = planeY + 80f;
                int beamAlpha = (int)(80 + 60 * Math.sin(frameCount * 0.3f));
                paint.setColor(Color.argb(beamAlpha, 0, 255, 180));
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
                canvas.drawLine(planeCx, planeCy, x, y + bobY, paint);
                paint.setStyle(Paint.Style.FILL);
                // Animated dots along beam
                for (int d = 1; d <= 4; d++) {
                    float t = ((frameCount * 0.05f + d * 0.25f) % 1f);
                    float bx = planeCx + (x - planeCx) * t;
                    float by = planeCy + (y + bobY - planeCy) * t;
                    paint.setColor(Color.argb(200, 100, 255, 210));
                    canvas.drawCircle(bx, by, 4f, paint);
                }
            }

            // Seconds remaining label
            int secsLeft = (int)Math.ceil((RESCUE_FRAMES - a.rescueProgress) / 60f);
            paint.setColor(Color.parseColor("#00ffcc"));
            paint.setTextSize(20); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(secsLeft + "s", x, y + bobY - 62, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        } else if (inRange) {
            // Just entered range — show "HOLD!" prompt
            paint.setColor(Color.parseColor("#ffffff"));
            paint.setTextSize(18); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("STAY CLOSE!", x, y + bobY - 62, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Canvas-drawn astronaut fallback (white suit, helmet, visor) */
    private void drawCanvasAstronaut(Canvas canvas, float x, float y) {
        float s = 1f;
        // Body suit — white
        paint.setColor(Color.parseColor("#dde8f0"));
        canvas.drawRoundRect(new RectF(x-14*s, y-4*s, x+14*s, y+24*s), 8, 8, paint);
        // Helmet
        paint.setColor(Color.parseColor("#eef4f8"));
        canvas.drawCircle(x, y-14*s, 16*s, paint);
        // Visor — dark blue reflective
        paint.setColor(Color.parseColor("#1a3a5c"));
        canvas.drawOval(new RectF(x-9*s, y-22*s, x+9*s, y-8*s), paint);
        // Visor glare
        paint.setColor(Color.argb(120, 150, 210, 255));
        canvas.drawOval(new RectF(x-5*s, y-20*s, x-1*s, y-14*s), paint);
        // Arms
        paint.setColor(Color.parseColor("#ccd8e0"));
        canvas.drawRoundRect(new RectF(x-24*s, y-2*s, x-14*s, y+14*s), 5, 5, paint);
        canvas.drawRoundRect(new RectF(x+14*s, y-2*s, x+24*s, y+14*s), 5, 5, paint);
        // Legs
        canvas.drawRoundRect(new RectF(x-12*s, y+18*s, x-4*s, y+32*s), 4, 4, paint);
        canvas.drawRoundRect(new RectF(x+4*s, y+18*s, x+12*s, y+32*s), 4, 4, paint);
        // Backpack / life support
        paint.setColor(Color.parseColor("#99aab8"));
        canvas.drawRoundRect(new RectF(x-8*s, y+2*s, x+8*s, y+14*s), 3, 3, paint);
        // Oxygen light blink
        paint.setColor(frameCount % 60 < 30 ? Color.parseColor("#00ff88") : Color.parseColor("#005522"));
        canvas.drawCircle(x+6*s, y+6*s, 3*s, paint);
    }

    private void drawHeartPickup(Canvas canvas, float x, float y) {
        float p = (float)Math.sin(frameCount * 0.14f) * 4 + 4;
        paint.setColor(Color.argb(50, 255, 60, 80));
        canvas.drawCircle(x, y, 36 + p, paint);
        float s = 1.45f;
        paint.setColor(Color.parseColor("#dd0030"));
        canvas.drawCircle(x - 8*s, y - 6*s, 10*s, paint);
        canvas.drawCircle(x + 8*s, y - 6*s, 10*s, paint);
        Path htri = new Path();
        htri.moveTo(x - 17*s, y - 2*s); htri.lineTo(x + 17*s, y - 2*s);
        htri.lineTo(x, y + 16*s); htri.close();
        canvas.drawPath(htri, paint);
        paint.setColor(Color.WHITE); paint.setTextSize(13); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("+30%", x, y + 32, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCoin(Canvas canvas, float x, float y) {
        paint.setColor(Color.argb(50, 255, 215, 0));
        drawStar(canvas, x, y, 30, 14, paint);
        paint.setColor(Color.parseColor("#FFD700"));
        drawStar(canvas, x, y, 22, 10, paint);
        paint.setColor(Color.parseColor("#fff0a0"));
        drawStar(canvas, x, y, 14, 6, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        paint.setColor(Color.parseColor("#c89000"));
        drawStar(canvas, x, y, 22, 10, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(180, 255, 255, 220));
        canvas.drawCircle(x - 5, y - 8, 3.5f, paint);
    }

    private void drawStar(Canvas canvas, float cx, float cy, float r, float ir, Paint p) {
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(i * 36 - 90);
            float radius = (i % 2 == 0) ? r : ir;
            float sx = cx + (float)(Math.cos(angle) * radius);
            float sy = cy + (float)(Math.sin(angle) * radius);
            if (i == 0) star.moveTo(sx, sy); else star.lineTo(sx, sy);
        }
        star.close();
        canvas.drawPath(star, p);
    }

    private void drawPowerUp(Canvas canvas, float x, float y, PowerUpType type) {
        float pulse = 1f + (float)Math.sin(frameCount * 0.14f) * 0.08f;
        canvas.save();
        canvas.scale(pulse, pulse, x, y);

        // ── GUN UPGRADE — rotating gun with glowing core ──────────────────
        if (type == PowerUpType.GUN_UPGRADE) {
            float outerPulse = (float)Math.sin(frameCount * 0.13f) * 4 + 4;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5f);
            paint.setColor(Color.argb(200, 255, 200, 0));
            canvas.drawCircle(x, y, 36 + outerPulse, paint);
            paint.setStrokeWidth(2f);
            paint.setColor(Color.argb(80, 255, 220, 60));
            canvas.drawCircle(x, y, 50 + outerPulse, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(160, 20, 10, 0));
            canvas.drawCircle(x, y, 30, paint);
            canvas.save(); canvas.rotate(frameCount * 0.5f, x, y);
            paint.setColor(Color.parseColor("#1a3a6a"));
            canvas.drawRoundRect(new RectF(x-18, y-6, x+18, y+6), 4, 4, paint);
            paint.setColor(Color.parseColor("#2255aa"));
            canvas.drawRoundRect(new RectF(x-14, y-10, x+8, y-2), 3, 3, paint);
            paint.setColor(Color.parseColor("#0d2244"));
            canvas.drawRoundRect(new RectF(x+12, y-4, x+22, y+2), 2, 2, paint);
            paint.setColor(Color.parseColor("#152b55"));
            canvas.drawRoundRect(new RectF(x-8, y+4, x+2, y+13), 3, 3, paint);
            int cg = (int)(150 + Math.sin(frameCount * 0.22f) * 80 + 80);
            paint.setColor(Color.argb(Math.min(255,cg), 0, 180, 255));
            canvas.drawCircle(x+4, y, 5, paint);
            paint.setColor(Color.argb(200, 140, 230, 255));
            canvas.drawCircle(x+4, y, 3, paint);
            canvas.restore();
            paint.setColor(Color.parseColor("#FFD700")); paint.setTextSize(13);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("GUN LV" + (gunPower < MAX_GUN_LEVEL ? gunPower + 1 : "MAX") + "!", x, y + 54, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.restore();
            return;
        }

        // ── MAGNET — horseshoe magnet with attraction sparks ──────────────
        if (type == PowerUpType.MAGNET) {
            float ringR = 48 + (float)Math.sin(frameCount * 0.1f) * 5;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
            paint.setColor(Color.argb(120, 180, 60, 255));
            canvas.drawCircle(x, y, ringR, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(50, 180, 60, 255));
            canvas.drawCircle(x, y, 34, paint);
            paint.setColor(Color.argb(140, 15, 0, 30));
            canvas.drawCircle(x, y, 30, paint);
            float ms = 0.85f;
            paint.setColor(Color.parseColor("#cc0000"));
            canvas.drawRoundRect(new RectF(x-16*ms, y-14*ms, x-5*ms, y+14*ms), 5,5, paint);
            canvas.drawRoundRect(new RectF(x+5*ms,  y-14*ms, x+16*ms, y+14*ms), 5,5, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(10*ms);
            canvas.drawArc(new RectF(x-16*ms, y-22*ms, x+16*ms, y+4*ms), 180, 180, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#aaaaaa"));
            canvas.drawRoundRect(new RectF(x-16*ms, y+10*ms, x-5*ms, y+18*ms), 3,3, paint);
            paint.setColor(Color.parseColor("#4444ff"));
            canvas.drawRoundRect(new RectF(x+5*ms,  y+10*ms, x+16*ms, y+18*ms), 3,3, paint);
            for (int sp = 0; sp < 6; sp++) {
                float sa = frameCount * 0.09f + sp * (float)(Math.PI/3);
                paint.setColor(Color.argb(160, 180, 60, 255));
                canvas.drawCircle(x+(float)Math.cos(sa)*30, y+(float)Math.sin(sa)*30, 3.5f, paint);
            }
            paint.setColor(Color.parseColor("#dd88ff")); paint.setTextSize(14);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("MAGNET 7s", x, y + 62, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.restore();
            return;
        }

        // ── SUPER FIRE — layered flame orb ───────────────────────────────
        if (type == PowerUpType.SUPER) {
            float fireR = 50 + (float)Math.sin(frameCount * 0.16f) * 6;
            paint.setColor(Color.argb(50, 255, 100, 0));
            canvas.drawCircle(x, y, fireR, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
            paint.setColor(Color.argb(140, 255, 140, 0));
            canvas.drawCircle(x, y, fireR - 8, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(160, 30, 10, 0));
            canvas.drawCircle(x, y, 30, paint);
            paint.setColor(Color.parseColor("#ff6600"));
            canvas.drawCircle(x, y, 18, paint);
            paint.setColor(Color.parseColor("#ffaa00"));
            canvas.drawCircle(x, y-3, 13, paint);
            paint.setColor(Color.parseColor("#ffee44"));
            canvas.drawCircle(x, y-6, 8, paint);
            paint.setColor(Color.argb(180, 255, 255, 150));
            canvas.drawCircle(x, y-8, 4, paint);
            Path flameTip = new Path();
            flameTip.moveTo(x, y-20); flameTip.lineTo(x-6, y-10); flameTip.lineTo(x+6, y-10);
            flameTip.close();
            paint.setColor(Color.parseColor("#ffcc00"));
            canvas.drawPath(flameTip, paint);
            for (int sp = 0; sp < 6; sp++) {
                float sa = frameCount * 0.08f + sp * (float)(Math.PI/3);
                paint.setColor(Color.argb(200, 255, 200, 0));
                canvas.drawCircle(x+(float)Math.cos(sa)*36, y+(float)Math.sin(sa)*36, 4, paint);
            }
            paint.setColor(Color.parseColor("#ff6600")); paint.setTextSize(14);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("SUPER FIRE", x, y + 62, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.restore();
            return;
        }

        // ── SHIELD — drawn shield body with cross symbol ──────────────────
        float shieldR = 44 + (float)Math.sin(frameCount * 0.11f) * 5;
        paint.setColor(Color.argb(45, 68, 170, 255));
        canvas.drawCircle(x, y, shieldR, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(100, 100, 200, 255));
        canvas.drawCircle(x, y, shieldR + 8, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(160, 0, 10, 40));
        canvas.drawCircle(x, y, 30, paint);
        paint.setColor(Color.parseColor("#1155aa"));
        Path shieldPath = new Path();
        shieldPath.moveTo(x, y-22); shieldPath.lineTo(x+18, y-12);
        shieldPath.lineTo(x+18, y+4); shieldPath.lineTo(x, y+22);
        shieldPath.lineTo(x-18, y+4); shieldPath.lineTo(x-18, y-12);
        shieldPath.close();
        canvas.drawPath(shieldPath, paint);
        paint.setColor(Color.parseColor("#3388ee"));
        Path shieldHi = new Path();
        shieldHi.moveTo(x, y-17); shieldHi.lineTo(x+13, y-9);
        shieldHi.lineTo(x+13, y+2); shieldHi.lineTo(x, y+16);
        shieldHi.lineTo(x-13, y+2); shieldHi.lineTo(x-13, y-9);
        shieldHi.close();
        canvas.drawPath(shieldHi, paint);
        paint.setColor(Color.argb(220, 200, 230, 255));
        canvas.drawRoundRect(new RectF(x-3, y-14, x+3, y+14), 2,2, paint);
        canvas.drawRoundRect(new RectF(x-10, y-3, x+10, y+3), 2,2, paint);
        paint.setColor(Color.parseColor("#44aaff")); paint.setTextSize(16);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("SHIELD", x, y + 62, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.restore();
    }

    // ===================== FX =====================
    private void updateExplosions(Canvas canvas) {
        for (int i = explosions.size()-1; i >= 0; i--) {
            ExplosionFx fx = explosions.get(i);
            fx.frame++;
            if (fx.frame <= 0) continue; // delayed secondary

            fx.radius += fx.type == 3 ? 9f : 5.5f; // rings expand faster
            fx.alpha  -= fx.type == 3 ? 18f : 11f;
            fx.phase   = 1f - (fx.alpha / 255f);
            if (fx.alpha <= 0) { explosions.remove(i); continue; }

            int a = Math.min(255, (int)fx.alpha);

            if (fx.type == 3) {
                // Shockwave ring — thin expanding circle only
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, 5f * (1f - fx.phase)));
                paint.setColor(Color.argb(a, 255, 200, 100));
                canvas.drawCircle(fx.x, fx.y, fx.radius, paint);
                // Outer faint ring
                paint.setColor(Color.argb(a/3, 255, 180, 80));
                canvas.drawCircle(fx.x, fx.y, fx.radius * 1.2f, paint);
                paint.setStyle(Paint.Style.FILL);
                continue;
            }

            // ── Fireball explosion ────────────────────────────────────────
            // 1. Outer smoke halo — dark grey, largest
            paint.setColor(Color.argb(a/4, 60, 50, 40));
            canvas.drawCircle(fx.x, fx.y, fx.radius * 1.35f, paint);

            // 2. Outer orange glow
            paint.setColor(Color.argb(a/2, 255, 120, 30));
            canvas.drawCircle(fx.x, fx.y, fx.radius * 1.1f, paint);

            // 3. Main fireball — orange→red based on phase
            int fr = (int)(255);
            int fg = (int)(Math.max(0, 160 - fx.phase * 130));
            int fb = 0;
            paint.setColor(Color.argb(a, fr, fg, fb));
            canvas.drawCircle(fx.x, fx.y, fx.radius, paint);

            // 4. Mid gold layer
            paint.setColor(Color.argb(Math.min(255, a+30), 255, 220, 50));
            canvas.drawCircle(fx.x, fx.y, fx.radius * 0.62f, paint);

            // 5. Hot white core — only early in explosion
            if (fx.phase < 0.45f) {
                int coreA = (int)(255 * (1f - fx.phase / 0.45f));
                paint.setColor(Color.argb(coreA, 255, 255, 240));
                canvas.drawCircle(fx.x, fx.y, fx.radius * 0.28f, paint);
                // Bright white flash centre
                paint.setColor(Color.argb(coreA/2, 255, 255, 255));
                canvas.drawCircle(fx.x, fx.y, fx.radius * 0.12f, paint);
            }

            // 6. Ember sparks radiating out (for large explosions)
            if (fx.type >= 1 && fx.frame % 2 == 0) {
                int sparkCount = fx.type == 2 ? 8 : 5;
                for (int s = 0; s < sparkCount; s++) {
                    double angle = Math.PI * 2 * s / sparkCount + fx.frame * 0.15f;
                    float sr = fx.radius * (0.7f + random.nextFloat() * 0.5f);
                    float sx2 = fx.x + (float)Math.cos(angle) * sr;
                    float sy2 = fx.y + (float)Math.sin(angle) * sr;
                    paint.setColor(Color.argb(a/2, 255, 180, 60));
                    canvas.drawCircle(sx2, sy2, 3f + random.nextFloat() * 4f, paint);
                }
            }
        }
    }

    private void updateFloatingTexts(Canvas canvas) {
        for (int i = floatingTexts.size()-1; i >= 0; i--) {
            FloatingTextFx fx = floatingTexts.get(i);
            fx.y     -= fx.vy;
            fx.vy    *= 0.94f;           // decelerate — floats up then slows
            fx.alpha -= 4.5f;
            fx.scale  = Math.max(0.7f, fx.scale * 0.97f); // punch-in shrink
            if (fx.alpha <= 0) { floatingTexts.remove(i); continue; }
            int a = Math.min(255, (int)fx.alpha);
            canvas.save();
            canvas.scale(fx.scale, fx.scale, fx.x, fx.y);
            // Drop shadow
            paint.setColor(Color.argb(a/3, 0, 0, 0));
            paint.setTextSize(fx.size);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(fx.text, fx.x + 2, fx.y + 3, paint);
            // Main text
            paint.setColor(fx.color);
            paint.setAlpha(a);
            canvas.drawText(fx.text, fx.x, fx.y, paint);
            canvas.restore();
        }
        paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void updateDeathParticles(Canvas canvas) {
        for (int i = deathParticles.size()-1; i >= 0; i--) {
            DeathParticle p = deathParticles.get(i);
            p.x  += p.vx;
            p.y  += p.vy;
            p.vy += 0.18f;       // gravity pull
            p.vx *= 0.93f;       // air friction
            p.vy *= 0.93f;
            p.size  *= 0.94f;    // shrink as they fade
            p.alpha -= 8f;
            if (p.alpha <= 0 || p.size < 0.8f) { deathParticles.remove(i); continue; }
            int a = Math.min(255, (int)p.alpha);
            // Outer glow
            paint.setColor(Color.argb(a / 3, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
            canvas.drawCircle(p.x, p.y, p.size * 2f, paint);
            // Core spark
            paint.setColor(Color.argb(a, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
            canvas.drawCircle(p.x, p.y, p.size, paint);
            // Bright centre
            paint.setColor(Color.argb(Math.min(255, a + 60), 255, 255, 255));
            canvas.drawCircle(p.x, p.y, p.size * 0.4f, paint);
        }
    }

    private void updateCoinParticles(Canvas canvas) {
        for (int i = coinParticles.size()-1; i >= 0; i--) {
            CoinParticle p = coinParticles.get(i);
            p.update();
            if (p.isDead()) { coinParticles.remove(i); continue; }
            p.draw(canvas, paint);
        }
    }

    private void updateBulletTrails(Canvas canvas) {
        Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);

        for (int i = bulletTrails.size()-1; i >= 0; i--) {
            BulletTrail t = bulletTrails.get(i);
            t.alpha -= 32f;
            if (t.alpha <= 0) { bulletTrails.remove(i); continue; }

            int a = (int)t.alpha;
            // Colour by bullet type — matches bullet colour
            int r, g, b;
            switch (t.bulletType) {
                case 1:
                    // Normal bullet trail — colour matches map theme
                    if (currentMap == MAP_DESERT) { r=60;  g=160; b=255; } // blue
                    else if (currentMap == MAP_OCEAN) { r=255; g=60;  b=60;  } // red
                    else { r=100; g=220; b=255; } // space default cyan
                    break;
                case 2:  r=255; g=140; b=20;  break; // super — orange always
                default: r=180; g=255; b=180; break; // cannon — lime always
            }

            // Tapering line: thicker at tail (prevX/prevY), thinner at head (x/y)
            float ratio = t.alpha / 200f; // 1.0 → 0.0
            float strokeW = 1.5f + ratio * 4.5f; // 6px at birth → 1.5px when fading
            trailPaint.setStrokeWidth(strokeW);
            trailPaint.setColor(Color.argb(a, r, g, b));

            Path path = new Path();
            path.moveTo(t.prevX, t.prevY);
            path.lineTo(t.x, t.y);
            canvas.drawPath(path, trailPaint);
        }
    }

    // ===================== UI =====================
    /** Convenience — sets paint text align and returns paint for inline use */
    /** Scale a design-space pixel value to the current screen density */
    private float dp(float value) { return value * uiScale; }

    private Paint setTextAlignTemp(Paint.Align align) {
        paint.setTextAlign(align);
        return paint;
    }

    private void drawPauseButton(Canvas canvas) {
        float cx = pauseBtn.centerX(), cy = pauseBtn.centerY();
        float r  = (pauseBtn.width()) / 2f;

        // Breathing glow behind button
        float pulse = 0.7f + (float)Math.sin(frameCount * 0.10f) * 0.3f;
        Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowP.setShader(new RadialGradient(cx, cy, r * 2.2f * pulse,
                new int[]{Color.argb((int)(60*pulse), 0, 180, 255), Color.argb(0, 0, 100, 200)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * 2.2f * pulse, glowP);

        // Circle background
        paint.setColor(Color.argb(180, 8, 18, 40));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        paint.setColor(Color.argb(100, 0, 180, 255));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);

        // Pause bars ❚❚
        paint.setColor(Color.argb(230, 255, 255, 255));
        float bw = r * 0.28f, bh = r * 0.72f, gap = r * 0.22f;
        canvas.drawRoundRect(new RectF(cx-gap-bw, cy-bh, cx-gap, cy+bh), 3, 3, paint);
        canvas.drawRoundRect(new RectF(cx+gap,    cy-bh, cx+gap+bw, cy+bh), 3, 3, paint);
    }

    private void drawHUD(Canvas canvas) {
        float s = uiScale;                          // shorthand
        float safeTop = dp(8);                      // notch/rounded-corner safe padding
        float m  = dp(12);                          // standard margin
        float panelH = (superFireTimeLeft > 0) ? dp(184) : dp(164);

        // ── HP bar shake offset (resolution-safe) ─────────────────────────
        float hpSX = 0, hpSY = 0;
        if (hpShakeTimer > 0) {
            hpShakeTimer--;
            hpSX = (random.nextFloat() - 0.5f) * hpShakeTimer * 0.6f * s;
            hpSY = (random.nextFloat() - 0.5f) * hpShakeTimer * 0.3f * s;
        }
        canvas.save();
        canvas.translate(hpSX, hpSY);

        // ── Background top bar ───────────────────────────────────────────
        paint.setColor(Color.argb(165, 6, 12, 26));
        canvas.drawRect(0, 0, screenWidth, panelH, paint);

        // Soft glossy strip
        Paint topGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        topGlow.setShader(new LinearGradient(0, 0, 0, dp(70),
                new int[]{Color.argb(55,255,255,255), Color.argb(18,255,255,255), Color.argb(0,255,255,255)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, screenWidth, dp(70), topGlow);

        // Safe zone for pause button
        float pauseSafeLeft = pauseBtn.left - dp(14);
        float rightPanelW   = dp(175);
        float rightPanelL   = pauseSafeLeft - rightPanelW;
        float rightPanelR   = pauseSafeLeft - dp(6);

        float row1top = safeTop + dp(2), row1bot = safeTop + dp(50);
        float row2top = safeTop + dp(58), row2bot = safeTop + dp(100);

        // ── LEFT: SCORE card ─────────────────────────────────────────────
        RectF scoreCard = new RectF(m, row1top, dp(210), row1bot);
        paint.setColor(Color.argb(120, 10, 16, 30));
        canvas.drawRoundRect(scoreCard, dp(18), dp(18), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.8f));
        paint.setColor(Color.argb(90, 120, 180, 255));
        canvas.drawRoundRect(scoreCard, dp(18), dp(18), paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(dp(14)); paint.setColor(Color.argb(170, 180, 210, 255));
        canvas.drawText("SCORE", scoreCard.left + dp(12), row1top + dp(16), paint);
        paint.setFakeBoldText(true); paint.setTextSize(dp(28)); paint.setColor(Color.WHITE);
        canvas.drawText(String.valueOf(score), scoreCard.left + dp(12), row1top + dp(40), paint);
        paint.setFakeBoldText(false);

        // ── CENTRE: MAP badge ─────────────────────────────────────────────
        float centerX = screenWidth * 0.50f;
        RectF mapCard = new RectF(centerX - dp(92), row1top, centerX + dp(92), row1top + dp(40));
        paint.setColor(Color.argb(105, 10, 14, 24));
        canvas.drawRoundRect(mapCard, dp(18), dp(18), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f));
        int mapColor = getMapColor();
        paint.setColor(Color.argb(120, Color.red(mapColor), Color.green(mapColor), Color.blue(mapColor)));
        canvas.drawRoundRect(mapCard, dp(18), dp(18), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(18)); paint.setFakeBoldText(true); paint.setColor(mapColor);
        canvas.drawText(getMapName(), mapCard.centerX(), mapCard.centerY() + dp(7), paint);
        paint.setFakeBoldText(false);

        // ── RIGHT: BEST + GUN LEVEL card ─────────────────────────────────
        RectF rightCard = new RectF(rightPanelL, row1top, rightPanelR, row1top + dp(72));
        paint.setColor(Color.argb(120, 10, 16, 30));
        canvas.drawRoundRect(rightCard, dp(18), dp(18), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.8f));
        paint.setColor(Color.argb(90, 255, 215, 70));
        canvas.drawRoundRect(rightCard, dp(18), dp(18), paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(dp(13)); paint.setColor(Color.argb(165, 255, 225, 130));
        canvas.drawText("BEST", rightCard.left + dp(12), row1top + dp(16), paint);
        paint.setFakeBoldText(true); paint.setTextSize(dp(22)); paint.setColor(Color.parseColor("#FFD700"));
        canvas.drawText(String.valueOf(highScore), rightCard.left + dp(12), row1top + dp(38), paint);
        paint.setFakeBoldText(false);

        paint.setTextSize(dp(13)); paint.setColor(Color.argb(170, 130, 220, 255));
        canvas.drawText("GUN", rightCard.left + dp(12), row1top + dp(58), paint);
        paint.setFakeBoldText(true); paint.setTextSize(dp(18));
        String gunTxt = gunPower >= MAX_GUN_LEVEL ? "MAX" : "LV " + gunPower;
        paint.setColor(gunPower >= MAX_GUN_LEVEL ? Color.parseColor("#66ff99") : Color.parseColor("#66ccff"));
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(gunTxt, rightCard.right - dp(12), row1top + dp(58), paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);

        // ── SECOND ROW: Astronauts + HP bar ──────────────────────────────
        RectF infoRow = new RectF(m, row2top, pauseSafeLeft - dp(6), row2bot);
        paint.setColor(Color.argb(82, 8, 14, 26));
        canvas.drawRoundRect(infoRow, dp(16), dp(16), paint);

        // Astronauts
        paint.setColor(Color.parseColor("#00ffcc")); paint.setTextSize(dp(22));
        canvas.drawText("🚀 " + astronautsSaved + "/" + MAX_ASTRONAUTS,
                infoRow.left + dp(10), row2top + dp(27), paint);

        // HP bar — fully ratio-based, never overflows
        int maxHP = MAX_PLAYER_HP;
        float hpRatio = Math.min(1f, Math.max(0f, playerHP / (float) maxHP));
        float hpW = dp(148), hpH = dp(14);
        float hpX = infoRow.right - hpW - dp(14);
        float hpY = row2top + dp(12);
        // Clamp so it never goes off screen
        hpX = Math.max(m, Math.min(hpX, screenWidth - hpW - m));
        paint.setColor(Color.argb(120, 0, 0, 0));
        canvas.drawRoundRect(new RectF(hpX, hpY, hpX+hpW, hpY+hpH), dp(7), dp(7), paint);
        int hpColor = hpRatio > 0.5f ? Color.parseColor("#39d353")
                : hpRatio > 0.25f ? Color.parseColor("#ffd33d")
                : Color.parseColor("#ff5c5c");
        paint.setColor(hpColor);
        if (hpRatio > 0)
            canvas.drawRoundRect(new RectF(hpX, hpY, hpX+hpW*hpRatio, hpY+hpH), dp(7), dp(7), paint);
        paint.setTextAlign(Paint.Align.RIGHT); paint.setTextSize(dp(15)); paint.setColor(Color.WHITE);
        canvas.drawText("HP " + playerHP + "%", infoRow.right - dp(14), row2top + dp(32), paint);
        paint.setTextAlign(Paint.Align.LEFT);

        // Low HP siren
        if (lowHealthSirenTimer > 0) lowHealthSirenTimer--;
        if (playerHP <= 15 && lowHealthSirenTimer <= 0 && playerHP > 0) {
            playSound(sndLowHealth, 0.95f, 1.0f);
            lowHealthSirenTimer = 180;
        }

        canvas.restore(); // end HP bar shake

        // ── HP popup above player (on damage) ────────────────────────────
        if (hpPopupTimer > 0) {
            hpPopupTimer--;
            if (hpShakeTimer > 0) hpShakeTimer--;
            float hpOX = hpShakeTimer > 0 ? (float)Math.sin(hpShakeTimer * 1.8f) * dp(5) : 0f;
            float hpOY = hpShakeTimer > 0 ? (float)Math.cos(hpShakeTimer * 2.2f) * dp(3) : 0f;
            float popAlpha = hpPopupTimer > 30 ? 1f : hpPopupTimer / 30f;
            int popA = (int)(popAlpha * 255);
            int barColor2 = hpRatio > 0.5f ? Color.argb(popA, 50, 220, 80)
                    : hpRatio > 0.25f ? Color.argb(popA, 255, 200, 30)
                    : Color.argb(popA, 220, 40, 40);
            float px = planeX + 120f - dp(70) + hpOX;
            float py = planeY - dp(18) + hpOY;
            float pw = dp(140), ph = dp(10);
            paint.setColor(Color.argb(popA/2, 10, 10, 10));
            canvas.drawRoundRect(new RectF(px, py, px+pw, py+ph), ph/2, ph/2, paint);
            paint.setColor(barColor2);
            canvas.drawRoundRect(new RectF(px, py, px+pw*hpRatio, py+ph), ph/2, ph/2, paint);
            paint.setTextSize(dp(18)); paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.argb(popA, 255, 100, 120));
            canvas.drawText("♥", px + pw + dp(6), py + ph - dp(1), paint);
            paint.setColor(Color.argb(popA, 255, 255, 255)); paint.setTextSize(dp(15));
            canvas.drawText(playerHP + "%", px + pw + dp(22), py + ph - dp(1), paint);
        }

        // ── Powerup timers (left side, below panel) ───────────────────────
        paint.setTextAlign(Paint.Align.LEFT);
        float puY = panelH + dp(8);
        if (hasShield) {
            paint.setColor(Color.parseColor("#44aaff")); paint.setTextSize(dp(20));
            canvas.drawText("SHIELD " + shieldTimer/60 + "s", dp(16), puY, paint);
            puY += dp(24);
        }
        if (magnetTimer > 0) {
            paint.setColor(Color.parseColor("#cc66ff")); paint.setTextSize(dp(20));
            canvas.drawText("MAGNET " + magnetTimer/60 + "s", dp(16), puY, paint);
            puY += dp(24);
        }
        if (superFireTimeLeft > 0) {
            float sfBarW = screenWidth * 0.50f, sfBarX = dp(16), sfBarY = puY;
            paint.setColor(Color.parseColor("#330800"));
            canvas.drawRoundRect(new RectF(sfBarX, sfBarY, sfBarX+sfBarW, sfBarY+dp(14)), dp(7), dp(7), paint);
            float sfRatio = Math.min(1f, superFireTimeLeft / 360f);
            paint.setColor(Color.parseColor("#ff6e00"));
            canvas.drawRoundRect(new RectF(sfBarX, sfBarY, sfBarX+sfBarW*sfRatio, sfBarY+dp(14)), dp(7), dp(7), paint);
            paint.setColor(Color.WHITE); paint.setTextSize(dp(12));
            canvas.drawText(String.format("SUPER FIRE %.1fs", superFireTimeLeft/60f), sfBarX+dp(6), sfBarY+dp(11), paint);
        }
        if (comboCount >= 3) {
            // ── Bottom-right: combo text + decay bar ──────────────────────
            paint.setTextAlign(Paint.Align.RIGHT);
            int comboColor = comboCount >= 15 ? Color.parseColor("#ff0000")
                    : comboCount >= 10 ? Color.parseColor("#ff4400")
                    : comboCount >= 6  ? Color.parseColor("#ff8800") : Color.parseColor("#ffcc00");
            paint.setColor(comboColor);
            paint.setTextSize(dp(28) + (float)Math.sin(frameCount * 0.2f) * dp(3));
            canvas.drawText("COMBO x" + (comboMult >= 5 ? "5" : comboMult >= 3 ? "3" :
                            comboMult >= 2 ? "2" : "1.5") + "  " + comboCount + " kill",
                    screenWidth - dp(16), screenHeight - dp(40), paint);
            float cBarW = dp(180), cBarX = screenWidth - dp(196), cBarY = screenHeight - dp(28);
            paint.setColor(Color.parseColor("#222222"));
            canvas.drawRoundRect(new RectF(cBarX, cBarY, cBarX+cBarW, cBarY+dp(8)), dp(4), dp(4), paint);
            paint.setColor(comboColor);
            canvas.drawRoundRect(new RectF(cBarX, cBarY, cBarX+cBarW*(float)comboTimer/COMBO_TIMEOUT, cBarY+dp(8)), dp(4), dp(4), paint);

            // ── Top-right: punchy X badge ────────────────────────────────
            if (comboCount >= 5) {
                float badgeScale = 1.0f + (float)Math.sin(frameCount * 0.35f) * 0.08f
                        + (comboCount >= 10 ? 0.12f : 0f);
                String badgeText = "×" + (comboMult >= 5f ? "5" : comboMult >= 3f ? "3"
                        : comboMult >= 2f ? "2" : "1.5");
                String killText  = comboCount + " KILLS";

                float badgeCX = screenWidth - dp(70);
                float badgeCY = dp(190); // just below the HUD panel

                // Glow ring for high combo
                if (comboCount >= 10) {
                    float glowR = dp(48) * badgeScale;
                    paint.setShader(new RadialGradient(badgeCX, badgeCY, glowR,
                            new int[]{Color.argb(80, 255, 80, 0), Color.argb(0, 255, 80, 0)},
                            null, Shader.TileMode.CLAMP));
                    canvas.drawCircle(badgeCX, badgeCY, glowR, paint);
                    paint.setShader(null);
                }

                // Badge background pill
                float pillW = dp(52) * badgeScale, pillH = dp(28) * badgeScale;
                paint.setColor(Color.argb(180, 20, 10, 5));
                canvas.drawRoundRect(new RectF(badgeCX-pillW, badgeCY-pillH,
                        badgeCX+pillW, badgeCY+pillH), pillH, pillH, paint);
                // Badge border
                paint.setColor(comboColor); paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2.5f) * badgeScale);
                canvas.drawRoundRect(new RectF(badgeCX-pillW, badgeCY-pillH,
                        badgeCX+pillW, badgeCY+pillH), pillH, pillH, paint);
                paint.setStyle(Paint.Style.FILL);

                // Multiplier text
                paint.setColor(comboColor); paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(32) * badgeScale); paint.setFakeBoldText(true);
                canvas.drawText(badgeText, badgeCX, badgeCY + dp(4) * badgeScale, paint);
                paint.setFakeBoldText(false);

                // Kill count below badge
                paint.setColor(Color.argb(200, 255, 255, 255));
                paint.setTextSize(dp(13));
                canvas.drawText(killText, badgeCX, badgeCY + pillH + dp(14), paint);
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawProgressBar(Canvas canvas) {
        float bw = screenWidth * 0.5f, bx = screenWidth * 0.25f;
        float by = screenHeight - dp(28), bh = dp(18);
        paint.setColor(Color.parseColor("#111111"));
        canvas.drawRoundRect(new RectF(bx, by, bx+bw, by+bh), dp(8), dp(8), paint);
        float ratio = mapProgress / (float)MAX_PROGRESS;
        paint.setColor(totalWavesCompleted >= BOSS_AFTER_WAVES && airEnemies.isEmpty()
                ? Color.parseColor("#cc0000") : Color.parseColor("#00aaff"));
        canvas.drawRoundRect(new RectF(bx, by, bx+bw*ratio, by+bh), dp(8), dp(8), paint);
        // Live star preview
        int liveStars = calcWaveStars();
        float starX = screenWidth / 2f - dp(24);
        paint.setTextAlign(Paint.Align.LEFT); paint.setTextSize(dp(16));
        for (int s2 = 0; s2 < 3; s2++) {
            paint.setColor(s2 < liveStars ? Color.parseColor("#FFD700")
                    : Color.argb(70, 180, 160, 60));
            canvas.drawText("★", starX + s2 * dp(18), by + dp(14), paint);
        }
    }

    private void drawHpBar(Canvas canvas,float x,float y,float w,float h,int hp,int maxHp){
        paint.setColor(Color.parseColor("#111111"));
        canvas.drawRect(x,y,x+w,y+h,paint);
        paint.setColor(Color.parseColor("#00cc44"));
        canvas.drawRect(x,y,x+w*hp/(float)maxHp,y+h,paint);
    }

    private void drawMenuButton(Canvas canvas,RectF rect,String text,int base,int tc){
        paint.setColor(base); canvas.drawRoundRect(rect,16,16,paint);
        paint.setColor(Color.argb(60,255,255,255));
        canvas.drawRoundRect(new RectF(rect.left+4,rect.top+4,rect.right-4,rect.top+24),14,14,paint);
        paint.setColor(tc); paint.setTextSize(30); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text,rect.centerX(),rect.centerY()+10,paint);
    }

    /** Premium interactive button — scales down when pressed, glows on hover */
    // ── Tactical HUD button — sharp, glowing, futuristic ─────────────────
    private void drawMenuBtn(Canvas canvas, RectF rect, String label,
                             int baseColor, int textColor, boolean pressed) {
        if (rect == null) return;
        float cx = rect.centerX(), cy = rect.centerY();
        float scale = pressed ? 0.96f : 1.0f;
        canvas.save();
        canvas.scale(scale, scale, cx, cy);

        // Base fill — semi-transparent dark
        paint.setColor(pressed ? Color.argb(220, 8, 18, 40) : Color.argb(200, 6, 14, 32));
        canvas.drawRoundRect(rect, 6, 6, paint);

        // Left accent bar — coloured stripe on left edge
        int accentR = (textColor >> 16) & 0xFF;
        int accentG = (textColor >> 8)  & 0xFF;
        int accentB =  textColor        & 0xFF;
        paint.setColor(Color.argb(pressed ? 255 : 180, accentR, accentG, accentB));
        canvas.drawRoundRect(new RectF(rect.left, rect.top, rect.left + 4, rect.bottom), 3, 3, paint);

        // Outer glow border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 2f : 1.2f);
        paint.setColor(Color.argb(pressed ? 200 : 80, accentR, accentG, accentB));
        canvas.drawRoundRect(rect, 6, 6, paint);
        paint.setStyle(Paint.Style.FILL);

        // Corner marks — tactical aesthetic
        float cm = 8f, ct = 2f;
        paint.setColor(Color.argb(pressed ? 255 : 120, accentR, accentG, accentB));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(ct);
        canvas.drawLine(rect.right - cm, rect.top,    rect.right, rect.top,    paint);
        canvas.drawLine(rect.right,      rect.top,    rect.right, rect.top+cm, paint);
        canvas.drawLine(rect.left,       rect.bottom, rect.left+cm, rect.bottom, paint);
        canvas.drawLine(rect.left,       rect.bottom-cm, rect.left, rect.bottom, paint);
        paint.setStyle(Paint.Style.FILL);

        // Label
        paint.setColor(pressed ? Color.WHITE : textColor);
        paint.setTextSize(26); paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText(label, cx, cy + 9, paint);
        paint.setFakeBoldText(false);

        canvas.restore();
    }

    // ── Large primary action button (START MISSION) ───────────────────────
    private void drawPrimaryBtn(Canvas canvas, RectF rect, String label, boolean pressed) {
        if (rect == null) return;
        float cx = rect.centerX(), cy = rect.centerY();
        float pulse = pressed ? 0f : (float)Math.sin(frameCount * 0.09f) * 6f;

        // Outer pulse glow
        paint.setColor(Color.argb(30 + (int)(pulse*3), 0, 212, 255));
        canvas.drawRoundRect(new RectF(rect.left-pulse, rect.top-pulse,
                rect.right+pulse, rect.bottom+pulse), 8, 8, paint);

        // Base fill
        paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                Color.argb(240, 0, 40, 80),
                Color.argb(240, 0, 20, 50),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 6, 6, paint);
        paint.setShader(null);

        // Top inner highlight
        paint.setColor(Color.argb(40, 0, 212, 255));
        canvas.drawRoundRect(new RectF(rect.left+2, rect.top+2, rect.right-2, rect.top+20), 4, 4, paint);

        // Cyan border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 2.5f : 1.8f);
        paint.setColor(pressed ? Color.argb(255, 0, 212, 255) : Color.argb(160, 0, 180, 220));
        canvas.drawRoundRect(rect, 6, 6, paint);
        paint.setStyle(Paint.Style.FILL);

        // Corner marks — all 4 corners
        float cm = 12f; paint.setStrokeWidth(2.5f);
        paint.setColor(Color.argb(200, 0, 212, 255));
        paint.setStyle(Paint.Style.STROKE);
        float[][] corners = {
                {rect.left, rect.top, rect.left+cm, rect.top, rect.left, rect.top+cm},
                {rect.right-cm, rect.top, rect.right, rect.top, rect.right, rect.top+cm},
                {rect.left, rect.bottom-cm, rect.left, rect.bottom, rect.left+cm, rect.bottom},
                {rect.right-cm, rect.bottom, rect.right, rect.bottom, rect.right, rect.bottom-cm},
        };
        for (float[] c2 : corners) {
            canvas.drawLine(c2[0], c2[1], c2[2], c2[3], paint);
            canvas.drawLine(c2[2], c2[3], c2[4], c2[5], paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // Label
        paint.setColor(Color.WHITE); paint.setTextSize(32);
        paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true);
        canvas.drawText(label, cx, cy + 11, paint);
        paint.setFakeBoldText(false);

        // Subtitle
        paint.setColor(Color.argb(140, 0, 212, 255)); paint.setTextSize(14);
        canvas.drawText("TAP TO BEGIN", cx, cy + 28, paint);
    }

    private void drawFancyTitle(Canvas canvas, float cx, float titleCY, float scale) {
        canvas.save();
        canvas.scale(scale, scale, cx, titleCY);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);

        float skyY    = titleCY;
        float strikeY = titleCY + 88f;

        // ── Deep drop shadow (both words together) ──────────────────────
        paint.setTextSize(86);
        paint.setColor(Color.argb(90, 0, 10, 40));
        canvas.drawText("SKY",    cx + 6, skyY    + 7, paint);
        paint.setTextSize(96);
        canvas.drawText("STRIKE", cx + 6, strikeY + 7, paint);

        // ── Outer blue glow for STRIKE ───────────────────────────────────
        paint.setTextSize(96);
        paint.setColor(Color.argb(55, 30, 140, 255));
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dy = -4; dy <= 4; dy += 2) {
                if (dx == 0 && dy == 0) continue;
                canvas.drawText("STRIKE", cx + dx, strikeY + dy, paint);
            }
        }

        // ── Outer white glow for SKY ─────────────────────────────────────
        paint.setTextSize(86);
        paint.setColor(Color.argb(40, 255, 255, 255));
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dy = -3; dy <= 3; dy += 3) {
                if (dx == 0 && dy == 0) continue;
                canvas.drawText("SKY", cx + dx, skyY + dy, paint);
            }
        }

        // ── SKY — white with inner highlight ────────────────────────────
        paint.setTextSize(86);
        paint.setColor(Color.WHITE);
        canvas.drawText("SKY", cx, skyY, paint);
        // Slight top-left shine
        paint.setColor(Color.argb(80, 255, 255, 255));
        canvas.drawText("SKY", cx - 1, skyY - 1, paint);

        // ── STRIKE — deep blue with cyan highlight ───────────────────────
        paint.setTextSize(96);
        paint.setColor(Color.parseColor("#1a7fdd"));
        canvas.drawText("STRIKE", cx + 1, strikeY + 2, paint); // depth layer
        paint.setColor(Color.parseColor("#44aaff"));
        canvas.drawText("STRIKE", cx, strikeY, paint);
        // Top highlight shimmer
        paint.setColor(Color.argb(70, 160, 220, 255));
        canvas.drawText("STRIKE", cx - 1, strikeY - 1, paint);

        paint.setFakeBoldText(false);
        canvas.restore();
    }

    private void drawHomeScreen(Canvas canvas) {
        drawSky(canvas);
        frameCount++;
        float W = screenWidth, H = screenHeight, cx = W / 2f;

        // Subtle ambient particles
        if (frameCount % 20 == 0) {
            deathParticles.add(new DeathParticle(
                    40 + random.nextFloat()*(W-80), H*0.95f,
                    (random.nextFloat()-0.5f)*1.0f, -0.7f-random.nextFloat()*1.2f,
                    Color.argb(50, 0, 180+random.nextInt(75), 255), 1.5f+random.nextFloat()*2.5f));
        }
        updateDeathParticles(canvas);

        // ── 1. TITLE — centred at 18% ────────────────────────────────────
        float titleY = H * 0.18f;
        float titlePulse = 1f + (float)Math.sin(frameCount * 0.04f) * 0.018f;
        drawFancyTitle(canvas, cx, titleY, titlePulse);

        // Subtitle line
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.argb(120, 160, 200, 230)); paint.setTextSize(16);
        canvas.drawText("TACTICAL AIR COMBAT", cx, titleY + 52, paint);

        // Separator
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(50, 0, 212, 255));
        canvas.drawLine(cx-140, titleY+64, cx+140, titleY+64, paint);
        paint.setStyle(Paint.Style.FILL);

        // ── 2. STATS PANEL — structured card ─────────────────────────────
        float panelTop = H * 0.30f;
        float panelH   = H * 0.16f;
        float panelL   = W * 0.08f, panelR = W * 0.92f;
        RectF statsPanel = new RectF(panelL, panelTop, panelR, panelTop + panelH);

        // Panel background
        paint.setColor(Color.argb(140, 6, 14, 32));
        canvas.drawRoundRect(statsPanel, 10, 10, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(60, 0, 212, 255));
        canvas.drawRoundRect(statsPanel, 10, 10, paint);
        paint.setStyle(Paint.Style.FILL);

        // Panel header tag
        paint.setColor(Color.argb(100, 0, 212, 255)); paint.setTextSize(12);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("PILOT STATS", panelL + 14, panelTop + 16, paint);

        // Three stat rows
        float leftX  = panelL + 20;
        float rightX = panelR - 20;
        float row1   = panelTop + panelH * 0.38f;
        float row2   = panelTop + panelH * 0.62f;
        float row3   = panelTop + panelH * 0.86f;

        // Draw left-right stat lines
        String[][] stats = {
                {"⭐  Stars",       String.valueOf(totalStarsEver), "#88eeFF"},
                {"🏆  Best Score",  String.valueOf(highScore),      "#00D4FF"},
                {"💰  Coins",       String.valueOf(totalCoins),     "#FFD700"},
        };
        float[] rows = {row1, row2, row3};
        for (int s = 0; s < 3; s++) {
            paint.setColor(Color.argb(160, 160, 185, 215)); paint.setTextSize(17);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(stats[s][0], leftX, rows[s], paint);
            paint.setColor(Color.parseColor(stats[s][2])); paint.setTextSize(17);
            paint.setFakeBoldText(true); paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(stats[s][1], rightX, rows[s], paint);
            paint.setFakeBoldText(false);
        }

        // ── 3. MISSION PATH — visual road ───────────────────────────────
        float pathY   = H * 0.535f;
        float nodeR   = 22f;
        float pathGap = W * 0.28f;
        float pathStartX = cx - pathGap;

        String[] missionNames = {"DEEP\nSPACE", "DESERT", "OCEAN"};
        String[] missionNums  = {"01", "02", "03"};

        // Road line — draw between first and last node
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
        paint.setColor(Color.argb(60, 80, 120, 180));
        canvas.drawLine(pathStartX, pathY, pathStartX + pathGap*2, pathY, paint);

        // Completed segment — up to current map
        if (currentMap > 0) {
            paint.setColor(Color.argb(140, 0, 212, 255));
            canvas.drawLine(pathStartX, pathY, pathStartX + pathGap*currentMap, pathY, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // ── Animated player ship above road ──────────────────────────────
        // Ship hovers between node 0 and current map position
        float shipX = pathStartX + pathGap * currentMap;
        float shipY = pathY - 72f + (float)Math.sin(frameCount * 0.05f) * 8f;
        if (playerSprite != null) {
            float sw = playerSprite.getWidth() * 0.38f;
            float sh = playerSprite.getHeight() * 0.38f;
            // Engine glow
            Paint engGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
            float ep = 0.6f + (float)Math.sin(frameCount * 0.12f) * 0.4f;
            engGlow.setShader(new RadialGradient(shipX, shipY + sh * 0.6f, 22f * ep,
                    new int[]{Color.argb(120, 0, 200, 255), Color.argb(0, 0, 100, 200)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(shipX, shipY + sh * 0.6f, 22f * ep, engGlow);
            // Magnet aura if maxed
            if (permMagnet >= UPGRADE_MAX[4]) {
                Paint magnetAura = new Paint(Paint.ANTI_ALIAS_FLAG);
                float mp = 0.5f + (float)Math.sin(frameCount * 0.08f) * 0.5f;
                magnetAura.setShader(new RadialGradient(shipX, shipY, sw * 1.6f,
                        new int[]{Color.argb((int)(60*mp), 0, 100, 255), Color.argb(0, 0, 60, 200)},
                        null, Shader.TileMode.CLAMP));
                canvas.drawCircle(shipX, shipY, sw * 1.6f, magnetAura);
            }
            canvas.drawBitmap(playerSprite, null,
                    new RectF(shipX-sw, shipY-sh, shipX+sw, shipY+sh), bitmapPaint);
        }

        // ── Node drawing with map strip preview ──────────────────────────
        int[] nodeColors = {
                Color.parseColor("#001a33"), // space - dark blue
                Color.parseColor("#2a1200"), // desert - dark amber
                Color.parseColor("#00111a"), // ocean - dark teal
        };
        int[] nodeAccents = {
                Color.parseColor("#00D4FF"),
                Color.parseColor("#FF9900"),
                Color.parseColor("#00ccff"),
        };

        for (int m = 0; m < 3; m++) {
            float nx = pathStartX + pathGap * m;
            boolean unlocked = m < unlockedMapCount;
            boolean isCurrent = m == currentMap;

            // ── Map strip preview thumbnail — clipped to node circle ──────
            canvas.save();
            Path nodeClip = new Path();
            nodeClip.addCircle(nx, pathY, nodeR - 1f, Path.Direction.CW);
            canvas.clipPath(nodeClip);

            Bitmap previewBm = (m == 1 && desertStrip[0] != null) ? desertStrip[0]
                    : (m == 2 && oceanStrip[0]  != null) ? oceanStrip[0]
                    : null;
            if (previewBm != null && unlocked) {
                Paint previewPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
                previewPaint.setAlpha(90);
                // Accumulating scroll offset so strip moves continuously
                float tileSize = nodeR * 2f;
                float scrollOff = (frameCount * 0.5f + m * 37f) % tileSize;
                android.graphics.Matrix pm = new android.graphics.Matrix();
                // Scale bitmap to fill the node diameter
                float sc = (nodeR * 2f) / Math.min(previewBm.getWidth(), previewBm.getHeight());
                pm.setScale(sc, sc);
                // Two passes for seamless loop
                pm.postTranslate(nx - nodeR, pathY - nodeR + scrollOff - tileSize);
                canvas.drawBitmap(previewBm, pm, previewPaint);
                pm.postTranslate(0, tileSize);
                canvas.drawBitmap(previewBm, pm, previewPaint);
            } else if (m == 0 && unlocked) {
                // Space — draw starfield inside node
                paint.setColor(Color.argb(80, 2, 6, 20));
                canvas.drawCircle(nx, pathY, nodeR, paint);
                for (int st2 = 0; st2 < 12; st2++) {
                    float sx = nx - nodeR + (st2 * 73 + 11) % (int)(nodeR*2);
                    float sy = pathY - nodeR + (st2 * 53 + 7 + (int)(frameCount*0.3f) + st2*7) % (int)(nodeR*2);
                    paint.setColor(Color.argb(120 + st2 * 8, 180, 220, 255));
                    canvas.drawCircle(sx, sy, st2 % 2 == 0 ? 1.2f : 0.7f, paint);
                }
            } else {
                paint.setColor(Color.argb(30, 8, 16, 36));
                canvas.drawCircle(nx, pathY, nodeR, paint);
            }
            canvas.restore();

            // Pulse ring on current node
            if (isCurrent) {
                float pulse = nodeR + 8f + (float)Math.sin(frameCount * 0.1f) * 5f;
                paint.setColor(Color.argb(50, 0, 212, 255));
                canvas.drawCircle(nx, pathY, pulse, paint);
            }

            // Node fill
            paint.setColor(isCurrent ? Color.argb(220, 0, 40, 80) :
                    unlocked ? nodeColors[m] : Color.argb(120, 14, 20, 34));
            canvas.drawCircle(nx, pathY, nodeR, paint);

            // Node border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(isCurrent ? 2.5f : 1.5f);
            paint.setColor(isCurrent ? nodeAccents[m] :
                    unlocked ? Color.argb(140, 60, 120, 200) :
                            Color.argb(60, 60, 80, 120));
            canvas.drawCircle(nx, pathY, nodeR, paint);
            paint.setStyle(Paint.Style.FILL);

            // Number or lock icon inside node
            paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(16);
            paint.setFakeBoldText(true);
            paint.setColor(isCurrent ? Color.WHITE :
                    unlocked ? Color.argb(180, 160, 200, 240) : Color.argb(80, 120, 140, 160));
            canvas.drawText(unlocked ? missionNums[m] : "🔒", nx, pathY + 6, paint);
            paint.setFakeBoldText(false);

            // Stage name below node
            String[] emojiArr = {"🌌", "🏜", "🌊"};
            paint.setTextSize(13);
            paint.setColor(isCurrent ? nodeAccents[m] :
                    unlocked ? Color.argb(140, 160, 190, 220) : Color.argb(60, 100, 120, 150));
            canvas.drawText(emojiArr[m], nx, pathY - nodeR - 18, paint);
            canvas.drawText(missionNames[m].replace("\n"," "), nx, pathY + nodeR + 18, paint);

            // Per-map best score below name
            if (unlocked && mapHighScore[m] > 0) {
                paint.setTextSize(11);
                paint.setColor(Color.argb(100, 255, 215, 0));
                canvas.drawText("BEST "+mapHighScore[m], nx, pathY + nodeR + 33, paint);
            }

            // ✓ checkmark for completed stages
            if (unlocked && !isCurrent && m < currentMap) {
                paint.setColor(Color.parseColor("#44dd88")); paint.setTextSize(14);
                canvas.drawText("✓", nx, pathY - nodeR - 38, paint);
            }
        }

        // ── 4. START MISSION button ───────────────────────────────────────
        boolean sp = menuTouchDown && btnHomeStart != null && btnHomeStart.contains(menuTouchX, menuTouchY);
        drawPrimaryBtn(canvas, btnHomeStart, "START MISSION", sp);

        // ── 5. NAV BUTTONS ────────────────────────────────────────────────
        boolean up = menuTouchDown && btnHomeUpgrades != null && btnHomeUpgrades.contains(menuTouchX, menuTouchY);
        boolean st = menuTouchDown && btnHomeSettings != null && btnHomeSettings.contains(menuTouchX, menuTouchY);
        drawMenuBtn(canvas, btnHomeUpgrades, "⬆  UPGRADES", Color.argb(0,0,0,0), Color.parseColor("#FF6A3D"), up);
        drawMenuBtn(canvas, btnHomeSettings, "⚙  SETTINGS", Color.argb(0,0,0,0), Color.parseColor("#00D4FF"), st);

        // Difficulty badge — small top-right corner
        String diffTxt = difficulty == 0 ? "EASY" : difficulty == 2 ? "HARD" : "NORMAL";
        int diffC = difficulty == 0 ? Color.parseColor("#44dd44")
                : difficulty == 2 ? Color.parseColor("#ff4444") : Color.parseColor("#FFD700");
        paint.setColor(Color.argb(90, 4, 10, 24));
        canvas.drawRoundRect(new RectF(W-100, 8, W-8, 40), 5, 5, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(70, (diffC>>16)&0xFF, (diffC>>8)&0xFF, diffC&0xFF));
        canvas.drawRoundRect(new RectF(W-100, 8, W-8, 40), 5, 5, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(13);
        paint.setColor(diffC);
        canvas.drawText(diffTxt, W-54, 29, paint);

        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ===================== UPGRADE SHOP =====================
    // ===================== UPGRADE SHOP — Hangar Bay =====================
    private static final String[] UPGRADE_NAMES = {"WEAPON","FIRE RATE","ARMOR","SHIELD","MAGNET"};
    private static final String[] UPGRADE_DESC1 = {"Bullet damage","Shot cooldown","Max HP","Shield duration","Coin radius"};
    private static final String[] UPGRADE_NEXT  = {"+25% dmg / shot","−3 frames cd","+10 HP","  +1.5s duration","+30px range"};
    private static final String[] UPGRADE_ICONS = {"🔫","⚡","❤","🛡","🧲"};
    private static final int[]    UPGRADE_MAX   = {3, 5, 5, 5, 5};
    private static final String[] IAP_PRICES    = {"$0.99","$0.99","$0.99","$0.99","$0.99"};

    private int   selectedUpgrade = 0;  // which node is tapped
    private float nodeGlowPhase   = 0f;

    private int[] getPermLevels() {
        return new int[]{permGunLevel, permFireRate, permHealth, permShield, permMagnet};
    }

    private void drawUpgradeShop(Canvas canvas) {
        float W = screenWidth, H = screenHeight, cx = W/2f;
        nodeGlowPhase += 0.06f;
        int[] levels = getPermLevels();

        // ── Animated hangar background ────────────────────────────────────
        // Base dark overlay
        paint.setColor(Color.argb(255, 4, 8, 20));
        canvas.drawRect(0, 0, W, H, paint);

        // Scroll the current map's strip very slowly as hangar floor texture
        Bitmap hangarBm = (currentMap == MAP_DESERT && desertStrip[0] != null) ? desertStrip[0]
                : (currentMap == MAP_OCEAN  && oceanStrip[0]  != null) ? oceanStrip[0]
                : null;
        if (hangarBm != null) {
            Paint hangarPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            hangarPaint.setAlpha(28); // very subtle — just a hint of terrain
            float hTileH = screenHeight;
            float hScroll = (nodeGlowPhase * 1.2f) % hTileH; // slow drift
            android.graphics.Matrix hm = new android.graphics.Matrix();
            float hScaleX = W / hangarBm.getWidth();
            float hScaleY = hTileH / hangarBm.getHeight();
            hm.setScale(hScaleX, hScaleY);
            hm.postTranslate(0, hScroll - hTileH);
            canvas.drawBitmap(hangarBm, hm, hangarPaint);
            hm.postTranslate(0, hTileH);
            canvas.drawBitmap(hangarBm, hm, hangarPaint);
        } else {
            // Space map — subtle star field drift
            Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (int si = 0; si < 30; si++) {
                float sx = (si * 137 + 23) % W;
                float sy = ((si * 89 + 11) + nodeGlowPhase * 0.4f * (0.3f + si % 4 * 0.2f)) % H;
                starPaint.setColor(Color.argb(40 + si % 3 * 15, 180, 210, 255));
                canvas.drawCircle(sx, sy, si % 3 == 0 ? 1.5f : 0.8f, starPaint);
            }
        }

        // Dark vignette over background so UI stays readable
        Paint vigPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        vigPaint.setShader(new RadialGradient(W/2f, H/2f, H * 0.65f,
                new int[]{Color.TRANSPARENT, Color.argb(200, 2, 5, 14)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, H, vigPaint);

        // Hangar grid floor lines — perspective grid at bottom third
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.8f);
        paint.setColor(Color.argb(18, 0, 180, 255));
        float gridY = H * 0.55f;
        for (int gi = 0; gi < 8; gi++) {
            float gy = gridY + gi * (H - gridY) / 7f;
            canvas.drawLine(0, gy, W, gy, paint);
        }
        for (int gi = -4; gi <= 4; gi++) {
            canvas.drawLine(W/2f, gridY, W/2f + gi * W * 0.35f, H, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // ── Top bar ───────────────────────────────────────────────────────
        paint.setShader(new LinearGradient(0,0,0,88,
                Color.argb(255,8,16,38), Color.argb(200,4,10,24), Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,W,88,paint); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(60,0,212,255));
        canvas.drawLine(0,88,W,88,paint); paint.setStyle(Paint.Style.FILL);

        // Back
        boolean bp = menuTouchDown && btnShopBack!=null && btnShopBack.contains(menuTouchX,menuTouchY);
        drawMenuBtn(canvas, btnShopBack, "← BACK", Color.argb(0,0,0,0), Color.parseColor("#7a9abf"), bp);

        // Title
        paint.setColor(Color.parseColor("#00D4FF")); paint.setTextSize(30); paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("UPGRADE BAY", cx, 50, paint);
        paint.setFakeBoldText(false);

        // Coins
        paint.setTextAlign(Paint.Align.RIGHT); paint.setTextSize(20);
        paint.setColor(Color.parseColor("#FFD700"));
        canvas.drawText("⬡ "+totalCoins, W-16, 54, paint);
        paint.setTextAlign(Paint.Align.CENTER);

        // ── Node layout around ship centre ────────────────────────────────
        float shipCY  = H * 0.42f;
        float nodeR   = Math.min(W, H) * 0.30f; // radius of circle of nodes
        float nodeSize= 80f;

        // Node positions: top=WEAPON, left=SHIELD, right=FIRERATE, bot-L=MAGNET, bot-R=ARMOR
        float[] nodeAngles = {-90f, 180f, 0f, -210f, -330f}; // degrees
        float[] nodeX = new float[5];
        float[] nodeY = new float[5];
        for (int u=0; u<5; u++) {
            double rad = Math.toRadians(nodeAngles[u]);
            nodeX[u] = cx + (float)Math.cos(rad)*nodeR;
            nodeY[u] = shipCY + (float)Math.sin(rad)*nodeR;
            btnBuyUpgrade[u] = new RectF(nodeX[u]-nodeSize/2, nodeY[u]-nodeSize/2,
                    nodeX[u]+nodeSize/2, nodeY[u]+nodeSize/2);
        }

        // Connector lines from ship to each node
        paint.setStyle(Paint.Style.STROKE);
        for (int u=0; u<5; u++) {
            boolean sel = selectedUpgrade==u;
            paint.setStrokeWidth(sel ? 2f : 1f);
            paint.setColor(sel ? Color.argb(120,0,212,255) : Color.argb(35,60,120,180));
            canvas.drawLine(cx, shipCY, nodeX[u], nodeY[u], paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // ── Ship in centre ────────────────────────────────────────────────
        // Rotating ring behind ship
        float ringR = nodeSize*0.88f;
        float ringPulse = 30f + (float)Math.sin(nodeGlowPhase*0.7f)*8f;
        paint.setColor(Color.argb((int)ringPulse, 0, 212, 255));
        canvas.drawCircle(cx, shipCY, ringR*1.6f, paint);
        paint.setColor(Color.argb((int)(ringPulse*0.5f), 0, 180, 220));
        canvas.drawCircle(cx, shipCY, ringR*2.2f, paint);

        // Draw player plane sprite with gun-level upgrade visuals
        if (playerSprite != null) {
            float hw2 = playerSprite.getWidth()*0.55f, hh2 = playerSprite.getHeight()*0.55f;
            // Magnet aura if maxed — glowing blue wings
            if (permMagnet >= UPGRADE_MAX[4]) {
                Paint magAura = new Paint(Paint.ANTI_ALIAS_FLAG);
                float mp = 0.5f + (float)Math.sin(nodeGlowPhase * 0.9f) * 0.5f;
                magAura.setShader(new RadialGradient(cx, shipCY, hw2 * 2.2f,
                        new int[]{Color.argb((int)(80*mp), 0, 100, 255), Color.argb(0, 0, 60, 200)},
                        null, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, shipCY, hw2 * 2.2f, magAura);
            }
            // Shield aura if maxed
            if (permShield >= UPGRADE_MAX[3]) {
                paint.setColor(Color.argb((int)(25 + 20*Math.sin(nodeGlowPhase*1.1f)), 0, 180, 255));
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
                canvas.drawCircle(cx, shipCY, hw2 * 1.5f, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            // ── Gun-level extra cannons drawn around the ship ────────────
            // Level 0: single centre barrel (default, nothing extra drawn)
            // Level 1: twin barrels (+side wings)
            // Level 2: quad barrels
            // Level 3: wide V formation
            Paint cannonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            cannonPaint.setStyle(Paint.Style.STROKE);
            cannonPaint.setStrokeCap(Paint.Cap.ROUND);
            if (permGunLevel >= 1) {
                // Twin wing cannons
                cannonPaint.setColor(Color.argb(200, 0, 200, 255));
                cannonPaint.setStrokeWidth(5f);
                float cy1 = shipCY - hh2 * 0.15f;
                canvas.drawLine(cx - hw2*0.55f, cy1, cx - hw2*0.55f, cy1 - hh2*0.65f, cannonPaint);
                canvas.drawLine(cx + hw2*0.55f, cy1, cx + hw2*0.55f, cy1 - hh2*0.65f, cannonPaint);
            }
            if (permGunLevel >= 2) {
                // Outer extended cannons
                cannonPaint.setColor(Color.argb(160, 0, 230, 150));
                cannonPaint.setStrokeWidth(4f);
                float cy2 = shipCY + hh2 * 0.05f;
                canvas.drawLine(cx - hw2*0.85f, cy2, cx - hw2*0.85f, cy2 - hh2*0.50f, cannonPaint);
                canvas.drawLine(cx + hw2*0.85f, cy2, cx + hw2*0.85f, cy2 - hh2*0.50f, cannonPaint);
            }
            if (permGunLevel >= 3) {
                // Wide V angled guns — max level
                cannonPaint.setColor(Color.argb(200, 255, 180, 0));
                cannonPaint.setStrokeWidth(5f);
                canvas.drawLine(cx - hw2*0.3f, shipCY + hh2*0.1f,
                        cx - hw2*1.0f, shipCY - hh2*0.7f, cannonPaint);
                canvas.drawLine(cx + hw2*0.3f, shipCY + hh2*0.1f,
                        cx + hw2*1.0f, shipCY - hh2*0.7f, cannonPaint);
            }
            // Gun muzzle glow dots
            if (permGunLevel >= 1) {
                Paint muzzle = new Paint(Paint.ANTI_ALIAS_FLAG);
                float ep2 = 0.6f + (float)Math.sin(nodeGlowPhase * 2.2f) * 0.4f;
                muzzle.setColor(Color.argb((int)(180*ep2), 0, 220, 255));
                float muzzleY = shipCY - hh2 * 0.80f;
                canvas.drawCircle(cx - hw2*0.55f, muzzleY, 4f * ep2, muzzle);
                canvas.drawCircle(cx + hw2*0.55f, muzzleY, 4f * ep2, muzzle);
            }

            canvas.drawBitmap(playerSprite, null, new RectF(cx-hw2, shipCY-hh2, cx+hw2, shipCY+hh2), bitmapPaint);
        } else {
            // Fallback shape
            paint.setColor(Color.parseColor("#1a3a5a"));
            canvas.drawCircle(cx, shipCY, 40, paint);
            paint.setColor(Color.parseColor("#00D4FF")); paint.setTextSize(36); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("✈", cx, shipCY+12, paint);
        }

        // ── Upgrade nodes ─────────────────────────────────────────────────
        for (int u=0; u<5; u++) {
            boolean sel = selectedUpgrade==u;
            boolean maxed = levels[u]>=UPGRADE_MAX[u];
            float nx = nodeX[u], ny = nodeY[u];
            float glow = sel ? 0.6f + (float)Math.sin(nodeGlowPhase)*0.4f : 0.15f;

            // Outer glow ring
            paint.setColor(Color.argb((int)(glow*120), maxed?255:0, maxed?215:212, maxed?0:255));
            canvas.drawCircle(nx, ny, nodeSize*0.65f, paint);

            // Node bg
            paint.setColor(sel ? Color.argb(220,0,30,60) : Color.argb(180,6,14,30));
            canvas.drawRoundRect(btnBuyUpgrade[u], 12, 12, paint);

            // Node border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sel ? 2.2f : 1f);
            paint.setColor(maxed ? Color.argb(sel?255:160, 255,215,0)
                    : sel   ? Color.argb(230,0,212,255) : Color.argb(70,60,120,180));
            canvas.drawRoundRect(btnBuyUpgrade[u], 12, 12, paint);
            paint.setStyle(Paint.Style.FILL);

            // Icon
            paint.setTextSize(26); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(UPGRADE_ICONS[u], nx, ny-6, paint);

            // Level dots below icon
            int lv = levels[u], maxLv = UPGRADE_MAX[u];
            float dotW = Math.min(8f, (nodeSize-16)/maxLv);
            float dotsW = dotW*maxLv + (maxLv-1)*2f;
            float dotX = nx - dotsW/2f;
            for (int p=0; p<maxLv; p++) {
                paint.setColor(p<lv ? (maxed ? Color.parseColor("#FFD700") : Color.parseColor("#00D4FF"))
                        : Color.argb(50,150,170,200));
                canvas.drawRoundRect(new RectF(dotX,ny+14,dotX+dotW,ny+20),2,2,paint);
                dotX += dotW+2;
            }

            // MAX label
            if (maxed) {
                paint.setColor(Color.parseColor("#FFD700")); paint.setTextSize(11);
                canvas.drawText("MAX", nx, ny+32, paint);
            }
        }

        // ── Bottom detail panel ───────────────────────────────────────────
        float panelTop = H*0.70f;
        paint.setColor(Color.argb(200,4,10,24));
        canvas.drawRect(0, panelTop, W, H, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(80,0,212,255));
        canvas.drawLine(0,panelTop,W,panelTop,paint);
        paint.setStyle(Paint.Style.FILL);

        int sel2 = selectedUpgrade;
        int lv2  = levels[sel2];
        int max2 = UPGRADE_MAX[sel2];
        boolean maxed2 = lv2>=max2;

        // Upgrade name
        paint.setColor(Color.parseColor("#00D4FF")); paint.setTextSize(22); paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(UPGRADE_ICONS[sel2]+"  "+UPGRADE_NAMES[sel2]+" SYSTEM  LV."+lv2, 20, panelTop+34, paint);
        paint.setFakeBoldText(false);

        // Level bar
        float barX=20, barY=panelTop+46, barW=W-40, barH=6;
        paint.setColor(Color.argb(60,60,80,120));
        canvas.drawRoundRect(new RectF(barX,barY,barX+barW,barY+barH),3,3,paint);
        float fillRatio = max2>0 ? (float)lv2/max2 : 0;
        paint.setColor(maxed2 ? Color.parseColor("#FFD700") : Color.parseColor("#00D4FF"));
        if (fillRatio>0)
            canvas.drawRoundRect(new RectF(barX,barY,barX+barW*fillRatio,barY+barH),3,3,paint);

        // Stat lines with current/next comparison bars
        paint.setColor(Color.argb(160,160,185,215)); paint.setTextSize(17);
        canvas.drawText(UPGRADE_DESC1[sel2], 20, panelTop+72, paint);
        if (!maxed2) {
            paint.setColor(Color.parseColor("#44ee88")); paint.setTextSize(17);
            canvas.drawText("Next:  "+UPGRADE_NEXT[sel2], 20, panelTop+94, paint);

            // ── Current vs Next progress bar ─────────────────────────────
            float cmpX = 20, cmpY = panelTop+108, cmpW = W-40, cmpH = 8f;
            // Track
            paint.setColor(Color.argb(50, 60, 80, 120));
            canvas.drawRoundRect(new RectF(cmpX, cmpY, cmpX+cmpW, cmpY+cmpH), 4, 4, paint);
            // Current level fill
            float curFill = max2>0 ? (float)lv2/max2 : 0;
            paint.setColor(Color.parseColor("#00D4FF"));
            if (curFill>0) canvas.drawRoundRect(new RectF(cmpX, cmpY, cmpX+cmpW*curFill, cmpY+cmpH), 4, 4, paint);
            // Next level fill (lighter, shows what upgrade adds)
            float nxtFill = max2>0 ? (float)(lv2+1)/max2 : 0;
            paint.setColor(Color.argb(100, 0, 230, 120));
            canvas.drawRoundRect(new RectF(cmpX+cmpW*curFill, cmpY, cmpX+cmpW*nxtFill, cmpY+cmpH), 4, 4, paint);
            // Labels
            paint.setTextSize(11); paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.argb(120, 160, 200, 220));
            canvas.drawText("LV."+lv2, cmpX, cmpY-2, paint);
            paint.setColor(Color.argb(120, 0, 230, 120));
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("→ LV."+(lv2+1), cmpX+cmpW, cmpY-2, paint);

            // ── Weapon spread diagram for WEAPON upgrade ──────────────────
            if (sel2 == 0) {
                float diagX = W * 0.62f, diagY = panelTop + 80f;
                paint.setColor(Color.argb(60, 0, 212, 255)); paint.setTextSize(11);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("SPREAD", diagX, diagY - 4, paint);
                // Draw bullet lines representing current gun spread
                Paint bulletLine = new Paint(Paint.ANTI_ALIAS_FLAG);
                bulletLine.setStyle(Paint.Style.STROKE); bulletLine.setStrokeWidth(2.5f);
                bulletLine.setStrokeCap(Paint.Cap.ROUND);
                int bCount = lv2 == 0 ? 1 : lv2 == 1 ? 2 : lv2 == 2 ? 4 : 5;
                float[] spreads = bCount == 1 ? new float[]{0}
                        : bCount == 2 ? new float[]{-12, 12}
                        : bCount == 4 ? new float[]{-20,-8,8,20}
                        : new float[]{-26,-13,0,13,26};
                for (float sp : spreads) {
                    bulletLine.setColor(Color.argb(200, 0, 220, 255));
                    canvas.drawLine(diagX + sp*0.4f, diagY + 22, diagX + sp*0.4f, diagY + 5, bulletLine);
                }
                // Ship icon at bottom
                paint.setColor(Color.argb(120, 0, 212, 255)); paint.setTextSize(16);
                canvas.drawText("✈", diagX, diagY + 36, paint);
            }

            paint.setColor(Color.parseColor("#FFD700")); paint.setTextSize(17);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Cost:  "+IAP_PRICES[sel2], 20, panelTop+122, paint);
        } else {
            paint.setColor(Color.parseColor("#FFD700")); paint.setTextSize(17);
            canvas.drawText("✓  Fully upgraded", 20, panelTop+94, paint);
        }

        // Upgrade / Max button
        float buyBtnY = panelTop+136, buyBtnH = 62;
        RectF buyBtn = new RectF(W*0.12f, buyBtnY, W*0.88f, buyBtnY+buyBtnH);
        btnBuyUpgrade[sel2] = buyBtn;  // override with actual buy button for selected
        if (!maxed2) {
            // Green buy button
            paint.setShader(new LinearGradient(0,buyBtnY,0,buyBtnY+buyBtnH,
                    Color.argb(230,0,60,30), Color.argb(230,0,40,18), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(buyBtn,8,8,paint); paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
            paint.setColor(Color.argb(160,0,212,100));
            canvas.drawRoundRect(buyBtn,8,8,paint); paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE); paint.setTextSize(22); paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("UPGRADE  "+IAP_PRICES[sel2], buyBtn.centerX(), buyBtn.centerY()+8, paint);
            paint.setFakeBoldText(false);
            paint.setColor(Color.argb(140,0,212,100)); paint.setTextSize(13);
            canvas.drawText("One-time purchase · Permanent", buyBtn.centerX(), buyBtn.centerY()+24, paint);
        } else {
            paint.setColor(Color.argb(120,20,30,20));
            canvas.drawRoundRect(buyBtn,8,8,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
            paint.setColor(Color.argb(80,255,215,0));
            canvas.drawRoundRect(buyBtn,8,8,paint); paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#FFD700")); paint.setTextSize(22); paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("✓  MAX LEVEL", buyBtn.centerX(), buyBtn.centerY()+8, paint);
            paint.setFakeBoldText(false);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ===================== SETTINGS — Full Screen ========================
    private float sfxVolume   = 1.0f;   // 0.0–1.0
    private float musicVolume = 0.5f;   // 0.0–1.0 (for future music)

    // Slider drag state
    private int   settingsDragSlot = -1; // -1=none, 0=sfx, 1=music
    private RectF sliderSfxTrack, sliderMusicTrack;
    private RectF btnResetDefaults;

    // ===================== SETTINGS — Full Screen + Scroll ===============
    private void drawSettingsScreen(Canvas canvas) {
        float W = screenWidth, H = screenHeight, cx = W/2f;

        // Sky background dimmed
        drawSky(canvas);
        paint.setColor(Color.argb(210, 4, 8, 20));
        canvas.drawRect(0, 0, W, H, paint);

        // ── Top bar ───────────────────────────────────────────────────────
        float barH = H * 0.10f;
        paint.setShader(new LinearGradient(0,0,0,barH,
                Color.argb(255,8,16,40), Color.argb(240,4,10,24), Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,W,barH,paint); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f);
        paint.setColor(Color.argb(80,0,212,255));
        canvas.drawLine(0,barH,W,barH,paint); paint.setStyle(Paint.Style.FILL);

        boolean bpBack = menuTouchDown && btnSettingsBack!=null
                && btnSettingsBack.contains(menuTouchX, menuTouchY);
        drawMenuBtn(canvas, btnSettingsBack, "← BACK",
                Color.argb(0,0,0,0), Color.parseColor("#7a9abf"), bpBack);
        paint.setColor(Color.parseColor("#E0F7FF"));
        paint.setTextSize(32); paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("SETTINGS", cx, barH*0.62f, paint);
        paint.setFakeBoldText(false); paint.setTextAlign(Paint.Align.LEFT);

        // ── Scrollable body ───────────────────────────────────────────────
        float lx = W*0.04f, rx = W*0.96f;
        float rowH   = H * 0.082f;
        float slH    = H * 0.096f;
        float secGap = H * 0.022f;
        float itemGap= 4f;

        canvas.save();
        canvas.clipRect(0, barH, W, H);
        canvas.translate(0, barH - settingsScrollY);

        float y = H * 0.015f; // padding inside scroll area

        // ──────── AUDIO ───────────────────────────────────────────────────
        y = stgCard(canvas, "AUDIO", y, lx, rx, new float[]{slH+itemGap+slH});
        y = stgSliderRow(canvas, "SFX",   sfxVolume,   y, slH, lx, rx, 0);
        y += itemGap;
        y = stgSliderRow(canvas, "Music", musicVolume, y, slH, lx, rx, 1);
        y += secGap;

        // ──────── CONTROLS ────────────────────────────────────────────────
        y = stgCard(canvas, "CONTROLS", y, lx, rx, new float[]{rowH+itemGap+rowH});
        y = stgSegRow(canvas, "Sensitivity",
                new String[]{"LOW","MID","HIGH"}, new float[]{0.7f,1.0f,1.4f}, dragSensitivity,
                y, rowH, lx, rx, new RectF[]{btnSensLow,btnSensMed,btnSensHigh},
                Color.parseColor("#00D4FF"));
        y += itemGap;
        y = stgToggleRow(canvas, "Vibration", vibrationEnabled, y, rowH, lx, rx, btnVibToggle);
        y += secGap;

        // ──────── GAMEPLAY ────────────────────────────────────────────────
        y = stgCard(canvas, "GAMEPLAY", y, lx, rx, new float[]{rowH});
        y = stgSegRow(canvas, "Difficulty",
                new String[]{"EASY","NORM","HARD"}, new float[]{0f,1f,2f}, (float)difficulty,
                y, rowH, lx, rx,
                new RectF[]{btnSettingsDiff0,btnSettingsDiff1,btnSettingsDiff2},
                difficulty==0?Color.parseColor("#44dd44"):
                        difficulty==2?Color.parseColor("#ff5555"):Color.parseColor("#FFD700"));
        y += secGap;

        // ──────── MAP ─────────────────────────────────────────────────────
        float mapChipH = rowH;
        y = stgCard(canvas, "MAP SELECT", y, lx, rx, new float[]{mapChipH});

        // Stage chips inline
        String[] mNames = {"🌌 SPACE","🏜 DESERT","🌊 OCEAN"};
        RectF[] mBtns = {btnSettingsMap0,btnSettingsMap1,
                btnSettingsMap2!=null?btnSettingsMap2:btnSettingsMap1};
        float cW = (rx-lx)/3f;
        for (int m=0; m<3; m++) {
            boolean unlocked=m<unlockedMapCount, sel=currentMap==m&&unlocked;
            float bx=lx+m*cW;
            RectF chip=new RectF(bx+3,y,bx+cW-3,y+mapChipH);
            if(mBtns[m]!=null) mBtns[m].set(bx, y, bx+cW, y+mapChipH);
            paint.setColor(sel?Color.argb(220,0,40,80):Color.argb(80,8,15,32));
            canvas.drawRoundRect(chip,6,6,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(sel?1.5f:0.8f);
            paint.setColor(sel?Color.argb(200,0,212,255):unlocked?Color.argb(50,80,120,180):Color.argb(25,60,80,100));
            canvas.drawRoundRect(chip,6,6,paint); paint.setStyle(Paint.Style.FILL);
            paint.setColor(sel?Color.parseColor("#00D4FF"):unlocked?Color.argb(150,160,190,220):Color.argb(60,100,120,140));
            paint.setTextSize(14); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(unlocked?mNames[m]:"🔒",chip.centerX(),chip.centerY()+6,paint);
        }
        y += mapChipH + secGap;

        // ──────── RESET button ────────────────────────────────────────────
        float btnW=W*0.5f, btnX=cx-btnW/2f;
        btnResetDefaults=new RectF(btnX,y,btnX+btnW,y+rowH*0.85f);
        boolean resetPr=menuTouchDown&&btnResetDefaults.contains(menuTouchX,menuTouchY);
        paint.setColor(resetPr?Color.argb(180,55,12,12):Color.argb(100,25,8,8));
        canvas.drawRoundRect(btnResetDefaults,6,6,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(120,200,65,65));
        canvas.drawRoundRect(btnResetDefaults,6,6,paint); paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(200,230,100,100)); paint.setTextSize(rowH*0.34f);
        paint.setFakeBoldText(true); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("RESET DEFAULTS",btnResetDefaults.centerX(),btnResetDefaults.centerY()+7,paint);
        paint.setFakeBoldText(false);
        y += rowH + secGap;

        // Track content height for scroll clamping
        settingsContentH = y;
        canvas.restore();

        // Clamp & apply scroll
        float maxScroll = Math.max(0, settingsContentH - (H - barH));
        settingsScrollY = Math.max(0, Math.min(settingsScrollY, maxScroll));

        // Scroll indicator — thin right bar
        if (maxScroll > 0) {
            float trackH = H - barH;
            float thumbH = trackH * ((H-barH) / settingsContentH);
            float thumbY = barH + (settingsScrollY / maxScroll) * (trackH - thumbH);
            paint.setColor(Color.argb(60, 0, 212, 255));
            canvas.drawRoundRect(new RectF(W-6, barH, W-2, H), 3, 3, paint);
            paint.setColor(Color.argb(160, 0, 212, 255));
            canvas.drawRoundRect(new RectF(W-6, thumbY, W-2, thumbY+thumbH), 3, 3, paint);
        }

        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Draw a card background for a settings section and return y after header */
    private float stgCard(Canvas canvas, String label, float y,
                          float lx, float rx, float[] contentHeights) {
        float headerH = screenHeight * 0.038f;
        float totalContentH = 0;
        for (float h : contentHeights) totalContentH += h;
        float cardH = headerH + totalContentH + screenHeight * 0.012f;

        // Card bg
        paint.setColor(Color.argb(80, 6, 14, 32));
        canvas.drawRoundRect(new RectF(lx, y, rx, y+cardH), 10, 10, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(60, 0, 212, 255));
        canvas.drawRoundRect(new RectF(lx, y, rx, y+cardH), 10, 10, paint);
        paint.setStyle(Paint.Style.FILL);

        // Header label
        paint.setColor(Color.parseColor("#00D4FF"));
        paint.setTextSize(screenHeight * 0.018f); paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(label, lx+14, y + headerH*0.78f, paint);
        paint.setFakeBoldText(false);

        // Divider under header
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.8f);
        paint.setColor(Color.argb(50, 0, 212, 255));
        canvas.drawLine(lx+10, y+headerH, rx-10, y+headerH, paint);
        paint.setStyle(Paint.Style.FILL);

        return y + headerH + screenHeight * 0.006f; // return y inside card
    }

    /** Toggle row — label left, sliding pill right */
    private float stgToggleRow(Canvas canvas, String label, boolean on,
                               float y, float rowH, float lx, float rx, RectF btnRef) {
        paint.setColor(Color.argb(210, 200, 220, 240));
        paint.setTextSize(rowH*0.38f); paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(label, lx+14, y+rowH*0.65f, paint);

        float pillW=rowH*2.1f, pillH=rowH*0.54f;
        float pillX=rx-pillW-12, pillY=y+(rowH-pillH)/2f;
        if(btnRef!=null) btnRef.set(pillX-8, y, pillX+pillW+8, y+rowH);

        paint.setColor(on?Color.argb(220,0,75,38):Color.argb(180,55,15,15));
        canvas.drawRoundRect(new RectF(pillX,pillY,pillX+pillW,pillY+pillH),pillH/2,pillH/2,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        paint.setColor(on?Color.argb(200,0,212,100):Color.argb(100,200,55,55));
        canvas.drawRoundRect(new RectF(pillX,pillY,pillX+pillW,pillY+pillH),pillH/2,pillH/2,paint);
        paint.setStyle(Paint.Style.FILL);

        float knobR=pillH*0.38f;
        float knobX=on?pillX+pillW-knobR-5:pillX+knobR+5;
        paint.setColor(on?Color.parseColor("#00D4FF"):Color.parseColor("#cc3333"));
        canvas.drawCircle(knobX,pillY+pillH/2f,knobR,paint);

        paint.setColor(Color.WHITE); paint.setTextSize(pillH*0.44f);
        paint.setTextAlign(Paint.Align.CENTER);
        float labelCx=on?pillX+pillW*0.30f:pillX+pillW*0.70f;
        canvas.drawText(on?"ON":"OFF",labelCx,pillY+pillH*0.66f,paint);

        return y+rowH+3;
    }

    /** Slider row — glowing fill track with draggable handle */
    private float stgSliderRow(Canvas canvas, String label, float value,
                               float y, float rowH, float lx, float rx, int slot) {
        paint.setColor(Color.argb(210,200,220,240));
        paint.setTextSize(rowH*0.32f); paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(label, lx+14, y+rowH*0.38f, paint);
        paint.setColor(Color.parseColor("#00D4FF")); paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText((int)(value*100)+"%", rx-12, y+rowH*0.38f, paint);

        float trkL=lx+14, trkR=rx-14, trkY=y+rowH*0.68f, trkH=rowH*0.20f;
        // Store in content coords — touch handler converts screen→content
        RectF track=new RectF(trkL, trkY-14, trkR, trkY+trkH+14);
        if(slot==0) sliderSfxTrack=track; else sliderMusicTrack=track;

        paint.setColor(Color.argb(100,20,35,70));
        canvas.drawRoundRect(new RectF(trkL,trkY,trkR,trkY+trkH),trkH/2,trkH/2,paint);
        float fillX=trkL+(trkR-trkL)*value;
        if(value>0.01f) {
            paint.setShader(new LinearGradient(trkL,0,trkR,0,
                    Color.argb(200,0,140,200),Color.argb(255,0,212,255),Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(trkL,trkY,fillX,trkY+trkH),trkH/2,trkH/2,paint);
            paint.setShader(null);
        }
        float hR=trkH*1.7f;
        paint.setColor(Color.WHITE); canvas.drawCircle(fillX,trkY+trkH/2f,hR,paint);
        paint.setColor(Color.parseColor("#00D4FF")); canvas.drawCircle(fillX,trkY+trkH/2f,hR*0.52f,paint);

        return y+rowH+3;
    }

    /** Segmented 3-button row — fixed overlap issues */
    private float stgSegRow(Canvas canvas, String label, String[] opts,
                            float[] vals, float cur, float y, float rowH,
                            float lx, float rx, RectF[] refs, int activeColor) {
        // Label takes 36% of width, segments take 64%
        float labelW = (rx-lx)*0.36f;
        float segLeft = lx + labelW;
        float segW = (rx-segLeft) / opts.length;

        paint.setColor(Color.argb(210,200,220,240));
        paint.setTextSize(rowH*0.34f); paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(label, lx+14, y+rowH*0.65f, paint);

        for (int s=0; s<opts.length; s++) {
            boolean sel = Math.abs(cur-vals[s])<0.05f;
            float l = segLeft + s*segW + 5;
            float r = segLeft + (s+1)*segW - 5;
            RectF box = new RectF(l, y+rowH*0.14f, r, y+rowH*0.86f);
            // Store ref in content coords — touch handler converts screen→content
            if(refs[s]!=null) refs[s].set(segLeft+s*segW, y, segLeft+(s+1)*segW, y+rowH);

            int ar=(activeColor>>16)&0xFF, ag=(activeColor>>8)&0xFF, ab=activeColor&0xFF;
            paint.setColor(sel?Color.argb(200,ar,ag,ab):Color.argb(60,15,28,55));
            canvas.drawRoundRect(box,8,8,paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sel?2f:1f);
            paint.setColor(sel?Color.argb(220,ar,ag,ab):Color.argb(55,100,140,190));
            canvas.drawRoundRect(box,8,8,paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setColor(sel?Color.WHITE:Color.argb(150,170,195,225));
            paint.setTextSize(rowH*0.28f); paint.setFakeBoldText(sel);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(opts[s], box.centerX(), box.centerY()+rowH*0.10f, paint);
            paint.setFakeBoldText(false);
        }
        return y+rowH+3;
    }

    private void drawDifficultyScreen(Canvas canvas) { drawHomeScreen(canvas); }

    private void drawStartScreen(Canvas canvas) {
        drawSky(canvas);
        frameCount++;

        float cx = screenWidth / 2f;

        // Animate popup open/close
        if (difficultyPopupOpen) {
            difficultyPopupAnim = Math.min(1f, difficultyPopupAnim + 0.08f);
        } else {
            difficultyPopupAnim = Math.max(0f, difficultyPopupAnim - 0.1f);
        }

        // Ambient rising sparks
        if (frameCount % 12 == 0) {
            deathParticles.add(new DeathParticle(
                    60 + random.nextFloat() * (screenWidth - 120),
                    screenHeight * 0.85f,
                    (random.nextFloat() - 0.5f) * 2f,
                    -1.5f - random.nextFloat() * 2f,
                    Color.argb(100, 80 + random.nextInt(80), 140, 255),
                    2f + random.nextFloat() * 5f));
        }
        updateDeathParticles(canvas);

        // Player plane — static position in upper area
        planeY = screenHeight * 0.30f;
        planeX = cx - 120f;
        drawPlayerPlane(canvas);

        // ── Fancy title ───────────────────────────────────────────────────
        float titlePulse = 1f + (float)Math.sin(frameCount * 0.05f) * 0.035f;
        drawFancyTitle(canvas, cx, screenHeight * 0.10f, titlePulse);

        // Glowing separator line under title
        float lineAlpha = (float)(140 + Math.sin(frameCount * 0.07f) * 60);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        paint.setColor(Color.argb((int)lineAlpha, 80, 160, 255));
        canvas.drawLine(cx - 200, screenHeight * 0.295f, cx + 200, screenHeight * 0.295f, paint);
        paint.setStyle(Paint.Style.FILL);

        // ── Instruction card ──────────────────────────────────────────────
        float cardY = screenHeight * 0.52f;
        float cardW = screenWidth * 0.84f;
        float cardX = cx - cardW / 2f;
        // Card backdrop
        paint.setColor(Color.argb(80, 5, 15, 40));
        canvas.drawRoundRect(new RectF(cardX, cardY - 16, cardX + cardW, cardY + 108), 20, 20, paint);
        // Card border
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f);
        paint.setColor(Color.argb(50, 100, 160, 255));
        canvas.drawRoundRect(new RectF(cardX, cardY - 16, cardX + cardW, cardY + 108), 20, 20, paint);
        paint.setStyle(Paint.Style.FILL);
        // Tips
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(20);
        paint.setColor(Color.argb(180, 120, 190, 255));
        canvas.drawText("✈  Drag to fly your ship anywhere", cx, cardY + 14, paint);
        paint.setColor(Color.argb(170, 255, 210, 80));
        canvas.drawText("⭐  Collect gold stars → Upgrade gun", cx, cardY + 46, paint);
        paint.setColor(Color.argb(170, 80, 255, 170));
        canvas.drawText("🚀  Rescue astronauts → Bonus score", cx, cardY + 78, paint);

        // ── START button ──────────────────────────────────────────────────
        boolean startPressed = menuTouchDown && btnStart.contains(menuTouchX, menuTouchY);
        float glowR = 6f + (float)Math.sin(frameCount * 0.1f) * 4f;
        paint.setColor(Color.argb(40, 255, 160, 0));
        canvas.drawRoundRect(new RectF(btnStart.left - glowR, btnStart.top - glowR,
                btnStart.right + glowR, btnStart.bottom + glowR), 36, 36, paint);
        drawMenuBtn(canvas, btnStart, "START!", Color.parseColor("#b35500"), Color.WHITE, startPressed);

        // ── SELECT DIFFICULTY button ──────────────────────────────────────
        String diffLabel = difficulty == 0 ? "⬤ EASY" : difficulty == 2 ? "⬤ HARD" : "⬤ NORMAL";
        int diffAccent = difficulty == 0 ? Color.parseColor("#44ee44")
                : difficulty == 2 ? Color.parseColor("#ff5555")
                : Color.parseColor("#FFD700");
        boolean diffBtnPressed = menuTouchDown && btnDifficultySelect.contains(menuTouchX, menuTouchY);
        // Custom draw — outlined style with colour accent
        float dCx = btnDifficultySelect.centerX(), dCy = btnDifficultySelect.centerY();
        float dScale = diffBtnPressed ? 0.95f : 1.0f;
        canvas.save();
        canvas.scale(dScale, dScale, dCx, dCy);
        paint.setColor(Color.argb(100, 10, 20, 50));
        canvas.drawRoundRect(btnDifficultySelect, 22, 22, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(180, Color.red(diffAccent), Color.green(diffAccent), Color.blue(diffAccent)));
        canvas.drawRoundRect(btnDifficultySelect, 22, 22, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(diffAccent); paint.setTextSize(26);
        paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true);
        canvas.drawText(diffLabel, dCx, dCy + 10, paint);
        paint.setFakeBoldText(false);
        // Small dropdown arrow
        paint.setColor(Color.argb(160, 220, 220, 220)); paint.setTextSize(18);
        canvas.drawText("▼", dCx + 110, dCy + 8, paint);
        canvas.restore();

        // ── SELECT MAP button ─────────────────────────────────────────────
        String mapLabel = getMapEmoji() + " " + getMapName();
        int mapAccent   = getMapColor() | 0xFF000000;  // opaque version
        boolean mapBtnPressed = menuTouchDown && btnMapSelect != null && btnMapSelect.contains(menuTouchX, menuTouchY);
        float mCx = btnMapSelect.centerX(), mCy = btnMapSelect.centerY();
        float mScale = mapBtnPressed ? 0.95f : 1.0f;
        canvas.save();
        canvas.scale(mScale, mScale, mCx, mCy);
        paint.setColor(Color.argb(100, 10, 20, 50));
        canvas.drawRoundRect(btnMapSelect, 22, 22, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(180, Color.red(mapAccent), Color.green(mapAccent), Color.blue(mapAccent)));
        canvas.drawRoundRect(btnMapSelect, 22, 22, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mapAccent); paint.setTextSize(24);
        paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true);
        canvas.drawText(mapLabel + "  ⇄", mCx, mCy + 9, paint);
        paint.setFakeBoldText(false);
        // Difficulty indicator for map 2
        if (currentMap != MAP_SPACE) {
            paint.setColor(Color.argb(140, 255, 220, 100)); paint.setTextSize(14);
            String diffTxt2 = currentMap == MAP_DESERT ? "+8% harder, +10% coins" : "-5% easier, +20% coins";
            canvas.drawText(diffTxt2, mCx, mCy + 28, paint);
        }
        canvas.restore();
        canvas.drawText("Best: " + highScore + "   ★ " + totalStarsEver + "   $ " + totalCoins,
                cx, screenHeight * 0.92f, paint);

        // ── Difficulty popup overlay ──────────────────────────────────────
        if (difficultyPopupAnim > 0f) {
            drawDifficultyPopup(canvas, difficultyPopupAnim);
        }

        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Animated popup panel with 3 difficulty choices */
    private void drawDifficultyPopup(Canvas canvas, float anim) {
        float cx = screenWidth / 2f;

        // Elastic ease-out for scale
        float scale = 0.85f + anim * 0.15f;
        float alpha = anim;

        float popW  = screenWidth * 0.82f;
        float popH  = screenHeight * 0.44f;
        float popX  = cx - popW / 2f;
        float popY  = screenHeight * 0.28f;

        canvas.save();
        canvas.scale(scale, scale, cx, popY + popH / 2f);

        // Dim backdrop behind popup
        paint.setColor(Color.argb((int)(alpha * 160), 0, 0, 0));
        canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

        // Popup card shadow
        paint.setColor(Color.argb((int)(alpha * 100), 0, 0, 0));
        canvas.drawRoundRect(new RectF(popX + 6, popY + 8, popX + popW + 6, popY + popH + 8),
                28, 28, paint);

        // Popup card background
        paint.setColor(Color.argb((int)(alpha * 230), 8, 16, 38));
        canvas.drawRoundRect(new RectF(popX, popY, popX + popW, popY + popH), 28, 28, paint);

        // Card border glow
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
        paint.setColor(Color.argb((int)(alpha * 120), 80, 160, 255));
        canvas.drawRoundRect(new RectF(popX, popY, popX + popW, popY + popH), 28, 28, paint);
        paint.setStyle(Paint.Style.FILL);

        // Title inside popup
        paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true);
        paint.setColor(Color.argb((int)(alpha * 255), 255, 215, 0));
        paint.setTextSize(26);
        canvas.drawText("SELECT DIFFICULTY", cx, popY + 44, paint);
        paint.setFakeBoldText(false);

        // Thin divider
        paint.setColor(Color.argb((int)(alpha * 60), 200, 200, 200));
        canvas.drawLine(popX + 20, popY + 56, popX + popW - 20, popY + 56, paint);

        // Three buttons stacked vertically inside popup
        float btnH   = 72f;
        float btnW   = popW - 48f;
        float btnX   = popX + 24f;
        float gap    = 14f;
        float startB = popY + 70f;

        String[] labels = {"EASY", "NORMAL", "HARD"};
        int[]  baseColors = {
                Color.parseColor("#1a5c1a"),
                Color.parseColor("#4a430e"),
                Color.parseColor("#5c1212")
        };
        int[] textColors = {
                Color.parseColor("#88ff88"),
                Color.parseColor("#FFD700"),
                Color.parseColor("#ff8888")
        };
        String[] descs = {"Relax and enjoy", "Balanced challenge", "No mercy"};

        for (int i = 0; i < 3; i++) {
            float by = startB + i * (btnH + gap);
            RectF bRect = new RectF(btnX, by, btnX + btnW, by + btnH);
            boolean isSelected = difficulty == (i == 1 ? 1 : i == 0 ? 0 : 2);
            boolean isPressed  = menuTouchDown && bRect.contains(menuTouchX, menuTouchY);

            float bScale = isPressed ? 0.96f : 1f;
            canvas.save();
            canvas.scale(bScale, bScale, bRect.centerX(), bRect.centerY());

            // Button fill
            paint.setColor(Color.argb((int)(alpha * 200), Color.red(baseColors[i]),
                    Color.green(baseColors[i]), Color.blue(baseColors[i])));
            canvas.drawRoundRect(bRect, 18, 18, paint);

            // Selected highlight border
            if (isSelected) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
                paint.setColor(Color.argb((int)(alpha * 255),
                        Color.red(textColors[i]), Color.green(textColors[i]), Color.blue(textColors[i])));
                canvas.drawRoundRect(bRect, 18, 18, paint);
                paint.setStyle(Paint.Style.FILL);
                // Checkmark
                paint.setColor(Color.argb((int)(alpha * 255),
                        Color.red(textColors[i]), Color.green(textColors[i]), Color.blue(textColors[i])));
                paint.setTextSize(22); paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText("✓", btnX + btnW - 36, by + btnH * 0.65f, paint);
            }

            // Top shine
            paint.setColor(Color.argb((int)(alpha * 40), 255, 255, 255));
            canvas.drawRoundRect(new RectF(bRect.left + 5, bRect.top + 5,
                    bRect.right - 5, bRect.top + 26), 14, 14, paint);

            // Label
            paint.setFakeBoldText(true);
            paint.setColor(Color.argb((int)(alpha * 255),
                    Color.red(textColors[i]), Color.green(textColors[i]), Color.blue(textColors[i])));
            paint.setTextSize(28); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(labels[i], bRect.centerX(), by + btnH * 0.52f, paint);
            paint.setFakeBoldText(false);

            // Description sub-text
            paint.setColor(Color.argb((int)(alpha * 130), 200, 210, 220));
            paint.setTextSize(16);
            canvas.drawText(descs[i], bRect.centerX(), by + btnH * 0.82f, paint);

            canvas.restore();
        }

        // Close hint at bottom
        paint.setColor(Color.argb((int)(alpha * 80), 180, 190, 200));
        paint.setTextSize(16); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("tap outside to close", cx, popY + popH - 14, paint);

        canvas.restore();
    }

    private void drawPausedScreen(Canvas canvas) {
        float W = screenWidth, H = screenHeight, cx = W / 2f;

        // ── 1. Frozen game frame + multi-layer dark overlay ───────────────
        drawSky(canvas);

        // Edge vignette — darkens corners to focus centre
        Paint vigP = new Paint(Paint.ANTI_ALIAS_FLAG);
        vigP.setShader(new RadialGradient(cx, H * 0.45f, H * 0.7f,
                new int[]{Color.argb(100,0,0,0), Color.argb(220,0,0,0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, H, vigP);

        paint.setColor(Color.argb(130, 0, 4, 12));
        canvas.drawRect(0, 0, W, H, paint);

        // ── 2. Animated "PAUSED" title ────────────────────────────────────
        float fadeIn = Math.min(1f, pauseAnimFrame / 18f);
        float titleY = H * 0.20f;

        // Cyan glow pulse behind title
        float gp = 0.6f + (float)Math.sin(pauseAnimFrame * 0.07f) * 0.4f;
        Paint titleGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        titleGlow.setShader(new RadialGradient(cx, titleY, W * 0.5f * gp,
                new int[]{Color.argb((int)(70*fadeIn*gp), 0, 200, 255), Color.argb(0, 0, 100, 200)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, titleY - 80, W, titleY + 40, titleGlow);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.argb((int)(140*fadeIn), 0, 0, 0)); paint.setTextSize(90);
        canvas.drawText("PAUSED", cx + 3, titleY + 3, paint);
        paint.setColor(Color.argb((int)(255*fadeIn), 255, 255, 255)); paint.setTextSize(90);
        canvas.drawText("PAUSED", cx, titleY, paint);
        paint.setColor(Color.argb((int)(90*fadeIn), 0, 212, 255)); paint.setTextSize(90);
        canvas.drawText("PAUSED", cx, titleY, paint);

        // Separator
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f);
        paint.setColor(Color.argb((int)(120*fadeIn), 0, 180, 255));
        canvas.drawLine(cx - 150, titleY + 22, cx + 150, titleY + 22, paint);
        paint.setStyle(Paint.Style.FILL);

        // ── 3. Three buttons — Resume / Restart / Home ────────────────────
        float btnW = W * 0.82f, btnH = 76f, btnX = cx - btnW / 2f;
        float btnGap = 14f;
        float b1Y = titleY + 46f;
        float b2Y = b1Y + btnH + btnGap;
        float b3Y = b2Y + btnH + btnGap;

        resumeBtn       = new RectF(btnX, b1Y, btnX+btnW, b1Y+btnH);
        btnPauseRestart = new RectF(btnX, b2Y, btnX+btnW, b2Y+btnH);
        btnPauseHome    = new RectF(btnX, b3Y, btnX+btnW, b3Y+btnH);

        boolean resumePressed  = menuTouchDown && resumeBtn.contains(menuTouchX, menuTouchY);
        boolean restartPressed = menuTouchDown && btnPauseRestart.contains(menuTouchX, menuTouchY);
        boolean homePressed    = menuTouchDown && btnPauseHome.contains(menuTouchX, menuTouchY);

        drawPauseBtn(canvas, resumeBtn,       "▶  RESUME",
                Color.parseColor("#007733"), Color.parseColor("#00C853"),
                resumePressed, fadeIn);
        drawPauseBtn(canvas, btnPauseRestart, "↺  RESTART",
                Color.parseColor("#994400"), Color.parseColor("#FF9100"),
                restartPressed, fadeIn);
        drawPauseBtn(canvas, btnPauseHome,    "⌂  HOME",
                Color.parseColor("#660011"), Color.parseColor("#D50000"),
                homePressed, fadeIn);

        // ── 4. Stats row below buttons ────────────────────────────────────
        int elapsedSec = frameCount / 60;
        String timeStr  = String.format("%02d:%02d", elapsedSec/60, elapsedSec%60);
        String comboStr = comboCount >= 3 ? "x" + String.format("%.1f", comboMult) : "x1.0";
        float hpPct     = playerHP;

        float statsY = b3Y + btnH + 28f;
        float cardL  = W * 0.06f, cardR = W * 0.94f;
        float cardBot= statsY + 130f;

        // Stats card bg
        paint.setColor(Color.argb((int)(100*fadeIn), 0, 0, 0));
        canvas.drawRoundRect(new RectF(cardL+3, statsY+3, cardR+3, cardBot+3), 14, 14, paint);
        paint.setColor(Color.argb((int)(150*fadeIn), 4, 12, 28));
        canvas.drawRoundRect(new RectF(cardL, statsY, cardR, cardBot), 14, 14, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.8f);
        paint.setColor(Color.argb((int)(50*fadeIn), 0, 180, 255));
        canvas.drawRoundRect(new RectF(cardL, statsY, cardR, cardBot), 14, 14, paint);
        paint.setStyle(Paint.Style.FILL);

        // Four stats in a row
        String[] labels = {"SCORE", "COMBO", "HP", "TIME"};
        String[] values = {String.valueOf(score), comboStr, (int)hpPct + "%", timeStr};
        int[]    colors = {0xFF00D4FF, 0xFFFFD700, hpPct > 50 ? 0xFF44FF88 : hpPct > 25 ? 0xFFFFAA00 : 0xFFFF4444, 0xFF88CCFF};

        float colW = (cardR - cardL) / 4f;
        for (int i = 0; i < 4; i++) {
            float sx = cardL + colW * i + colW / 2f;
            float labelY2 = statsY + 38f;
            float valueY2 = statsY + 82f;

            paint.setColor(Color.argb((int)(120*fadeIn), 140, 170, 200));
            paint.setTextSize(14); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(labels[i], sx, labelY2, paint);

            paint.setColor(Color.argb((int)(230*fadeIn),
                    (colors[i] >> 16) & 0xFF, (colors[i] >> 8) & 0xFF, colors[i] & 0xFF));
            paint.setTextSize(28); paint.setFakeBoldText(true);
            canvas.drawText(values[i], sx, valueY2, paint);
            paint.setFakeBoldText(false);

            // Vertical divider between cols
            if (i < 3) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.6f);
                paint.setColor(Color.argb((int)(30*fadeIn), 100, 160, 220));
                canvas.drawLine(cardL + colW*(i+1), statsY+12, cardL + colW*(i+1), cardBot-12, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        // HP progress bar at bottom of card
        float hpBarX = cardL + 16, hpBarY = cardBot - 18, hpBarW = cardR - cardL - 32, hpBarH = 6f;
        paint.setColor(Color.argb((int)(60*fadeIn), 60, 80, 100));
        canvas.drawRoundRect(new RectF(hpBarX, hpBarY, hpBarX+hpBarW, hpBarY+hpBarH), 3, 3, paint);
        float hpFill = Math.max(0, Math.min(1, playerHP / (float) MAX_PLAYER_HP));
        int hpCol = hpFill > 0.5f ? Color.parseColor("#44FF88")
                : hpFill > 0.25f ? Color.parseColor("#FFAA00") : Color.parseColor("#FF4444");
        paint.setColor(Color.argb((int)(200*fadeIn), Color.red(hpCol), Color.green(hpCol), Color.blue(hpCol)));
        if (hpFill > 0)
            canvas.drawRoundRect(new RectF(hpBarX, hpBarY, hpBarX+hpBarW*hpFill, hpBarY+hpBarH), 3, 3, paint);

        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Styled pause menu button with scale-on-press and glow */
    private void drawPauseBtn(Canvas canvas, RectF rect, String label,
                              int bgColor, int accentColor, boolean pressed, float fadeIn) {
        float scale = pressed ? 0.96f : 1.0f;
        float cx = rect.centerX(), cy = rect.centerY();
        float w  = rect.width() * scale, h = rect.height() * scale;
        RectF r  = new RectF(cx-w/2f, cy-h/2f, cx+w/2f, cy+h/2f);

        int alpha = (int)(255 * fadeIn);

        // Glow on press
        if (pressed) {
            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setShader(new RadialGradient(cx, cy, w * 0.6f,
                    new int[]{Color.argb(80, Color.red(accentColor),
                            Color.green(accentColor), Color.blue(accentColor)), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, 14, 14, glow);
        }

        // Shadow
        paint.setColor(Color.argb((int)(80*fadeIn), 0, 0, 0));
        canvas.drawRoundRect(new RectF(r.left+3, r.top+3, r.right+3, r.bottom+3), 14, 14, paint);

        // Fill — two-tone top highlight
        paint.setColor(Color.argb(alpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)));
        canvas.drawRoundRect(r, 14, 14, paint);
        paint.setColor(Color.argb((int)(alpha*0.6f),
                Math.min(255, Color.red(bgColor)+40),
                Math.min(255, Color.green(bgColor)+40),
                Math.min(255, Color.blue(bgColor)+40)));
        canvas.drawRoundRect(new RectF(r.left, r.top, r.right, r.centerY()), 14, 14, paint);

        // Border
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
        paint.setColor(Color.argb((int)(180*fadeIn),
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        canvas.drawRoundRect(r, 14, 14, paint);
        paint.setStyle(Paint.Style.FILL);

        // Label
        paint.setColor(Color.argb(alpha, 255, 255, 255));
        paint.setTextSize(28f); paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, cx, cy + 9f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawGameOverScreen(Canvas canvas) {
        drawSky(canvas);
        final float cx = screenWidth / 2f;
        final int accuracy = runBulletsShot > 0 ? (int)(runBulletsHit * 100f / runBulletsShot) : 0;
        final int earnedCoins = coinCount;
        final int hpLost = Math.max(0, runStartHP - playerHP);

        // ── Background darkening ──────────────────────────────────────────
        Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlay.setColor(Color.argb(165, 0, 0, 0));
        canvas.drawRect(0, 0, screenWidth, screenHeight, overlay);

        // Soft red vignette / danger glow
        float pulse = 0.86f + (float)Math.sin(frameCount * 0.10f) * 0.14f;
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(
                cx, screenHeight * 0.18f, screenWidth * 0.55f * pulse,
                new int[]{Color.argb(90,220,30,30), Color.argb(35,120,10,10), Color.argb(0,0,0,0)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, screenHeight * 0.18f, screenWidth * 0.55f * pulse, glow);

        // ── Header ────────────────────────────────────────────────────────
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.argb(90, 0, 0, 0));
        paint.setTextSize(76f); paint.setFakeBoldText(true);
        canvas.drawText("MISSION FAILED", cx + 4, screenHeight * 0.12f + 4, paint);
        paint.setColor(Color.parseColor("#ff5555"));
        paint.setShadowLayer(24f, 0f, 0f, Color.parseColor("#aa0000"));
        canvas.drawText("MISSION FAILED", cx, screenHeight * 0.12f, paint);
        paint.clearShadowLayer();
        paint.setFakeBoldText(false);

        // Subtitle — context-aware
        paint.setColor(Color.parseColor("#ffb3b3")); paint.setTextSize(22f);
        String sub;
        if (boss != null && boss.bodyHP > 0) {
            int bossPct = Math.max(1, (int)(boss.bodyHP * 100f / 220));
            sub = "Boss survived with " + bossPct + "% HP";
        } else if (totalWavesCompleted >= BOSS_AFTER_WAVES - 2) {
            sub = "You were very close to the boss";
        } else {
            sub = "Regroup, upgrade, and try again";
        }
        canvas.drawText(sub, cx, screenHeight * 0.175f, paint);

        // ── Grade badge (circle, top-right of panel) ──────────────────────
        String rank;
        int rankColor;
        if      (score >= 22000 || (accuracy >= 75 && totalWavesCompleted >= 12)) { rank = "A"; rankColor = Color.parseColor("#ffd54f"); }
        else if (score >= 14000 || (accuracy >= 62 && totalWavesCompleted >= 9))  { rank = "B"; rankColor = Color.parseColor("#8cff98"); }
        else if (score >= 8000  || totalWavesCompleted >= 6)                      { rank = "C"; rankColor = Color.parseColor("#7fd3ff"); }
        else                                                                       { rank = "D"; rankColor = Color.parseColor("#ff9e80"); }

        float badgeR = 40f, badgeCx = screenWidth - 84f, badgeCy = screenHeight * 0.30f;
        Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setColor(Color.argb(140, 0, 0, 0));
        canvas.drawCircle(badgeCx + 3, badgeCy + 4, badgeR, badge);
        badge.setColor(Color.argb(220, 18, 18, 28));
        canvas.drawCircle(badgeCx, badgeCy, badgeR, badge);
        badge.setStyle(Paint.Style.STROKE); badge.setStrokeWidth(3.5f); badge.setColor(rankColor);
        canvas.drawCircle(badgeCx, badgeCy, badgeR - 2f, badge);
        badge.setStyle(Paint.Style.FILL);
        paint.setColor(rankColor); paint.setTextSize(42f); paint.setFakeBoldText(true);
        canvas.drawText(rank, badgeCx, badgeCy + 14f, paint);
        paint.setFakeBoldText(false);

        // ── Main stats panel ──────────────────────────────────────────────
        float panelL = 28f, panelR = screenWidth - 28f;
        float panelTop = screenHeight * 0.23f, panelBot = screenHeight * 0.74f;

        Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelPaint.setColor(Color.argb(175, 8, 10, 18));
        canvas.drawRoundRect(new RectF(panelL, panelTop, panelR, panelBot), 24f, 24f, panelPaint);

        // Gloss top strip
        panelPaint.setShader(new LinearGradient(0, panelTop, 0, panelTop + 80f,
                new int[]{Color.argb(50,255,255,255), Color.argb(8,255,255,255), Color.argb(0,255,255,255)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(panelL, panelTop, panelR, panelTop + 80f), 24f, 24f, panelPaint);
        panelPaint.setShader(null);

        // Panel border — red tint
        panelPaint.setStyle(Paint.Style.STROKE); panelPaint.setStrokeWidth(2.2f);
        panelPaint.setColor(Color.argb(120, 255, 110, 110));
        canvas.drawRoundRect(new RectF(panelL, panelTop, panelR, panelBot), 24f, 24f, panelPaint);
        panelPaint.setStyle(Paint.Style.FILL);

        // Section title
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.WHITE); paint.setTextSize(28f); paint.setFakeBoldText(true);
        canvas.drawText("BATTLE REPORT", panelL + 24f, panelTop + 38f, paint);
        paint.setFakeBoldText(false);

        // Divider under title
        Paint div = new Paint(Paint.ANTI_ALIAS_FLAG);
        div.setColor(Color.argb(55, 255, 255, 255));
        canvas.drawRect(panelL + 22f, panelTop + 52f, panelR - 22f, panelTop + 54f, div);

        // Stat rows
        float y = panelTop + 90f;
        float rowGap = (panelBot - y - 16f) / 8f;

        drawFancyStatLine(canvas, panelL, panelR, y,           "SCORE",            String.valueOf(score),                     Color.WHITE);
        drawFancyStatLine(canvas, panelL, panelR, y+rowGap,    "HIGH SCORE",       String.valueOf(Math.max(highScore,score)), Color.parseColor("#ffd54f"));
        drawFancyStatLine(canvas, panelL, panelR, y+rowGap*2,  "WAVES CLEARED",    String.valueOf(totalWavesCompleted),       Color.parseColor("#8be9fd"));
        drawFancyStatLine(canvas, panelL, panelR, y+rowGap*3,  "ENEMIES DESTROYED",String.valueOf(runKills),                  Color.parseColor("#ff8a80"));
        drawFancyStatLine(canvas, panelL, panelR, y+rowGap*4,  "ACCURACY",         accuracy + "%",
                accuracy >= 60 ? Color.parseColor("#8cff98") : Color.parseColor("#ffcc80"));
        drawFancyStatLine(canvas, panelL, panelR, y+rowGap*5,  "COINS COLLECTED",  "+" + earnedCoins,                        Color.parseColor("#ffd54f"));
        drawFancyStatLine(canvas, panelL, panelR, y+rowGap*6,  "ASTRONAUTS SAVED", String.valueOf(astronautsSaved),           Color.parseColor("#82b1ff"));
        drawFancyStatLine(canvas, panelL, panelR, y+rowGap*7,  "HP LOST",          String.valueOf(hpLost),
                hpLost < 35 ? Color.parseColor("#8cff98") : Color.parseColor("#ff8a80"));

        // ── Motivational tip ──────────────────────────────────────────────
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(20f); paint.setColor(Color.argb(190, 210, 220, 230));
        String tip;
        if      (accuracy < 45)              tip = "Tip: focus on fewer, cleaner shots";
        else if (playerHP < 25)              tip = "Tip: dodge earlier — stop chasing pickups under fire";
        else if (totalWavesCompleted < 5)    tip = "Tip: opening game is weak — survive early waves cleaner";
        else                                 tip = "Strong run. One more try could break through";
        canvas.drawText(tip, cx, screenHeight * 0.785f, paint);

        // ── Buttons ───────────────────────────────────────────────────────
        float btnGap = 18f, btnH2 = 72f;
        float btnW2 = (screenWidth * 0.88f - btnGap) / 2f;
        float btnY2 = screenHeight * 0.825f;
        float btnLX = cx - btnW2 - btnGap / 2f;
        float btnRX = cx + btnGap / 2f;

        btnPlayAgain = new RectF(btnLX, btnY2, btnLX+btnW2, btnY2+btnH2);
        btnMainPage  = new RectF(btnRX, btnY2, btnRX+btnW2, btnY2+btnH2);

        boolean againPressed = menuTouchDown && btnPlayAgain.contains(menuTouchX, menuTouchY);
        boolean homePressed  = menuTouchDown && btnMainPage.contains(menuTouchX, menuTouchY);

        drawGameOverButton(canvas, btnPlayAgain, "↺  PLAY AGAIN",
                Color.parseColor("#7f1010"), Color.parseColor("#ff4d4d"), againPressed);
        drawGameOverButton(canvas, btnMainPage,  "⌂  MAIN PAGE",
                Color.parseColor("#102040"), Color.parseColor("#5ca8ff"), homePressed);

        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawGameOverButton(Canvas canvas, RectF rect, String text,
                                    int baseColor, int glowColor, boolean pressed) {
        float scale = pressed ? 0.965f : 1f;
        float cx2 = rect.centerX(), cy2 = rect.centerY();
        float w = rect.width() * scale, h = rect.height() * scale;
        RectF r = new RectF(cx2-w/2f, cy2-h/2f, cx2+w/2f, cy2+h/2f);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Glow
        p.setShader(new RadialGradient(r.centerX(), r.centerY(), r.width() * 0.75f,
                new int[]{Color.argb(80, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                        Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(r.left-4, r.top-4, r.right+4, r.bottom+4), 22f, 22f, p);
        p.setShader(null);

        // Shadow
        p.setColor(Color.argb(110, 0, 0, 0));
        canvas.drawRoundRect(new RectF(r.left+4, r.top+5, r.right+4, r.bottom+5), 18f, 18f, p);

        // Base fill
        p.setColor(baseColor);
        canvas.drawRoundRect(r, 18f, 18f, p);

        // Highlight strip top
        p.setShader(new LinearGradient(0, r.top, 0, r.top + r.height()*0.55f,
                new int[]{Color.argb(85,255,255,255), Color.argb(16,255,255,255), Color.TRANSPARENT},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(r.left, r.top, r.right, r.top+r.height()*0.55f), 18f, 18f, p);
        p.setShader(null);

        // Border
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(glowColor);
        canvas.drawRoundRect(r, 18f, 18f, p);
        p.setStyle(Paint.Style.FILL);

        // Label
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE); paint.setTextSize(28f); paint.setFakeBoldText(true);
        canvas.drawText(text, r.centerX(), r.centerY() + 10f, paint);
        paint.setFakeBoldText(false);
    }

    /** Fancy two-column stat line with label left, value right and a subtle separator */
    private void drawFancyStatLine(Canvas canvas, float panelL, float panelR, float y,
                                   String label, String value, int valueColor) {
        float labelX = panelL + 28f;
        float valueX = panelR - 160f;  // keep away from grade
        // Subtle row bg on every other line
        paint.setTextSize(28); paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.argb(50, 255, 255, 255));
        paint.setColor(Color.argb(160, 200, 200, 220)); paint.setTextSize(26);
        canvas.drawText(label, labelX, y, paint);
        paint.setColor(valueColor); paint.setTextSize(28); paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(value, valueX, y, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawVictoryScreen(Canvas canvas) {
        drawSky(canvas);
        float cx = screenWidth / 2f;
        int accuracy = runBulletsShot > 0 ? (int)(runBulletsHit * 100f / runBulletsShot) : 0;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.parseColor("#FFD700")); paint.setTextSize(72);
        canvas.drawText("VICTORY!", cx, screenHeight * 0.13f, paint);
        paint.setColor(Color.parseColor("#00ff88")); paint.setTextSize(28);
        canvas.drawText("Boss Defeated!", cx, screenHeight * 0.21f, paint);

        float panelTop = screenHeight * 0.27f, panelBot = screenHeight * 0.82f;
        paint.setColor(Color.argb(160, 5, 20, 10));
        canvas.drawRoundRect(new RectF(30, panelTop, screenWidth-30, panelBot), 20, 20, paint);
        float lineH = (panelBot - panelTop) / 8f, ty = panelTop + lineH * 0.8f;
        paint.setTextSize(30); paint.setColor(Color.WHITE);
        drawStatLine(canvas, cx, ty,        "SCORE",       String.valueOf(score));
        drawStatLine(canvas, cx, ty+lineH,   "BEST",      String.valueOf(highScore));
        drawStatLine(canvas, cx, ty+lineH*2, "KILLS",      String.valueOf(runKills));
        drawStatLine(canvas, cx, ty+lineH*3, "ACCURACY",  accuracy + "%");
        drawStatLine(canvas, cx, ty+lineH*4, "RESCUED",   astronautsSaved + " / " + MAX_ASTRONAUTS + (astronautsSaved == MAX_ASTRONAUTS ? " ✓" : ""));

        // Grade: full rescue = bonus toward S
        String grade = (accuracy >= 75 && astronautsSaved >= MAX_ASTRONAUTS) ? "S"
                : (accuracy >= 55 || astronautsSaved >= 6) ? "A" : "B";
        int gradeColor = grade.equals("S") ? Color.parseColor("#FFD700") :
                grade.equals("A") ? Color.parseColor("#00ff88") : Color.parseColor("#44aaff");
        paint.setColor(gradeColor); paint.setTextSize(80);
        canvas.drawText(grade, cx, ty + lineH * 5.8f, paint);

        // ── Challenge badges ──────────────────────────────────────────────
        drawChallenges(canvas, cx, ty + lineH * 7.0f, accuracy);

        paint.setColor(Color.parseColor("#d0dbe8")); paint.setTextSize(26);
        canvas.drawText("Tap to play again", cx, panelBot + 44, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 4 challenge badges shown at end of game */
    private void drawChallenges(Canvas canvas, float cx, float y, int accuracy) {
        // Compute challenge results
        int killPct = runTotalEnemiesSpawned > 0
                ? (int)(runKills * 100f / runTotalEnemiesSpawned) : 0;
        boolean ch1 = killPct >= 75;   // 75% kills
        boolean ch2 = killPct >= 100;  // 100% kills
        boolean ch3 = totalStarsEver >= (BOSS_AFTER_WAVES * 3); // all stars collected ever
        boolean ch4 = astronautsSaved >= MAX_ASTRONAUTS;         // all rescued

        String[] labels   = {"75% KILLS", "100% KILLS", "ALL STARS", "ALL RESCUED"};
        boolean[] done    = {ch1, ch2, ch3, ch4};
        String[] icons    = {"💀", "💀", "★", "🚀"};
        int[]    onColors = {
                Color.parseColor("#ff8800"),
                Color.parseColor("#ff2200"),
                Color.parseColor("#FFD700"),
                Color.parseColor("#00ffcc")
        };

        float badgeW = (screenWidth - 60) / 4f;
        float badgeH = 56f;
        float startX  = 30f;

        for (int i = 0; i < 4; i++) {
            float bx = startX + i * badgeW;
            float by = y;
            boolean achieved = done[i];

            // Badge background
            paint.setColor(achieved
                    ? Color.argb(180, Color.red(onColors[i])/4, Color.green(onColors[i])/4, Color.blue(onColors[i])/4)
                    : Color.argb(80, 20, 25, 35));
            canvas.drawRoundRect(new RectF(bx + 3, by, bx + badgeW - 3, by + badgeH), 12, 12, paint);

            // Border
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(achieved ? 2f : 1f);
            paint.setColor(achieved ? onColors[i] : Color.argb(60, 150, 160, 180));
            canvas.drawRoundRect(new RectF(bx + 3, by, bx + badgeW - 3, by + badgeH), 12, 12, paint);
            paint.setStyle(Paint.Style.FILL);

            // Icon
            paint.setTextSize(22); paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(achieved ? onColors[i] : Color.argb(80, 160, 170, 180));
            canvas.drawText(icons[i], bx + badgeW / 2f, by + 26, paint);

            // Label
            paint.setTextSize(13);
            paint.setColor(achieved ? Color.WHITE : Color.argb(80, 180, 190, 200));
            canvas.drawText(labels[i], bx + badgeW / 2f, by + 46, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawStatLine(Canvas canvas, float cx, float y, String label, String value) {
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.parseColor("#8899aa"));
        canvas.drawText(label, cx - 160, y, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.WHITE);
        canvas.drawText(value, cx + 160, y, paint);
        paint.setColor(Color.argb(40, 200, 200, 200));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        canvas.drawLine(cx - 155, y - 8, cx + 155, y - 8, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    // ===================== RESET =====================
    private void resetGame() {
        score=0; coinCount=0; playerHP=MAX_PLAYER_HP;
        applyPermanentUpgrades();
        mapProgress=0; frameCount=0; planetScrollY=0f; bgScrollY=0f;
        gameStartFrame = 0;
        scrollSpeed=2f; skyWaveOffset=0f;
        currentWave=0; totalWavesCompleted=0; currentWaveType=-1;
        waveEnemiesRemaining=0; waveInProgress=false; waveSpawnFinished=false; waveEnemiesAlive=0; waveClearTimeout=0;
        waveCooldown=240; waveEnemySpawnTimer=0;
        if (DEBUG_BOSS) {
            // Skip all waves — go straight to boss
            totalWavesCompleted = BOSS_AFTER_WAVES;
            waveCooldown = 10;
            playerHP = 999; // high HP so you can test without dying
        }
        superFireTimeLeft=0; screenFlashAlpha=0f; shakeIntensity=0f;
        eagleSpawnTimer=1800;
        gunPower=1; coinsForNext=5; coinsProgress=0;
        bullets.clear(); enemyBullets.clear();
        airEnemies.clear(); explosions.clear();
        floatingTexts.clear(); coins.clear(); powerUps.clear(); bossMines.clear();
        healthPickups.clear(); healthPickupsSpawned=0;
        asteroids.clear(); starPickups.clear(); coinParticles.clear(); oceanPropObjs.clear(); desertPropObjs.clear();
        astronauts.clear(); astronautsSaved=0; astronautsSpawned=0;
        boss=null; bossDefeated=false;
        hasShield=false; shieldTimer=0; magnetTimer=0; magnetSpawned=0;
        shootCooldown=0; superShootCooldown=0; cannonCooldown=360;
        burstShotsLeft=0; burstShotTimer=0;
        gunSoundThrottle=0; enemySoundThrottle=0;
        lowHealthSirenTimer=0;
        isDragging=false; dragPointerId=-1; hpPopupTimer=0; releaseSlowTimer=0;
        slowMoScale=1.0f; // start at full speed
        planeX=screenWidth/2f-PLAYER_W/2f;
        planeY=screenHeight*0.82f;
        runKills=0; runBulletsShot=0; runBulletsHit=0;
        runTotalEnemiesSpawned=0; meteorShowerStarsCollected=0;
        comboCount=0; comboTimer=0; comboMult=1f;
        performancePressure=1.0f; recentKills=0; recentKillTimer=0;
        recentHits=0; recentHitTimer=0;
        hitFreezeFrames=0; slowMoFrames=0; slowMoScale=1.0f;
        bulletTrails.clear(); deathParticles.clear();
        waveKillsGot=0; waveKillsNeeded=0; waveStartHP=MAX_PLAYER_HP;
        waveAstroSpawned=false; waveAstroSaved=false; runWaveStars=0;
        menuTouchDown=false; menuTouchX=-1; menuTouchY=-1;
        menuTransitionTimer=0; menuTransitionTarget=null;
        difficultyPopupOpen=false; difficultyPopupAnim=0f;
        preFillProps(); // populate screen with props immediately
    }

    /** Scatter props across the full visible screen at game start so there's no empty period. */
    private void preFillProps() {
        if (currentMap == MAP_DESERT) {
            desertPropObjs.clear();
            int[] dCounts = new int[DESERT_PROP_COUNT];
            // Place ~15 props spread across the entire screen height
            for (int s = 0; s < 15; s++) {
                int tries = 0;
                while (tries++ < 20) {
                    int idx = random.nextInt(DESERT_PROP_COUNT);
                    if (dCounts[idx] < 3 && desertProps[idx] != null) {
                        float sx    = 30 + random.nextFloat() * (screenWidth - 60);
                        float scale = 0.45f + random.nextFloat() * 0.55f;
                        // Spread Y across the full screen so map is covered from frame 1
                        float sy    = random.nextFloat() * (screenHeight + 200) - 100;
                        desertPropObjs.add(new DesertPropObj(sx, sy, 0, idx, scale));
                        dCounts[idx]++;
                        break;
                    }
                }
            }
        } else if (currentMap == MAP_OCEAN) {
            oceanPropObjs.clear();
            int[] pCounts = new int[OCEAN_PROP_COUNT];
            float riverL = screenWidth * 0.35f;
            float riverR = screenWidth * 0.70f;
            for (int s = 0; s < 12; s++) {
                int tries = 0;
                while (tries++ < 20) {
                    int idx = random.nextInt(OCEAN_PROP_COUNT);
                    if (pCounts[idx] < 3 && oceanProps[idx] != null) {
                        // Left bank (0-35%) or right bank (70-100%)
                        float sx = random.nextBoolean()
                                ? 20 + random.nextFloat() * (riverL - 40)
                                : riverR + 20 + random.nextFloat() * (screenWidth - riverR - 40);
                        float scale = 0.5f + random.nextFloat() * 0.6f;
                        float sy    = random.nextFloat() * (screenHeight + 200) - 100;
                        oceanPropObjs.add(new OceanPropObj(sx, sy, 0, idx, scale));
                        pCounts[idx]++;
                        break;
                    }
                }
            }
        }
    }

    // ===================== TOUCH =====================
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseSound();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        // Track touch for button animations in all menu states
        if (gameState != GameState.PLAYING) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                menuTouchX = event.getX(); menuTouchY = event.getY();
                menuTouchDown = true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                menuTouchDown = false;
            }
        }

        if (gameState == GameState.HOME) {
            if (action == MotionEvent.ACTION_UP) {
                float tx = event.getX(), ty = event.getY();
                if (btnHomeStart != null && btnHomeStart.contains(tx, ty)) {
                    resetGame();
                    screenFlashAlpha = 80f; screenFlashColor = Color.WHITE;
                    playSound(sndGunUpgrade, 0.6f, 1.3f);
                    menuTransitionTarget = GameState.PLAYING;
                    menuTransitionTimer  = 14;
                } else if (btnHomeUpgrades != null && btnHomeUpgrades.contains(tx, ty)) {
                    playSound(sndCoinPickup, 0.5f, 1.1f);
                    gameState = GameState.UPGRADE_SHOP;
                } else if (btnHomeSettings != null && btnHomeSettings.contains(tx, ty)) {
                    playSound(sndCoinPickup, 0.5f, 1.0f);
                    gameState = GameState.SETTINGS;
                }
            }
            return true;
        }

        if (gameState == GameState.UPGRADE_SHOP) {
            if (action == MotionEvent.ACTION_UP) {
                float tx = event.getX(), ty = event.getY();
                if (btnShopBack != null && btnShopBack.contains(tx, ty)) {
                    playSound(sndCoinPickup, 0.5f, 0.9f);
                    gameState = GameState.HOME;
                } else {
                    int[] levels = getPermLevels();
                    // Check node taps (top 70% of screen = node area)
                    if (ty < screenHeight * 0.70f) {
                        for (int u = 0; u < 5; u++) {
                            // Use circular hit test matching the drawn node size
                            float nx = (btnBuyUpgrade[u] != null) ? btnBuyUpgrade[u].centerX() : -999;
                            float ny = (btnBuyUpgrade[u] != null) ? btnBuyUpgrade[u].centerY() : -999;
                            float dx = tx - nx, dy2 = ty - ny;
                            if (dx*dx + dy2*dy2 < 55f*55f) { // generous 55px tap radius
                                selectedUpgrade = u;
                                playSound(sndCoinPickup, 0.4f, 1.2f);
                                break;
                            }
                        }
                    } else {
                        // Bottom panel — launch purchase flow for selected upgrade
                        int u = selectedUpgrade;
                        int lv = levels[u], max = UPGRADE_MAX[u];
                        if (lv < max) {
                            purchaseUpgrade(u);
                        }
                    }
                }
            }
            return true;
        }

        if (gameState == GameState.SETTINGS) {
            float tx = event.getX(), ty = event.getY();
            float barH = screenHeight * 0.10f;

            // Convert screen touch Y → content Y (undo the clip+translate)
            float contentTY = ty - barH + settingsScrollY;
            // contentTX is same as tx (no horizontal scroll)

            // Scroll drag — only when touch is in body area
            if (action == MotionEvent.ACTION_DOWN) {
                settingsLastTouchY = ty;
                settingsDragging = ty > barH; // only drag in body, not top bar
            }
            if (action == MotionEvent.ACTION_MOVE && settingsDragging) {
                float dy = settingsLastTouchY - ty;
                // Slider drag takes priority over page scroll
                if (sliderSfxTrack != null && sliderSfxTrack.contains(tx, contentTY)) {
                    float t = (tx - sliderSfxTrack.left) / sliderSfxTrack.width();
                    sfxVolume = Math.max(0f, Math.min(1f, t));
                } else if (sliderMusicTrack != null && sliderMusicTrack.contains(tx, contentTY)) {
                    float t = (tx - sliderMusicTrack.left) / sliderMusicTrack.width();
                    musicVolume = Math.max(0f, Math.min(1f, t));
                    applyMusicVolume(); // update MediaPlayer volume in real time
                } else {
                    settingsScrollY += dy; // page scroll
                }
                settingsLastTouchY = ty;
            }

            if (action == MotionEvent.ACTION_UP) {
                settingsDragging = false;
                float totalDrag = Math.abs(ty - settingsLastTouchY);
                boolean isTap = totalDrag < 14;

                if (btnSettingsBack != null && btnSettingsBack.contains(tx, ty)) {
                    // Back button is in top bar — uses screen coords
                    saveProfile(); playSound(sndCoinPickup, 0.5f, 0.9f);
                    settingsScrollY = 0f; gameState = GameState.HOME;
                } else if (isTap) {
                    // All other controls use content coords
                    if      (btnVibToggle   !=null&&btnVibToggle.contains(tx,contentTY))   { vibrationEnabled=!vibrationEnabled; if(vibrationEnabled)vibrate(30); }
                    else if (btnSensLow !=null&&btnSensLow.contains(tx,contentTY))  { dragSensitivity=0.7f; saveProfile(); }
                    else if (btnSensMed !=null&&btnSensMed.contains(tx,contentTY))  { dragSensitivity=1.0f; saveProfile(); }
                    else if (btnSensHigh!=null&&btnSensHigh.contains(tx,contentTY)) { dragSensitivity=1.4f; saveProfile(); }
                    else if (btnSettingsDiff0!=null&&btnSettingsDiff0.contains(tx,contentTY)) { difficulty=0; saveProfile(); }
                    else if (btnSettingsDiff1!=null&&btnSettingsDiff1.contains(tx,contentTY)) { difficulty=1; saveProfile(); }
                    else if (btnSettingsDiff2!=null&&btnSettingsDiff2.contains(tx,contentTY)) { difficulty=2; saveProfile(); }
                    else if (btnSettingsMap0!=null&&btnSettingsMap0.contains(tx,contentTY)) { currentMap=MAP_SPACE;  saveProfile(); asteroids.clear(); }
                    else if (btnSettingsMap1!=null&&btnSettingsMap1.contains(tx,contentTY)&&unlockedMapCount>=2) { currentMap=MAP_DESERT; saveProfile(); asteroids.clear(); }
                    else if (btnSettingsMap2!=null&&btnSettingsMap2.contains(tx,contentTY)&&unlockedMapCount>=3) { currentMap=MAP_OCEAN;  saveProfile(); asteroids.clear(); }
                    else if (btnResetDefaults!=null&&btnResetDefaults.contains(tx,contentTY)) {
                        sfxEnabled=true; vibrationEnabled=true; dragSensitivity=1.0f;
                        difficulty=1; sfxVolume=1.0f; musicVolume=0.5f;
                        saveProfile();
                        screenFlashAlpha=30f; screenFlashColor=Color.parseColor("#00D4FF");
                    }
                }
            }
            return true;
        }

        if (gameState == GameState.PAUSED) {
            if (action == MotionEvent.ACTION_UP) {
                float tx = event.getX(), ty = event.getY();
                if (resumeBtn != null && resumeBtn.contains(tx, ty)) {
                    playSound(sndCoinPickup, 0.6f, 1.1f);
                    gameState = GameState.PLAYING;
                } else if (btnPauseRestart != null && btnPauseRestart.contains(tx, ty)) {
                    playSound(sndCoinPickup, 0.6f, 0.9f);
                    resetGame();
                    gameState = GameState.PLAYING;
                } else if (btnPauseHome != null && btnPauseHome.contains(tx, ty)) {
                    playSound(sndCoinPickup, 0.6f, 0.8f);
                    resetGame();
                    gameState = GameState.HOME;
                }
            }
            return true;
        }
        if (gameState == GameState.GAME_OVER || gameState == GameState.VICTORY) {
            if (action == MotionEvent.ACTION_UP) {
                float tx = event.getX(), ty = event.getY();
                if (btnPlayAgain != null && btnPlayAgain.contains(tx, ty)) {
                    resetGame();
                    gameState = GameState.PLAYING;
                } else if (btnMainPage != null && btnMainPage.contains(tx, ty)) {
                    gameState = GameState.HOME;
                }
                // No accidental home on random taps — must use a button
            }
            return true;
        }

        int pc = event.getPointerCount();
        for (int i = 0; i < pc; i++) {
            float tx = event.getX(i), ty = event.getY(i);
            if (action == MotionEvent.ACTION_DOWN && pauseBtn.contains(tx, ty)) {
                gameState = GameState.PAUSED; pauseAnimFrame = 0; return true;
            }
        }



        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float tx=event.getX(), ty=event.getY();
                RectF sa=new RectF(planeX-60,planeY-60,planeX+240+60,planeY+240+60);
                if (sa.contains(tx,ty)) {
                    isDragging=true; dragPointerId=event.getPointerId(0);
                    dragOffsetX=tx-planeX; dragOffsetY=ty-planeY;
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx=event.getActionIndex();
                float tx=event.getX(idx), ty=event.getY(idx);
                if (!isDragging) {
                    RectF sa=new RectF(planeX-60,planeY-60,planeX+240+60,planeY+240+60);
                    if (sa.contains(tx,ty)) {
                        isDragging=true; dragPointerId=event.getPointerId(idx);
                        dragOffsetX=tx-planeX; dragOffsetY=ty-planeY;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    int pi=event.findPointerIndex(dragPointerId);
                    if (pi>=0) {
                        float dx = event.getX(pi) - dragOffsetX - planeX;
                        float dy = event.getY(pi) - dragOffsetY - planeY;
                        planeX += dx * dragSensitivity;
                        planeY += dy * dragSensitivity;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) { /* slow-mo now handled by isDragging state continuously */ }
                isDragging = false; dragPointerId = -1;
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                int pid=event.getPointerId(event.getActionIndex());
                if (pid==dragPointerId) { isDragging=false; dragPointerId=-1; }
                break;
            }
        }
        return true;
    }

    // ===================== MODEL CLASSES =====================
    private static class AirEnemy {
        float x, y, speedX, speedY;
        AirType type;
        int hp, maxHp, shootTimer;
        float waveAmt, waveSpd, waveAmp;
        float damage;
        int   hitFlash = 0;
        float angle    = 0f;
        float velX     = 0f;
        float velY     = 0f;
        int   kamiState   = 0;
        float kamiTargetX = 0;
        float kamiTargetY = 0;
        boolean kamiSweepRight = true;
        float kamiEntryX  = 0;
        int   escapeTimer = -1;  // -1 = no escape timer. When >0 counts down, at 0 enemy escapes
        float dirVelX  = 0f;   // smoothed velocity for sprite direction (StarSparrow/SwitchBlade)
        float dirVelY  = 1f;   // default facing down

        AirEnemy(float x, float y, AirType t, int hp, float sx, float sy,
                 int shootTimer, int baseShootTimer, float damage) {
            this.x = x; this.y = y; this.type = t; this.hp = hp; this.maxHp = hp;
            this.speedX = sx; this.speedY = sy;
            this.shootTimer = shootTimer; this.damage = damage;
        }
    }

    private static class BossEnemy {
        float x, y, moveDir = 1f;
        // ── Boss type ──────────────────────────────────────────────────
        int   bossType = 0;  // 0=Space, 1=Desert, 2=Ocean
        // ── Multi-part HP ──────────────────────────────────────────────
        int   hp = 0;          // legacy field kept for compat
        int   bodyHP      = 200;
        int   leftArmHP   = 80;
        int   rightArmHP  = 80;
        int   headHP      = 120;
        boolean leftArmAlive  = true;
        boolean rightArmAlive = true;
        boolean headAlive     = true;
        // hit flash per part
        int   bodyFlash = 0, leftArmFlash = 0, rightArmFlash = 0, headFlash = 0;
        // smoke particles for destroyed parts (drawn each frame)
        int   smokeTimer = 0;
        // ── Phase / timers ─────────────────────────────────────────────
        int   phase = 0;
        int   spreadTimer  = 30;
        int   laserTimer   = BOSS_LASER_INTERVAL;
        int   spiralTimer  = BOSS_SPIRAL_INTERVAL;
        int   mineTimer    = BOSS_MINE_INTERVAL;
        int   barrageTimer = BOSS_BARRAGE_INTERVAL;
        boolean laserFiring  = false;
        int     laserDuration= 0;
        float   laserAngle   = 0f;
        float   spiralAngle  = 0f;
        int     phaseFlashTimer = 0;
        boolean introDone    = false;
        float   dashVelX     = 0f;
        int     dashTimer    = 0;
        // ── Desert boss parts ──────────────────────────────────────────
        int   leftCannonHP  = 80;
        int   rightCannonHP = 80;
        int   armorHP       = 120;
        boolean leftCannonAlive  = true;
        boolean rightCannonAlive = true;
        boolean armorAlive       = true;
        int   leftCannonFlash=0, rightCannonFlash=0, armorFlash=0;
        // ── Ocean boss parts ──────────────────────────────────────────
        int   frontFinHP  = 80;
        int   backFinHP   = 80;
        int   serpentHeadHP = 120;
        boolean frontFinAlive  = true;
        boolean backFinAlive   = true;
        boolean serpentHeadAlive = true;
        int   frontFinFlash=0, backFinFlash=0, serpentHeadFlash=0;
        // ── Desert/Ocean shared fields ─────────────────────────────────
        int   sandWaveTimer  = 120;
        int   cannonTimer    = 80;
        float zigzagOffset   = 0f;
        // ── Ocean boss fields ──────────────────────────────────────────
        boolean diveActive   = false;
        int     diveTimer    = 0;
        float   diveTargetX  = 0f;
        int     waveAttackTimer = 90;
        int     serpentTimer    = 60;
        // Independent turret positions (float around screen separately)
        float leftTurretX = 0f,  leftTurretY = 0f;
        float rightTurretX = 0f, rightTurretY = 0f;
        float leftTurretPhase  = 0f;
        float rightTurretPhase = (float)Math.PI;
        int   leftTurretFlash2 = 0, rightTurretFlash2 = 0;
        // ── Serpent chain segments — each follows the previous ────────────
        // seg[0]=head anchor, seg[1]=body anchor, seg[2]=tail anchor
        static final int SEGS = 3;
        float[] segX    = new float[SEGS];
        float[] segY    = new float[SEGS];
        float[] segAngle= new float[SEGS]; // degrees, for rotation
        boolean serpentInitDone = false;
        // Total HP helper
        int totalHP() { return bodyHP + (leftArmAlive?leftArmHP:0) + (rightArmAlive?rightArmHP:0) + (headAlive?headHP:0); }
        BossEnemy(float x, float y, int ignored) {
            this.x = x; this.y = y;
        }
    }

    private static class BossMine {
        float x, y, vx, vy;
        int   life = 420;
        int   armTimer = 60;
        float rotAngle = 0f;
        BossMine(float x, float y, float vx, float vy) { this.x=x; this.y=y; this.vx=vx; this.vy=vy; }
    }

    private static class PlayerBullet {
        float x,y,prevX,prevY,speed,vx; int type;
        PlayerBullet(float x,float y,float sp,int t){this.x=x;this.y=y;this.prevX=x;this.prevY=y;speed=sp;vx=0;type=t;}
        PlayerBullet(float x,float y,float sp,float vx,int t){this.x=x;this.y=y;this.prevX=x;this.prevY=y;speed=sp;this.vx=vx;type=t;}
    }

    private static class EnemyBullet {
        float x, y, dx, dy, damage; int type;
        EnemyBullet(float x, float y, float dx, float dy, int type, float damage) {
            this.x=x; this.y=y; this.dx=dx; this.dy=dy; this.type=type; this.damage=damage;
        }
    }

    private static class Asteroid {
        float x, y, speed, rotation, rotSpeed; int hp = 3;
        Asteroid(float x, float y, float spd, float rot, float rotSpd) {
            this.x=x; this.y=y; speed=spd; rotation=rot; rotSpeed=rotSpd;
        }
    }

    private void updateAsteroids(Canvas canvas, RectF planeRect) {
        boolean isMeteorWave = currentWaveType == 4;
        // Regular asteroid sprite half-size — loaded at 140x140 so radius = 70
        float regularR = spriteAsteroid != null
                ? spriteAsteroid.getWidth() / 2f   // 70px actual sprite radius
                : 35f;                              // fallback for canvas circle

        for (int i = asteroids.size()-1; i >= 0; i--) {
            Asteroid a = asteroids.get(i);
            a.y += a.speed * slowMoScale; a.rotation += a.rotSpeed * slowMoScale;

            // Hit radius — match what is actually drawn on screen
            float hw = isMeteorWave ? 14f + a.hp * 14f : 35f;  // logical size
            float hitR = isMeteorWave ? hw * 1.1f : regularR;   // actual visual radius

            // Player collision
            if (RectF.intersects(planeRect, new RectF(a.x-hitR, a.y-hitR, a.x+hitR, a.y+hitR))) {
                damagePlayer(isMeteorWave ? 8f : 15f);
                addExplosion(a.x, a.y, hitR * 1.5f);
                if (isMeteorWave) dropStarsFromMeteor(a.x, a.y, a.hp);
                asteroids.remove(i); continue;
            }

            // Bullet collision — circle check using actual visual size
            boolean shotDown = false;
            for (int j = bullets.size()-1; j >= 0; j--) {
                PlayerBullet b = bullets.get(j);
                float dx = b.x - a.x, dy = b.y - a.y;
                if (dx*dx + dy*dy < hitR*hitR) {
                    int dmg = b.type == 2 ? 3 : 1;
                    a.hp -= dmg;
                    bullets.remove(j); runBulletsHit++;
                    if (a.hp <= 0) {
                        if (isMeteorWave) {
                            dropStarsFromMeteor(a.x, a.y, 2 + random.nextInt(2));
                            addExplosion(a.x, a.y, hitR * 1.5f);
                            spawnDeathParticles(a.x, a.y, AirType.DRONE);
                            playSound(sndExplosion, 0.7f, 1.1f + random.nextFloat() * 0.2f);
                            score += 5; waveKillsGot++;
                        } else {
                            giveReward(4, a.x, a.y);
                            addExplosion(a.x, a.y, hitR);
                        }
                        asteroids.remove(i); shotDown = true;
                    } else {
                        shake(2f);
                        if (isMeteorWave) {
                            spawnDeathParticles(a.x, a.y, AirType.KAMIKAZE);
                            playSound(sndExplosionSm, 0.5f, 1.2f);
                        }
                    }
                    break;
                }
            }
            if (shotDown) continue;
            if (a.y > screenHeight + 80) { asteroids.remove(i); continue; }

            // Draw
            if (isMeteorWave) {
                drawMeteor(canvas, a, hw);
            } else if (spriteAsteroid != null) {
                canvas.save(); canvas.rotate(a.rotation, a.x, a.y);
                canvas.drawBitmap(spriteAsteroid, null,
                        new RectF(a.x - regularR, a.y - regularR, a.x + regularR, a.y + regularR),
                        bitmapPaint);
                // Directional shading — sun from top-left, dark on bottom-right
                if (currentMap == MAP_SPACE) {
                    paint.setShader(new RadialGradient(
                            a.x - regularR * 0.4f, a.y - regularR * 0.4f, regularR * 1.6f,
                            new int[]{Color.argb(0,0,0,0), Color.argb(100,0,0,0)},
                            new float[]{0.4f, 1f}, Shader.TileMode.CLAMP));
                    canvas.drawCircle(a.x, a.y, regularR, paint);
                    paint.setShader(null);
                }
                canvas.restore();
            } else {
                paint.setColor(Color.parseColor("#556070"));
                canvas.drawCircle(a.x, a.y, regularR, paint);
            }
        }
    }

    /** Drop star pickups at a meteor's death position */
    private void dropStarsFromMeteor(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            float sx = x + (random.nextFloat() - 0.5f) * 40f;
            float sy = y + (random.nextFloat() - 0.5f) * 20f;
            starPickups.add(new StarPickup(sx, sy, 1));
        }
        addFloating(x, y - 30, "+" + count + "★", Color.parseColor("#FFD700"));
    }

    /** Draw a meteor using the enemy_asteroid sprite, scaled by HP (size tier) */
    private void drawMeteor(Canvas canvas, Asteroid a, float hw) {
        // Subtle inner glow only — drawn UNDER the sprite, not as a visible ring
        int glowAlpha = (int)(18 + Math.sin(a.rotation * 0.05f) * 8 + 8);
        paint.setColor(Color.argb(glowAlpha, 255, 80, 0));
        canvas.drawCircle(a.x, a.y, hw * 1.05f, paint); // tight — barely larger than sprite

        if (spriteAsteroid != null) {
            float half = hw * 1.1f;
            canvas.save();
            canvas.rotate(a.rotation, a.x, a.y);
            canvas.drawBitmap(spriteAsteroid, null,
                    new RectF(a.x - half, a.y - half, a.x + half, a.y + half), bitmapPaint);
            canvas.restore();
        } else {
            paint.setColor(Color.parseColor("#3a3028"));
            canvas.save();
            canvas.rotate(a.rotation, a.x, a.y);
            canvas.drawCircle(a.x, a.y, hw, paint);
            canvas.restore();
        }

        // Crack marks when damaged (HP < max)
        if (a.hp < 3) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
            paint.setColor(Color.argb(200, 255, 60, 0));
            for (int c = a.hp; c < 3; c++) {
                float angle = c * 2.1f;
                canvas.drawLine(
                        a.x + (float)Math.cos(angle) * hw * 0.4f,
                        a.y + (float)Math.sin(angle) * hw * 0.4f,
                        a.x + (float)Math.cos(angle) * hw * 0.95f,
                        a.y + (float)Math.sin(angle) * hw * 0.95f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** Update and draw desert ambient prop objects (cacti, skulls, bones, rocks). */
    private void updateDesertProps(Canvas canvas) {
        if (currentMap != MAP_DESERT) { desertPropObjs.clear(); return; }

        Paint propPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        android.graphics.Matrix propMatrix = new android.graphics.Matrix();

        for (int i = desertPropObjs.size() - 1; i >= 0; i--) {
            DesertPropObj p = desertPropObjs.get(i);
            // Same speed as desert strip — prop locked to terrain
            p.y += scrollSpeed * slowMoScale * 0.85f;

            if (p.y > screenHeight + 120) { desertPropObjs.remove(i); continue; }

            Bitmap bm = desertProps[p.spriteIdx];
            if (bm == null) continue;
            float drawW = bm.getWidth()  * p.scale;
            float drawH = bm.getHeight() * p.scale;
            float drawX = p.x - drawW / 2f;
            float drawY = p.y - drawH;  // bottom-anchored

            propPaint.setAlpha(160); // match desert strip alpha
            propMatrix.setScale(p.scale, p.scale);
            propMatrix.postTranslate(drawX, drawY);
            canvas.drawBitmap(bm, propMatrix, propPaint);
            propMatrix.reset();
        }
    }

    /** Update and draw ocean ambient prop objects (ice formations, fossils). */
    private void updateOceanProps(Canvas canvas) {
        if (currentMap != MAP_OCEAN) { oceanPropObjs.clear(); return; }

        Paint propPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        propPaint.setAlpha(148); // set once — same alpha for every prop

        for (int i = oceanPropObjs.size() - 1; i >= 0; i--) {
            OceanPropObj p = oceanPropObjs.get(i);
            p.y += scrollSpeed * slowMoScale * 0.85f;
            if (p.y > screenHeight + 120) { oceanPropObjs.remove(i); continue; }

            Bitmap bm = oceanProps[p.spriteIdx];
            if (bm == null) continue;

            float drawW = bm.getWidth()  * p.scale;
            float drawH = bm.getHeight() * p.scale;
            float drawX = p.x - drawW / 2f;  // X is stored at spawn, never modified
            float drawY = p.y - drawH;

            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setScale(p.scale, p.scale);
            m.postTranslate(drawX, drawY);
            canvas.drawBitmap(bm, m, propPaint);
        }
    }

    private void updateStarPickups(Canvas canvas, RectF planeRect) {
        float planeCx = planeX + 120f, planeCy = planeY + 120f;
        for (int i = starPickups.size()-1; i >= 0; i--) {
            StarPickup s = starPickups.get(i);
            float dx = planeCx - s.x, dy = planeCy - s.y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);

            if (magnetTimer > 0) {
                // Magnet active — strong pull from anywhere on screen
                if (dist > 1f) {
                    s.x += dx / dist * 9f;
                    s.y += dy / dist * 9f;
                }
            } else if (dist < 160f && dist > 1f) {
                // Close range gentle drift toward player — only when within 160px
                s.x += dx / dist * 2.5f;
                s.y += dy / dist * 2.5f;
            } else {
                // Normal slow drift down — much slower so player has time to collect
                s.y += s.vy;
            }
            s.rotation += s.rotSpeed;

            // Collect — larger radius so it feels good
            if (dist < 90f) {
                meteorShowerStarsCollected++;
                score += 12;
                totalStarsEver++;
                coinCount += 3;
                totalCoins += 3;
                prefs.edit().putInt("tc", totalCoins).apply();
                addFloating(s.x, s.y - 20, "★+12", Color.parseColor("#FFD700"));
                spawnPickupBurst(s.x, s.y, Color.parseColor("#FFD700"));
                playSound(sndCoinPickup, 0.9f, 1.1f + random.nextFloat() * 0.2f);
                starPickups.remove(i); continue;
            }
            // Remove only when well off-screen bottom — give player time
            if (s.y > screenHeight + 200) { starPickups.remove(i); continue; }
            drawStarPickup(canvas, s);
        }
    }

    /** Draw a glowing collectible star */
    private void drawStarPickup(Canvas canvas, StarPickup s) {
        float pulse = 1f + (float)Math.sin(frameCount * 0.15f + s.x * 0.02f) * 0.12f;
        float r = 18f * pulse;
        canvas.save();
        canvas.rotate(s.rotation, s.x, s.y);
        // Outer glow
        paint.setColor(Color.argb(60, 255, 215, 0));
        drawStar(canvas, s.x, s.y, r * 1.9f, r * 0.85f, paint);
        // Mid glow
        paint.setColor(Color.argb(120, 255, 200, 30));
        drawStar(canvas, s.x, s.y, r * 1.4f, r * 0.65f, paint);
        // Core star
        paint.setColor(Color.parseColor("#FFD700"));
        drawStar(canvas, s.x, s.y, r, r * 0.45f, paint);
        // Bright centre
        paint.setColor(Color.argb(220, 255, 255, 180));
        drawStar(canvas, s.x, s.y, r * 0.55f, r * 0.24f, paint);
        canvas.restore();
        // Sparkle cross on top
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        paint.setColor(Color.argb(160, 255, 255, 200));
        float sp = r * 1.3f;
        canvas.drawLine(s.x - sp, s.y, s.x + sp, s.y, paint);
        canvas.drawLine(s.x, s.y - sp, s.x, s.y + sp, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static class HealthPickup {
        float x, y;
        HealthPickup(float x, float y) { this.x = x; this.y = y; }
    }

    private static class StarPickup {
        float x, y, vy, rotation, rotSpeed, alpha;
        int   starValue; // 1–3 score stars
        StarPickup(float x, float y, int val) {
            this.x = x; this.y = y;
            this.vy       = 0.4f + (float)Math.random() * 0.6f;  // slow drift — player has time to collect
            this.rotation = (float)(Math.random() * 360);
            this.rotSpeed = (float)(Math.random() - 0.5f) * 5f;
            this.starValue = val;
            this.alpha    = 255f;
        }
    }

    private static class CoinParticle {
        float x, y, vx, vy, life, size;
        int color;
        CoinParticle(float x, float y, float vx, float vy, int color, float size) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy;
            this.color=color; this.size=size; this.life=1f;
        }
        void update() {
            x += vx; y += vy;
            vy += 0.18f;   // gravity
            vx *= 0.97f;
            life -= 0.032f;
            size *= 0.98f;
        }
        boolean isDead() { return life <= 0 || size < 0.6f; }
        void draw(Canvas canvas, Paint paint) {
            int a = (int)(255 * Math.max(0, life));
            // Outer glow
            paint.setColor(Color.argb(a/3, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawCircle(x, y, size * 2f, paint);
            // Core
            paint.setColor(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawCircle(x, y, size, paint);
            // Bright centre
            if (life > 0.4f) {
                paint.setColor(Color.argb((int)(a * 0.8f), 255, 255, 220));
                canvas.drawCircle(x, y, size * 0.4f, paint);
            }
        }
    }

    private static class CoinPickup {
        float x,y;
        CoinPickup(float x,float y){this.x=x;this.y=y;}
    }

    private static class PowerUpPickup {
        float x,y; PowerUpType type;
        PowerUpPickup(float x,float y,PowerUpType t){this.x=x;this.y=y;type=t;}
    }

    private static class ExplosionFx {
        float x, y, radius, alpha, maxRadius;
        int   type;    // 0=normal, 1=large, 2=boss, 3=small impact
        float phase;   // 0→1 normalized life
        int   frame;
        ExplosionFx(float x,float y,float r,float a,float mr) {
            this.x=x; this.y=y; radius=r; alpha=a; maxRadius=mr; type=0; phase=0; frame=0;
        }
        ExplosionFx(float x,float y,float r,float a,float mr,int type) {
            this.x=x; this.y=y; radius=r; alpha=a; maxRadius=mr; this.type=type; phase=0; frame=0;
        }
    }

    private static class FloatingTextFx {
        float x, y, alpha, size, vy, scale;
        String text; int color;
        FloatingTextFx(float x,float y,String t,int c,float a){
            this.x=x; this.y=y; text=t; color=c; alpha=a; size=30f; vy=2f; scale=1f;
        }
        FloatingTextFx(float x,float y,String t,int c,float a,float sz){
            this.x=x; this.y=y; text=t; color=c; alpha=a; size=sz; vy=2f; scale=1f;
        }
        FloatingTextFx(float x,float y,String t,int c,float a,float sz,float vy,float scale){
            this.x=x; this.y=y; text=t; color=c; alpha=a; size=sz; this.vy=vy; this.scale=scale;
        }
    }

    // Desert ambient prop — cactus/skull/bone/rock drifting across the desert map
    private static class DesertPropObj {
        float x, y, speed; int spriteIdx; float alpha, scale;
        DesertPropObj(float x, float y, float speed, int sprite, float scale) {
            this.x=x; this.y=y; this.speed=speed; this.spriteIdx=sprite; this.alpha=0f; this.scale=scale;
        }
    }

    // Ocean ambient prop — ice formation / fossil drifting across the ocean map
    private static class OceanPropObj {
        float x, y, speed;
        int   spriteIdx;      // which of the 25 sprites to use
        float alpha;          // fade in/out
        float scale;          // size variation
        OceanPropObj(float x, float y, float speed, int sprite, float scale) {
            this.x = x; this.y = y; this.speed = speed;
            this.spriteIdx = sprite; this.alpha = 0f; this.scale = scale;
        }
    }

    // Bullet trail — fading line segment1234t670 left behind a player bullet
    private static class BulletTrail {
        float x, y, prevX, prevY, alpha; int bulletType;
        BulletTrail(float x, float y, float px, float py, int t) {
            this.x=x; this.y=y; this.prevX=px; this.prevY=py; alpha=200f; bulletType=t;
        }
    }

    // Death particle — coloured spark emitted on enemy death
    private static class DeathParticle {
        float x, y, vx, vy, alpha, size;
        int color;
        DeathParticle(float x, float y, float vx, float vy, int color, float size) {
                this.x=x; this.y=y; this.vx=vx; this.vy=vy;
            this.color=color; this.size=size; alpha=255f;
        }
    }

    // Astronaut rescue — drifts down, player must stay close for 7 seconds
    private static class AstronautRescue {
        float x, y;
        int   rescueProgress = 0;  // 0 → RESCUE_FRAMES (420)
        AstronautRescue(float x, float y) { this.x = x; this.y = y; }
    }
}