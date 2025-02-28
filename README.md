# CityWeatherSDK Documentation   [![](https://jitpack.io/v/padmitriev/CityWeatherSDK.svg)](https://jitpack.io/#padmitriev/CityWeatherSDK)

## Overview

`CityWeatherSDK` is a Java SDK designed to retrieve weather data based on city names using the OpenWeatherMap API. It supports caching of weather data for up to 10 recently requested cities, with cached data being valid for 10 minutes. The SDK operates in two modes: **ON_DEMAND** and **POLLING**.

## Installation

To use the `CityWeatherSDK`, you need to have the following dependencies in your project. If you are using Maven, add the following to your `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>com.github.padmitriev</groupId>
        <artifactId>CityWeatherSDK</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

## Usage Instructions

### 1. Create an Instance of the SDK

To use the SDK, you first need to create an instance by providing your OpenWeatherMap API key and selecting the operation mode (either `ON_DEMAND` or `POLLING`).
You can obtain the key at https://home.openweathermap.org/api_keys.

```java
CityWeatherSDK sdk = CityWeatherSDK.createInstance("your_api_key", CityWeatherSDK.Mode.ON_DEMAND);
```

### 2. Set Polling Interval (for POLLING mode)

If you are using the `POLLING` mode, you can set the polling interval after creating the instance:

```java
sdk.setPollingInterval(60000); // Set polling interval to 60 seconds
```

### 3. Retrieve Weather Data

You can retrieve weather data for a specific city using the `getWeatherByCity` method:

```java
    JsonObject weatherData = sdk.getWeatherByCity("London");
```

### 4. Retrieve Geographical Data

To get geographical data for a city, use the `getGeoData` method:

```java
    JsonObject geoData = sdk.getGeoData("Berlin");
```

### 5. Cache Management

You can check the current size of the cache and clear it if needed:

```java
int cacheSize = sdk.getCacheSize();
System.out.println("Current cache size: " + cacheSize);

// Clear the cache
sdk.clearCache();
```

### 6. Delete an Instance

If you want to delete an instance of the SDK, use the `deleteInstance` method:

```java
CityWeatherSDK.deleteInstance("your_api_key");
```

## Example Code

Here is a complete example demonstrating how to use the `CityWeatherSDK` SDK:

```java
import weathersdk.CityWeatherSDK;
import com.google.gson.JsonObject;

public class WeatherApp {
    public static void main(String[] args) {
        // Create an instance of the SDK
        CityWeatherSDK sdk = CityWeatherSDK.createInstance("your_api_key", CityWeatherSDK.Mode.ON_DEMAND);
        
        // Retrieve weather data
        try {
            JsonObject weatherData = sdk.getWeatherByCity("New York");
            System.out.println("Weather data for New York: " + weatherData);
        } catch (IOException e) {
            System.err.println("Error retrieving weather data: " + e.getMessage());
        }

        // Retrieve geographical data
        try {
            JsonObject geoData = sdk.getGeoData("Tokyo");
            System.out.println("Geographical data for Tokyo: " + geoData);
        } catch (IOException e) {
            System.err.println("Error retrieving geographical data: " + e.getMessage());
        }

        // Clean up
        CityWeatherSDK.deleteInstance("your_api_key");
    }
}
```

## Conclusion

The `CityWeatherSDK` provides a simple and effective way to access weather data from the OpenWeatherMap API. With caching support and the option for polling updates, it is well-suited for applications that require real-time weather information.

For further inquiries or issues, please refer to the official OpenWeatherMap API documentation or contact support.
