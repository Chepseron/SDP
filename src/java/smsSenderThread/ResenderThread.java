package smsSenderThread;

import com.mongodb.BasicDBObject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import com.mongodb.DBCollection;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.json.JSONException;
import org.json.JSONObject;
import service.SendSMS_11122019;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import static service.SendSMS_11122019.load;

public class ResenderThread extends Thread implements ServletContextListener {

    MongoDatabase db;
    String absolutePath = "/TOKEN";
    String absolutePath2 = "/LOGS";

    public ResenderThread() {

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

    public void run() {
        MongoCollection coll = db.getCollection("SMS");
        BasicDBObject whereQuery = new BasicDBObject();
        whereQuery.put("ResultCode", "2");
        FindIterable cursor = coll.find(whereQuery);
        while (cursor.iterator().hasNext()) {
            try {
                System.out.println(cursor.cursor().next());
                JSONObject obj = new JSONObject(cursor.cursor().next());
                sendSms(obj.getString("ResultDetails"), obj.getString("serviceName"));
            } catch (JSONException ex) {
                Logger.getLogger(ResenderThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public Map sendSms(String json, String serviceName) {
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
                    JsonWriterSettings prettyPrint = JsonWriterSettings.builder().indent(true).build();
                    try (MongoClient mongoClient = new MongoClient("127.17.20.50", 27017)) {
                        MongoDatabase sampleTrainingDB = mongoClient.getDatabase("SMS");
                        MongoCollection<Document> gradesCollection = sampleTrainingDB.getCollection("SMS");
                        Bson filter = eq("ResultDetails", content.toString());
                        Bson updateOperation = set("ResultCode", "0");
                        UpdateResult updateResult = gradesCollection.updateOne(filter, updateOperation);
                        System.out.println(gradesCollection.find(filter).first().toJson(prettyPrint));
                        System.out.println(updateResult);
                    }
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
            Map<String, String> map = new HashMap<>();
            Logger.getLogger(SendSMS_11122019.class.getName()).log(Level.SEVERE, null, ex);
            map.put("ResultCode", "2");
            map.put("ResultDesc", "Error");
            map.put("ResultDetails", ex.getMessage());
            return map;
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {

        try {
            TimerTask task = new TimerTask() {
                public void run() {
                    try {
                        SendSMS_11122019 refreshTok = new SendSMS_11122019();
                        System.out.println("Task performed on: " + new Date() + "n" + "Thread's name: " + Thread.currentThread().getName());
                        String currentToken = new String();
                        StringBuilder br = new StringBuilder();
                        FileReader fileReader = new FileReader(absolutePath + "/token.txt");
                        int ch = fileReader.read();
                        while (ch != -1) {
                            br.append((char) ch);
                            fileReader.close();
                        }
                        currentToken = br.toString();
                        refreshTok.refreshToken(currentToken);
                    } catch (Exception ex) {
                        Logger.getLogger(SendSMS_11122019.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };
            Timer timer = new Timer("Timer");
            long delay = 50 * 60 * 1000;
            timer.schedule(task, delay);

            ResenderThread thread = new ResenderThread();
            thread.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
