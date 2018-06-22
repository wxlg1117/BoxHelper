import com.gargoylesoftware.htmlunit.BrowserVersion;
import models.Spider;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import tools.ConvertJson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sun.org.apache.xalan.internal.lib.ExsltDatetime.time;
import static java.lang.Thread.sleep;

/**
 * Created by SpereShelde on 2018/6/6.
 */
public class BoxHelper {

    private Map configures = new HashMap();
    private Map passkeys = new HashMap();
    private HtmlUnitDriver driver = new HtmlUnitDriver(BrowserVersion.CHROME);

    private void getConfigures() {// Get configures from file.

        driver.setJavascriptEnabled(false);
        Logger logger = Logger.getLogger("");
        logger.setLevel(Level.OFF);
        try {
            configures = ConvertJson.convertConfigure("config.json");
        } catch (IOException e) {
            e.printStackTrace();
        }

        ArrayList<Path> jsonFiles = new ArrayList<>();

        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("cookies"));
            for(Path path : stream){
                if (path.getFileName().toString().endsWith(".json")) {
                    jsonFiles.add(path);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (Path path: jsonFiles) {
            System.out.println("Loading " +  path.getFileName().toString() + "...");
            try {
                driver.get("http://" + path.getFileName().toString().substring(0, path.getFileName().toString().length() - 5));
                for (Cookie cookie :ConvertJson.convertCookie(path.toString())) {
                    driver.manage().addCookie(cookie);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Initialization done.");
    }

    private String getMaxDisk(){

        String maxDisk = "/home";
        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("sh -c df -l | awk '{print $4, $5, $6}'");
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String maxLine = null;
                int maxSize = 0;
                int currentSize = 0;
                String line = in.readLine();
                while ((line = in.readLine()) != null) {
                    currentSize = Integer.parseInt(line.substring(0, line.indexOf(" ")));
                    if (currentSize > maxSize){
                        maxSize = currentSize;
                        maxLine = line;
                    }
                }
                maxDisk = maxLine.substring(maxLine.lastIndexOf(" ") + 1);
            } catch (Exception e) {
                System.out.println("Cannot get max disk 1.");
            } finally {
                in.close();
                System.exit(107);
            }
        } catch (Exception e) {
            System.out.println("Cannot get max disk 2.");
            System.exit(108);
        }
        return maxDisk;
    }

    private boolean canContinue(String disk, int limit){
        boolean flag = true;

        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("sh -c df -l | grep " + disk + " | awk '{print $5, $6}'");
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = in.readLine();
                if (Integer.parseInt(line.substring(0, line.indexOf("%"))) < limit) flag = false;
            } catch (Exception e) {
                System.out.println("Cannot restrict 1.");
            } finally {
                in.close();
                System.exit(109);
            }
        } catch (Exception e) {
            System.out.println("Cannot restrict 2.");
            System.exit(110);
        }

        return flag;
    }

    public static void main(String[] args) {


        BoxHelper boxHelper = new BoxHelper();
        boxHelper.getConfigures();
        int type = 0;
        if ("true".equals(boxHelper.configures.get("isFree").toString())){
            type += 1;
        }
        if ("true".equals(boxHelper.configures.get("isSticky").toString())) {
            type += 2;
        }
        String maxDisk = "";
        int limit = Integer.parseInt(boxHelper.configures.get("diskLimit").toString());
        if (limit != -1 && limit != 0) {
            maxDisk = boxHelper.getMaxDisk();
        }
        int cpuThreads = Runtime.getRuntime().availableProcessors();
        int count  = 1;
        ArrayList<Spider> spiders = new ArrayList<>();
        ArrayList<String> urls = (ArrayList<String>) boxHelper.configures.get("urls");
        for (String url: urls){
            spiders.add(new Spider(url.substring(url.indexOf("//") + 2, url.indexOf("/", 8)), url, boxHelper.configures.get("path").toString(), type, Double.parseDouble(boxHelper.configures.get("min").toString()), Double.parseDouble(boxHelper.configures.get("max").toString()), boxHelper.driver));
        }
        while (true){
            if (limit != -1 && limit != 0) {
                if (!boxHelper.canContinue(maxDisk, limit)){
                    System.out.println("Reached limit, exit.");
                    System.exit(111);
                }
            }
            ExecutorService executorService = Executors.newFixedThreadPool(cpuThreads);
            System.out.println("\nBoxHelper " + count + " begins at " + time());

            for (Spider spider: spiders) {
                executorService.execute(spider);
            }
            executorService.shutdown();
            try {
                sleep((long) (1000*60*Double.valueOf(boxHelper.configures.get("cycle").toString())));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            count++;
        }
    }
}
