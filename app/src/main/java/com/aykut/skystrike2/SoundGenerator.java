package com.aykut.skystrike2;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class SoundGenerator {

    private static final String TAG = "SoundGenerator";
    private static final int    SR  = 44100;
    private static final Random RNG = new Random(0xDEADBEEF);

    public static void generateAll(Context context) {
        File dir = getSoundDir(context);
        if (!dir.exists()) dir.mkdirs();
        File[] existing = dir.listFiles();
        if (existing != null) for (File f : existing) f.delete();

        write(dir, "gun_shoot",    buildGunShoot());
        write(dir, "super_shoot",  buildSuperShoot());
        write(dir, "cannon_boom",  buildCannonBoom());
        write(dir, "explosion_sm", buildExplosionSm());
        write(dir, "explosion_md", buildExplosionMd());
        write(dir, "explosion_lg", buildExplosionLg());
        write(dir, "player_hit",   buildPlayerHit());
        write(dir, "shield_up",    buildShieldUp());
        write(dir, "shield_block", buildShieldBlock());
        write(dir, "coin_pickup",  buildCoinPickup());
        write(dir, "gun_upgrade",  buildGunUpgrade());
        write(dir, "super_pickup", buildSuperPickup());
        write(dir, "boss_appear",  buildBossAppear());
        write(dir, "boss_death",   buildBossDeath());
        write(dir, "wave_start",   buildWaveStart());
        write(dir, "kamikaze",     buildKamikaze());
        write(dir, "enemy_shoot",  buildEnemyShoot());
        write(dir, "victory",      buildVictory());
        write(dir, "game_over",    buildGameOver());
        write(dir, "jet_engine",   buildJetEngine());
        write(dir, "missile_fire", buildMissileFire());

        Log.d(TAG, "All sounds generated -> " + dir.getAbsolutePath());
    }

    public static File getSoundDir(Context ctx) {
        return new File(ctx.getFilesDir(), "sounds");
    }

    public static File getSoundFile(Context ctx, String name) {
        return new File(getSoundDir(ctx), name + ".wav");
    }

    public static int loadSound(android.media.SoundPool pool, Context ctx, String name) {
        File f = getSoundFile(ctx, name);
        if (!f.exists()) return -1;
        try {
            android.content.res.AssetFileDescriptor afd =
                    ctx.getContentResolver().openAssetFileDescriptor(
                            android.net.Uri.fromFile(f), "r");
            if (afd == null) return -1;
            int id = pool.load(afd, 1);
            afd.close();
            return id;
        } catch (Exception e) {
            Log.e(TAG, "loadSound failed: " + name, e);
            return -1;
        }
    }

    private static void write(File dir, String name, float[] s) {
        File f = new File(dir, name + ".wav");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(toWav(s));
        } catch (IOException e) {
            Log.e(TAG, "write failed: " + name, e);
        }
    }

    private static byte[] toWav(float[] samples) {
        int data = samples.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'}); b.putInt(36 + data);
        b.put(new byte[]{'W','A','V','E'});
        b.put(new byte[]{'f','m','t',' '}); b.putInt(16);
        b.putShort((short)1); b.putShort((short)1);
        b.putInt(SR); b.putInt(SR * 2);
        b.putShort((short)2); b.putShort((short)16);
        b.put(new byte[]{'d','a','t','a'}); b.putInt(data);
        for (float v : samples)
            b.putShort((short) Math.max(-32768, Math.min(32767, (int)(v * 32767f))));
        return b.array();
    }

    private static int ms(int millis)    { return SR * millis / 1000; }
    private static int ms(double millis) { return (int)(SR * millis / 1000.0); }
    private static float clip(float v)  { return Math.max(-1f, Math.min(1f, v)); }

    private static float[] osc(int n, double hz, float amp) {
        float[] o = new float[n];
        for (int i = 0; i < n; i++)
            o[i] = (float)(Math.sin(2 * Math.PI * hz * i / SR) * amp);
        return o;
    }

    private static float[] oscFM(float[] freqHz, float amp) {
        float[] o = new float[freqHz.length];
        double ph = 0;
        for (int i = 0; i < freqHz.length; i++) {
            o[i] = (float)(Math.sin(ph) * amp);
            ph += 2 * Math.PI * freqHz[i] / SR;
            if (ph > Math.PI * 2) ph -= Math.PI * 2;
        }
        return o;
    }

    private static float[] sqr(int n, double hz, float amp) {
        float[] o = new float[n];
        for (int i = 0; i < n; i++)
            o[i] = (float)(Math.sin(2 * Math.PI * hz * i / SR) >= 0 ? amp : -amp);
        return o;
    }

    private static float[] saw(int n, double hz, float amp) {
        float[] o = new float[n];
        double period = SR / hz;
        for (int i = 0; i < n; i++)
            o[i] = (float)(((i % period) / period * 2.0 - 1.0) * amp);
        return o;
    }

    private static float[] fSweep(int n, double s, double e) {
        float[] f = new float[n];
        for (int i = 0; i < n; i++) f[i] = (float)(s + (e - s) * i / n);
        return f;
    }

    private static float[] fExpSweep(int n, double s, double e) {
        float[] f = new float[n];
        double r = Math.log(e / s);
        for (int i = 0; i < n; i++) f[i] = (float)(s * Math.exp(r * i / n));
        return f;
    }

    private static float[] noise(int n, float amp) {
        float[] o = new float[n];
        for (int i = 0; i < n; i++) o[i] = (RNG.nextFloat() * 2f - 1f) * amp;
        return o;
    }

    private static float[] bandNoise(int n, double centHz, double bw, float amp) {
        float[] raw = noise(n, amp);
        double w0 = 2 * Math.PI * centHz / SR;
        double alpha = Math.sin(w0) / (2.0 * centHz / bw);
        double b0 = alpha, b2 = -alpha;
        double a0 = 1 + alpha, a1 = -2 * Math.cos(w0), a2 = 1 - alpha;
        b0/=a0; b2/=a0; a1/=a0; a2/=a0;
        float[] out = new float[n];
        double x1=0,x2=0,y1=0,y2=0;
        for (int i = 0; i < n; i++) {
            double x0 = raw[i];
            double y0 = b0*x0 + b2*x2 - a1*y1 - a2*y2;
            out[i] = clip((float)y0);
            x2=x1; x1=x0; y2=y1; y1=y0;
        }
        return out;
    }

    private static float[] lpf(float[] src, double cutHz) {
        float[] out = new float[src.length];
        double a = (2*Math.PI*(cutHz/SR)) / (2*Math.PI*(cutHz/SR)+1);
        double p = 0;
        for (int i = 0; i < src.length; i++) { p = p + a*(src[i]-p); out[i]=(float)p; }
        return out;
    }

    private static float[] hpf(float[] src, double cutHz) {
        float[] out = new float[src.length];
        double rc = 1.0/(2*Math.PI*cutHz), dt = 1.0/SR, a = rc/(rc+dt);
        double px=0,py=0;
        for (int i = 0; i < src.length; i++) {
            double y = a*(py+src[i]-px); out[i]=clip((float)y); px=src[i]; py=y;
        }
        return out;
    }

    private static float[] sat(float[] src, float drive) {
        float[] out = new float[src.length];
        for (int i = 0; i < src.length; i++) {
            float v = src[i] * drive;
            out[i] = v / (1f + Math.abs(v));
        }
        return out;
    }

    private static float[] reverb(float[] src, int delaySmp, float fb, float wet) {
        float[] out = src.clone();
        float[] buf = new float[delaySmp];
        int pos = 0;
        for (int i = 0; i < out.length; i++) {
            float d = buf[pos];
            buf[pos] = clip(out[i] + d * fb);
            out[i] = clip(out[i]*(1-wet) + d*wet);
            pos = (pos+1) % delaySmp;
        }
        return out;
    }

    private static float[] decay(float[] src, double halfSec) {
        float[] out = src.clone();
        double k = Math.log(0.5)/(halfSec*SR);
        for (int i = 0; i < out.length; i++) out[i] *= (float)Math.exp(k*i);
        return out;
    }

    private static float[] ad(float[] src, int attSmp, double halfSec) {
        float[] out = src.clone();
        double k = Math.log(0.5)/(halfSec*SR);
        for (int i = 0; i < out.length; i++) {
            float a = i < attSmp ? (float)i/attSmp : 1f;
            float d = (float)Math.exp(k*Math.max(0, i-attSmp));
            out[i] *= a*d;
        }
        return out;
    }

    private static float[] gain(float[] src, float g) {
        float[] out = new float[src.length];
        for (int i = 0; i < src.length; i++) out[i] = clip(src[i]*g);
        return out;
    }

    private static float[] mix(float[]... layers) {
        int len = 0;
        for (float[] l : layers) if (l.length > len) len = l.length;
        float[] out = new float[len];
        for (float[] l : layers)
            for (int i = 0; i < l.length; i++) out[i] = clip(out[i] + l[i]);
        return out;
    }

    private static float[] cat(float[]... parts) {
        int t = 0; for (float[] p : parts) t += p.length;
        float[] out = new float[t]; int pos = 0;
        for (float[] p : parts) { System.arraycopy(p,0,out,pos,p.length); pos+=p.length; }
        return out;
    }

    private static float[] pad(float[] src, int n) {
        if (src.length == n) return src;
        float[] out = new float[n];
        System.arraycopy(src, 0, out, 0, Math.min(src.length, n));
        return out;
    }

    private static float[] fadeIn(float[] src, int n) {
        float[] out = src.clone();
        for (int i = 0; i < Math.min(n, out.length); i++) out[i] *= (float)i/n;
        return out;
    }

    private static float[] fadeOut(float[] src, int n) {
        float[] out = src.clone();
        for (int i = 0; i < Math.min(n, out.length); i++)
            out[out.length-1-i] *= (float)i/n;
        return out;
    }

    private static float[] norm(float[] src, float peak) {
        float max = 0;
        for (float v : src) if (Math.abs(v) > max) max = Math.abs(v);
        if (max < 0.001f) return src;
        return gain(src, peak/max);
    }

    private static void addAt(float[] dest, float[] src, int offset) {
        for (int i = 0; i < src.length && offset+i < dest.length; i++)
            dest[offset+i] = clip(dest[offset+i] + src[i]);
    }

    // GUN SHOOT - sharp laser zap with metallic click
    private static float[] buildGunShoot() {
        int len = ms(110);
        float[] zap    = ad(oscFM(fExpSweep(len, 1800, 180), 0.70f), ms(1), 0.018);
        float[] click  = decay(bandNoise(ms(12), 3800, 2400, 0.55f), 0.002);
        float[] thump  = ad(lpf(oscFM(fExpSweep(ms(60), 280, 45), 0.65f), 400), ms(2), 0.015);
        float[] sizzle = decay(bandNoise(ms(35), 6200, 4000, 0.28f), 0.005);
        float[] out    = mix(pad(zap,len), pad(click,len), pad(thump,len), pad(sizzle,len));
        return norm(fadeOut(sat(out, 1.4f), ms(8)), 0.78f);
    }

    // SUPER SHOOT - heavy plasma cannon charge and release
    private static float[] buildSuperShoot() {
        int len = ms(320);
        float[] charge  = ad(oscFM(fSweep(ms(90), 320, 3200), 0.55f), ms(10), 0.040);
        float[] plasma  = ad(sat(lpf(oscFM(fExpSweep(ms(240), 900, 40), 0.9f), 700), 2.8f), ms(5), 0.060);
        float[] crackle = decay(fadeIn(bandNoise(ms(200), 4200, 3000, 0.42f), ms(20)), 0.052);
        float[] shimmer = fadeOut(fadeIn(
                mix(osc(len, 220, 0.18f), osc(len, 440, 0.12f), osc(len, 880, 0.06f)),
                ms(30)), ms(40));
        float[] out = mix(pad(charge,len), pad(plasma,len), pad(crackle,len), shimmer);
        return norm(fadeOut(out, ms(25)), 0.88f);
    }

    // CANNON BOOM - deep space artillery
    private static float[] buildCannonBoom() {
        int mainLen = ms(900);
        float[] preCrack = decay(bandNoise(ms(30), 5500, 2800, 0.45f), 0.006);
        float[] sub  = ad(sat(lpf(oscFM(fExpSweep(mainLen, 55, 14), 1.0f), 120), 4.2f), ms(6), 0.230);
        float[] mid  = ad(sat(lpf(hpf(oscFM(fExpSweep(mainLen, 380, 45), 0.88f), 70), 640), 2.2f), ms(4), 0.120);
        float[] crack = mix(decay(noise(ms(20), 0.9f), 0.004),
                decay(oscFM(fExpSweep(ms(20), 2200, 700), 0.6f), 0.004));
        float[] smoke = decay(fadeIn(lpf(bandNoise(mainLen, 340, 500, 0.45f), 560), ms(28)), 0.160);
        float[] ring  = mix(decay(fadeIn(osc(ms(420), 190, 0.22f), ms(8)), 0.085),
                decay(fadeIn(osc(ms(300), 380, 0.13f), ms(6)), 0.065),
                decay(fadeIn(osc(ms(200), 760, 0.07f), ms(4)), 0.045));
        float[] rumble = decay(fadeIn(lpf(bandNoise(mainLen, 68, 88, 0.40f), 155), ms(75)), 0.310);
        float[] main = mix(pad(sub,mainLen), pad(mid,mainLen), pad(crack,mainLen),
                pad(smoke,mainLen), pad(ring,mainLen), pad(rumble,mainLen));
        main = reverb(main, ms(88), 0.28f, 0.16f);
        main = reverb(main, ms(195), 0.19f, 0.10f);
        float[] out = cat(pad(preCrack, ms(22)), new float[ms(22)], main);
        return norm(fadeOut(out, ms(115)), 0.90f);
    }

    // EXPLOSION SMALL - metallic pop with debris
    private static float[] buildExplosionSm() {
        int len = ms(280);
        float[] crack = decay(bandNoise(ms(18), 4200, 2800, 0.72f), 0.005);
        float[] body  = ad(sat(lpf(oscFM(fExpSweep(len, 220, 38), 0.78f), 420), 1.8f), ms(3), 0.042);
        float[] debris = new float[len];
        for (int i = 0; i < 5; i++) {
            int offset = ms(15 + i * 22);
            float[] ping = decay(osc(ms(45), 800 + i * 320, 0.18f), 0.012);
            addAt(debris, ping, offset);
        }
        float[] out = mix(pad(crack,len), body, debris);
        return norm(fadeOut(out, ms(22)), 0.72f);
    }

    // EXPLOSION MEDIUM
    private static float[] buildExplosionMd() {
        int len = ms(520);
        float[] sub   = ad(sat(lpf(oscFM(fExpSweep(len, 145, 22), 0.88f), 280), 2.6f), ms(4), 0.095);
        float[] crack = decay(mix(noise(ms(28), 0.80f),
                oscFM(fExpSweep(ms(28), 1800, 500), 0.55f)), 0.006);
        float[] smoke = decay(fadeIn(lpf(bandNoise(len, 280, 420, 0.42f), 480), ms(22)), 0.115);
        float[] groan = decay(fadeIn(sat(osc(len, 95, 0.38f), 1.6f), ms(12)), 0.065);
        float[] out   = mix(pad(crack,len), sub, smoke, pad(groan,len));
        out = reverb(out, ms(68), 0.22f, 0.13f);
        return norm(fadeOut(out, ms(55)), 0.82f);
    }

    // EXPLOSION LARGE - cinematic space detonation
    private static float[] buildExplosionLg() {
        int len = ms(1100);
        float[] sub    = ad(sat(lpf(oscFM(fExpSweep(len, 62, 8), 1.0f), 140), 5.5f), ms(7), 0.280);
        float[] mid    = ad(sat(lpf(hpf(oscFM(fExpSweep(len, 340, 38), 0.90f), 55), 580), 2.4f), ms(4), 0.145);
        float[] crack  = decay(mix(noise(ms(35), 1.0f),
                oscFM(fExpSweep(ms(35), 2800, 580), 0.70f)), 0.007);
        float[] rumble = decay(fadeIn(lpf(bandNoise(len, 55, 80, 0.50f), 150), ms(62)), 0.380);
        float[] ring   = decay(fadeIn(mix(osc(len, 88, 0.24f), osc(len, 176, 0.14f)), ms(15)), 0.120);
        float[] shock  = decay(fadeIn(bandNoise(ms(280), 500, 800, 0.55f), ms(8)), 0.090);
        float[] out    = mix(pad(sub,len), pad(mid,len), pad(crack,len),
                pad(rumble,len), pad(ring,len), pad(shock,len));
        out = reverb(out, ms(125), 0.32f, 0.20f);
        out = reverb(out, ms(245), 0.22f, 0.12f);
        return norm(fadeOut(out, ms(185)), 0.92f);
    }

    // PLAYER HIT - hull impact + warning alarm
    private static float[] buildPlayerHit() {
        int len = ms(380);
        float[] clang = decay(sat(mix(osc(len, 320, 0.42f), osc(len, 640, 0.22f),
                osc(len, 1280, 0.11f)), 2.0f), 0.032);
        float[] alarm  = ad(mix(sqr(ms(85), 880, 0.35f), sqr(ms(85), 1320, 0.18f)), ms(4), 0.038);
        float[] gap    = new float[ms(45)];
        float[] alarm2 = alarm.clone();
        float[] stress = decay(fadeIn(bandNoise(len, 2800, 2200, 0.28f), ms(12)), 0.038);
        float[] out = mix(clang, pad(cat(alarm, gap, alarm2), len), stress);
        return norm(fadeOut(out, ms(28)), 0.80f);
    }

    // SHIELD UP - energy field activation
    private static float[] buildShieldUp() {
        int len = ms(520);
        float[] hum = fadeOut(fadeIn(mix(
                oscFM(fSweep(len, 180, 820), 0.48f),
                oscFM(fSweep(len, 360, 1640), 0.24f),
                oscFM(fSweep(len, 540, 2460), 0.12f)), ms(22)), ms(58));
        float[] spark = decay(fadeIn(bandNoise(len, 4800, 3500, 0.28f), ms(35)), 0.072);
        float[] ping  = ad(mix(osc(ms(140), 1320, 0.45f), osc(ms(140), 1980, 0.22f)), ms(8), 0.065);
        float[] out   = mix(hum, spark);
        addAt(out, ping, ms(340));
        out = reverb(out, ms(48), 0.18f, 0.12f);
        return norm(fadeOut(out, ms(35)), 0.74f);
    }

    // SHIELD BLOCK - deflection
    private static float[] buildShieldBlock() {
        int len = ms(340);
        float[] ping  = ad(oscFM(fExpSweep(len, 2800, 620), 0.65f), ms(4), 0.065);
        float[] disp  = decay(fadeIn(bandNoise(ms(160), 3800, 2800, 0.38f), ms(5)), 0.035);
        float[] thud  = ad(lpf(oscFM(fExpSweep(ms(120), 220, 48), 0.52f), 320), ms(3), 0.028);
        float[] out   = mix(pad(ping,len), pad(disp,len), pad(thud,len));
        return norm(fadeOut(out, ms(22)), 0.76f);
    }

    // COIN PICKUP - digital chime
    private static float[] buildCoinPickup() {
        int len = ms(280);
        float[] blip1 = decay(fadeIn(mix(osc(len, 1046, 0.45f), osc(len, 1568, 0.28f),
                osc(len, 2093, 0.14f)), ms(3)), 0.068);
        float[] blip2 = decay(fadeIn(mix(osc(len, 1318, 0.42f), osc(len, 1976, 0.24f)), ms(3)), 0.058);
        float[] spark  = decay(bandNoise(ms(22), 6400, 3800, 0.20f), 0.005);
        float[] out    = new float[len];
        addAt(out, blip1, 0);
        addAt(out, blip2, ms(55));
        addAt(out, spark, 0);
        return norm(fadeOut(out, ms(22)), 0.66f);
    }

    // GUN UPGRADE - mechanical ratchet + power surge + chord
    private static float[] buildGunUpgrade() {
        float[] ratchet = new float[ms(180)];
        for (int i = 0; i < 6; i++) {
            float[] click = decay(bandNoise(ms(18), 3200+i*200, 1800, 0.45f), 0.004);
            addAt(ratchet, click, ms(i * 28));
        }
        float[] surge = fadeOut(fadeIn(oscFM(fExpSweep(ms(280), 220, 2800), 0.62f), ms(12)), ms(35));
        int cLen = ms(320);
        double[] notes = {523.25, 783.99, 1046.5, 1568.0};
        float[] chord  = new float[cLen];
        for (int i = 0; i < notes.length; i++) {
            float[] n = ad(mix(osc(cLen,notes[i],0.38f), osc(cLen,notes[i]*2,0.18f),
                    sqr(cLen,notes[i],0.12f)), ms(6), 0.068);
            addAt(chord, n, ms(i * 18));
        }
        float[] out = new float[ms(500)];
        addAt(out, ratchet, 0);
        addAt(out, pad(surge, ms(320)), ms(60));
        addAt(out, chord, ms(160));
        return norm(fadeOut(out, ms(42)), 0.80f);
    }

    // SUPER PICKUP - hyperspace power surge
    private static float[] buildSuperPickup() {
        int len = ms(620);
        float[] whine = fadeOut(fadeIn(mix(
                oscFM(fSweep(ms(340), 180, 3200), 0.62f),
                oscFM(fSweep(ms(340), 270, 4800), 0.32f)), ms(22)), ms(55));
        float[] boom  = ad(sat(lpf(oscFM(fExpSweep(ms(300), 310, 28), 0.88f), 520), 2.6f), ms(5), 0.082);
        float[] shimmer = decay(fadeIn(bandNoise(ms(450), 3200, 2600, 0.35f), ms(55)), 0.105);
        float[] beeps   = new float[ms(220)];
        for (int i = 0; i < 5; i++) {
            float[] b = ad(sqr(ms(28), 1800 + i*280, 0.32f), ms(3), 0.012);
            addAt(beeps, b, ms(i * 42));
        }
        float[] out = mix(pad(whine,len), pad(boom,len), pad(shimmer,len), pad(beeps,len));
        out = reverb(out, ms(62), 0.22f, 0.15f);
        return norm(fadeOut(out, ms(42)), 0.86f);
    }

    // BOSS APPEAR - deep space dread
    private static float[] buildBossAppear() {
        int len = ms(1800);
        float[] drone = fadeOut(fadeIn(sat(mix(
                osc(len, 28, 0.65f), osc(len, 42, 0.42f), osc(len, 56, 0.25f),
                saw(len, 28, 0.22f)), 2.0f), ms(145)), ms(220));
        float[] groan = fadeOut(fadeIn(sat(mix(
                oscFM(fSweep(ms(780), 55, 42), 0.52f),
                oscFM(fSweep(ms(780), 110, 84), 0.28f)), 1.8f), ms(95)), ms(180));
        float[] k1 = ad(mix(sqr(ms(95),660,0.55f), sqr(ms(95),990,0.28f)), ms(5), 0.038);
        float[] k2 = ad(mix(sqr(ms(95),880,0.55f), sqr(ms(95),1320,0.28f)), ms(5), 0.038);
        float[] klaxon = cat(k1, new float[ms(62)], k2, new float[ms(62)],
                k1.clone(), new float[ms(62)], k2.clone(), new float[ms(62)], k1.clone());
        float[] thunder = decay(fadeIn(lpf(bandNoise(len, 95, 260, 0.55f), 320), ms(220)), 0.425);
        float[] out = mix(drone, pad(groan,len), pad(klaxon,len), pad(thunder,len));
        out = reverb(out, ms(155), 0.36f, 0.22f);
        out = reverb(out, ms(310), 0.24f, 0.14f);
        return norm(fadeOut(out, ms(120)), 0.88f);
    }

    // BOSS DEATH - chain explosions
    private static float[] buildBossDeath() {
        int len = ms(2500);
        float[] combined = new float[len];
        float[] lg1 = buildExplosionLg();
        float[] lg2 = buildExplosionLg();
        float[] lg3 = buildExplosionLg();
        float[] md  = buildExplosionMd();
        addAt(combined, lg1, 0);
        addAt(combined, md,  ms(185));
        addAt(combined, lg2, ms(420));
        addAt(combined, md,  ms(680));
        addAt(combined, lg3, ms(920));
        float[] rumble = decay(fadeIn(lpf(bandNoise(len, 42, 68, 0.55f), 165), ms(85)), 0.580);
        float[] sting  = ad(mix(osc(ms(280), 880, 0.42f), osc(ms(280), 1320, 0.22f)), ms(12), 0.075);
        float[] out    = mix(combined, rumble);
        addAt(out, sting, ms(1850));
        out = reverb(out, ms(175), 0.32f, 0.20f);
        return norm(fadeOut(out, ms(220)), 0.92f);
    }

    // WAVE START - military space alert
    private static float[] buildWaveStart() {
        float[] ping  = ad(oscFM(fExpSweep(ms(120), 1800, 480), 0.55f), ms(4), 0.042);
        float[] b1    = ad(mix(sqr(ms(88), 880,  0.52f), sqr(ms(88), 1320, 0.26f)), ms(5), 0.038);
        float[] b2    = ad(mix(sqr(ms(88), 1100, 0.52f), sqr(ms(88), 1650, 0.26f)), ms(5), 0.038);
        float[] b3    = ad(mix(sqr(ms(88), 1320, 0.52f), sqr(ms(88), 1980, 0.26f)), ms(5), 0.038);
        float[] servo = fadeOut(fadeIn(bandNoise(ms(95), 1400, 900, 0.22f), ms(8)), ms(18));
        return norm(cat(ping, new float[ms(28)], b1, new float[ms(32)],
                b2, new float[ms(32)], b3, new float[ms(18)], servo), 0.78f);
    }

    // KAMIKAZE - screaming dive with Doppler
    private static float[] buildKamikaze() {
        int len = ms(780);
        float[] scream  = fadeIn(sat(oscFM(fExpSweep(len, 3800, 280), 0.68f), 1.6f), ms(15));
        float[] turbine = fadeIn(mix(oscFM(fExpSweep(len, 2100, 160), 0.40f),
                oscFM(fExpSweep(len, 4200, 320), 0.20f)), ms(32));
        float[] wind    = fadeOut(fadeIn(bandNoise(len, 2200, 3200, 0.30f), ms(48)), ms(88));
        float[] flutter = new float[len];
        for (int i = 0; i < len; i++) {
            double rate = 32 + 22.0 * i / len;
            flutter[i] = (float)(Math.sin(2*Math.PI*rate*i/SR) * 0.16f);
        }
        float[] sub = fadeIn(lpf(oscFM(fExpSweep(len, 85, 22), 0.48f), 180), ms(55));
        float[] out = mix(scream, turbine, wind, flutter, sub);
        out = reverb(out, ms(28), 0.14f, 0.09f);
        return norm(fadeOut(out, ms(55)), 0.78f);
    }

    // ENEMY SHOOT - dark plasma bolt
    private static float[] buildEnemyShoot() {
        int len = ms(140);
        float[] bolt  = ad(sat(lpf(oscFM(fExpSweep(len, 380, 55), 0.75f), 620), 2.0f), ms(3), 0.028);
        float[] crack = decay(bandNoise(ms(16), 2800, 2000, 0.45f), 0.003);
        float[] hiss  = decay(fadeIn(bandNoise(ms(80), 1200, 800, 0.20f), ms(5)), 0.018);
        return norm(fadeOut(mix(pad(crack,len), bolt, pad(hiss,len)), ms(12)), 0.65f);
    }

    // JET ENGINE - space fighter turbine roar
    private static float[] buildJetEngine() {
        int len = ms(1200);
        // Core turbine - layered sawtooth harmonics
        float[] core = lpf(sat(mix(
                saw(len, 88,  0.42f),
                saw(len, 132, 0.28f),
                saw(len, 176, 0.18f),
                saw(len, 220, 0.10f)), 2.2f), 1800);
        // Compressor stages - spinning turbine wheels
        float[] compressor = lpf(mix(
                saw(len, 44,  0.32f),
                saw(len, 118, 0.24f),
                saw(len, 266, 0.16f)), 2400);
        // Hot exhaust noise
        float[] exhaust = lpf(bandNoise(len, 380, 520, 0.38f), 1200);
        // High frequency turbine whine - sci-fi jet scream
        float[] whine = fadeIn(fadeOut(oscFM(fSweep(len, 2800, 3200), 0.22f), ms(200)), ms(200));
        // Mechanical vibration - airframe buzz
        float[] vibration = new float[len];
        for (int i = 0; i < len; i++) {
            vibration[i] = (float)(Math.sin(2*Math.PI * 28 * i / SR)
                    * Math.sin(2*Math.PI * 7.5 * i / SR) * 0.18f);
        }
        // Pulse modulation - cyclic power variation
        float[] pulse = new float[len];
        for (int i = 0; i < len; i++) {
            double mod = 0.82 + 0.18 * Math.sin(2*Math.PI * 3.2 * i / SR);
            pulse[i] = (float)(core[i] * mod);
        }
        float[] out = mix(pulse, compressor, exhaust, whine, vibration);
        out = fadeIn(out, ms(80));
        out = fadeOut(out, ms(80));
        return norm(out, 0.72f);
    }

    // MISSILE FIRE - rocket ignition and thrust
    private static float[] buildMissileFire() {
        int len = ms(680);
        float[] ignite = decay(mix(
                bandNoise(ms(25), 3200, 2200, 0.65f),
                oscFM(fExpSweep(ms(25), 1800, 400), 0.50f)), 0.005);
        float[] thrust = fadeIn(sat(lpf(mix(
                bandNoise(len, 320, 480, 0.55f),
                saw(len, 68, 0.32f),
                saw(len, 136, 0.18f)), 1600), 1.5f), ms(25));
        float[] scream = fadeIn(oscFM(fExpSweep(len, 1400, 680), 0.28f), ms(40));
        for (int i = len/2; i < len; i++) {
            float t = (float)(i - len/2) / (len/2);
            thrust[i] *= (1.0f - t * 0.6f);
            scream[i] *= (1.0f - t * 0.7f);
        }
        float[] out = mix(pad(ignite,len), thrust, scream);
        out = reverb(out, ms(45), 0.18f, 0.12f);
        return norm(fadeOut(out, ms(85)), 0.76f);
    }

    // VICTORY
    private static float[] buildVictory() {
        double[] freqs = {523.25, 659.25, 783.99, 1046.5, 1318.5, 1568.0};
        int[]    lens  = {ms(130), ms(130), ms(130), ms(160), ms(160), ms(420)};
        float[][] parts = new float[freqs.length][];
        for (int i = 0; i < freqs.length; i++) {
            int n = lens[i];
            float[] t = mix(osc(n,freqs[i],0.45f), osc(n,freqs[i]*2,0.24f),
                    osc(n,freqs[i]*3,0.12f), sqr(n,freqs[i]*0.5,0.10f));
            parts[i] = ad(t, ms(8), 0.065);
        }
        float[] out = cat(parts);
        out = reverb(out, ms(82), 0.22f, 0.15f);
        return norm(fadeOut(out, ms(62)), 0.78f);
    }

    // GAME OVER - descending space dirge
    private static float[] buildGameOver() {
        double[] freqs = {392.0, 349.23, 311.13, 261.63};
        int[]    lens  = {ms(220), ms(220), ms(220), ms(640)};
        float[][] parts = new float[freqs.length][];
        for (int i = 0; i < freqs.length; i++) {
            int n = lens[i];
            float[] t = mix(osc(n,freqs[i],0.50f), osc(n,freqs[i]*2,0.22f),
                    osc(n,freqs[i]*3,0.10f), saw(n,freqs[i],0.14f));
            parts[i] = ad(t, ms(10), 0.085);
        }
        float[] melody = cat(parts);
        int tLen = melody.length;
        float[] rumble   = decay(fadeIn(lpf(bandNoise(tLen, 58, 92, 0.34f), 185), ms(520)), 0.340);
        float[] shutdown = new float[ms(380)];
        double[] beepFreqs = {1200, 900, 600, 300};
        for (int i = 0; i < beepFreqs.length; i++) {
            float[] b = ad(sqr(ms(62), beepFreqs[i], 0.28f), ms(4), 0.025);
            addAt(shutdown, b, ms(i * 88));
        }
        float[] out = mix(melody, rumble);
        addAt(out, shutdown, ms(220));
        out = reverb(out, ms(105), 0.28f, 0.20f);
        return norm(fadeOut(out, ms(82)), 0.80f);
    }

    private static float[] addVibrato(float[] src, double rateHz, double depth) {
        float[] out = new float[src.length];
        for (int i = 0; i < src.length; i++) {
            double mod = 1.0 + depth * Math.sin(2*Math.PI*rateHz*i/SR);
            int j = (int)(i * mod);
            out[i] = (j >= 0 && j < src.length) ? src[j] : 0f;
        }
        return out;
    }
}