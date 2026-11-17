package com.smartassign.location;

import com.smartassign.core.scoring.DistanceProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeSet;

/**
 * City-to-city distances derived from the dataset (median distance_km per city pair, see dataset/prepare_dataset.py).
 * Loaded lazily and cached; distances are reference data and rarely change.
 */
@Service
public class CityDistanceService implements DistanceProvider {

    private final JdbcTemplate jdbc;
    private volatile Map<String, Double> cache;
    private volatile double max = 1.0;
    private volatile List<String> cities = List.of();

    public CityDistanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Map<String, Double> table() {
        Map<String, Double> c = cache;
        if (c == null) {
            synchronized (this) {
                if (cache == null) {
                    reload();
                }
                c = cache;
            }
        }
        return c;
    }

    public synchronized void reload() {
        Map<String, Double> m = new HashMap<>();
        TreeSet<String> names = new TreeSet<>();
        jdbc.query("SELECT from_city, to_city, distance_km FROM city_distances", rs -> {
            String from = rs.getString(1);
            String to = rs.getString(2);
            m.put(key(from, to), rs.getDouble(3));
            names.add(from);
            names.add(to);
        });
        this.max = m.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        this.cities = List.copyOf(names);
        this.cache = m;
    }

    @Override
    public OptionalDouble distanceKm(String fromCity, String toCity) {
        if (fromCity == null || toCity == null) return OptionalDouble.empty();
        Double d = table().get(key(fromCity, toCity));
        return d == null ? OptionalDouble.empty() : OptionalDouble.of(d);
    }

    @Override
    public double maxDistanceKm() {
        table();
        return max;
    }

    public List<String> knownCities() {
        table();
        return cities;
    }

    public boolean isKnownCity(String city) {
        return city != null && knownCities().stream().anyMatch(c -> c.equalsIgnoreCase(city.trim()));
    }

    /** The city name exactly as stored in the reference table. */
    public String canonical(String city) {
        return knownCities().stream().filter(c -> c.equalsIgnoreCase(city.trim())).findFirst().orElse(city.trim());
    }

    private static String key(String a, String b) {
        return a.trim().toLowerCase(Locale.ROOT) + "|" + b.trim().toLowerCase(Locale.ROOT);
    }
}
