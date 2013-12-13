package com.droidsyourelookingfornot.nestapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ResourceBundle;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java Nest API
 *
 */
public class Nest {

    static Logger log = LoggerFactory.getLogger(Nest.class);
    private final String USER = "user";
    private final String USERID = "userid";
    private final String URLS = "urls";
    private final String TRANSPORT_URL = "transport_url";
    private final String ACCESS_TOKEN = "access_token";
    private final String STRUCTURES = "structures";
    private final String STRUCTURE = "structure";
    private final String STRUCTURE_DOT = "structure.";
    private final String DEVICES = "devices";
    private final String DEVICE = "device";
    private final String DEVICE_DOT = "device.";
    private final String SHARED = "shared";
    private final String CURRENT_HUMIDITY = "current_humidity";
    private final String CURRENT_TEMPERATURE = "current_temperature";
    private final String POST = "POST";
    private final String GET = "GET";
    private final String NESTURL = "nesturl";
    private final String NESTRESTPATH = "nestrestpath";
    private ResourceBundle nestconfig;

    /**
     *
     * @param args The command line arguments
     * @throws MalformedURLException
     * @throws IOException
     * @throws InterruptedException
     * @throws org.apache.commons.cli.ParseException
     * @throws ParseException
     */
    public static void main(String[] args) throws MalformedURLException, IOException, InterruptedException, org.apache.commons.cli.ParseException, ParseException {

        Options options = new Options();

        options.addOption("u", "username", true, "username");
        options.addOption("p", "password", true, "password");
        options.addOption("r", "refreshRate", true, "Refresh rate in ms. ie 60000ms = 60s");
        options.addOption("i", "iterations", true, "Number of iterations. ie 0=forever");


        CommandLineParser parser = new BasicParser();
        CommandLine cmd = parser.parse(options, args);


        Nest nest = new Nest();
        nest.callNest(cmd);
    }

    /**
     * Simple method that gets all the nest(s) for your account and displays
     * the current temperature and humidity.  
     * It does this for a given refresh rate (milliseconds) and n number of  iterations (zero = forever).
     * 
     * @param cmd The argument commands as an apache CLI Command object
     * @throws InterruptedException
     * @throws ParseException
     */
    public void callNest(CommandLine cmd) throws InterruptedException, ParseException {
        String username = cmd.getOptionValue("username");
        String password = cmd.getOptionValue("password");
        int refreshrate = Integer.valueOf(cmd.getOptionValue("refreshRate"));
        int iterations = Integer.valueOf(cmd.getOptionValue("iterations"));

        nestconfig = ResourceBundle.getBundle("nestconfig");

        JSONObject nestHeaderJSON = getNestToken(username, password);

        for (int i = 0; i < iterations || iterations == 0; i++) {
            JSONObject nestDataJSON = getNestData(nestHeaderJSON);

            JSONArray structuresJSONArray = ((JSONArray) ((JSONObject) ((JSONObject) nestDataJSON.get(USER)).get(nestHeaderJSON.get(USERID))).get(STRUCTURES));

            for (Object structureObjectValue : structuresJSONArray.toArray()) {
                String structureValue = structureObjectValue.toString().replaceFirst(STRUCTURE_DOT, "");

                JSONArray devicesJSONArray;
                devicesJSONArray = ((JSONArray) ((JSONObject) ((JSONObject) nestDataJSON.get(STRUCTURE)).get(structureValue)).get(DEVICES));


                for (Object deviceObjectVlaue : devicesJSONArray.toArray()) {
                    String deviceValue = deviceObjectVlaue.toString().replaceFirst(DEVICE_DOT, "");

                    System.out.println(CURRENT_HUMIDITY + " : "
                            + getCurrentHumidity(nestDataJSON, deviceValue));
                    System.out.println(CURRENT_TEMPERATURE + " : "
                            + getCurrentTemperature(nestDataJSON, deviceValue));
                }
            }
            Thread.sleep(refreshrate);
        }
    }

    /**
     * Makes a HTTP call
     * @param requestMethod The HTTP request method (GET or POST)
     * @param url The url to call
     * @param basicAuth The basic authentication (ie the users token)
     * @param urlParameters The url parameters (ie username, password)
     * @return The response of the HTTP call
     */
    public String httpCall(String requestMethod, String url, String basicAuth, String urlParameters) {
        String returnValue = null;
        BufferedReader in = null;
        OutputStreamWriter writer = null;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod(requestMethod);

            if (basicAuth.length() > 0) {
                con.setRequestProperty("Authorization", basicAuth);
            }


            if (urlParameters.length() > 0) {
                try {
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Length", String.valueOf(urlParameters.getBytes().length));
                    writer = new OutputStreamWriter(con.getOutputStream());
                    writer.write(urlParameters);
                    writer.flush();


                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                } finally {
                    writer.close();
                }
            }

            in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            in.close();

            returnValue = response.toString();

        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }

        return returnValue;
    }

    /**
     * Get user credentials for the given account.  This has all the information to access the nest(s) data.
     * @param username The account username
     * @param password The account password
     * @return The JSON object of the credential for the given user 
     * @throws ParseException
     */
    public JSONObject getNestToken(String username, String password) throws ParseException {
        JSONObject returnValue = null;
        String url = nestconfig.getString(NESTURL);
        String response = httpCall(POST, url, "", "username=" + username + "&password=" + password);

        try {
            returnValue = (JSONObject) new JSONParser().parse(response.toString());
        } catch (ParseException ex) {
            log.error(ex.getMessage(), ex);
            throw ex;
        }

        return returnValue;
    }

    /**
     * Gets all the information of the nest(s) for the user token (you get this from getNestToken()).
     * This has static and real-time information for your nest(s) 
     * @param nestHeaderJSON
     * @return The JSON object of all the Nest(s) the account has
     * @throws ParseException
     */
    public JSONObject getNestData(JSONObject nestHeaderJSON) throws ParseException {
        JSONObject returnValue = null;

        String url2 = ((JSONObject) nestHeaderJSON.get(URLS)).get(TRANSPORT_URL)
                + nestconfig.getString(NESTRESTPATH)
                + nestHeaderJSON.get(USER);
        String basicAuth = "Basic " + nestHeaderJSON.get(ACCESS_TOKEN);

        String nestDataDump = httpCall(GET, url2, basicAuth, "");
        
        log.info(nestDataDump);


        try {
            returnValue = (JSONObject) new JSONParser().parse(nestDataDump.toString());
        } catch (ParseException ex) {
            log.error(ex.getMessage(), ex);
            throw ex;
        }

        return returnValue;
    }

    /**
     * Gets the current Temperature for a given nest (device)
     * @param nestDataJSON All the nest(s) data
     * @param deviceValue The unique device for the nest
     * @return The Current Temperature
     */
    public String getCurrentTemperature(JSONObject nestDataJSON, String deviceValue) {
        String returnValue = null;
        returnValue = ((JSONObject) ((JSONObject) nestDataJSON.get(SHARED)).get(deviceValue)).get(CURRENT_TEMPERATURE).toString();
        return returnValue;
    }

    /**
     * Gets the current Humidity for a given nest (device)
     * @param nestDataJSON All the nest(s) data
     * @param deviceValue The unique device for the nest
     * @return The Current Humidity
     */
    public String getCurrentHumidity(JSONObject nestDataJSON, String deviceValue) {
        String returnValue = null;
        returnValue = ((JSONObject) ((JSONObject) nestDataJSON.get(DEVICE)).get(deviceValue)).get(CURRENT_HUMIDITY).toString();
        return returnValue;
    }
}
