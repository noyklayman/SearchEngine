# Mini Google UI — Setup

The UI is served by Spring Boot from `src/main/resources/static`.
No separate React or Node.js application is required.

## Run infrastructure

```bash
docker compose up -d
```

## Configure Elasticsearch

Set these environment variables before starting Spring Boot:

```text
ELASTICSEARCH_BASE_URL=https://your-host:port
ELASTICSEARCH_KEY=username:password
ELASTICSEARCH_INDEX=noyka
```

## Run the application

From IntelliJ, run `SearchengineApplication`, or from a terminal with Maven installed:

```bash
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

## UI/API flow

- Search: `GET /api/search?query=java&size=20`
- Start crawler: `POST /api/crawl`
- Crawl status: `GET /api/crawl/{crawlId}`
