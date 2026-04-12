# Game Leaderboard API

Real-time multiplayer game leaderboard system built with AWS Lambda, API Gateway, and DynamoDB.

**CSC 575 / SER 375 — Cloud Computing — Option B: Developing NoSQL Solutions**

---

## Architecture

```
Game Clients → API Gateway → Lambda Functions → DynamoDB
                                                    ↕
                                              Global Tables
                                          (multi-region replication)
```

## API Endpoints

| Method | Endpoint                                  | Description                     |
|--------|-------------------------------------------|---------------------------------|
| POST   | /scores                                   | Submit a new score              |
| GET    | /leaderboard/{gameId}?limit=10            | Get top scores for a game       |
| GET    | /leaderboard/{gameId}/player/{playerId}   | Get a player's rank + neighbors |
| GET    | /players/{playerId}/scores                | Get all scores for a player     |
| DELETE | /scores/{gameId}/{scoreId}                | Delete a score (admin)          |

## Prerequisites

1. **Java 17** — `java -version`
2. **Maven** — `mvn -version`
3. **AWS CLI** — `aws --version`
4. **AWS SAM CLI** — `sam --version`

## Setup & Deploy

### Step 1: Configure AWS CLI
```bash
aws configure
# Enter your Access Key ID, Secret Access Key
# Default region: us-east-1
# Output format: json
```

### Step 2: Build the project
```bash
mvn clean package
```

### Step 3: Deploy with SAM
```bash
# First time — SAM will ask you configuration questions
sam deploy --guided

# Recommended answers:
#   Stack Name: game-leaderboard
#   Region: us-east-1
#   Confirm changes before deploy: y
#   Allow SAM CLI IAM role creation: y
#   Save arguments to samconfig.toml: y

# Subsequent deploys (uses saved config):
sam deploy
```

### Step 4: Note your API endpoint
After deployment, SAM prints an `ApiEndpoint` URL like:
```
https://abc123.execute-api.us-east-1.amazonaws.com/Prod/
```

## Testing with curl

### Submit scores
```bash
API="https://YOUR-API-ID.execute-api.us-east-1.amazonaws.com/Prod"

curl -X POST $API/scores \
  -H "Content-Type: application/json" \
  -d '{"playerId":"p1","playerName":"Alice","gameId":"battle-royale","score":9500}'

curl -X POST $API/scores \
  -H "Content-Type: application/json" \
  -d '{"playerId":"p2","playerName":"Bob","gameId":"battle-royale","score":8200}'

curl -X POST $API/scores \
  -H "Content-Type: application/json" \
  -d '{"playerId":"p3","playerName":"Charlie","gameId":"battle-royale","score":9800}'

curl -X POST $API/scores \
  -H "Content-Type: application/json" \
  -d '{"playerId":"p1","playerName":"Alice","gameId":"puzzle-quest","score":15000}'
```

### Get leaderboard
```bash
curl $API/leaderboard/battle-royale
curl "$API/leaderboard/battle-royale?limit=5"
```

### Get player rank
```bash
curl $API/leaderboard/battle-royale/player/p2
```

### Get player history
```bash
curl $API/players/p1/scores
```

## DynamoDB Key Design

```
Main Table:
  PK = "GAME#battle-royale"
  SK = "SCORE#0000499#player1#2026-04-01T..."   ← inverted score for descending order

GSI1 (Player Lookup):
  GSI1PK = "PLAYER#player1"
  GSI1SK = "GAME#battle-royale#2026-04-01T..."
```

The score is INVERTED (9999999 - actual_score) so DynamoDB's default ascending
sort returns highest scores first. This avoids needing ScanIndexForward=false.

## Cleanup

To avoid charges, delete the stack when you're done:
```bash
sam delete --stack-name game-leaderboard
```
