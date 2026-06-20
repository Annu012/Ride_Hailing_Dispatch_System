# 🚕 Ride-Hailing Dispatch System

> A production-grade, event-driven microservices system for real-time ride dispatch and driver coordination — built with Spring Boot, Apache Kafka, Redis, and full observability.

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)
![Kafka](https://img.shields.io/badge/Kafka-7.6.0-black.svg)
![Redis](https://img.shields.io/badge/Redis-7-red.svg)
![Docker](https://img.shields.io/badge/Docker-Compose-blue.svg)

---

## 🎯 Overview

This system simulates a real-world ride-hailing platform (Ola/Uber-style) that:

- Accepts ride requests from riders with pickup/drop coordinates
- Calculates estimated fare by ride type (ECONOMY, PREMIUM, XL)
- Maintains real-time driver location and availability in Redis
- Dispatches the **nearest available compatible driver** using the Haversine formula
- Manages driver lifecycle through a **Finite State Machine (FSM)**
- Provides real-time audit trail via a dedicated Notification Service
- Exposes full observability via Prometheus + Grafana dashboards

---

## 🏗️ Architecture

```
                         ┌─────────────────────────────────────────────────┐
                         │                  API GATEWAY :8080               │
                         │         (Spring Cloud Gateway + CORS)            │
                         └────────────┬───────────────┬────────────────────┘
                                      │               │
                     ┌────────────────▼──┐       ┌───▼──────────────────┐
                     │  Rider Service    │       │   Driver Service      │
                     │      :8081        │       │       :8082           │
                     │  • POST /request  │       │  • POST /register     │
                     │  • GET  /status   │       │  • PUT  /location     │
                     │  • Fare calc      │       │  • PUT  /status (FSM) │
                     │  • Redis TTL      │       │  • Auto-Heal scheduler│
                     └────────┬──────────┘       └────────┬─────────────┘
                              │                           │
                    Publish   │  ride-requested           │  Consumes
                              │                           │  ride-assigned
                              ▼                           ▼
                     ┌─────────────────────────────────────────────────────┐
                     │              Apache Kafka (Event Bus)               │
                     │   Topics: ride-requested │ ride-assigned │          │
                     │           ride-completed │ ride-cancelled           │
                     └──────────────────┬──────────────────────────────────┘
                                        │
                            Consumes ride-requested
                                        │
                     ┌──────────────────▼──────────────────────────────────┐
                     │              Dispatch Service :8083                  │
                     │  • Priority queue (Redis Sorted Set)                 │
                     │  • Haversine nearest-driver matching                 │
                     │  • Distributed locking (Redis SETNX + TTL)          │
                     │  • Dispatch loop (every 1 second)                    │
                     │  • Publishes ride-assigned events                    │
                     └─────────────────────────────────────────────────────┘
                                        │
                         Consumes ALL events
                                        │
                     ┌──────────────────▼──────────────────────────────────┐
                     │           Notification Service :8084                 │
                     │  • Audit log for all ride lifecycle events           │
                     │  • Extensible: SMS / Push / Email hooks              │
                     └─────────────────────────────────────────────────────┘

                     ┌─────────────────────────────────────────────────────┐
                     │                  Observability Stack                 │
                     │   Prometheus :9090  ←  /actuator/prometheus          │
                     │   Grafana    :3000  ←  Prometheus datasource        │
                     └─────────────────────────────────────────────────────┘
```

---

## 🔄 Driver Finite State Machine (FSM)

```
                    ┌─────────────┐
        System  ──► │  AVAILABLE  │ ◄─────────────────────────────┐
        Start       └──────┬──────┘                               │
                           │ Assignment received                   │
                    ┌──────▼──────┐                               │
                    │  ASSIGNED   │ ──── > 10 min stale ──► AUTO HEAL
                    └──────┬──────┘
                           │ Driver departs
                    ┌──────▼──────────┐
                    │ EN_ROUTE_TO     │ ──── > 30 min stale ──► AUTO HEAL
                    │ PICKUP          │
                    └──────┬──────────┘
                           │ Rider picked up
                    ┌──────▼──────────┐
                    │ RIDER_PICKED_UP │
                    └──────┬──────────┘
                           │ Trip ends
                    ┌──────▼──────────┐
                    │ TRIP_COMPLETED  │ ──► ride-completed event published
                    └──────┬──────────┘
                           │ Auto-reset
                           └───────────────────────────────────────┘
```

---

## 🛠️ Technology Stack

| Layer            | Technology                             |
|------------------|----------------------------------------|
| Language         | Java 17                                |
| Framework        | Spring Boot 3.2                        |
| API Gateway      | Spring Cloud Gateway                   |
| Messaging        | Apache Kafka + Spring Kafka            |
| State / Cache    | Redis 7 (sorted sets, distributed lock)|
| Observability    | Prometheus + Grafana                   |
| Containerization | Docker + Docker Compose                |
| Build            | Maven 3.8+                             |
| Patterns         | Event-Driven, FSM, CQRS, Saga, DLQ    |

---

## 📦 Project Structure

```
ride-hailing-dispatch/
├── api-gateway/                   # Spring Cloud Gateway — single entry point
│   ├── src/main/.../gateway/
│   ├── Dockerfile
│   └── pom.xml
│
├── rider-service/                 # Ride requests, fare calculation, status tracking
│   ├── src/main/.../rider/
│   │   ├── controller/            # REST: POST /request, GET /{id}/status
│   │   ├── dto/                   # RideRequestDto, RideEvent, RideType
│   │   ├── service/               # RiderService (Haversine fare, Kafka publish)
│   │   └── config/                # Kafka producer, Redis config
│   ├── Dockerfile
│   └── pom.xml
│
├── driver-service/                # Driver fleet management + FSM
│   ├── src/main/.../driver/
│   │   ├── controller/            # REST: register, location update, status
│   │   ├── model/                 # Driver, DriverStatus (FSM enum)
│   │   ├── dto/                   # DriverRegistrationDto, AssignmentEvent
│   │   ├── listener/              # Kafka consumer for ride-assigned
│   │   ├── service/               # FSM transitions, auto-heal scheduler
│   │   └── config/                # Kafka consumer+producer, Redis config
│   ├── Dockerfile
│   └── pom.xml
│
├── dispatch-service/              # Core matching engine
│   ├── src/main/.../dispatch/
│   │   ├── controller/            # GET /queue/depth
│   │   ├── dto/                   # RideEvent, AssignmentEvent, DriverSnapshot
│   │   ├── listener/              # Kafka consumer for ride-requested
│   │   ├── service/               # Haversine match, Redis queue, distributed lock
│   │   └── config/                # Kafka consumer+producer, Redis config
│   ├── Dockerfile
│   └── pom.xml
│
├── notification-service/          # Audit trail + notification hooks
│   ├── src/main/.../notification/
│   │   ├── listener/              # EventAuditListener (all topics)
│   │   └── config/                # Kafka consumer config
│   ├── Dockerfile
│   └── pom.xml
│
├── monitoring/
│   └── prometheus.yml             # Scrape config for all 4 services
│
├── docker-compose.yml             # Full stack orchestration
├── simulate-rides.ps1             # Windows load tester
├── simulate-rides.sh              # Linux/Mac load tester
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- Docker Desktop (with Docker Compose)
- Java 17 (for local dev)
- Maven 3.8+ (for local dev)

### Quick Start (Docker)

```bash
# 1. Clone / enter project
cd ride-hailing-dispatch

# 2. Start entire stack
docker-compose up -d

# 3. Verify all containers are up
docker-compose ps

# 4. Watch logs
docker-compose logs -f dispatch-service
```

### Service URLs

| Service              | URL                          |
|----------------------|------------------------------|
| API Gateway          | http://localhost:8080        |
| Rider Service        | http://localhost:8081        |
| Driver Service       | http://localhost:8082        |
| Dispatch Service     | http://localhost:8083        |
| Notification Service | http://localhost:8084        |
| Prometheus           | http://localhost:9090        |
| Grafana              | http://localhost:3000        |

Grafana login: **admin / admin**

---

## 📡 API Reference

### Register a Driver

```bash
curl -X POST http://localhost:8080/api/drivers/register \
  -H "Content-Type: application/json" \
  -d '{
    "driverId": "D001",
    "name": "Rahul Sharma",
    "vehicleNumber": "MH12-AB-1234",
    "vehicleType": "ECONOMY",
    "lat": 18.5204,
    "lon": 73.8567
  }'
```

### Request a Ride

```bash
curl -X POST http://localhost:8080/api/rides/request \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "R001",
    "riderName": "Priya Desai",
    "pickupLat": 18.5300,
    "pickupLon": 73.8600,
    "dropLat": 18.4950,
    "dropLon": 73.8553,
    "pickupAddress": "Shivajinagar",
    "dropAddress": "Hadapsar",
    "rideType": "ECONOMY"
  }'
```

### Check Ride Status

```bash
curl http://localhost:8080/api/rides/RIDE-A1B2C3D4/status
```

### Update Driver Location

```bash
curl -X PUT http://localhost:8080/api/drivers/D001/location \
  -H "Content-Type: application/json" \
  -d '{"lat": 18.5250, "lon": 73.8610}'
```

### Advance Driver FSM Status

```bash
# Driver heading to pickup
curl -X PUT http://localhost:8080/api/drivers/D001/status \
  -H "Content-Type: application/json" \
  -d '{"status": "EN_ROUTE_TO_PICKUP"}'

# Rider picked up
curl -X PUT http://localhost:8080/api/drivers/D001/status \
  -d '{"status": "RIDER_PICKED_UP"}' -H "Content-Type: application/json"

# Trip completed
curl -X PUT http://localhost:8080/api/drivers/D001/status \
  -d '{"status": "TRIP_COMPLETED"}' -H "Content-Type: application/json"

# Back to available
curl -X PUT http://localhost:8080/api/drivers/D001/status \
  -d '{"status": "AVAILABLE"}' -H "Content-Type: application/json"
```

### Get Available Drivers

```bash
curl http://localhost:8080/api/drivers/available
```

---

## 📊 Kafka Topics

| Topic            | Producer         | Consumers                          |
|------------------|------------------|------------------------------------|
| ride-requested   | Rider Service    | Dispatch Service, Notification     |
| ride-assigned    | Dispatch Service | Driver Service, Notification       |
| ride-completed   | Driver Service   | Notification Service               |

---

## 📈 Prometheus Metrics

Each service exposes custom business metrics at `/actuator/prometheus`:

| Metric                                  | Service      | Description                        |
|-----------------------------------------|--------------|------------------------------------|
| `ride.requests.total`                   | Rider        | Total ride requests received       |
| `dispatch.assignments.published.total`  | Dispatch     | Successful driver assignments      |
| `dispatch.no_driver_available.total`    | Dispatch     | Failed — no compatible driver      |
| `dispatch.lock.failures.total`          | Dispatch     | Redis lock acquisition failures    |
| `dispatch.ride.queue.depth`             | Dispatch     | Current ride queue depth (gauge)   |
| `driver.fsm.transitions.total`          | Driver       | Total FSM state transitions        |
| `driver.auto_heal.total`                | Driver       | Stale-state auto-recovery events   |
| `notification.ride.requested.total`     | Notification | Events consumed per topic          |
| `notification.ride.assigned.total`      | Notification | Events consumed per topic          |
| `notification.ride.completed.total`     | Notification | Events consumed per topic          |

---

## 🧪 Load Testing

### Windows (PowerShell)

```powershell
# Sequential — 50 rides, 200ms delay
.\simulate-rides.ps1 -Count 50 -DelayMs 200

# Parallel — 100 rides, 20 concurrent
.\simulate-rides.ps1 -Count 100 -Parallel -Throttle 20

# Stress test
.\simulate-rides.ps1 -Count 500 -Parallel -Throttle 50 -DelayMs 50
```

### Linux / Mac (Bash)

```bash
chmod +x simulate-rides.sh

# 50 rides, 200ms delay
./simulate-rides.sh 50 200

# 200 rides, fast
./simulate-rides.sh 200 50
```

---

## 🔑 Key Design Patterns

| Pattern                     | Where Used                                     |
|-----------------------------|------------------------------------------------|
| Event-Driven Architecture   | All inter-service communication via Kafka      |
| Finite State Machine (FSM)  | Driver lifecycle (AVAILABLE → COMPLETED)       |
| CQRS                        | Write via Kafka events, Read from Redis        |
| Distributed Locking         | Redis SETNX to prevent double-assignment       |
| Saga Pattern                | Ride request → dispatch → driver assignment    |
| Auto-Healing                | Scheduler resets stale driver states           |
| API Gateway Pattern         | Spring Cloud Gateway as single entry point     |
| Dead Letter Queue (DLQ)     | Kafka DLQ for failed message handling          |

---

## 🔮 Future Enhancements

- [ ] JWT authentication + Spring Security
- [ ] PostgreSQL for persistent ride history
- [ ] WebSocket for real-time driver tracking on map
- [ ] Distributed tracing with Jaeger / Zipkin
- [ ] Surge pricing engine (demand/supply ratio)
- [ ] ML-based ETA prediction
- [ ] Geofencing for service area validation
- [ ] Rate limiting at API Gateway
- [ ] Kubernetes deployment manifests (Helm charts)
- [ ] Multi-city / multi-region support

---

## 👤 Author

Built as a production-grade portfolio project demonstrating event-driven microservices architecture using the same patterns as real-world ride-hailing platforms.

---

## 📝 License

MIT License
