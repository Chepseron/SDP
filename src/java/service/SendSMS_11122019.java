package service;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;

/**
 *
 * @author Amon.Sabul
 */
@WebServlet(name = "SendSMS_11122019", urlPatterns = {"/SendSMS_11122019"}, initParams = {
    @WebInitParam(name = "receiver", value = "")
    , @WebInitParam(name = "message", value = "")
    , @WebInitParam(name = "serviceName", value = "")})

public class SendSMS_11122019 extends HttpServlet {

    MongoDatabase db;
    String absolutePath = "/TOKEN";
    String absolutePath2 = "/LOGS";

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println(sendSms(request.getParameter("receiver"), request.getParameter("serviceName"), request.getParameter("message")));
        }
    }

    public SendSMS_11122019() {
        try {
            Path currentRelativePath = Paths.get("");
            String s = currentRelativePath.toAbsolutePath().toString();
            String path = s + "/application.properties";
            Properties prop = load(path);
            MongoClient mongoClient = new MongoClient(
                    new MongoClientURI("mongodb://" + prop.getProperty("MONGO_HOST") + ":" + prop.getProperty("MONGO_PORT") + "/?readConcernLevel=majority"));
            db = mongoClient.getDatabase(prop.getProperty("MONGO_DB"));
        } catch (Exception ex) {
            Logger.getLogger(SendSMS_11122019.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Map sendSms(String recipients, String serviceName, String message) {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
        String strDate = formatter.format(date);

        try {
            Map<String, String> map = new HashMap<>();
            String filename = absolutePath2 + "/" + strDate + "/" + serviceName;

            String currentToken = null;
            StringBuilder br = new StringBuilder();
            FileReader fileReader = new FileReader(absolutePath + "/token.txt");
            int ch = fileReader.read();
            while (ch != -1) {
                System.out.print((char) ch);
                br.append((char) ch);
                fileReader.close();
            }

            currentToken = br.toString();
            String json = "{"
                    + "\"timeStamp\":\"" + new java.util.Date()
                    + "\",\"dataSet\":"
                    + "[{\"userName\":\"craftsiliconapi\","
                    + "\"channel\":\"SMS\","
                    + "\"packageId\":\"4629\","
                    + "\"oa\":\"" + serviceName + "\","
                    + "\"msisdn\": \"" + recipients
                    + "\",\"message\": \"" + message
                    + "\",\"uniqueId\": \"" + new java.util.Date()
                    + "\",\"actionResponseURL\": \"https://posthere.io/757b-47bb-8d0c\""
                    + "}]"
                    + "}";

            String status = null;
            String keyword = null;
            String statusCode = null;
            HttpURLConnection connection = (HttpURLConnection) (new URL("https://dsvc.safaricom.com:9480/api/public/CMS/bulksms")).openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("Authorization", "Bearer " + currentToken);
            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(json);
            int code = connection.getResponseCode();
            BufferedReader bufferedReader;

            if (code > 199 && code < 300) {
                bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                System.out.println(content.toString());
                try {
                    FileWriter fw = new FileWriter(filename, true);
                    JSONObject obj = new JSONObject(content.toString());
                    status = obj.getString("status");
                    keyword = obj.getString("keyword");
                    statusCode = obj.getString("statusCode");

                    fw.write("SMS sent " + new java.util.Date() + "++++++++++" + "\n");
                    fw.write(json + "\n");
                    fw.write("+++++++++++Results+++++++++++=" + "\n");
                    fw.write(content.toString() + "\n");
                    fw.close();
                } catch (IOException ioe) {
                    System.err.println("IOException: " + ioe.getMessage());
                }

                if (status.equalsIgnoreCase("SUCCESS")) {
                    map.put("ResultCode", "0");
                    map.put("ResultDesc", "Operation Successful");
                    map.put("ResultDetails", content.toString());

                    DBCollection coll = (DBCollection) db.getCollection("SMS");
                    BasicDBObject doc = new BasicDBObject("ResultCode", "0")
                            .append("serviceName", serviceName)
                            .append("ResultDesc", "Operation Successful")
                            .append("ResultDetails", content.toString());
                    coll.insert(doc);
                } else {
                    map.put("ResultCode", "2");
                    map.put("ResultDesc", "Error");
                    map.put("ResultDetails", status);

                    DBCollection coll = (DBCollection) db.getCollection("SMS");
                    BasicDBObject doc = new BasicDBObject("ResultCode", "2")
                            .append("serviceName", serviceName)
                            .append("ResultDesc", "Error")
                            .append("ResultDetails", json);
                    coll.insert(doc);
                }

            } else {
                bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                System.out.println(content.toString());
                try {
                    FileWriter fw = new FileWriter(filename, true);

                    fw.write("SMS sent " + new java.util.Date() + "++++++++++" + "\n");
                    fw.write(json + "\n");
                    fw.write("+++++++++++Results+++++++++++=" + "\n");
                    fw.write(content.toString() + "\n");
                    fw.close();
                } catch (IOException ioe) {
                }
                map.put("ResultCode", "2");
                map.put("ResultDesc", "Error");
                map.put("ResultDetails", content.toString());

                DBCollection coll = (DBCollection) db.getCollection("SMS");
                BasicDBObject doc = new BasicDBObject("ResultCode", "2")
                        .append("serviceName", serviceName)
                        .append("ResultDesc", "Error")
                        .append("ResultDetails", json);
                coll.insert(doc);
            }
            bufferedReader.close();
            out.flush();
            out.close();
            connection.disconnect();
            return map;
        } catch (Exception ex) {
            Logger.getLogger(SendSMS_11122019.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public String getToken(String username, String password) {
        try {
            String token = null;
            String json = "{\n\"username\": \"" + username + "\",\n\"password\":\"" + password + "\"\n}";
            HttpURLConnection connection = (HttpURLConnection) (new URL("https://dtsvc.safaricom.com:9480/api/auth/login")).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(json);
            int code = connection.getResponseCode();
            BufferedReader bufferedReader;

            if (code > 199 && code < 300) {
                bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                System.out.println(content.toString());
                boolean success = new File(absolutePath + "/token.txt").delete();
                try (FileWriter fileWriter = new FileWriter(absolutePath + "/token.txt")) {
                    JSONObject obj = new JSONObject(content.toString());
                    token = obj.getString("token");
                    fileWriter.write(token);
                    fileWriter.close();
                } catch (IOException e) {
                }
            } else {
                bufferedReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            bufferedReader.close();
            out.close();
            connection.disconnect();
            return token;
        } catch (Exception ex) {
            Logger.getLogger(SendSMS_11122019.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public String refreshToken(String currentToken) {
        try {

            String token = null;
            HttpURLConnection connection = (HttpURLConnection) (new URL("https://dtsvc.safaricom.com:9480/api/auth/RefreshToken")).openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("Authorization", "Bearer " + currentToken);
            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(currentToken);

            int code = connection.getResponseCode();
            BufferedReader bufferedReader;

            if (code > 199 && code < 300) {
                bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                System.out.println(content.toString());
                boolean success = new File(absolutePath + "/token.txt").delete();
                try (FileWriter fileWriter = new FileWriter(absolutePath + "/token.txt")) {
                    JSONObject obj = new JSONObject(content.toString());
                    token = obj.getString("token");
                    fileWriter.write(token);
                    fileWriter.close();
                } catch (IOException e) {
                }
            } else {
                bufferedReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            bufferedReader.close();
            out.flush();
            out.close();
            connection.disconnect();
            return token;
        } catch (Exception ex) {
            Logger.getLogger(SendSMS_11122019.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static Properties load(String filename) throws Exception {
        Properties properties = new Properties();
        FileInputStream input = new FileInputStream(filename);
        try {
            properties.load(input);
            return properties;
        } finally {
            input.close();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    public String getServletInfo() {
        return "Short description";
    }

}
