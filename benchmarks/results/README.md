# Benchmark Results

> Run benchmarks with both stacks running, then fill in the numbers below.

## How to Run

```bash
# Start infrastructure
docker compose -f infra/docker-compose.yml up -d

# Start Spring services (account: 8080, transfer: 8081)
./mvnw -pl services/account-spring spring-boot:run &
./mvnw -pl services/transfer-spring spring-boot:run &

# Wait for readiness, then measure startup time and RSS:
# curl http://localhost:8080/actuator/health
# ps aux | grep account-spring | awk '{print $6}'

# Run benchmark against Spring
k6 run benchmarks/k6/transfer-load.js \
  -e BASE_URL=http://localhost:8080 \
  -e TRANSFER_URL=http://localhost:8081

# Stop Spring, start Micronaut (account: 8082, transfer: 8083)
./mvnw -pl services/account-micronaut mn:run &
./mvnw -pl services/transfer-micronaut mn:run &

# Run benchmark against Micronaut
k6 run benchmarks/k6/transfer-load.js \
  -e BASE_URL=http://localhost:8082 \
  -e TRANSFER_URL=http://localhost:8083
```

## Hardware

- CPU: <!-- e.g. Apple M3 Pro, 12 cores -->
- RAM: <!-- e.g. 36 GB -->
- OS: <!-- e.g. Ubuntu 24.04 -->
- JVM: Eclipse Temurin 21.x.x
- JVM flags: `-XX:+UseZGC -Xms256m -Xmx512m`

## Results

| Metric | Spring Boot (WebFlux) | Micronaut (Netty) |
|---|---|---|
| Startup time | <!-- ms --> | <!-- ms --> |
| RSS memory (idle) | <!-- MB --> | <!-- MB --> |
| RSS memory (under load) | <!-- MB --> | <!-- MB --> |
| p50 latency | <!-- ms --> | <!-- ms --> |
| p95 latency | <!-- ms --> | <!-- ms --> |
| p99 latency | <!-- ms --> | <!-- ms --> |
| Throughput | <!-- req/s --> | <!-- req/s --> |

## Notes

<!-- Add context: warm vs cold JVM, GC pauses observed, any anomalies -->
