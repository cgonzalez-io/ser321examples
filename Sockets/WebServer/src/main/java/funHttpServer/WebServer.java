/*
Simple Web Server in Java which allows you to call 
localhost:9000/ and show you the root.html webpage from the www/root.html folder
You can also do some other simple GET requests:
1) /random shows you a random picture (well random from the set defined)
2) json shows you the response as JSON for /random instead the html page
3) /file/filename shows you the raw file (not as HTML)
4) /multiply?num1=3&num2=4 multiplies the two inputs and responses with the result
5) /github?query=users/amehlhase316/repos (or other GitHub repo owners) will lead to receiving
   JSON which will for now only be printed in the console. See the todo below

The reading of the request is done "manually", meaning no library that helps making things a 
little easier is used. This is done so you see exactly how to pars the request and 
write a response back
*/

package funHttpServer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;


class WebServer {

    private static final String API_KEY = System.getenv("OPENWEATHER_API_KEY");

    static {
        if (API_KEY == null || API_KEY.isEmpty()) {
            System.out.println("Error: OPENWEATHER_API_KEY environment variable is not set.");
            System.out.println("Please set it to your OpenWeatherMap API key or leave blank to use mock data.");
        }
    }

    private static final HashMap<String, WeatherCacheEntry> weatherCache = new HashMap<>();

    private static class WeatherCacheEntry {
        String response;
        long timestamp;

        WeatherCacheEntry(String response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }
    }

    public static void main(String[] args) {
        WebServer server = new WebServer(9000);
    }

    /**
     * Main thread
     *
     * @param port to listen on
     */
    public WebServer(int port) {
        ServerSocket server = null;
        Socket sock = null;
        InputStream in = null;
        OutputStream out = null;

        try {
            server = new ServerSocket(port);
            while (true) {
                sock = server.accept();
                out = sock.getOutputStream();
                in = sock.getInputStream();
                byte[] response = createResponse(in);
                out.write(response);
                out.flush();
                in.close();
                out.close();
                sock.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (sock != null) {
                try {
                    server.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Used in the "/random" endpoint
     */
    private final static HashMap<String, String> _images = new HashMap<>() {
        {
            put("streets", "https://iili.io/JV1pSV.jpg");
            put("bread", "https://iili.io/Jj9MWG.jpg");
        }
    };

    private final Random random = new Random();

    /**
     * Reads in socket stream and generates a response
     *
     * @param inStream HTTP input stream from socket
     * @return the byte encoded HTTP response
     */
    public byte[] createResponse(InputStream inStream) {

        byte[] response = null;
        BufferedReader in = null;

        try {

            // Read from socket's input stream. Must use an
            // InputStreamReader to bridge from streams to a reader
            in = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));

            // Get header and save the request from the GET line:
            // example GET format: GET /index.html HTTP/1.1

            String request = null;

            boolean done = false;
            while (!done) {
                String line = in.readLine();

                System.out.println("Received: " + line);

                // find end of header("\n\n")
                if (line == null || line.equals(""))
                    done = true;
                    // parse GET format ("GET <path> HTTP/1.1")
                else if (line.startsWith("GET")) {
                    int firstSpace = line.indexOf(" ");
                    int secondSpace = line.indexOf(" ", firstSpace + 1);

                    // extract the request, basically everything after the GET up to HTTP/1.1
                    request = line.substring(firstSpace + 2, secondSpace);
                }

            }
            System.out.println("FINISHED PARSING HEADER\n");

            // Generate an appropriate response to the user
            if (request == null) {
                response = "<html>Illegal request: no GET</html>".getBytes();
            } else {
                // create output buffer
                StringBuilder builder = new StringBuilder();
                // NOTE: output from buffer is at the end

                if (request.length() == 0) {
                    // shows the default directory page

                    // opens the root.html file
                    String page = new String(readFileInBytes(new File("www/root.html")));
                    // performs a template replacement in the page
                    page = page.replace("${links}", buildFileList());

                    // Generate response
                    builder.append("HTTP/1.1 200 OK\n");
                    builder.append("Content-Type: text/html; charset=utf-8\n");
                    builder.append("\n");
                    builder.append(page);

                } else if (request.equalsIgnoreCase("json")) {
                    // shows the JSON of a random image and sets the header name for that image

                    // pick a index from the map
                    int index = random.nextInt(_images.size());

                    // pull out the information
                    String header = (String) _images.keySet().toArray()[index];
                    String url = _images.get(header);

                    // Generate response
                    builder.append("HTTP/1.1 200 OK\n");
                    builder.append("Content-Type: application/json; charset=utf-8\n");
                    builder.append("\n");
                    builder.append("{");
                    builder.append("\"header\":\"").append(header).append("\",");
                    builder.append("\"image\":\"").append(url).append("\"");
                    builder.append("}");

                } else if (request.equalsIgnoreCase("random")) {
                    // opens the random image page

                    // open the index.html
                    File file = new File("www/index.html");

                    // Generate response
                    builder.append("HTTP/1.1 200 OK\n");
                    builder.append("Content-Type: text/html; charset=utf-8\n");
                    builder.append("\n");
                    builder.append(new String(readFileInBytes(file)));

                } else if (request.contains("file/")) {
                    // tries to find the specified file and shows it or shows an error

                    // take the path and clean it. try to open the file
                    File file = new File(request.replace("file/", ""));

                    // Generate response
                    if (file.exists()) { // success
                        builder.append("HTTP/1.1 200 OK\n");
                        builder.append("Content-Type: text/html; charset=utf-8\n");
                        builder.append("\n");
                        builder.append("Would theoretically be a file but removed this part, you do not have to do anything with it for the assignment");
                    } else { // failure
                        builder.append("HTTP/1.1 404 Not Found\n");
                        builder.append("Content-Type: text/html; charset=utf-8\n");
                        builder.append("\n");
                        builder.append("File not found: " + file);
                    }
                } else if (request.contains("multiply?")) {
                    try {
                        // Split the query string into key-value pairs
                        Map<String, String> queryPairs = splitQuery(request.replace("multiply?", ""));

                        // Check if both parameters exist
                        if (!queryPairs.containsKey("num1") || !queryPairs.containsKey("num2")) {
                            builder.append("HTTP/1.1 400 Bad Request\n");
                            builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                            builder.append("Missing parameters. Please provide both num1 and num2.");
                        } else {
                            try {
                                // Try to parse the parameters as integers
                                int num1 = Integer.parseInt(queryPairs.get("num1"));
                                int num2 = Integer.parseInt(queryPairs.get("num2"));
                                int result = num1 * num2;

                                builder.append("HTTP/1.1 200 OK\n");
                                builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                                builder.append("Result is: " + result);
                            } catch (NumberFormatException ex) {
                                // Handle the case where the parameters are not valid integers
                                builder.append("HTTP/1.1 400 Bad Request\n");
                                builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                                builder.append("Invalid input. Both num1 and num2 must be valid integers.");
                            }
                        }
                    } catch (UnsupportedEncodingException ex) {
                        // Handle any unexpected encoding issues
                        builder.append("HTTP/1.1 500 Internal Server Error\n");
                        builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                        builder.append("Error processing request: " + ex.getMessage());
                    }
                } else if (request.contains("github?")) {
                    try {
                        // Parse the query string and verify that the "query" parameter is present
                        Map<String, String> queryPairs = splitQuery(request.replace("github?", ""));
                        if (!queryPairs.containsKey("query")) {
                            builder.append("HTTP/1.1 400 Bad Request\n");
                            builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                            builder.append("Missing 'query' parameter.");
                        } else {
                            String query = queryPairs.get("query");
                            String json = fetchURL("https://api.github.com/" + query);

                            // Attempt to parse the fetched JSON response as an array
                            JSONArray repos = new JSONArray(json);

                            // Build an HTML response displaying each repository's full_name, id, and owner's login
                            builder.append("HTTP/1.1 200 OK\n");
                            builder.append("Content-Type: text/html; charset=utf-8\n\n");
                            builder.append("<html><body>");
                            builder.append("<h2>GitHub Repositories:</h2><ul>");

                            for (int i = 0; i < repos.length(); i++) {
                                JSONObject repo = repos.getJSONObject(i);
                                String fullName = repo.getString("full_name");
                                int id = repo.getInt("id");
                                String ownerLogin = repo.getJSONObject("owner").getString("login");

                                builder.append("<li>");
                                builder.append("Full Name: " + fullName + "<br>");
                                builder.append("ID: " + id + "<br>");
                                builder.append("Owner: " + ownerLogin);
                                builder.append("</li><br>");
                            }

                            builder.append("</ul></body></html>");
                        }
                    } catch (Exception e) {
                        // If any error occurs (such as network issues or JSON parsing problems), return a 500 error
                        builder.append("HTTP/1.1 500 Internal Server Error\n");
                        builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                        builder.append("Error fetching or parsing GitHub response: " + e.getMessage());
                    }
                } else if (request.contains("greet?")) {
                    try {
                        Map<String, String> queryPairs = splitQuery(request.replace("greet?", ""));
                        if (!queryPairs.containsKey("name") || !queryPairs.containsKey("lang")) {
                            builder.append("HTTP/1.1 400 Bad Request\n");
                            builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                            builder.append("Missing parameters. Usage: /greet?name=Alice&lang=en");
                        } else {
                            String name = queryPairs.get("name");
                            String lang = queryPairs.get("lang").toLowerCase();
                            String greeting;
                            switch (lang) {
                                case "fr":
                                    greeting = "Bonjour";
                                    break;
                                case "es":
                                    greeting = "Hola";
                                    break;
                                case "de":
                                    greeting = "Hallo";
                                    break;
                                case "en":
                                default:
                                    greeting = "Hello";
                                    break;
                            }
                            builder.append("HTTP/1.1 200 OK\n");
                            builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                            builder.append(greeting + ", " + name + "!");
                        }
                    } catch (Exception e) {
                        builder.append("HTTP/1.1 500 Internal Server Error\n");
                        builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                        builder.append("Unexpected error: " + e.getMessage());
                    }
                } else if (request.contains("weather?")) {
                    try {
                        Map<String, String> queryPairs = splitQuery(request.replace("weather?", ""));
                        if (!queryPairs.containsKey("city") || !queryPairs.containsKey("unit")) {
                            builder.append("HTTP/1.1 400 Bad Request\n");
                            builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                            builder.append("Missing parameters. Usage: /weather?city=London&unit=c");
                        } else {
                            String city = queryPairs.get("city");
                            String unit = queryPairs.get("unit").toLowerCase();
                            // Use "metric" for Celsius and "imperial" for Fahrenheit
                            String unitsParam = unit.equals("f") ? "imperial" : "metric";
                            // Build a cache key based on city and units
                            String cacheKey = city.toLowerCase() + "_" + unitsParam;

                            long now = System.currentTimeMillis();
                            String weatherResponse = null;
                            // Check if cached response is available and less than 10 minutes old
                            if (weatherCache.containsKey(cacheKey)) {
                                WeatherCacheEntry entry = weatherCache.get(cacheKey);
                                if (now - entry.timestamp < 600000) {  // 10 minutes in milliseconds
                                    weatherResponse = entry.response;
                                }
                            }

                            // If API key is missing, use mock data; otherwise, make an API call if needed.
                            if (weatherResponse == null) {
                                if (API_KEY == null || API_KEY.isEmpty()) {
                                    // Use a mock response JSON string
                                    weatherResponse = "{\"main\":{\"temp\":20}}";
                                } else {
                                    String url = "http://api.openweathermap.org/data/2.5/weather?q=" +
                                            URLEncoder.encode(city, StandardCharsets.UTF_8) +
                                            "&appid=" + API_KEY +
                                            "&units=" + unitsParam;
                                    weatherResponse = fetchURL(url);
                                }
                                // Cache the response (even the mock one) for consistency
                                weatherCache.put(cacheKey, new WeatherCacheEntry(weatherResponse, now));
                            }

                            // Parse the JSON response from either the API or mock data
                            JSONObject jsonObj = new JSONObject(weatherResponse);
                            double temp = jsonObj.getJSONObject("main").getDouble("temp");
                            String unitSymbol = unit.equals("f") ? "°F" : "°C";

                            builder.append("HTTP/1.1 200 OK\n");
                            builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                            builder.append("The current temperature in " + city + " is " + temp + unitSymbol + ".");
                        }
                    } catch (Exception e) {
                        builder.append("HTTP/1.1 500 Internal Server Error\n");
                        builder.append("Content-Type: text/plain; charset=utf-8\n\n");
                        builder.append("Unexpected error: " + e.getMessage());
                    }
                } else {
                    // if the request is not recognized at all

                    builder.append("HTTP/1.1 400 Bad Request\n");
                    builder.append("Content-Type: text/html; charset=utf-8\n");
                    builder.append("\n");
                    builder.append("I am not sure what you want me to do...");
                }

                // Output
                response = builder.toString().getBytes();
            }
        } catch (IOException e) {
            e.printStackTrace();
            response = ("<html>ERROR: " + e.getMessage() + "</html>").getBytes();
        }

        return response;
    }

    /**
     * Method to read in a query and split it up correctly
     *
     * @param query parameters on path
     * @return Map of all parameters and their specific values
     * @throws UnsupportedEncodingException If the URLs aren't encoded with UTF-8
     */
    public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        // "q=hello+world%2Fme&bob=5"
        String[] pairs = query.split("&");
        // ["q=hello+world%2Fme", "bob=5"]
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
        }
        // {{"q", "hello world/me"}, {"bob","5"}}
        return query_pairs;
    }

    /**
     * Builds an HTML file list from the www directory
     *
     * @return HTML string output of file list
     */
    public static String buildFileList() {
        ArrayList<String> filenames = new ArrayList<>();

        // Creating a File object for directory
        File directoryPath = new File("www/");
        filenames.addAll(Arrays.asList(directoryPath.list()));

        if (filenames.size() > 0) {
            StringBuilder builder = new StringBuilder();
            builder.append("<ul>\n");
            for (var filename : filenames) {
                builder.append("<li>" + filename + "</li>");
            }
            builder.append("</ul>\n");
            return builder.toString();
        } else {
            return "No files in directory";
        }
    }

    /**
     * Read bytes from a file and return them in the byte array. We read in blocks
     * of 512 bytes for efficiency.
     */
    public static byte[] readFileInBytes(File f) throws IOException {

        FileInputStream file = new FileInputStream(f);
        ByteArrayOutputStream data = new ByteArrayOutputStream(file.available());

        byte[] buffer = new byte[512];
        int numRead = file.read(buffer);
        while (numRead > 0) {
            data.write(buffer, 0, numRead);
            numRead = file.read(buffer);
        }
        file.close();

        byte[] result = data.toByteArray();
        data.close();

        return result;
    }

    /**
     * a method to make a web request. Note that this method will block execution
     * for up to 20 seconds while the request is being satisfied. Better to use a
     * non-blocking request.
     *
     * @param aUrl the String indicating the query url for the OMDb api search
     * @return the String result of the http request.
     **/
    public String fetchURL(String aUrl) {
        StringBuilder sb = new StringBuilder();
        URLConnection conn = null;
        InputStreamReader in = null;
        try {
            URL url = new URL(aUrl);
            conn = url.openConnection();
            if (conn != null)
                conn.setReadTimeout(20 * 1000); // timeout in 20 seconds
            if (conn != null && conn.getInputStream() != null) {
                in = new InputStreamReader(conn.getInputStream(), Charset.defaultCharset());
                BufferedReader br = new BufferedReader(in);
                if (br != null) {
                    int ch;
                    // read the next character until end of reader
                    while ((ch = br.read()) != -1) {
                        sb.append((char) ch);
                    }
                    br.close();
                }
            }
            in.close();
        } catch (Exception ex) {
            System.out.println("Exception in url request:" + ex.getMessage());
        }
        return sb.toString();
    }
}
