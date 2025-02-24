package weathersdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SDK for retrieving weather data based on city name (via openweathermap.org).
 * Supports caching weather data for up to 10 recently requested cities.
 * Cached data is valid for 10 minutes.
 * Supports two modes: on-demand and polling.
 */
public class CityWeatherSDK {

    private static final String WEATHER_BASE_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String GEO_BASE_URL = "http://api.openweathermap.org/geo/1.0/direct";
    private static final int MAX_CITIES = 10;
    private static final long CACHE_EXPIRY_TIME_MS = 10 * 60 * 1000; // 10 minutes in milliseconds
    private static final long MIN_POLLING_INTERVAL_MS = 1; // Minimum interval: 1 ms
    private static final long MAX_POLLING_INTERVAL_MS = 60 * 60 * 1000; // Maximum interval: 60 minutes

    // Static collection for storing SDK instances
    private static final Map<String, CityWeatherSDK> instances = new ConcurrentHashMap<>();

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final LinkedHashMap<String, CachedWeatherData> weatherCache;
    private final Mode mode;
    private ScheduledExecutorService scheduler;
    private long pollingIntervalMs;

    /**
     * Enum representing the SDK's operation mode.
     */
    public enum Mode {
        ON_DEMAND, // Data is updated only on customer requests
        POLLING    // Data is updated periodically for all cached cities
    }

    /**
     * Creates a new CityWeatherSDK instance with the specified API key and mode.
     * If an instance with the same key already exists, it returns the existing instance.
     *
     * @param apiKey API key for accessing OpenWeatherMap.
     * @param mode   The operation mode of the SDK (ON_DEMAND or POLLING).
     * @return The created or existing CityWeatherSDK instance.
     * @throws IllegalArgumentException if the apiKey is null or empty.
     */
    public static synchronized CityWeatherSDK createInstance(String apiKey, Mode mode) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API KEY cannot be empty.");
        }

        // If an instance with this key already exists, return it
        if (instances.containsKey(apiKey)) {
            return instances.get(apiKey);
        }

        // Create a new instance
        CityWeatherSDK instance = new CityWeatherSDK(apiKey, mode);
        instances.put(apiKey, instance);
        return instance;
    }

    /**
     * Deletes the CityWeatherSDK instance associated with the specified API key.
     *
     * @param apiKey The API key of the instance to delete.
     */
    public static synchronized void deleteInstance(String apiKey) {
        if (instances.containsKey(apiKey)) {
            CityWeatherSDK instance = instances.get(apiKey);
            instance.shutdown(); // Stop the scheduler if it exists
            instances.remove(apiKey);
        }
    }

    /**
     * Constructs a CityWeatherSDK instance with the specified API key and mode.
     *
     * @param apiKey API key for accessing OpenWeatherMap.
     * @param mode   The operation mode of the SDK (ON_DEMAND or POLLING).
     */
    private CityWeatherSDK(String apiKey, Mode mode) {
        this.apiKey = apiKey;
        this.mode = mode;
        this.httpClient = new OkHttpClient();
        this.weatherCache = new LinkedHashMap<String, CachedWeatherData>(MAX_CITIES + 1, 1.0f) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedWeatherData> eldest) {
                return size() > MAX_CITIES; // Limit the cache size to 10 entries
            }
        };
    }

    /**
     * Sets the polling interval in milliseconds for polling mode.
     *
     * @param pollingIntervalMs The polling interval in milliseconds.
     * @throws IllegalArgumentException if the polling interval is invalid or if the mode is not POLLING.
     */
    public void setPollingInterval(long pollingIntervalMs) {
        if (mode != Mode.POLLING) {
            throw new IllegalArgumentException("Polling interval can only be set in POLLING mode.");
        }
        if (pollingIntervalMs < MIN_POLLING_INTERVAL_MS || pollingIntervalMs > MAX_POLLING_INTERVAL_MS) {
            throw new IllegalArgumentException("Polling interval must be between 1 ms and 60 minutes.");
        }
        this.pollingIntervalMs = pollingIntervalMs;

        // Initialize the scheduler for polling mode
        if (scheduler == null) {
            this.scheduler = Executors.newScheduledThreadPool(1);
            this.scheduler.scheduleAtFixedRate(this::updateAllCachedWeatherData, 0, pollingIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Retrieves weather data for the specified city.
     * If the data is already in the cache and not expired, it returns the cached data.
     * Otherwise, it makes a new request and updates the cache.
     *
     * @param cityName the name of the city.
     * @return a JSON object containing weather data.
     * @throws IOException if an error occurs while executing the request.
     */
    public JsonObject getWeatherByCity(String cityName) throws IOException {
        // Check if data is in the cache and not expired
        if (weatherCache.containsKey(cityName)) {
            CachedWeatherData cachedData = weatherCache.get(cityName);
            if (!isCacheExpired(cachedData)) {
                return cachedData.getWeatherData();
            }
        }

        // If data is not in the cache or is expired, make a request
        JsonObject weatherData = fetchWeatherData(cityName);

        // Save data in the cache
        weatherCache.put(cityName, new CachedWeatherData(weatherData, System.currentTimeMillis()));

        return weatherData;
    }

    /**
     * Fetches weather data for the specified city from the API.
     *
     * @param cityName the name of the city.
     * @return a JSON object containing weather data.
     * @throws IOException if an error occurs while executing the request.
     */
    private JsonObject fetchWeatherData(String cityName) throws IOException {
        JsonObject geoData = getGeoData(cityName);
        double lat = geoData.get("lat").getAsDouble();
        double lon = geoData.get("lon").getAsDouble();

        String url = String.format("%s?lat=%f&lon=%f&appid=%s",
                WEATHER_BASE_URL,
                lat,
                lon,
                apiKey);

        return transformWeatherResponse(executeWeatherRequest(url));
    }

    /**
     * Updates weather data for all cities in the cache (used in polling mode).
     */
    private void updateAllCachedWeatherData() {
        for (Map.Entry<String, CachedWeatherData> entry : weatherCache.entrySet()) {
            String cityName = entry.getKey();
            try {
                JsonObject weatherData = fetchWeatherData(cityName);
                weatherCache.put(cityName, new CachedWeatherData(weatherData, System.currentTimeMillis()));
            } catch (IOException e) {
                System.err.println("Failed to update weather data for city: " + cityName + ", Error: " + e.getMessage());
            }
        }
    }

    /**
     * Checks if the cached weather data is expired.
     *
     * @param cachedData the cached weather data.
     * @return true if the data is expired, false otherwise.
     */
    private boolean isCacheExpired(CachedWeatherData cachedData) {
        long currentTime = System.currentTimeMillis();
        return (currentTime - cachedData.getTimestamp()) > CACHE_EXPIRY_TIME_MS;
    }

    /**
     * Retrieves geographical data for the specified city.
     *
     * @param cityName the name of the city.
     * @return a JSON object containing geographical data.
     * @throws IOException if an error occurs while executing the request.
     */
    public JsonObject getGeoData(String cityName) throws IOException {
        String url = String.format("%s?q=%s&limit=1&appid=%s",
                GEO_BASE_URL,
                cityName,
                apiKey);

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error while requesting geo data: " + response.code() + " - " + response.message());
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response from server.");
            }

            String jsonResponse = responseBody.string();
            JsonObject jsonObject = JsonParser.parseString(jsonResponse)
                    .getAsJsonArray()
                    .get(0)
                    .getAsJsonObject();

            JsonObject filteredJson = new JsonObject();
            filteredJson.addProperty("name", jsonObject.get("name").getAsString());
            filteredJson.addProperty("lat", jsonObject.get("lat").getAsDouble());
            filteredJson.addProperty("lon", jsonObject.get("lon").getAsDouble());

            return filteredJson;
        }
    }

    /**
     * Executes a weather request to the specified URL.
     *
     * @param url the URL for the weather request.
     * @return a JSON object containing the weather response.
     * @throws IOException if an error occurs while executing the request.
     */
    private JsonObject executeWeatherRequest(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error while requesting weather data: " + response.code() + " - " + response.message());
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response from server.");
            }

            String jsonResponse = responseBody.string();
            return JsonParser.parseString(jsonResponse).getAsJsonObject();
        }
    }

    /**
     * Transforms the raw weather response into a more user-friendly format.
     *
     * @param weatherResponse the raw weather response JSON object.
     * @return a JSON object containing transformed weather data.
     */
    private JsonObject transformWeatherResponse(JsonObject weatherResponse) {
        JsonObject result = new JsonObject();

        // Weather
        JsonArray weatherArray = weatherResponse.getAsJsonArray("weather");
        if (weatherArray != null && !weatherArray.isEmpty()) {
            JsonObject weatherObj = weatherArray.get(0).getAsJsonObject();
            JsonObject weather = new JsonObject();
            weather.addProperty("main", weatherObj.get("main").getAsString());
            weather.addProperty("description", weatherObj.get("description").getAsString());
            result.add("weather", weather);
        }

        // Temperature
        JsonObject main = weatherResponse.getAsJsonObject("main");
        if (main != null) {
            JsonObject temperature = new JsonObject();
            temperature.addProperty("temp", main.get("temp").getAsDouble());
            temperature.addProperty("feels_like", main.get("feels_like").getAsDouble());
            result.add("temperature", temperature);
        }

        // Visibility
        if (weatherResponse.has("visibility")) {
            result.addProperty("visibility", weatherResponse.get("visibility").getAsInt());
        }

        // Wind
        JsonObject wind = weatherResponse.getAsJsonObject("wind");
        if (wind != null) {
            JsonObject windObj = new JsonObject();
            windObj.addProperty("speed", wind.get("speed").getAsDouble());
            result.add("wind", windObj);
        }

        // Datetime
        if (weatherResponse.has("dt")) {
            result.addProperty("datetime", weatherResponse.get("dt").getAsLong());
        }

        // Sys
        JsonObject sys = weatherResponse.getAsJsonObject("sys");
        if (sys != null) {
            JsonObject sysObj = new JsonObject();
            if (sys.has("sunrise")) {
                sysObj.addProperty("sunrise", sys.get("sunrise").getAsLong());
            }
            if (sys.has("sunset")) {
                sysObj.addProperty("sunset", sys.get("sunset").getAsLong());
            }
            result.add("sys", sysObj);
        }

        // Timezone
        if (weatherResponse.has("timezone")) {
            result.addProperty("timezone", weatherResponse.get("timezone").getAsInt());
        }

        // Name
        if (weatherResponse.has("name")) {
            result.addProperty("name", weatherResponse.get("name").getAsString());
        }

        return result;
    }

    /**
     * Clears the weather cache.
     */
    public void clearCache() {
        weatherCache.clear();
    }

    /**
     * Returns the current size of the weather cache.
     *
     * @return the number of cities currently cached.
     */
    public int getCacheSize() {
        return weatherCache.size();
    }

    /**
     * Returns the cached weather data for the specified city.
     *
     * @param cityName the name of the city.
     * @return a JSON object containing cached weather data, or null if not found or expired.
     */
    public JsonObject getCachedWeather(String cityName) {
        if (weatherCache.containsKey(cityName)) {
            CachedWeatherData cachedData = weatherCache.get(cityName);
            if (!isCacheExpired(cachedData)) {
                return cachedData.getWeatherData();
            }
        }
        return null;
    }

    /**
     * Shuts down the scheduler (used in polling mode).
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Helper class to store cached weather data and its timestamp.
     */
    private static class CachedWeatherData {
        private final JsonObject weatherData;
        private final long timestamp;

        public CachedWeatherData(JsonObject weatherData, long timestamp) {
            this.weatherData = weatherData;
            this.timestamp = timestamp;
        }

        public JsonObject getWeatherData() {
            return weatherData;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}