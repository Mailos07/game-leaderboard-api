# Game Leaderboard API v2

Real-time multiplayer game leaderboard system built on AWS.

**CSC 575 / SER 375 — Cloud Computing — Option B: Developing NoSQL Solutions**

## Architecture

```
S3 (Frontend) → API Gateway → Lambda Functions → DynamoDB
                                    ↓                  ↓
                              Cognito Auth      DynamoDB Streams
                                                      ↓
                                              PlayerStats Cache
```

## Features

- **Score Submission** — POST /scores
- **Leaderboard** — GET /leaderboard/{gameId}
- **Player Rank** — GET /leaderboard/{gameId}/player/{playerId}
- **Player History** — GET /players/{playerId}/scores (via GSI)
- **Admin Delete** — POST /admin/delete (Cognito-protected)
- **Admin Auth** — POST /admin/login, POST /admin/signup (Cognito)
- **Best Players** — GET /stats/best-players (auto-updated via Streams)
- **DynamoDB Streams** — Auto-updates player stats cache on every score insert

## AWS Services

| Service | Purpose |
|---------|---------|
| DynamoDB | Primary data store + Streams for event processing |
| Lambda (x9) | Serverless compute for all endpoints + stream processor |
| API Gateway | REST API with CORS |
| S3 | Static website hosting |
| Cognito | Admin authentication |
| CloudWatch | Monitoring and logging |
| CloudFormation/SAM | Infrastructure as code |

## Deploy

```bash
mvn clean package
sam deploy --stack-name game-leaderboard --region us-east-1 --resolve-s3 --capabilities CAPABILITY_IAM --no-confirm-changeset
```

## Cleanup

```bash
sam delete --stack-name game-leaderboard
aws s3 rb s3://game-leaderboard-575-mailos --force
```
