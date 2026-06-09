from flask import Flask, request, jsonify
from flask_cors import CORS
import random
import math

app = Flask(__name__)
CORS(app)


def distance(a, b):
    lat1, lon1 = a[0], a[1]
    lat2, lon2 = b[0], b[1]

    R = 6371.0

    lat1_rad = math.radians(lat1)
    lon1_rad = math.radians(lon1)
    lat2_rad = math.radians(lat2)
    lon2_rad = math.radians(lon2)

    dlon = lon2_rad - lon1_rad
    dlat = lat2_rad - lat1_rad

    a_val = (
        math.sin(dlat / 2) ** 2
        + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a_val), math.sqrt(1 - a_val))

    return R * c


def fitness(route, locations):
    total_dist = 0.0
    n = len(route)
    for i in range(n):
        a = route[i]
        b = route[(i + 1) % n]
        total_dist += distance(locations[a], locations[b])
    return total_dist


def calculate_details(route, locations, speed_kmh=45):
    total_dist = 0.0
    n = len(route)
    for i in range(n):
        a = route[i]
        b = route[(i + 1) % n]
        total_dist += distance(locations[a], locations[b])

    total_time_hours = total_dist / speed_kmh
    total_time_minutes = total_time_hours * 60

    return round(total_dist, 2), round(total_time_minutes, 2)


def create_particle(n):
    route = list(range(n))
    random.shuffle(route)
    return route


def pso(locations, num_particles=30, iterations=200):
    n = len(locations)

    particles = [create_particle(n) for _ in range(num_particles)]
    pBest = [p[:] for p in particles]
    pBest_val = [fitness(p, locations) for p in particles]

    gBest = min(pBest, key=lambda x: fitness(x, locations))
    gBest_val = fitness(gBest, locations)

    for _ in range(iterations):
        for i in range(num_particles):
            current = particles[i][:]

            if random.random() < 0.5:
                diff = [
                    j for j in range(n) if current[j] != pBest[i][j]
                ]
                if diff:
                    idx = random.choice(diff)
                    target_val = pBest[i][idx]
                    swap_idx = current.index(target_val)
                    current[idx], current[swap_idx] = (
                        current[swap_idx],
                        current[idx],
                    )

            if random.random() < 0.5:
                diff = [j for j in range(n) if current[j] != gBest[j]]
                if diff:
                    idx = random.choice(diff)
                    target_val = gBest[idx]
                    swap_idx = current.index(target_val)
                    current[idx], current[swap_idx] = (
                        current[swap_idx],
                        current[idx],
                    )

            if random.random() < 0.3:
                a, b = random.sample(range(n), 2)
                current[a], current[b] = current[b], current[a]

            new_val = fitness(current, locations)
            particles[i] = current

            if new_val < pBest_val[i]:
                pBest[i] = current[:]
                pBest_val[i] = new_val

                if new_val < gBest_val:
                    gBest = current[:]
                    gBest_val = new_val

    return gBest


@app.route("/pso", methods=["POST"])
def run_pso():
    data = request.json

    locations = data.get("locations")
    speed_kmh = data.get("speed_kmh", 45)       # opsional, default 45 km/jam
    num_particles = data.get("num_particles", 30)
    iterations = data.get("iterations", 200)

    if not locations or len(locations) < 2:
        return jsonify({"error": "Butuh minimal 2 lokasi"}), 400

    if not isinstance(speed_kmh, (int, float)) or speed_kmh <= 0:
        return jsonify({"error": "speed_kmh harus angka positif"}), 400

    best_route = pso(locations, num_particles=num_particles, iterations=iterations)
    total_dist, total_time_minutes = calculate_details(best_route, locations, speed_kmh)

    ordered_locations = [locations[i] for i in best_route]

    return jsonify({
        "route": best_route,                      
        "ordered_locations": ordered_locations,   
        "distance_km": total_dist,
        "time_minutes": total_time_minutes,
        "speed_kmh": speed_kmh,
    })


@app.route("/reroute", methods=["POST"])
def reroute():
    """
    Frontend mengirim:
    {
        "current_position": [lat, lon],   <- posisi GPS sekarang
        "remaining_locations": [[lat,lon], ...],  <- tujuan yang belum dikunjungi
        "speed_kmh": 45   <- opsional
    }
    """
    data = request.json

    current_pos = data.get("current_position")
    remaining = data.get("remaining_locations")
    speed_kmh = data.get("speed_kmh", 45)

    if not current_pos or not remaining or len(remaining) < 1:
        return jsonify({"error": "current_position dan remaining_locations diperlukan"}), 400

    all_locations = [current_pos] + remaining

    best_route = pso(all_locations)
    total_dist, total_time_minutes = calculate_details(best_route, all_locations, speed_kmh)
    ordered_locations = [all_locations[i] for i in best_route]

    return jsonify({
        "route": best_route,
        "ordered_locations": ordered_locations,
        "distance_km": total_dist,
        "time_minutes": total_time_minutes,
        "speed_kmh": speed_kmh,
    })

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000, debug=True)