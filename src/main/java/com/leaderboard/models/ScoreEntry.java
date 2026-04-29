package com.leaderboard.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
