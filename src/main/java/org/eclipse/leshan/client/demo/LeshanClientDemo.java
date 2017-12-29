/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Zebra Technologies - initial API and implementation
 *     Sierra Wireless, - initial API and implementation
 *     Bosch Software Innovations GmbH, - initial API and implementation
 *******************************************************************************/

package org.eclipse.leshan.client.demo;

import static org.eclipse.leshan.LwM2mId.*;
import static org.eclipse.leshan.client.object.Security.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.Arrays;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.leshan.LwM2m;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.util.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LeshanClientDemo {

    private static final Logger LOG = LoggerFactory.getLogger(LeshanClientDemo.class);

    private final static String[] modelPaths = new String[] { "3303.xml" };

    private static final int OBJECT_ID_TEMPERATURE_SENSOR = 3303;
    private final static String DEFAULT_ENDPOINT = "LeshanClientDemo";
    private final static String USAGE = "java -jar leshan-client-demo.jar [OPTION]";
    private final static String DEFAULT_TOKEN = "TestToken";

    private static UUID idOne = UUID.randomUUID();
    private static UUID idTwo = UUID.randomUUID();
    private static String httpsURL = null;
    private static String URLPath = null; 

    private static MyLocation locationInstance;
    private static RandomTemperatureSensor temperatureInstance;
    private static SecretsConfig secretsConfiguration;
    private static String token = DEFAULT_TOKEN;
    private static String serverURI = null;
    private static String XDeviceNetwork = null;
    private static String pskKeyString = null;
    private static String pskIdentityString = null;
    private static String endpoint = null;
    private static String mongoURI = null;
    private static boolean LOCAL = false;
    private static final String SECRETS_DIR = "/run/secrets/";

    public static void main(final String[] args) {

        String deviceId = null;
        Options options = new Options();

        options.addOption("h", "help", false, "Display help information.");
        options.addOption("l", false, "If present use configuration.json in local directory.");
        options.addOption("n", true, String.format(
                "Set the endpoint name of the Client.\nDefault: the local hostname or '%s' if any.", DEFAULT_ENDPOINT));
        options.addOption("b", false, "If present use bootstrap.");
        options.addOption("lh", true, "Set the local CoAP address of the Client.\n  Default: any local address.");
        options.addOption("lp", true,
                "Set the local CoAP port of the Client.\n  Default: A valid port value is between 0 and 65535.");
        options.addOption("slh", true, "Set the secure local CoAP address of the Client.\nDefault: any local address.");
        options.addOption("slp", true,
                "Set the secure local CoAP port of the Client.\nDefault: A valid port value is between 0 and 65535.");
        options.addOption("u", true, String.format("Set the LWM2M or Bootstrap server URL.\nDefault: localhost:%d.",
                LwM2m.DEFAULT_COAP_PORT));
        options.addOption("i", true,
                "Set the LWM2M or Bootstrap server PSK identity in ascii.\nUse none secure mode if not set.");
        options.addOption("p", true,
                "Set the LWM2M or Bootstrap server Pre-Shared-Key in hexa.\nUse none secure mode if not set.");
        options.addOption("pos", true,
                "Set the initial location (latitude, longitude) of the device to be reported by the Location object. Format: lat_float:long_float");
        options.addOption("sf", true, "Scale factor to apply when shifting position. Default is 1.0.");
        options.addOption("auth", true, "Set the token for IoTA access");
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null);

        // Parse arguments
        CommandLine cl;
        try {
            cl = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println("Parsing failed.  Reason: " + e.getMessage());
            formatter.printHelp(USAGE, options);
            return;
        }

        // Use local configuration
        if (cl.hasOption("l")) {
            LOCAL = true;
        } else {
            LOCAL = false;
        }

        // Print help
        if (cl.hasOption("help")) {
            formatter.printHelp(USAGE, options);
            return;
        }

        // Abort if unexpected options
        if (cl.getArgs().length > 0) {
            System.err.println("Unexpected option or arguments : " + cl.getArgList());
            formatter.printHelp(USAGE, options);
            return;
        }

        // Abort if we have not identity and key for psk.
        if ((cl.hasOption("i") && !cl.hasOption("p")) || !cl.hasOption("i") && cl.hasOption("p")) {
            System.err.println("You should precise identity and Pre-Shared-Key if you want to connect in PSK");
            formatter.printHelp(USAGE, options);
            return;
        }

        // Get endpoint name
        if (cl.hasOption("n")) {
            endpoint = cl.getOptionValue("n") + "-" + idOne;
        } else {
            try {
                endpoint = InetAddress.getLocalHost().getHostName() + "-" + idOne;
            } catch (UnknownHostException e) {
                endpoint = DEFAULT_ENDPOINT + "-" + idOne;
            }
        }

        // Get authentication token
        if (cl.hasOption("auth")) {
            token = cl.getOptionValue("auth");
        } else {
                token = DEFAULT_TOKEN;
        }


        // Get server URI
        if (cl.hasOption("u")) {
            if (cl.hasOption("i"))
                serverURI = "coaps://" + cl.getOptionValue("u");
            else
                serverURI = "coap://" + cl.getOptionValue("u");
        } else {
            if (cl.hasOption("i"))
                serverURI = "coaps://localhost:" + LwM2m.DEFAULT_COAP_SECURE_PORT;
            else
                serverURI = "coap://localhost:" + LwM2m.DEFAULT_COAP_PORT;
        }

        // get security info
        byte[] pskIdentity = null;
        byte[] pskKey = null;
        if (cl.hasOption("i") && cl.hasOption("p")) {
            pskIdentity = cl.getOptionValue("i").getBytes();
            pskKey = Hex.decodeHex(cl.getOptionValue("p").toCharArray());
        }

        // get local address
        String localAddress = null;
        int localPort = 0;
        if (cl.hasOption("lh")) {
            localAddress = cl.getOptionValue("lh");
        }
        if (cl.hasOption("lp")) {
            localPort = Integer.parseInt(cl.getOptionValue("lp"));
        }

        // get secure local address
        String secureLocalAddress = null;
        int secureLocalPort = 0;
        if (cl.hasOption("slh")) {
            secureLocalAddress = cl.getOptionValue("slh");
        }
        if (cl.hasOption("slp")) {
            secureLocalPort = Integer.parseInt(cl.getOptionValue("slp"));
        }

        Float latitude = null;
        Float longitude = null;
        Float scaleFactor = 1.0f;
        // get initial Location
        if (cl.hasOption("pos")) {
            try {
                String pos = cl.getOptionValue("pos");
                int colon = pos.indexOf(':');
                if (colon == -1 || colon == 0 || colon == pos.length() - 1) {
                    System.err.println("Position must be a set of two floats separated by a colon, e.g. 48.131:11.459");
                    formatter.printHelp(USAGE, options);
                    return;
                }
                latitude = Float.valueOf(pos.substring(0, colon));
                longitude = Float.valueOf(pos.substring(colon + 1));
            } catch (NumberFormatException e) {
                System.err.println("Position must be a set of two floats separated by a colon, e.g. 48.131:11.459");
                formatter.printHelp(USAGE, options);
                return;
            }
        }
        if (cl.hasOption("sf")) {
            try {
                scaleFactor = Float.valueOf(cl.getOptionValue("sf"));
            } catch (NumberFormatException e) {
                System.err.println("Scale factor must be a float, e.g. 1.0 or 0.01");
                formatter.printHelp(USAGE, options);
                return;
            }
        }
        //pskIdentity = endpoint.getBytes();
        JSONObject confObject = readConfig();
        deviceId = generateClient(endpoint, pskIdentity, pskKey, confObject);
        if (deviceId != null) {
            createAndStartClient(endpoint, localAddress, localPort, secureLocalAddress, secureLocalPort, cl.hasOption("b"),
                    serverURI, latitude, longitude, scaleFactor, token, deviceId, 2.64d, httpsURL, URLPath, mongoURI);
        } else {
            System.out.println ("Error in registrating device. Aborting");
            System.exit(0);
        }
    }

    public static JSONObject readConfig() {
        String fileName = null;
        secretsConfiguration = new SecretsConfig();
        //for(String key: secretsConfiguration.localSecrets().keySet())
        //    System.out.println(key + " - " + secretsConfiguration.localSecrets().get(key));
            //System.out.println("TESTING TESTING " + secretsConfiguration.localSecrets().get("LocationId"));
        String deviceId = null;
        String pskKey = null;
        JSONParser fileparser = new JSONParser();
        JSONObject configObject = new JSONObject();
        if (LOCAL) {
            fileName = "configuration.json";
        } else {
            fileName = SECRETS_DIR + "configuration.json";
        } 
        try {
            Object file = fileparser.parse(new FileReader(fileName));
            configObject =  (JSONObject) file;
            token = configObject.get("Authorization").toString();
            configObject.remove("Authorization");
            httpsURL = configObject.get("httpsURL").toString();
            configObject.remove("httpsURL");
            URLPath = configObject.get("URLPath").toString();
            configObject.remove("URLPath");
            serverURI = "coaps://" + configObject.get("serverURI").toString();
            configObject.remove("serverURI");
            XDeviceNetwork = configObject.get("X-DeviceNetwork").toString();
            configObject.remove("X-DeviceNetwork");
            endpoint = configObject.get("endpoint").toString() + "-" + idOne;
            configObject.remove("endpoint");
            configObject.put("DeviceIdentifier", endpoint);
            configObject.put("Name", endpoint);
            pskIdentityString = endpoint;
            pskKeyString = configObject.get("pskKey").toString();
            configObject.remove("pskKey");
            mongoURI = configObject.get("MongoURI").toString();
            configObject.remove("mongoURI");
            JSONArray SettingsCat = new JSONArray();
            JSONObject SettingsCat1 = new JSONObject();
            JSONArray Settings = new JSONArray();
            JSONObject identity = new JSONObject();
            JSONObject key = new JSONObject();
            key.put("DataType", "String");
            key.put("Key", "preSharedKey");
            key.put("Value", pskKeyString);
            identity.put("DataType", "String");
            identity.put("Key", "identity");
            identity.put("Value", endpoint);
            Settings.add(identity);
            Settings.add(key);
            SettingsCat1.put("Name", "PSK");
            SettingsCat1.put("Settings", Settings);
            SettingsCat.add(SettingsCat1);
            configObject.put("SettingCategories", SettingsCat);

        } catch (IOException | org.json.simple.parser.ParseException e) {
            e.printStackTrace();
            System.exit(0);
        }
    return configObject;
    }

    public static String generateClient(String endpoint, byte[] pskIdentity, byte[] pskKey, JSONObject configObject) {

        String deviceId = null;
        HttpClient httpClient = HttpClientBuilder.create().build();
        try {
            String PATH = "devices";
            HttpPost request = new HttpPost(httpsURL + "/" + URLPath +"/api/v3/" + PATH);
            StringEntity params =new StringEntity(configObject.toString());
            request.addHeader("content-type", "application/json");
            request.addHeader("X-DeviceNetwork", XDeviceNetwork);
            request.addHeader("Authorization", token);
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);
            if (response != null) {
                if (response.getStatusLine().getStatusCode() == 201) {
                    InputStream in = response.getEntity().getContent(); //Get the data in the entity
                    String result = IOUtils.toString(in, StandardCharsets.UTF_8);
                    JSONParser jsonParser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) jsonParser.parse(result);
                    deviceId = (String) jsonObject.get("Id");
                    System.out.println("Device Id: : " + deviceId);
                    System.out.println("Response from IoTA is: " + result);
                    return deviceId;
                } else {
                    System.out.println("Error in responce code from IoTA: " + response.getStatusLine().getStatusCode());
                    InputStream in = response.getEntity().getContent(); //Get the data in the entity
                    String result = IOUtils.toString(in, StandardCharsets.UTF_8);
                    System.out.println("Response from IoTA is: " + result);
                    System.exit(0);
                }
            }
        } catch (Exception ex) {

            System.out.println("Error in Communication to IoTA " + ex);
            return null;
        } finally {
            //httpClient.releaseConnection();;
        }
        return null;
    }

    public static void createAndStartClient(String endpoint, String localAddress, int localPort,
            String secureLocalAddress, int secureLocalPort, boolean needBootstrap, String serverURI,
            Float latitude, Float longitude, float scaleFactor, final String token, final String deviceId, Double temp, final String httpsURL, final String URLPathi, final String mongoURI) {

        byte [] pskIdentity = pskIdentityString.getBytes();
        byte[] pskKey = pskKeyString.getBytes();
        locationInstance = new MyLocation(latitude, longitude, scaleFactor);
        temperatureInstance = new RandomTemperatureSensor(deviceId, token, httpsURL, URLPath, mongoURI);

        // Initialize model
        List<ObjectModel> models = ObjectLoader.loadDefault();
        models.addAll(ObjectLoader.loadDdfResources("/models", modelPaths));

        // Initialize object list
        ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(models));
        if (needBootstrap) {
            if (pskIdentity == null)
                initializer.setInstancesForObject(SECURITY, noSecBootstap(serverURI));
            else
                initializer.setInstancesForObject(SECURITY, pskBootstrap(serverURI, pskIdentity, pskKey));
        } else {
            if (pskIdentity == null) {
                initializer.setInstancesForObject(SECURITY, noSec(serverURI, 123));
                initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
            } else {
                initializer.setInstancesForObject(SECURITY, psk(serverURI, 123, pskIdentity, pskKey));
                initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
            }
        }
        initializer.setClassForObject(DEVICE, MyDevice.class);
        initializer.setInstancesForObject(LOCATION, locationInstance);
        //initializer.setInstancesForObject(OBJECT_ID_TEMPERATURE_SENSOR, new RandomTemperatureSensor());
        initializer.setInstancesForObject(OBJECT_ID_TEMPERATURE_SENSOR, temperatureInstance);
        List<LwM2mObjectEnabler> enablers = initializer.create(SECURITY, SERVER, DEVICE, LOCATION,
                OBJECT_ID_TEMPERATURE_SENSOR);

        // Create CoAP Config
        NetworkConfig coapConfig;
        File configFile = new File(NetworkConfig.DEFAULT_FILE_NAME);
        if (configFile.isFile()) {
            coapConfig = new NetworkConfig();
            coapConfig.load(configFile);
        } else {
            coapConfig = LeshanClientBuilder.createDefaultNetworkConfig();
            coapConfig.store(configFile);
        }

        // Create client
        LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
        builder.setLocalAddress(localAddress, localPort);
        builder.setLocalSecureAddress(secureLocalAddress, secureLocalPort);
        builder.setObjects(enablers);
        builder.setCoapConfig(coapConfig);
        // if we don't use bootstrap, client will always use the same unique endpoint
        // so we can disable the other one.
        if (!needBootstrap) {
            if (pskIdentity == null)
                builder.disableSecuredEndpoint();
            else
                builder.disableUnsecuredEndpoint();
        }
        final LeshanClient client = builder.build();

        LOG.info("Press 'w','a','s','d' to change reported Location ({},{}).", locationInstance.getLatitude(),
                locationInstance.getLongitude());

        // Start the client
        client.start();

        // De-register on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                client.destroy(true); // send de-registration request before destroy
                HttpClient httpClient = HttpClientBuilder.create().build();
                try {
                    String COMMAND = "devices";
                    HttpDelete delete = new HttpDelete(httpsURL + "/" + URLPath +"/api/v3/" + COMMAND + "/" + deviceId);
                    //StringEntity params =new StringEntity(obj.toString());
                    delete.addHeader("X-DeviceNetwork", XDeviceNetwork);
                    delete.addHeader("Authorization", token);
                    //delete.setEntity(params);
                    HttpResponse response = httpClient.execute(delete);
                    if (response != null) {
                        String status = response.getStatusLine().toString();
                        System.out.println("Response from delete: " + status);
                    }
                } catch (Exception ex) {
                    System.out.println("Error in deleting Device " + deviceId + " with error code " + ex);

                } finally {
                    //httpClient.releaseConnection();;
                }
            }
        });

        // Change the location through the Console
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNext()) {
                String nextMove = scanner.next();
                locationInstance.moveLocation(nextMove);
            }
        }
    }
}
