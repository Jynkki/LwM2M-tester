package org.eclipse.leshan.client.demo;

import java.lang.reflect.Field;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Date;
import java.util.UUID;
import java.lang.*;
import java.text.SimpleDateFormat;

import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import java.nio.charset.StandardCharsets;

import io.swagger.client.ApiException;
import io.swagger.client.api.*;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.VersionApi;
import io.swagger.client.api.DeviceApi;
import io.swagger.client.model.*;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.util.NamedThreadFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import com.mongodb.Cursor;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ParallelScanOptions;
import com.mongodb.ServerAddress;
import com.mongodb.MongoClientURI;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoException;
import static com.mongodb.client.model.Filters.*;
import com.mongodb.client.result.DeleteResult;
import static com.mongodb.client.model.Updates.*;
import com.mongodb.client.result.UpdateResult;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import java.util.Arrays;
import com.mongodb.Block;
import java.util.ArrayList;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;

public class RandomTemperatureSensor extends BaseInstanceEnabler {

    private static final String UNIT_CELSIUS = "cel";
    private static final int SENSOR_VALUE = 5700;
    private static final int UNITS = 5701;
    private static final int MAX_MEASURED_VALUE = 5602;
    private static final int MIN_MEASURED_VALUE = 5601;
    private static final int RESET_MIN_MAX_MEASURED_VALUES = 5605;
    private final ScheduledExecutorService scheduler;
    private final Random rng = new Random();
    private float currentTemp = 20;
    private float minMeasuredValue = currentTemp;
    private float maxMeasuredValue = currentTemp;
    private int min = 20;
    private int max = 50;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Date timeStamp = new Date();
    private static String deviceStatus = "UNKNOWN";
    private static MongoCollection<Document> devices = null;
    private static MongoClient mongo = null;

    public RandomTemperatureSensor(final String deviceId, final String token, final String httpsURL, final String URLPath, final String mongoURI) {        

        String baseApiUrl = httpsURL + "/" + URLPath;
        String xDeviceNetwork = "613b7124-e2db-4e76-9feb-102a869bd497"; // String | Device Network Id
        UUID endpoint = UUID.fromString(deviceId); // String | Device endpoint
        String v3Token = token;
        int connectTimeout = 60000;
        int readTimeout = 120000;

        try { 
            MongoClientURI uri  = new MongoClientURI(mongoURI); 
            mongo = new MongoClient(uri);
            MongoDatabase db = mongo.getDatabase(uri.getDatabase());
            devices = db.getCollection("devices");
            Document initialData = new Document(new Document("deviceid", deviceId)
                .append("time", sdf.format(timeStamp.getTime() ))
                .append("status", "UNKNOWN")
            );
            devices.insertOne(initialData);
        } catch (Exception e) {
            System.out.println("Error in connecting to DB and planting a seed: " + e);
        }


        this.scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Temperature Sensor"));
        scheduler.scheduleAtFixedRate(new Runnable() {
            private float temp = 1.0f;
            @Override
            public void run() {

                ApiClient client = new ApiClient();
                String authorization = v3Token; // String | Basic Access Authentication
                client.setBasePath(baseApiUrl);
                client.setDebugging(false);
 
                //StringProperty smartObjectId = new SimpleStringProperty();
                String smartObjectId = " ";
                temp = (ThreadLocalRandom.current().nextInt(min, max + 1))/1.0f;
                adjustTemperature(temp);

                // Wait for a while for the measurement to be sent
                try {
                    // thread to sleep for x milliseconds
                    Thread.sleep(3000);
                } catch (Exception e) {
                        System.out.println("Sleep failed: " + e);
                }

                //Read the device info
                DeviceApi deviceApiInstance = new DeviceApi(client);
                SigmaSensationDTODeviceDeviceRequest deviceRequest = new SigmaSensationDTODeviceDeviceRequest();
                try {
                    SigmaSensationDTODeviceDeviceResponse result = deviceApiInstance.deviceGet_0(endpoint, xDeviceNetwork, authorization);

                    if (result != null) {
                        if ((client.getStatusCode() == 200) || (client.getStatusCode() == 204) ) {
                                for (SigmaSensationDTOSmartObjectSmartObjectListResponse g: result.getSmartObjects()) {
                                    if (g.getName().equals("Temperature")) {
                                        smartObjectId = g.getId().toString();
                                    }
                                }
                        } else {
                            Document deviceData = new Document(new Document("deviceid", deviceId)
                                .append("time", sdf.format(timeStamp.getTime() ))
                            );
                            if (!deviceStatus.equals("Not OK")) {
                                deviceData.append("status", "Not OK");
                                Document newData = new Document("$set", deviceData);
                                devices.updateOne(eq("deviceid", deviceId), newData);
                            }
                            deviceStatus = "Not OK";
                            System.out.println(sdf.format(timeStamp.getTime()) + ": Could not read SmartObject id, Error code: " + client.getStatusCode() + ". Response from IoTA is: " + result);
                            return;
                        }
                    }

                } catch (ApiException e) {
                    Document deviceData = new Document(new Document("deviceid", deviceId)
                        .append("time", sdf.format(timeStamp.getTime() ))
                    );

                    if (!deviceStatus.equals("Not OK")) {
                        deviceData.append("status", "Not OK");
                        Document newData = new Document("$set", deviceData);
                        devices.updateOne(eq("deviceid", deviceId), newData);
                    }
                    deviceStatus = "Not OK";
                    System.out.println(sdf.format(timeStamp.getTime()) + ": Error reading the device: " + endpoint);
                    e.printStackTrace();
                }

                // Read SmartObject
                if (smartObjectId.equals(" ")){
                    System.out.println("No Temperature device found");
                    return;
                }
                try {
                    // thread to sleep for 1000 milliseconds
                    Thread.sleep(3000);
                } catch (Exception e) {
                        System.out.println("Sleep failed: " + e);
                }

                SmartObjectApi readInstance = new SmartObjectApi(client);
                try {
                    readInstance.smartObjectReadResources(UUID.fromString(smartObjectId), xDeviceNetwork, authorization);
                } catch (ApiException e) {
                    System.err.println("Error: Unable to perform a read on smart object" + smartObjectId);
                    e.printStackTrace();
                }

                // Fetch SmartObject Value
                try {
                    // thread to sleep for 1000 milliseconds
                    Thread.sleep(3000);
                } catch (Exception e) {
                        System.out.println("Sleep failed: " + e);
                }

                SmartObjectApi fetchInstance = new SmartObjectApi(client);
                try {
                    SigmaSensationDTOSmartObjectSmartObjectResponse result = fetchInstance.smartObjectGetLatestMeasurementForResourcesAsync(UUID.fromString(smartObjectId), xDeviceNetwork, authorization);
                    timeStamp = new Date();
                    Document deviceData = new Document(new Document("deviceid", deviceId)
                        .append("time", sdf.format(timeStamp.getTime() ))
                    );
                    if (result != null) {
                        if ((client.getStatusCode() == 200) || (client.getStatusCode() == 204) ) {
                            for (SigmaSensationDTOResourceResourceResponse g: result.getResources()) {
                                if (g.getName().equals("Sensor Value")) {
                                     SigmaSensationDTOGrainCommunicationSignalRMeasurementMessage measurement = g.getLatestMeasurement();
                                     System.out.println("Temperature Read: " + measurement.getV());
                                     //Document deviceData = new Document(new Document("deviceid", deviceId)
                                     //   .append("time", sdf.format(timeStamp.getTime() ))
                                     //);
                                     if (temp != measurement.getV()) {
                                         if (!deviceStatus.equals("Not OK")) {
                                             deviceData.append("status", "Not OK");
                                             Document newData = new Document("$set", deviceData);
                                             devices.updateOne(eq("deviceid", deviceId), newData);
                                         }
                                         deviceStatus = "Not OK";                                                  
                                         System.out.println(sdf.format(timeStamp.getTime()) + ": Measurement failed. The smartobject is " + smartObjectId + ".  Read value = " + measurement.getV() + " should have been " + temp);
                                     } else {
                                         if (!deviceStatus.equals("OK")) {
                                             deviceData.append("status", "OK");
                                             Document newData = new Document("$set", deviceData);
                                             devices.updateOne(eq("deviceid", deviceId), newData);
                                         }
                                         deviceStatus = "OK";
                                         System.out.println(sdf.format(timeStamp.getTime()) + ": Measurement OK");
                                     }
                                }
                            }
                        } else {
                            if (!deviceStatus.equals("Not OK")) {
                                deviceData.append("status", "Not OK");
                                Document newData = new Document("$set", deviceData);
                                devices.updateOne(eq("deviceid", deviceId), newData);
                            }
                            deviceStatus = "Not OK";
                            System.out.println(sdf.format(timeStamp.getTime()) + ": Could not read the value from SmartObject, Error code: " + client.getStatusCode() + ". Response from IoTA is: " + result);
                            return;
                        }
                    }
                } catch (ApiException e) {
                    Document deviceData = new Document(new Document("deviceid", deviceId)
                        .append("time", sdf.format(timeStamp.getTime() ))
                    );

                    if (!deviceStatus.equals("Not OK")) {
                        deviceData.append("status", "Not OK");
                        Document newData = new Document("$set", deviceData);
                        devices.updateOne(eq("deviceid", deviceId), newData);
                    }
                    deviceStatus = "Not OK";
                    System.out.println(sdf.format(timeStamp.getTime()) + ": Error reading the value from the device: " + endpoint);
                    e.printStackTrace();
                }

            }
        }, 2, 2, TimeUnit.SECONDS);
        // De-register on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Deleting Record and closing connection");
                try {
                    DeleteResult deleteResult = devices.deleteOne(eq("deviceid", deviceId));
                    System.out.println("Deleted records: " + deleteResult.getDeletedCount());
                    mongo.close();
                } catch (Exception e) {
                    System.out.println("Mongo DB not properly closed: " + e);
                }
            }
        });


    }

    @Override
    public synchronized ReadResponse read(int resourceId) {
        switch (resourceId) {
        case MIN_MEASURED_VALUE:
            return ReadResponse.success(resourceId, getTwoDigitValue(minMeasuredValue));
        case MAX_MEASURED_VALUE:
            return ReadResponse.success(resourceId, getTwoDigitValue(maxMeasuredValue));
        case SENSOR_VALUE:
            return ReadResponse.success(resourceId, getTwoDigitValue(currentTemp));
        //case UNITS:
        //    return ReadResponse.success(resourceId, UNIT_CELSIUS);
        default:
            return super.read(resourceId);
        }
    }

    @Override
    public synchronized ExecuteResponse execute(int resourceId, String params) {
        switch (resourceId) {
        case RESET_MIN_MAX_MEASURED_VALUES:
            resetMinMaxMeasuredValues();
            return ExecuteResponse.success();
        default:
            return super.execute(resourceId, params);
        }
    }

    private float getTwoDigitValue(final float value) {
        BigDecimal toBeTruncated = BigDecimal.valueOf(value);
        return toBeTruncated.setScale(2, RoundingMode.HALF_UP).floatValue();
    }

    private synchronized void adjustTemperature(Float temp) {
        currentTemp = temp;
        Integer changedResource = adjustMinMaxMeasuredValue(currentTemp);
        if (changedResource != null) {
            fireResourcesChange(SENSOR_VALUE, changedResource);
        } else {
            fireResourcesChange(SENSOR_VALUE);
        }
    }

    private Integer adjustMinMaxMeasuredValue(float newTemperature) {

        if (newTemperature > maxMeasuredValue) {
            maxMeasuredValue = newTemperature;
            return MAX_MEASURED_VALUE;
        } else if (newTemperature < minMeasuredValue) {
            minMeasuredValue = newTemperature;
            return MIN_MEASURED_VALUE;
        } else {
            return null;
        }
    }

    private void resetMinMaxMeasuredValues() {
        minMeasuredValue = currentTemp;
        maxMeasuredValue = currentTemp;
    }
}
