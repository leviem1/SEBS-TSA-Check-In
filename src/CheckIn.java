import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckIn {
    public static void main(String[] args) {
        URL website;
        Integer interval;
        String searchTerm;
        ReadableByteChannel rbc;
        final int[] originalCount = {0};
        final String[] originalHash = {""};
        Scanner reader = new Scanner(System.in);
        File temp = new File("http_download");

        while (true) {
            try {
                System.out.print("Enter url of the page that you want to monitor:\n");
                String rawUrl = reader.nextLine();
                if (!rawUrl.contains("http")) rawUrl = "http://" + rawUrl;
                website = new URL(rawUrl);
                rbc = Channels.newChannel(website.openStream());
                rbc.close();
                break;
            } catch (IOException e) {
                System.out.println("URL Invalid!");
            }
        }

        System.out.println("Monitored URL is set to " + website.toString() + "\n");

        while (true) {
            try {
                System.out.print("Enter the number of seconds to wait between check-ins [30]: ");
                String rawInterval = reader.nextLine();

                if (rawInterval.isEmpty()) {
                    interval = 30;
                } else {
                    interval = Integer.parseInt(rawInterval);
                }

                if (interval > 0) break;
            } catch (NumberFormatException ignored) {}

            System.out.println("Invalid number");
        }

        System.out.println("Interval is set to " + interval + " seconds\n");
        interval *= 1000;

        while (true) {
            System.out.print("Enter the term to search for: ");
            searchTerm = reader.nextLine();

            if (!searchTerm.isEmpty()) {
                break;
            }
        }

        System.out.println("Search term set to \"" + searchTerm + "\"");

        try {
            temp = File.createTempFile("html_download", ".tmp");
            temp.deleteOnExit();
        } catch (IOException ioe) {
            System.out.println("Cannot create temp file, using " + System.getProperty("user.dir") + File.separator + "http_download instead");
        }

        try {
            updateFile(temp, website);
            originalHash[0] = getMD5Hash(temp);
            originalCount[0] = getNumberOfInstances(temp, searchTerm);
        } catch (IOException ioe) {
            System.out.println("Unable to retrieve initial hash!\nExiting...");
            System.exit(0);
        }

        System.out.println("Hash acquired successfully, starting monitoring services...");

        File finalTemp = temp;
        URL finalWebsite = website;
        String finalSearchTerm = searchTerm;
        TimerTask checkIn = new TimerTask() {
            @Override
            public void run() {
                try {
                    updateFile(finalTemp, finalWebsite);
                    String newHash = getMD5Hash(finalTemp);

                    if (newHash.equals(originalHash[0])) {
                        System.out.println("Checked-in at " + new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a").format(new Date()) + " with no new results");
                    } else {
                        originalHash[0] = newHash;
                        int newCount = getNumberOfInstances(finalTemp, finalSearchTerm);

                        if (newCount > originalCount[0]) {
                            System.out.println("THERE ARE MORE OCCURRENCES OF \"" + finalSearchTerm + "\" AS OF " + new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a").format(new Date()));
                            //email stuff
                            originalCount[0] = newCount;
                        } else {
                            System.out.println("Checked-in at " + new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a").format(new Date()) + " and found changes, but no matches");
                        }
                    }
                } catch (IOException ioe) {
                    System.out.println("Check-in FAILURE at " + new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a").format(new Date()));
                }
            }
        };

        Timer checkInTimer = new Timer();
        checkInTimer.scheduleAtFixedRate(checkIn, interval, interval);

    }

    private static int getNumberOfInstances(File temp, String searchTerm) throws IOException {
        int matchCount = 0;
        Pattern p = Pattern.compile(searchTerm);

        try (
                BufferedReader br = new BufferedReader(new FileReader(temp))
                ) {
            String line;

            while ((line = br.readLine()) != null) {
                Matcher m = p.matcher(line);
                while (m.find()) {
                    matchCount++;
                }
            }
        }

        return matchCount;
    }

    private static void updateFile(File temp, URL website) throws IOException {
        try (
                FileOutputStream fos = new FileOutputStream(temp);
                ReadableByteChannel rbc = Channels.newChannel(website.openStream())
        ) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }

    private static String getMD5Hash(File temp) throws IOException {
        StringBuilder hash = new StringBuilder("");

        try (
                FileInputStream fis = new FileInputStream(temp)
        ) {
            MessageDigest md = MessageDigest.getInstance("MD5");

            int br;
            byte[] data = new byte[1024];

            while ((br = fis.read(data, 0, data.length)) != -1) {
                md.update(data, 0, br);
            }

            byte[] mdData = md.digest();

            for (byte aMdData : mdData) {
                hash.append(Integer.toString((aMdData & 0xff) + 0x100, 16).substring(1));
            }
        } catch (NoSuchAlgorithmException ignored) {}

        return hash.toString();
    }
}
