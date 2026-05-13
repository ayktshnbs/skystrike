package com.aykut.skystrike2;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final String TAG            = "SkyStrike";
    private static final String SAVE_SLOT      = "sky_strike_save_v1";
    public  static final String LEADERBOARD_ID = "YOUR_LEADERBOARD_ID_HERE";

    private GameView      gameView;
    private boolean       signedIn    = false;
    private boolean       gameStarted = false;
    private LinearLayout  signInScreen;
    private TextView      statusText;
    private Button        signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        showSignInScreen();
        attemptSignIn();
    }

    // ── Sign-in screen built in code (no XML needed) ─────────────────────────

    private void showSignInScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(0xFF08111f);

        TextView title = new TextView(this);
        title.setText("SKY STRIKE");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(48f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, 50);
        root.addView(title);

        TextView icon = new TextView(this);
        icon.setText("\uD83C\uDFAE"); // 🎮
        icon.setTextSize(64f);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setPadding(0, 0, 0, 24);
        root.addView(icon);

        statusText = new TextView(this);
        statusText.setText("Signing in with Google Play...");
        statusText.setTextColor(0xFFAABBCC);
        statusText.setTextSize(18f);
        statusText.setGravity(android.view.Gravity.CENTER);
        statusText.setPadding(48, 0, 48, 40);
        root.addView(statusText);

        signInButton = new Button(this);
        signInButton.setText("Sign In with Google Play");
        signInButton.setTextColor(0xFFFFFFFF);
        signInButton.setBackgroundColor(0xFF1a73e8);
        signInButton.setTextSize(18f);
        signInButton.setVisibility(View.GONE);
        signInButton.setOnClickListener(v -> {
            signInButton.setVisibility(View.GONE);
            statusText.setText("Signing in...");
            attemptSignIn();
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 16, 0, 0);
        root.addView(signInButton, bp);

        TextView note = new TextView(this);
        note.setText("A Google Play account is required to play.\nYour progress is saved to the cloud.");
        note.setTextColor(0xFF445566);
        note.setTextSize(14f);
        note.setGravity(android.view.Gravity.CENTER);
        note.setPadding(60, 48, 60, 0);
        root.addView(note);

        signInScreen = root;
        setContentView(root);
    }

    // ── Sign-in flow ─────────────────────────────────────────────────────────

    private void attemptSignIn() {
        GamesSignInClient signInClient = PlayGames.getGamesSignInClient(this);
        signInClient.isAuthenticated().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().isAuthenticated()) {
                onSignInSuccess();
            } else {
                signInClient.signIn().addOnCompleteListener(t -> {
                    if (t.isSuccessful() && t.getResult().isAuthenticated()) {
                        onSignInSuccess();
                    } else {
                        onSignInFailed();
                    }
                });
            }
        });
    }

    private void onSignInSuccess() {
        signedIn = true;
        Log.i(TAG, "Signed in");
        PlayGames.getPlayersClient(this).getCurrentPlayer()
                .addOnSuccessListener(player -> {
                    String playerId   = player.getPlayerId();
                    String playerName = player.getDisplayName();
                    Log.i(TAG, "Player: " + playerName + " id=" + playerId);
                    runOnUiThread(() -> startGame(playerId, playerName));
                })
                .addOnFailureListener(e ->
                        runOnUiThread(() -> startGame("default", "")));
    }

    private void onSignInFailed() {
        runOnUiThread(() -> {
            statusText.setText("Sign in required to play Sky Strike.\nPlease connect your Google Play account.");
            signInButton.setVisibility(View.VISIBLE);

            // ── TEST / SCREENSHOT mode — remove before publishing ─────────
            Button guestButton = new Button(this);
            guestButton.setText("Continue as Guest (Test Only)");
            guestButton.setTextColor(0xFFFFFFFF);
            guestButton.setBackgroundColor(0xFF444444);
            guestButton.setTextSize(14f);
            guestButton.setOnClickListener(v -> startGame("guest_local", "Guest"));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            gp.setMargins(0, 20, 0, 0);
            signInScreen.addView(guestButton, gp);
            // ── END TEST ──────────────────────────────────────────────────
        });
    }

    private void startGame(String playerId, String playerName) {
        if (gameStarted) return;
        gameStarted = true;

        // Each player ID gets its own SharedPreferences file — fully isolated saves
        gameView = new GameView(this, "save_" + playerId);
        gameView.setMainActivity(this);
        gameView.setPlayerName(playerName);
        setContentView(gameView);

        // Load their cloud save
        loadCloudSave();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override protected void onPause()   { super.onPause();   if (gameView != null) gameView.pauseMusic();   }
    @Override protected void onResume()  { super.onResume();  if (gameView != null) gameView.resumeMusic();  }
    @Override protected void onDestroy() { super.onDestroy(); if (gameView != null) gameView.releaseMusic(); }

    // ── Cloud save ────────────────────────────────────────────────────────────

    public void saveToCloud(String json) {
        if (!signedIn) return;
        SnapshotsClient client = PlayGames.getSnapshotsClient(this);
        client.open(SAVE_SLOT, true).addOnSuccessListener(dOrC -> {
            if (dOrC.isConflict()) {
                resolveConflict(client, dOrC.getConflict(), json);
                return;
            }
            Snapshot snap = dOrC.getData();
            if (snap == null) return;
            snap.getSnapshotContents().writeBytes(json.getBytes(StandardCharsets.UTF_8));
            client.commitAndClose(snap, new SnapshotMetadataChange.Builder()
                            .setDescription("Sky Strike save").build())
                    .addOnSuccessListener(m -> Log.i(TAG, "Cloud save OK"))
                    .addOnFailureListener(e -> Log.w(TAG, "Cloud save failed", e));
        }).addOnFailureListener(e -> Log.w(TAG, "Snapshot open failed", e));
    }

    private void loadCloudSave() {
        if (!signedIn) return;
        SnapshotsClient client = PlayGames.getSnapshotsClient(this);
        client.open(SAVE_SLOT, false).addOnSuccessListener(dOrC -> {
            if (dOrC.isConflict()) return;
            Snapshot snap = dOrC.getData();
            if (snap == null) return;
            try {
                byte[] bytes = snap.getSnapshotContents().readFully();
                String json  = new String(bytes, StandardCharsets.UTF_8);
                client.discardAndClose(snap);
                if (!json.isEmpty()) runOnUiThread(() -> gameView.applyCloudSave(json));
            } catch (Exception e) { Log.w(TAG, "Cloud load failed", e); }
        }).addOnFailureListener(e -> Log.w(TAG, "Cloud load open failed", e));
    }

    private void resolveConflict(SnapshotsClient client,
                                 SnapshotsClient.SnapshotConflict conflict,
                                 String localJson) {
        try {
            byte[] sb     = conflict.getConflictingSnapshot().getSnapshotContents().readFully();
            String server = new String(sb, StandardCharsets.UTF_8);
            JSONObject lj = new JSONObject(localJson);
            JSONObject sj = new JSONObject(server);
            String winner = sj.optInt("ts", 0) > lj.optInt("ts", 0) ? server : localJson;
            Snapshot base = conflict.getSnapshot();
            base.getSnapshotContents().writeBytes(winner.getBytes(StandardCharsets.UTF_8));
            client.resolveConflict(conflict.getConflictId(), base);
        } catch (Exception e) { Log.w(TAG, "Conflict resolve failed", e); }
    }

    // ── Leaderboard & Achievements ────────────────────────────────────────────

    public void submitScore(long score) {
        if (!signedIn) return;
        PlayGames.getLeaderboardsClient(this).submitScore(LEADERBOARD_ID, score);
    }

    public void showLeaderboard() {
        if (!signedIn) return;
        PlayGames.getLeaderboardsClient(this).getLeaderboardIntent(LEADERBOARD_ID)
                .addOnSuccessListener(intent -> startActivityForResult(intent, 100));
    }

    public void unlockAchievement(String id) {
        if (!signedIn || id == null) return;
        PlayGames.getAchievementsClient(this).unlock(id);
    }

    public void showAchievements() {
        if (!signedIn) return;
        PlayGames.getAchievementsClient(this).getAchievementsIntent()
                .addOnSuccessListener(intent -> startActivityForResult(intent, 101));
    }

    public boolean isSignedIn() { return signedIn; }
}