#!/bin/bash
# simulate-rides.sh — Linux/Mac load tester
# Usage: ./simulate-rides.sh [count] [delay_ms]

COUNT=${1:-50}
DELAY_MS=${2:-200}
BASE_URL="http://localhost:8081/api/rides/request"

RIDE_TYPES=("ECONOMY" "PREMIUM" "XL")
PICKUPS=("Pune Station" "Shivajinagar" "Kothrud" "Baner" "Hinjewadi")
DROPS=("Airport" "FC Road" "Magarpatta" "Hadapsar" "Camp")

CENTER_LAT=18.5204
CENTER_LON=73.8567

success=0
failure=0
total_ms=0

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║     🚕 Ride-Hailing Dispatch - Load Tester      ║"
echo "╚══════════════════════════════════════════════════╝"
echo "  Endpoint : $BASE_URL"
echo "  Count    : $COUNT"
echo "  Delay    : ${DELAY_MS}ms"
echo ""

for i in $(seq 1 $COUNT); do
    RIDER_ID="RIDER-$RANDOM"
    RIDE_TYPE="${RIDE_TYPES[$((RANDOM % 3))]}"
    PICKUP="${PICKUPS[$((RANDOM % 5))]}"
    DROP="${DROPS[$((RANDOM % 5))]}"

    PLAT=$(echo "scale=6; $CENTER_LAT + ($RANDOM % 100 - 50) * 0.0003" | bc)
    PLON=$(echo "scale=6; $CENTER_LON + ($RANDOM % 100 - 50) * 0.0003" | bc)
    DLAT=$(echo "scale=6; $CENTER_LAT + ($RANDOM % 100 - 50) * 0.0003" | bc)
    DLON=$(echo "scale=6; $CENTER_LON + ($RANDOM % 100 - 50) * 0.0003" | bc)

    BODY=$(cat <<EOF
{
  "riderId": "$RIDER_ID",
  "riderName": "TestRider_$i",
  "pickupLat": $PLAT,
  "pickupLon": $PLON,
  "dropLat": $DLAT,
  "dropLon": $DLON,
  "pickupAddress": "$PICKUP",
  "dropAddress": "$DROP",
  "rideType": "$RIDE_TYPE"
}
EOF
)

    START=$(date +%s%N)
    RESPONSE=$(curl -s -o /tmp/ride_resp.json -w "%{http_code}" \
        -X POST "$BASE_URL" \
        -H "Content-Type: application/json" \
        -d "$BODY" \
        --max-time 10)
    END=$(date +%s%N)
    MS=$(( (END - START) / 1000000 ))
    total_ms=$((total_ms + MS))

    if [ "$RESPONSE" = "200" ]; then
        RIDE_ID=$(python3 -c "import json,sys; d=json.load(open('/tmp/ride_resp.json')); print(d.get('rideId','?'))" 2>/dev/null || echo "?")
        echo "  [$i] ✅ $RIDE_ID | $RIDE_TYPE | ${MS}ms"
        ((success++))
    else
        echo "  [$i] ❌ HTTP $RESPONSE | $RIDE_TYPE | ${MS}ms"
        ((failure++))
    fi

    sleep $(echo "scale=3; $DELAY_MS/1000" | bc)
done

AVG_MS=$((total_ms / COUNT))

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║                   📊 RESULTS                    ║"
echo "╠══════════════════════════════════════════════════╣"
echo "║  Total   : $COUNT"
echo "║  Success : $success ✅"
echo "║  Failed  : $failure ❌"
echo "║  Avg Lat : ${AVG_MS}ms"
echo "╚══════════════════════════════════════════════════╝"
