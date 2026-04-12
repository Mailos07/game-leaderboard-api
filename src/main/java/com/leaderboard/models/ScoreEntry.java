package com.leaderboard.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single score submission.
 *
 * DynamoDB key design:
 *   PK  = "GAME#<gameId>"         → groups all scores for one game
 *   SK  = "SCORE#<paddedScore>#<playerId>#<timestamp>"
 *                                  → sorts by score descending (we pad + invert)
 *
 *   GSI1PK = "PLAYER#<playerId>"  → groups all games for one player
 *   GSI1SK = "GAME#<gameId>#<timestamp>"
 *
 * Why this design:
 * - Query PK="GAME#xyz" with ScanIndexForward=false → instant top-N leaderboard
 * - Query GSI1PK="PLAYER#abc" → all scores for a player across games
 * - No full table scans needed for any of our access patterns
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreEntry {

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("playerName")
    private String playerName;

    @JsonProperty("gameId")
    private String gameId;

    @JsonProperty("score")
    private int score;

    @JsonProperty("timestamp")
    private String timestamp;

    // DynamoDB keys (set by the handler, not by the client)
    private String pk;
    private String sk;
    private String gsi1pk;
    private String gsi1sk;

    public ScoreEntry() {}

    public ScoreEntry(String playerId, String playerName, String gameId, int score, String timestamp) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.gameId = gameId;
        this.score = score;
        this.timestamp = timestamp;
    }

    // ── Getters and Setters ──

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getGsi1pk() { return gsi1pk; }
    public void setGsi1pk(String gsi1pk) { this.gsi1pk = gsi1pk; }

    public String getGsi1sk() { return gsi1sk; }
    public void setGsi1sk(String gsi1sk) { this.gsi1sk = gsi1sk; }
}
