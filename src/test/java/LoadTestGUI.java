import com.itextpdf.io.source.ByteArrayOutputStream;
import com.itextpdf.text.Image;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadTestGUI extends JFrame {
    private JTextField urlField;
    private JTextField usersField;
    private JTextField rampUpTimeField;
    private JTextField loopCountField;
    private JTextField requestsPerSecondField;
    private JTextField reportDirectoryField;
    private JTextArea bodyArea;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JButton selectBodyFileButton;
    private JButton selectReportDirButton;
    private JTabbedPane tabbedPane;
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private ChartPanel responseTimeChart;
    private ChartPanel throughputChart;
    private JPanel statsPanel;
    private JPanel resultsPanel;
    private JPanel headersPanel;
    private JPanel paramsPanel;
    private JPanel authPanel;
    private JComboBox<String> httpMethodCombo;
    private JComboBox<String> authTypeCombo;
    private JTextField authTokenField;
    private static final String CONFIG_FILE = "loadtest_config.json";
    private JSONObject savedConfig;
    private LoadTest currentTest;
    private volatile boolean isTestRunning = false;
    private long testStartTime;

    public LoadTestGUI() {
        try {
            setTitle("Loader");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1200, 800);
            setMinimumSize(new Dimension(1200, 800));

            initializeComponents();

            JPanel mainPanel = new JPanel(new BorderLayout());

            tabbedPane = new JTabbedPane();
            tabbedPane.addTab("Configuration", createConfigurationPanel());
            tabbedPane.addTab("Results", createResultsPanel());
            mainPanel.add(tabbedPane, BorderLayout.CENTER);

            add(mainPanel);

            setupActionListeners();

            setLocationRelativeTo(null);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error initializing GUI: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initializeComponents() {
        // Initialize all text fields
        urlField = new JTextField();
        usersField = new JTextField();
        rampUpTimeField = new JTextField();
        loopCountField = new JTextField();
        requestsPerSecondField = new JTextField();
        reportDirectoryField = new JTextField();
        authTokenField = new JTextField();

        // Initialize text areas
        bodyArea = new JTextArea();
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        // Initialize panels
        headersPanel = new JPanel();
        headersPanel.setLayout(new BoxLayout(headersPanel, BoxLayout.Y_AXIS));

        paramsPanel = new JPanel();
        paramsPanel.setLayout(new BoxLayout(paramsPanel, BoxLayout.Y_AXIS));
        paramsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        authPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Initialize HTTP method combo box
        httpMethodCombo = new JComboBox<>(new String[]{"GET", "POST", "PUT", "PATCH"});

        // Initialize auth type combo box
        authTypeCombo = new JComboBox<>(new String[]{"None", "Bearer", "JWT", "Basic"});

        // Initialize buttons
        startButton = new JButton("Start Test");
        stopButton = new JButton("Stop Test");
        stopButton.setEnabled(false);
        selectBodyFileButton = new JButton("Select Body File");
        selectReportDirButton = new JButton("Select Directory");

        // Initialize table
        tableModel = new DefaultTableModel(
            new String[]{"Request #", "Response Time (ms)", "Status Code", "Success"},
            0
        );
        resultsTable = new JTable(tableModel);

        // Setup auth panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        authPanel.add(new JLabel("Auth Type:"), gbc);
        gbc.gridx = 1;
        authPanel.add(authTypeCombo, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        authPanel.add(new JLabel("Token:"), gbc);
        gbc.gridx = 1;
        authPanel.add(authTokenField, gbc);
    }

    private void loadConfiguration() {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Load Configuration");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));

            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                String content = new String(Files.readAllBytes(selectedFile.toPath()));
                JSONObject config = new JSONObject(content);

                // Load values into fields
                urlField.setText(config.optString("url", ""));
                usersField.setText(config.optString("users", ""));
                rampUpTimeField.setText(config.optString("rampUpTime", ""));
                loopCountField.setText(config.optString("loopCount", ""));
                requestsPerSecondField.setText(config.optString("requestsPerSecond", ""));
                reportDirectoryField.setText(config.optString("reportDirectory", ""));
                bodyArea.setText(config.optString("body", ""));

                // Load headers
                headersPanel.removeAll();
                JSONArray headers = config.optJSONArray("headers");
                if (headers != null) {
                    for (int i = 0; i < headers.length(); i++) {
                        JSONObject header = headers.getJSONObject(i);
                        addHeaderRow(headersPanel,
                            header.optString("key", ""),
                            header.optString("value", ""));
                    }
                }
                if (headersPanel.getComponentCount() == 0) {
                    addHeaderRow(headersPanel);
                }

                headersPanel.revalidate();
                headersPanel.repaint();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading configuration: " + e.getMessage());
        }
    }

    private void saveConfiguration() {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Configuration");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
            fileChooser.setSelectedFile(new File("loadtest_config.json"));

            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                if (!selectedFile.getName().toLowerCase().endsWith(".json")) {
                    selectedFile = new File(selectedFile.getAbsolutePath() + ".json");
                }

                JSONObject config = new JSONObject();
                config.put("url", urlField.getText());
                config.put("users", usersField.getText());
                config.put("rampUpTime", rampUpTimeField.getText());
                config.put("loopCount", loopCountField.getText());
                config.put("requestsPerSecond", requestsPerSecondField.getText());
                config.put("reportDirectory", reportDirectoryField.getText());
                config.put("body", bodyArea.getText());

                // Save headers
                JSONArray headers = new JSONArray();
                Component[] components = headersPanel.getComponents();
                for (Component comp : components) {
                    if (comp instanceof JPanel) {
                        JPanel rowPanel = (JPanel) comp;
                        Component[] rowComponents = rowPanel.getComponents();
                        if (rowComponents.length >= 2) {
                            // Get key panel and its text field
                            JPanel keyPanel = (JPanel) rowComponents[0];
                            JTextField keyField = (JTextField) keyPanel.getComponent(1);

                            // Get value panel and its text field
                            JPanel valuePanel = (JPanel) rowComponents[1];
                            JTextField valueField = (JTextField) valuePanel.getComponent(1);

                            String key = keyField.getText().trim();
                            String value = valueField.getText().trim();

                            if (!key.isEmpty() && !value.isEmpty()) {
                                JSONObject header = new JSONObject();
                                header.put("key", key);
                                header.put("value", value);
                                headers.put(header);
                            }
                        }
                    }
                }
                config.put("headers", headers);

                Files.write(selectedFile.toPath(), config.toString(2).getBytes());
                JOptionPane.showMessageDialog(this, "Configuration saved successfully to: " + selectedFile.getAbsolutePath());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error saving configuration: " + e.getMessage());
        }
    }

    private void addHeaderRow(JPanel panel) {
        addHeaderRow(panel, "", "");
    }

    private void addHeaderRow(JPanel panel, String key, String value) {
        JPanel rowPanel = new JPanel(new BorderLayout(10, 0)); // Increased horizontal gap
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50)); // Increased height

        JTextField keyField = new JTextField(key);
        JTextField valueField = new JTextField(value);
        JButton removeButton = new JButton("X");

        keyField.setPreferredSize(new Dimension(400, 5)); // Increased size
        valueField.setPreferredSize(new Dimension(800, 5)); // Increased size
        removeButton.setPreferredSize(new Dimension(50, 5)); // Increased size

        // Add labels
        JPanel keyPanel = new JPanel(new BorderLayout(5, 0));
        keyPanel.add(new JLabel("Key: "), BorderLayout.WEST);
        keyPanel.add(keyField, BorderLayout.CENTER);

        JPanel valuePanel = new JPanel(new BorderLayout(5, 0));
        valuePanel.add(new JLabel("Value: "), BorderLayout.WEST);
        valuePanel.add(valueField, BorderLayout.CENTER);

        rowPanel.add(keyPanel, BorderLayout.WEST);
        rowPanel.add(valuePanel, BorderLayout.CENTER);
        rowPanel.add(removeButton, BorderLayout.EAST);

        removeButton.addActionListener(e -> {
            panel.remove(rowPanel);
            panel.revalidate();
            panel.repaint();
        });

        panel.add(rowPanel);
        panel.add(Box.createVerticalStrut(15)); // Increased spacing
        panel.revalidate();
        panel.repaint();
    }

    private String getHeadersText() {
        StringBuilder headers = new StringBuilder();
        Component[] components = headersPanel.getComponents();

        for (Component comp : components) {
            if (comp instanceof JPanel) {
                JPanel rowPanel = (JPanel) comp;
                Component[] rowComponents = rowPanel.getComponents();
                if (rowComponents.length >= 2) {
                    // Get key panel and its text field
                    JPanel keyPanel = (JPanel) rowComponents[0];
                    JTextField keyField = (JTextField) keyPanel.getComponent(1);

                    // Get value panel and its text field
                    JPanel valuePanel = (JPanel) rowComponents[1];
                    JTextField valueField = (JTextField) valuePanel.getComponent(1);

                    String key = keyField.getText().trim();
                    String value = valueField.getText().trim();

                    if (!key.isEmpty() && !value.isEmpty()) {
                        headers.append(key).append(": ").append(value).append("\n");
                    }
                }
            }
        }
        return headers.toString();
    }

    private JPanel createConfigurationPanel() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Configuration Buttons Panel
        JPanel configButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadConfigButton = new JButton("Load Configuration");
        JButton saveConfigButton = new JButton("Save Configuration");
        loadConfigButton.addActionListener(e -> loadConfiguration());
        saveConfigButton.addActionListener(e -> saveConfiguration());
        configButtonsPanel.add(loadConfigButton);
        configButtonsPanel.add(saveConfigButton);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        mainPanel.add(configButtonsPanel, gbc);

        // HTTP Method and URL Panel
        JPanel urlPanel = new JPanel(new BorderLayout(5, 0));
        httpMethodCombo = new JComboBox<>(new String[]{"GET", "POST", "PUT", "PATCH"});
        urlPanel.add(httpMethodCombo, BorderLayout.WEST);
        urlPanel.add(urlField, BorderLayout.CENTER);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("URL:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        mainPanel.add(urlPanel, gbc);

        // Parameters Panel
        JPanel paramsContainer = new JPanel(new BorderLayout());
        paramsContainer.setBorder(BorderFactory.createTitledBorder("Request Parameters"));
        JScrollPane paramsScroll = new JScrollPane(paramsPanel);
        paramsScroll.setMinimumSize(new Dimension(110, 40));
        JPanel addParamButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addParamButton = new JButton("Add Parameter");
        addParamButton.addActionListener(e -> addParamRow(paramsPanel));
        addParamButtonPanel.add(addParamButton);
        paramsContainer.add(paramsScroll, BorderLayout.CENTER);
        paramsContainer.add(addParamButtonPanel, BorderLayout.SOUTH);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        mainPanel.add(paramsContainer, gbc);

        // Test Parameters Panel
        JPanel testParamsPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        testParamsPanel.setBorder(BorderFactory.createTitledBorder("Test Parameters"));

        testParamsPanel.add(new JLabel("Number of Users:"));
        testParamsPanel.add(usersField);

        testParamsPanel.add(new JLabel("Ramp-up Time (seconds):"));
        testParamsPanel.add(rampUpTimeField);

        testParamsPanel.add(new JLabel("Loop Count:"));
        testParamsPanel.add(loopCountField);

        testParamsPanel.add(new JLabel("Requests per Second:"));
        testParamsPanel.add(requestsPerSecondField);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        mainPanel.add(testParamsPanel, gbc);

        // Request Configuration Tabbed Pane
        JTabbedPane requestConfigPane = new JTabbedPane();

        // Headers Tab
        JPanel headersContainer = new JPanel(new BorderLayout());
        headersContainer.setBorder(BorderFactory.createTitledBorder("HTTP Headers"));
        JScrollPane headersScroll = new JScrollPane(headersPanel);
        headersScroll.setPreferredSize(new Dimension(1100, 200));
        JPanel addHeaderButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addHeaderButton = new JButton("Add Header");
        addHeaderButton.addActionListener(e -> addHeaderRow(headersPanel));
        addHeaderButtonPanel.add(addHeaderButton);
        headersContainer.add(headersScroll, BorderLayout.CENTER);
        headersContainer.add(addHeaderButtonPanel, BorderLayout.SOUTH);
        requestConfigPane.addTab("Headers", headersContainer);

        // Authorization Tab
        JPanel authContainer = new JPanel(new BorderLayout());
        authContainer.setBorder(BorderFactory.createTitledBorder("Authorization"));
        JPanel authContent = new JPanel(new GridBagLayout());
        GridBagConstraints authGbc = new GridBagConstraints();
        authGbc.insets = new Insets(5, 5, 5, 5);
        authGbc.fill = GridBagConstraints.HORIZONTAL;

        authTypeCombo = new JComboBox<>(new String[]{"None", "Bearer", "JWT", "Basic"});
        authTokenField = new JTextField();

        authGbc.gridx = 0;
        authGbc.gridy = 0;
        authContent.add(new JLabel("Type:"), authGbc);
        authGbc.gridx = 1;
        authGbc.weightx = 1.0;
        authContent.add(authTypeCombo, authGbc);

        authGbc.gridx = 0;
        authGbc.gridy = 1;
        authGbc.weightx = 0.0;
        authContent.add(new JLabel("Token:"), authGbc);
        authGbc.gridx = 1;
        authGbc.weightx = 1.0;
        authContent.add(authTokenField, authGbc);

        authContainer.add(authContent, BorderLayout.CENTER);
        requestConfigPane.addTab("Authorization", authContainer);

        gbc.gridy = 4;
        mainPanel.add(requestConfigPane, gbc);

        // Body Panel
        gbc.gridy = 5;
        gbc.weighty = 0.7;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.add(new JLabel("Request Body:"), BorderLayout.NORTH);
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setPreferredSize(new Dimension(1100, 200));
        bodyPanel.add(bodyScroll, BorderLayout.CENTER);
        bodyPanel.add(selectBodyFileButton, BorderLayout.SOUTH);
        mainPanel.add(bodyPanel, gbc);

        // Report Directory Panel
        gbc.gridy = 6;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel reportPanel = new JPanel(new BorderLayout());
        reportPanel.add(new JLabel("Report Directory:"), BorderLayout.NORTH);
        reportPanel.add(reportDirectoryField, BorderLayout.CENTER);
        reportPanel.add(selectReportDirButton, BorderLayout.EAST);
        mainPanel.add(reportPanel, gbc);

        // Log Area
        gbc.gridy = 7;
        mainPanel.add(new JLabel("Test Log:"), gbc);
        gbc.gridy = 8;
        gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(1100, 150));
        mainPanel.add(logScroll, gbc);

        // Start/Stop Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        gbc.gridy = 9;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(buttonPanel, gbc);

        return mainPanel;
    }

    private void addParamRow(JPanel panel) {
        addParamRow(panel, "", "");
    }

    private void addParamRow(JPanel panel, String key, String value) {
        JPanel rowPanel = new JPanel(new BorderLayout(3, 0));
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JTextField keyField = new JTextField(key);
        JTextField valueField = new JTextField(value);
        JButton removeButton = new JButton("X");

        keyField.setPreferredSize(new Dimension(400, 10));
        valueField.setPreferredSize(new Dimension(800, 10));
        removeButton.setPreferredSize(new Dimension(50, 10));

        JPanel keyPanel = new JPanel(new BorderLayout(5, 0));
        keyPanel.add(new JLabel("Key: "), BorderLayout.WEST);
        keyPanel.add(keyField, BorderLayout.CENTER);

        JPanel valuePanel = new JPanel(new BorderLayout(5, 0));
        valuePanel.add(new JLabel("Value: "), BorderLayout.WEST);
        valuePanel.add(valueField, BorderLayout.CENTER);

        rowPanel.add(keyPanel, BorderLayout.WEST);
        rowPanel.add(valuePanel, BorderLayout.CENTER);
        rowPanel.add(removeButton, BorderLayout.EAST);

        removeButton.addActionListener(e -> {
            panel.remove(rowPanel);
            panel.revalidate();
            panel.repaint();
        });

        panel.add(rowPanel);
        panel.add(Box.createVerticalStrut(15));
        panel.revalidate();
        panel.repaint();
    }

    private String getParamsText() {
        StringBuilder params = new StringBuilder();
        Component[] components = paramsPanel.getComponents();

        for (Component comp : components) {
            if (comp instanceof JPanel) {
                JPanel rowPanel = (JPanel) comp;
                Component[] rowComponents = rowPanel.getComponents();
                if (rowComponents.length >= 2) {
                    JPanel keyPanel = (JPanel) rowComponents[0];
                    JTextField keyField = (JTextField) keyPanel.getComponent(1);

                    JPanel valuePanel = (JPanel) rowComponents[1];
                    JTextField valueField = (JTextField) valuePanel.getComponent(1);

                    String key = keyField.getText().trim();
                    String value = valueField.getText().trim();

                    if (!key.isEmpty() && !value.isEmpty()) {
                        params.append(key).append("=").append(value).append("&");
                    }
                }
            }
        }
        String result = params.toString();
        return result.isEmpty() ? "" : result.substring(0, result.length() - 1);
    }

    private String getAuthHeader() {
        String authType = (String) authTypeCombo.getSelectedItem();
        String token = authTokenField.getText().trim();

        if (token.isEmpty() || "None".equals(authType)) {
            return "";
        }

        switch (authType) {
            case "Bearer":
                return "Bearer " + token;
            case "JWT":
                return "JWT " + token;
            case "Basic":
                return "Basic " + token;
            default:
                return "";
        }
    }

    private void startTest() {
        try {
            // Validate inputs
            if (urlField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter URL");
                return;
            }
            if (usersField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter number of users");
                return;
            }
            if (rampUpTimeField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter ramp-up time");
                return;
            }
            if (loopCountField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter loop count");
                return;
            }
            if (requestsPerSecondField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter requests per second");
                return;
            }
            if (reportDirectoryField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select report directory");
                return;
            }

            String url = urlField.getText();
            String params = getParamsText();
            if (!params.isEmpty()) {
                url += (url.contains("?") ? "&" : "?") + params;
            }

            currentTest = new LoadTest();
            currentTest.configure(
                url,
                Integer.parseInt(usersField.getText()),
                Integer.parseInt(rampUpTimeField.getText()),
                Integer.parseInt(loopCountField.getText()),
                Integer.parseInt(requestsPerSecondField.getText()),
                reportDirectoryField.getText(),
                getHeadersText(),
                bodyArea.getText(),
                (String) httpMethodCombo.getSelectedItem(),
                getAuthHeader()
            );

            // Start test in separate thread
            isTestRunning = true;
            testStartTime = System.currentTimeMillis();
            new Thread(() -> {
                try {
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    logArea.append("Starting test...\n");
                    currentTest.runTest(new LoadTest.TestProgressCallback() {
                        @Override
                        public void onProgress(String message) {
                            SwingUtilities.invokeLater(() -> {
                                logArea.append(message + "\n");
                                logArea.setCaretPosition(logArea.getDocument().getLength());
                            });
                        }

                        @Override
                        public void onComplete(List<ResponseDetails> responseDetails) {
                            SwingUtilities.invokeLater(() -> {
                                startButton.setEnabled(true);
                                stopButton.setEnabled(false);
                                isTestRunning = false;
                                logArea.append("Test completed!\n");
                                updateResults(responseDetails);
                                tabbedPane.setSelectedIndex(1);
                            });
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                        isTestRunning = false;
                        logArea.append("Error during test: " + e.getMessage() + "\n");
                        JOptionPane.showMessageDialog(LoadTestGUI.this,
                            "Error during test: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers for test parameters");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error starting test: " + e.getMessage());
        }
    }

    private void stopTest() {
        if (currentTest != null && isTestRunning) {
            currentTest.stopTest();
            isTestRunning = false;
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            logArea.append("Test stopped by user.\n");
        }
    }

    private void updateResults(List<ResponseDetails> responseDetails) {
        // Update statistics
        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        int successCount = 0;
        int failureCount = 0;

        DefaultCategoryDataset responseTimeDataset = new DefaultCategoryDataset();
        DefaultCategoryDataset throughputDataset = new DefaultCategoryDataset();

        // Clear existing data
        tableModel.setRowCount(0);

        // Group requests by time intervals for throughput calculation
        Map<Integer, Integer> requestsPerSecond = new HashMap<>();

        for (int i = 0; i < responseDetails.size(); i++) {
            ResponseDetails detail = responseDetails.get(i);
            long responseTime = detail.getResponseTime();
            boolean isSuccess = detail.getResponseCode().equals("200");

            // Update statistics
            totalTime += responseTime;
            minTime = Math.min(minTime, responseTime);
            maxTime = Math.max(maxTime, responseTime);
            if (isSuccess) successCount++; else failureCount++;

            // Add to table
            tableModel.addRow(new Object[]{
                i + 1,
                responseTime,
                detail.getResponseCode(),
                isSuccess ? "Yes" : "No"
            });

            // Add to response time chart
            responseTimeDataset.addValue(responseTime, "Response Time", String.valueOf(i + 1));

            // Calculate throughput (group by second)
            int second = i / 10; // Assuming 10 requests per second
            requestsPerSecond.put(second, requestsPerSecond.getOrDefault(second, 0) + 1);
        }

        // Add throughput data
        for (Map.Entry<Integer, Integer> entry : requestsPerSecond.entrySet()) {
            throughputDataset.addValue(entry.getValue(), "Throughput", String.valueOf(entry.getKey()));
        }

        // Calculate test duration
        long testDuration = System.currentTimeMillis() - testStartTime;

        // Update statistics panel
        statsPanel.removeAll();
        statsPanel.add(createStatLabel("Total Requests", String.valueOf(responseDetails.size())));
        statsPanel.add(createStatLabel("Successful", String.valueOf(successCount)));
        statsPanel.add(createStatLabel("Failed", String.valueOf(failureCount)));
        statsPanel.add(createStatLabel("Min Response Time", minTime + " ms"));
        statsPanel.add(createStatLabel("Max Response Time", maxTime + " ms"));
        statsPanel.add(createStatLabel("Avg Response Time",
            String.format("%.2f ms", (double)totalTime / responseDetails.size())));
        statsPanel.add(createStatLabel("Test Duration",
            String.format("%.2f seconds", testDuration / 1000.0)));
        statsPanel.add(createStatLabel("Start Time",
            new java.util.Date(testStartTime).toString()));
        statsPanel.add(createStatLabel("End Time",
            new java.util.Date(testStartTime + testDuration).toString()));

        // Update charts
        responseTimeChart.getChart().getCategoryPlot().setDataset(responseTimeDataset);
        throughputChart.getChart().getCategoryPlot().setDataset(throughputDataset);

        statsPanel.revalidate();
        statsPanel.repaint();
    }

    private JLabel createStatLabel(String title, String value) {
        JLabel label = new JLabel("<html><b>" + title + ":</b><br>" + value + "</html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    private void setupActionListeners() {
        startButton.addActionListener(e -> startTest());
        stopButton.addActionListener(e -> stopTest());

        selectBodyFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    String content = new String(Files.readAllBytes(selectedFile.toPath()));
                    bodyArea.setText(content);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error reading file: " + ex.getMessage());
                }
            }
        });

        selectReportDirButton.addActionListener(e -> {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (dirChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                reportDirectoryField.setText(dirChooser.getSelectedFile().getAbsolutePath());
            }
        });
    }

    private JPanel createResultsPanel() {
        resultsPanel = new JPanel(new BorderLayout());

        // Create stats panel
        statsPanel = new JPanel(new GridLayout(3, 3, 10, 10));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Test Statistics"));

        // Create table for detailed results
        String[] columnNames = {"Request #", "Response Time (ms)", "Status Code", "Success"};
        tableModel = new DefaultTableModel(columnNames, 0);
        resultsTable = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(resultsTable);

        // Create charts with proper styling
        responseTimeChart = new ChartPanel(createResponseTimeChart());
        responseTimeChart.setPreferredSize(new Dimension(500, 300));
        responseTimeChart.setMouseWheelEnabled(true);

        throughputChart = new ChartPanel(createThroughputChart());
        throughputChart.setPreferredSize(new Dimension(500, 300));
        throughputChart.setMouseWheelEnabled(true);

        // Add stats panel to top
        resultsPanel.add(statsPanel, BorderLayout.NORTH);

        // Add charts + table in split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, responseTimeChart, throughputChart),
                tableScroll);
        splitPane.setDividerLocation(400);
        resultsPanel.add(splitPane, BorderLayout.CENTER);

        // PDF export button
        JButton exportBtn = new JButton("Export PDF");
        exportBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("results.pdf"));
            if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                exportResultsPanelToPDF(resultsPanel, fc.getSelectedFile().getAbsolutePath());
            }
        });

        // Add button to bottom
        resultsPanel.add(exportBtn, BorderLayout.SOUTH);

        return resultsPanel;
    }


    private JFreeChart createResponseTimeChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        JFreeChart chart = ChartFactory.createLineChart(
            "Response Time Over Time",
            "Request Number",
            "Response Time (ms)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );

        // Customize chart appearance
        chart.setBackgroundPaint(Color.WHITE);
        com.itextpdf.text.Font font = new com.itextpdf.text.Font(
                com.itextpdf.text.Font.FontFamily.HELVETICA, 12, com.itextpdf.text.Font.BOLD
        );

        // Customize plot appearance
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

        // Customize renderer
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesShapesVisible(0, true);
        plot.setRenderer(renderer);

        return chart;
    }

    private JFreeChart createThroughputChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        JFreeChart chart = ChartFactory.createLineChart(
            "Throughput Over Time",
            "Time (s)",
            "Requests/sec",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );

        // Customize chart appearance
        chart.setBackgroundPaint(Color.WHITE);
        com.itextpdf.text.Font font = new com.itextpdf.text.Font(
                com.itextpdf.text.Font.FontFamily.HELVETICA, 12, com.itextpdf.text.Font.BOLD
        );

        // Customize plot appearance
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

        // Customize renderer
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.GREEN);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesShapesVisible(0, true);
        plot.setRenderer(renderer);

        return chart;
    }

    private void normalizeSplitPanes(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JSplitPane sp) {
                sp.setResizeWeight(0.5);
                sp.setDividerLocation(0.5); // yüzde ile
                sp.doLayout();
            }
            if (c instanceof Container child) {
                normalizeSplitPanes(child);
            }
        }
    }

    public void exportResultsPanelToPDF(JPanel panel, String filePath) {
        try {
            // PDF oluştur
            Document document = new Document(PageSize.A4, 36, 36, 70, 50);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // Başlık ve tarih
            com.itextpdf.text.Font headerFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 14, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font dateFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 10, com.itextpdf.text.Font.NORMAL);

            Paragraph header = new Paragraph("Test Results Report", headerFont);
            header.setAlignment(Element.ALIGN_LEFT);
            document.add(header);

            String timestamp = new SimpleDateFormat("dd MMM yyyy HH:mm:ss").format(new Date());
            Paragraph date = new Paragraph(timestamp, dateFont);
            date.setAlignment(Element.ALIGN_RIGHT);
            document.add(date);
            document.add(Chunk.NEWLINE);

            // PDF sayfa boyutları
            float pageWidth = PageSize.A4.getWidth() - document.leftMargin() - document.rightMargin();

            // Not: header/date eklendikten sonra kalan yükseklik daha doğru
            float pageHeight = writer.getVerticalPosition(true) - document.bottomMargin();

            // ====== KRİTİK: Export öncesi paneli layout et ve splitpane divider’ları düzelt ======
            Dimension oldSize = panel.getSize();
            Dimension oldPref = panel.getPreferredSize();

            int targetW = Math.max(1, (int) pageWidth);

            // önce genişliği ver, yükseklik hesaplasın
            panel.setSize(new Dimension(targetW, 10));
            panel.doLayout();
            panel.validate();

            // nested splitpane’leri normalize et (1)
            normalizeSplitPanes(panel);

            // preferred height'i al ve tam boyutta bir daha layout et
            Dimension pref = panel.getPreferredSize();
            panel.setSize(new Dimension(targetW, pref.height));
            panel.doLayout();
            panel.validate();

            // nested splitpane’leri normalize et (2) - nested yapılarda çok fark eder
            normalizeSplitPanes(panel);
            panel.doLayout();
            panel.validate();
            // ================================================================================

            // ====== Paneli image’a çiz ======
            int scale = 2;
            BufferedImage image = new BufferedImage(targetW * scale, pref.height * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2.scale(scale, scale);
            panel.printAll(g2);
            g2.dispose();

            // paneli eski haline geri al
            panel.setSize(oldSize);
            panel.setPreferredSize(oldPref);
            panel.doLayout();
            panel.validate();

            // ====== PDF’e ekleme + çok sayfaya bölme ======
            float ratio = pageWidth / image.getWidth();
            int scaledHeight = (int) (image.getHeight() * ratio);

            int pages = (int) Math.ceil((double) scaledHeight / pageHeight);

            for (int i = 0; i < pages; i++) {
                int y = (int) (i * pageHeight / ratio);
                int h = Math.min((int) (pageHeight / ratio), image.getHeight() - y);

                BufferedImage subImage = image.getSubimage(0, y, image.getWidth(), h);

                // temp file yerine memory (daha temiz)
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(subImage, "png", baos);

                Image pdfImage = Image.getInstance(baos.toByteArray());
                pdfImage.scaleToFit(pageWidth, pageHeight);
                pdfImage.setAlignment(Image.ALIGN_CENTER);
                document.add(pdfImage);

                if (i < pages - 1) document.newPage();
            }

            document.close();
            JOptionPane.showMessageDialog(null, "PDF başarıyla oluşturuldu:\n" + filePath);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "PDF oluşturulurken hata: " + e.getMessage());
        }
    }






    public static void main(String[] args) {
        // Ensure we're running on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Set system look and feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

                // Create and show the GUI
                LoadTestGUI gui = new LoadTestGUI();
                gui.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Error starting application: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}