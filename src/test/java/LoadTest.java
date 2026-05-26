import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.io.File;

class ResponseDetails {
    private final String responseCode;
    private final long responseTime;

    public ResponseDetails(String responseCode, long responseTime) {
        this.responseCode = responseCode;
        this.responseTime = responseTime;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public long getResponseTime() {
        return responseTime;
    } // detay ayrıca alınmadan rapor detayına yazılamadı
}

public class LoadTest {
    private String url;
    private int users;
    private int rampUpTime;
    private int loopCount;
    private int requestsPerSecond;
    private String reportDirectory;
    private String headers;
    private String body;
    private String httpMethod;
    private String authHeader;
    private volatile boolean shouldStop = false;
    private long testStartTime;

    public interface TestProgressCallback {
        void onProgress(String message);
        void onComplete(List<ResponseDetails> responseDetails);
    }

    public void configure(String url, int users, int rampUpTime, int loopCount, 
                         int requestsPerSecond, String reportDirectory, 
                         String headers, String body, String httpMethod, String authHeader) {
        this.url = url;
        this.users = users;
        this.rampUpTime = rampUpTime;
        this.loopCount = loopCount;
        this.requestsPerSecond = requestsPerSecond;
        this.reportDirectory = reportDirectory;
        this.headers = headers;
        this.body = body;
        this.httpMethod = httpMethod;
        this.authHeader = authHeader;
    }

    public void stopTest() {
        shouldStop = true;
    }

    public void runTest(TestProgressCallback callback) {
        // Create report directory
        try {
            Files.createDirectories(Paths.get(reportDirectory));
        } catch (IOException e) {
            callback.onProgress("Error creating report directory: " + e.getMessage());
            return;
        }

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(users);
        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicLong successfulResponses = new AtomicLong(0);
        AtomicLong failedResponses = new AtomicLong(0);
        AtomicLong totalRequestsCount = new AtomicLong(0);
        List<ResponseDetails> responseDetailsList = new CopyOnWriteArrayList<>();

        testStartTime = System.currentTimeMillis(); // Set test start time

        // Calculate total requests per user
        int totalRequestsPerUser = loopCount * requestsPerSecond;
        int expectedTotalRequests = users * totalRequestsPerUser;
        
        // Calculate delays
        long delayBetweenRequests = Math.max(1, 1000L / requestsPerSecond);// milliseconds between requests
        long rampUpDelay; // milliseconds between user starts
        if (rampUpTime == 0) rampUpDelay = 0;
        else rampUpDelay = rampUpTime * 1000L / users;

        // Create a CountDownLatch to track completion of all requests
        CountDownLatch completionLatch = new CountDownLatch(expectedTotalRequests);

        callback.onProgress("Starting test with " + users + " users...");
        callback.onProgress("Total requests per user: " + totalRequestsPerUser);
        callback.onProgress("Total requests: " + expectedTotalRequests);

        // Schedule requests for each user
        for (int i = 0; i < users; i++) {
            final int userIndex = i;
            executor.schedule(() -> {
                for (int j = 0; j < totalRequestsPerUser; j++) {
                    if (shouldStop) {
                        callback.onProgress("Test stopped by user");
                        break;
                    }

                    long requestStartTime = System.currentTimeMillis();
                    ResponseDetails responseDetails = performRequest();
                    long requestEndTime = System.currentTimeMillis();
                    long responseTime = requestEndTime - requestStartTime;

                    totalRequestsCount.incrementAndGet();

                    if (responseDetails.getResponseCode().equals("200")) {
                        successfulResponses.incrementAndGet();
                    } else {
                        failedResponses.incrementAndGet();
                    }

                    totalResponseTime.addAndGet(responseTime);
                    responseDetailsList.add(new ResponseDetails(responseDetails.getResponseCode(), responseTime));

                    callback.onProgress(String.format("User %d - Request %d/%d completed with status %s in %d ms",
                            userIndex + 1, j + 1, totalRequestsPerUser, responseDetails.getResponseCode(), responseTime));

                    try {
                        Thread.sleep(delayBetweenRequests);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    
                    completionLatch.countDown();
                }
            }, userIndex * rampUpDelay, TimeUnit.MILLISECONDS);
        }

        try {
            completionLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        shutdownExecutor(executor);

        long testEndTime = System.currentTimeMillis();
        long duration = testEndTime - testStartTime;
        generateSummaryReport(successfulResponses.get(), failedResponses.get(), 
                            totalResponseTime.get(), duration, totalRequestsCount.get());
        generateDetailedReport(responseDetailsList);
        
        callback.onProgress("Test completed. Reports generated in: " + reportDirectory);
        callback.onComplete(responseDetailsList);
    }

    private ResponseDetails performRequest() {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpRequestBase request;
            
            // Create request based on HTTP method
            switch (httpMethod) {
                case "GET":
                    request = new HttpGet(url);
                    break;
                case "POST":
                    HttpPost post = new HttpPost(url);
                    post.setEntity(new StringEntity(body));
                    request = post;
                    break;
                case "PUT":
                    HttpPut put = new HttpPut(url);
                    put.setEntity(new StringEntity(body));
                    request = put;
                    break;
                case "PATCH":
                    HttpPatch patch = new HttpPatch(url);
                    patch.setEntity(new StringEntity(body));
                    request = patch;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
            }
            
            // Add headers
            String[] headerLines = headers.split("\n");
            for (String headerLine : headerLines) {
                String[] parts = headerLine.split(":", 2);
                if (parts.length == 2) {
                    request.setHeader(parts[0].trim(), parts[1].trim());
                }
            }
            
            // Add authorization header if present
            if (authHeader != null && !authHeader.isEmpty()) {
                request.setHeader("Authorization", authHeader);
            }
            
            long startTime = System.currentTimeMillis();
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                long endTime = System.currentTimeMillis();
                return new ResponseDetails(
                    String.valueOf(response.getStatusLine().getStatusCode()),
                    endTime - startTime
                );
            }
        } catch (IOException e) {
            return new ResponseDetails("Request failed: " + e.getMessage(), 0);
        }
    }

    private void generateSummaryReport(long successfulResponses, long failedResponses, 
                                     long totalResponseTime, long duration, long totalRequests) {
        try {
            String summaryPath = Paths.get(reportDirectory, "summary_report.txt").toString();
            StringBuilder report = new StringBuilder();
            report.append("Load Test Summary Report\n");
            report.append("=======================\n\n");
            report.append("Test Configuration:\n");
            report.append("------------------\n");
            report.append("URL: ").append(url).append("\n");
            report.append("HTTP Method: ").append(httpMethod).append("\n");
            report.append("Number of Users: ").append(users).append("\n");
            report.append("Ramp-up Time: ").append(rampUpTime).append(" seconds\n");
            report.append("Loop Count: ").append(loopCount).append("\n");
            report.append("Requests per Second: ").append(requestsPerSecond).append("\n\n");
            
            report.append("Test Results:\n");
            report.append("-------------\n");
            report.append("Test Start Time: ").append(new java.util.Date(testStartTime)).append("\n");
            report.append("Test End Time: ").append(new java.util.Date(testStartTime + duration)).append("\n");
            report.append("Test Duration: ").append(String.format("%.2f", duration / 1000.0)).append(" seconds\n");
            report.append("Total Requests: ").append(totalRequests).append("\n");
            report.append("Successful Requests: ").append(successfulResponses).append("\n");
            report.append("Failed Requests: ").append(failedResponses).append("\n");
            report.append("Average Response Time: ").append(String.format("%.2f", totalResponseTime / (double)totalRequests)).append(" ms\n");
            
            Files.write(Paths.get(summaryPath), report.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateDetailedReport(List<ResponseDetails> responseDetailsList) {
        StringBuilder detailedReportBuilder = new StringBuilder();
        detailedReportBuilder.append("Detailed Report:\n");
        detailedReportBuilder.append("Test Start Time: ").append(new java.util.Date(testStartTime)).append("\n");
        detailedReportBuilder.append("Test End Time: ").append(new java.util.Date(testStartTime + (System.currentTimeMillis() - testStartTime))).append("\n");
        detailedReportBuilder.append("Test Duration: ").append(String.format("%.2f", (System.currentTimeMillis() - testStartTime) / 1000.0)).append(" seconds\n\n");

        for (ResponseDetails details : responseDetailsList) {
            detailedReportBuilder.append("Response Code: ").append(details.getResponseCode())
                    .append(" | Response Time: ").append(details.getResponseTime()).append(" ms\n");
        }

        String detailedReportPath = reportDirectory + File.separator + "detailed_report.txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(detailedReportPath))) {
            writer.write(detailedReportBuilder.toString());
        } catch (IOException e) {
            System.err.println("Failed to save detailed report: " + e.getMessage());
        }
    }

    private void shutdownExecutor(ScheduledExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } // timeout
    }

    public static void main(String[] args) {
        // Default configuration
        LoadTest loadTest = new LoadTest();


        // Run test with console output
        loadTest.runTest(new TestProgressCallback() {
            @Override
            public void onProgress(String message) {
                System.out.println(message);
            }

            @Override
            public void onComplete(List<ResponseDetails> responseDetails) {
                System.out.println("\nTest completed!");
                System.out.println("Total requests: " + responseDetails.size());
                
                // Calculate statistics
                long totalTime = 0;
                long minTime = Long.MAX_VALUE;
                long maxTime = 0;
                int successCount = 0;
                
                for (ResponseDetails detail : responseDetails) {
                    long responseTime = detail.getResponseTime();
                    totalTime += responseTime;
                    minTime = Math.min(minTime, responseTime);
                    maxTime = Math.max(maxTime, responseTime);
                    if (detail.getResponseCode().equals("200")) {
                        successCount++;
                    }
                }
                
                // Print statistics
                System.out.println("\nTest Statistics:");
                System.out.println("Successful requests: " + successCount);
                System.out.println("Failed requests: " + (responseDetails.size() - successCount));
                System.out.println("Min response time: " + minTime + " ms");
                System.out.println("Max response time: " + maxTime + " ms");
                System.out.println("Average response time: " + 
                    String.format("%.2f ms", (double)totalTime / responseDetails.size()));
            }
        });
    }
}