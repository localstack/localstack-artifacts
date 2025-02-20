package cloud.localstack.LocalStackClient;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tinkerpop.shaded.jackson.databind.DeserializationFeature;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.json.JsonOutput;

/*
 * Localstack client
 * Will handle all request made to LocalStack
 */
public class LocalStackClient {
    private static final Logger logger = LoggerFactory.getLogger(LocalStackClient.class);
    private static String localstackHost;
    private static ObjectMapper objectMapper = new ObjectMapper();

    public LocalStackClient(String host) {
        localstackHost = host;
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public CheckActionAllowedResponse evaluatePermission(String userName, String regionName, String resourceArn,
            List<String> actions) {
        // TODO simplify this method and extract the request code that can be reusable for other calls
        HttpURLConnection connection = null;

        List<Map<String, String>> requiredPermissions = new ArrayList<Map<String, String>>();
        for (String action : actions) {
            requiredPermissions.add(Map.of("action", action, "resource", resourceArn));
        }
        HashMap<String, Object> body = new HashMap<String, Object>();
        body.put("access_key_id", userName);
        body.put("region_name", regionName);
        body.put("required_permissions", requiredPermissions);

        try {
            String json_payload = JsonOutput.toJson(body);

            // Create connection

            URL url = new URL(String.format("http://%s/_aws/iam/check-actions-allowed", localstackHost));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type",
                    "application/json");

            connection.setRequestProperty("Content-Length",
                    Integer.toString(json_payload.getBytes().length));
            connection.setRequestProperty("Content-Language", "en-US");

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            // Send request
            DataOutputStream wr = new DataOutputStream(
                    connection.getOutputStream());
            wr.writeBytes(json_payload);
            wr.close();

            // Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\n');
            }
            rd.close();

            return objectMapper.readValue(response.toString(), CheckActionAllowedResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
