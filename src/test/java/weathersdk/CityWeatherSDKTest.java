package weathersdk;

import com.google.gson.JsonObject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class CityWeatherSDKTest {

    private MockWebServer mockWebServer;
    private CityWeatherSDK sdk;

    @BeforeEach
    public void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Replace the base URL with the mock server URL
        String apiKey = "1f8627c052f33a9e3686e12e6e6876ae";
        sdk = CityWeatherSDK.createInstance(apiKey, CityWeatherSDK.Mode.ON_DEMAND);
    }

    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void testGetWeatherByCity_CacheHit() throws IOException {
        // Prepare mock response
        String cityName = "London";
        JsonObject mockResponse = new JsonObject();
        mockResponse.addProperty("name", cityName);
        mockResponse.addProperty("weather", "Clear");
        mockResponse.addProperty("temp", 15.0);
        mockResponse.addProperty("visibility", 10000);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse.toString())
                .addHeader("Content-Type", "application/json"));

        // First call to fetch and cache data
        JsonObject weatherData1 = sdk.getWeatherByCity(cityName);
        assertNotNull(weatherData1);
        assertEquals(cityName, weatherData1.get("name").getAsString());

        // Second call should hit the cache
        JsonObject weatherData2 = sdk.getWeatherByCity(cityName);
        assertSame(weatherData1, weatherData2); // Should return the same object
    }

    @Test
    public void testGetWeatherByCity_CacheMiss() throws IOException {
        String cityName = "Moscow";
        JsonObject mockResponse = new JsonObject();
        mockResponse.addProperty("name", cityName);
        mockResponse.addProperty("weather", "Rain");
        mockResponse.addProperty("temp", 12.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse.toString())
                .addHeader("Content-Type", "application/json"));

        // Call to fetch data
        JsonObject weatherData = sdk.getWeatherByCity(cityName);
        assertNotNull(weatherData);
        assertEquals(cityName, weatherData.get("name").getAsString());
    }

    @Test
    public void testGetGeoData() throws IOException {
        String cityName = "Berlin";
        JsonObject mockGeoResponse = new JsonObject();
        mockGeoResponse.addProperty("name", cityName);
        mockGeoResponse.addProperty("lat", 52.52);
        mockGeoResponse.addProperty("lon", 13.405);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockGeoResponse.toString())
                .addHeader("Content-Type", "application/json"));

        JsonObject geoData = sdk.getGeoData(cityName);
        assertNotNull(geoData);
        assertEquals(cityName, geoData.get("name").getAsString());
        assertEquals(52.52, geoData.get("lat").getAsDouble(), 0.01);
    }

//    @Test
//    public void testCacheSize() {
//        String cityName1 = "Madrid";
//        String cityName2 = "Rome";
//
//        sdk.getWeatherByCity(cityName1);
//        sdk.getWeatherByCity(cityName2);
//
//        assertEquals(2, sdk.getCacheSize());
//    }

    @Test
    public void testCacheSize() {
        String cityName1 = "Madrid";
        String cityName2 = "Rome";

        try {
            sdk.getWeatherByCity(cityName1);
            sdk.getWeatherByCity(cityName2);
            assertEquals(2, sdk.getCacheSize());
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException occurred: " + e.getMessage());
        }
    }

//    @Test
//    public void testClearCache() {
//        String cityName = "Tokyo";
//        sdk.getWeatherByCity(cityName);
//        assertEquals(1, sdk.getCacheSize());
//
//        sdk.clearCache();
//        assertEquals(0, sdk.getCacheSize());
//    }

    @Test
    public void testClearCache() {
        String cityName = "Tokyo";

        try {
            sdk.getWeatherByCity(cityName);
            assertEquals(1, sdk.getCacheSize());

            sdk.clearCache();
            assertEquals(0, sdk.getCacheSize());
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException occurred: " + e.getMessage());
        }
    }
}


//import org.junit.jupiter.api.Test;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class CityWeatherSDKTest {
//
//    @Test
//    void createInstance() {
//    }
//
//    @Test
//    void deleteInstance() {
//    }
//
//    @Test
//    void setPollingInterval() {
//    }
//
//    @Test
//    void getWeatherByCity() {
//    }
//
//    @Test
//    void getGeoData() {
//    }
//
//    @Test
//    void clearCache() {
//    }
//
//    @Test
//    void getCacheSize() {
//    }
//
//    @Test
//    void getCachedWeather() {
//    }
//
//    @Test
//    void shutdown() {
//    }
//}