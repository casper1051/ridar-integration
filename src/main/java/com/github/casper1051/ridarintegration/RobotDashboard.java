package com.github.casper1051.ridarintegration;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;

import javax.swing.plaf.basic.BasicScrollBarUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RobotDashboard extends JPanel {

    private static final String ROBOT_IP = "192.168.125.1";
    private static final int ROBOT_PORT = 8080;

    
    
    private static final Color BG_DARK = new Color(0, 0, 0);
    private static final Color PANEL_BG = new Color(18, 18, 18);
    private static final Color BORDER_COLOR = new Color(50, 50, 50);
    private static final Color TEXT_COLOR = new Color(190, 190, 190);
    private static final Color ACCENT_BLUE = new Color(80, 120, 190);
    private static final Color ACCENT_RED = new Color(210, 90, 90);
    private static final Color ACCENT_GREEN = new Color(90, 190, 90);
    private static final Color BUTTON_BG = new Color(28, 28, 28);
    private static final Color DANGER_RED = new Color(140, 40, 40);
    private static final Color WARN_ORANGE = new Color(170, 100, 20);
    private static final Color OK_GREEN = new Color(40, 110, 40);

    
    private static final String CARD_ROOT = "ROOT";
    private static final String CARD_EFFECTORS_MENU = "EFFECTORS_MENU";
    private static final String CARD_EFFECTORS_MOTORS = "EFFECTORS_MOTORS";
    private static final String CARD_EFFECTORS_SERVOS = "EFFECTORS_SERVOS";
    private static final String CARD_SENSORS_MENU = "SENSORS_MENU";
    private static final String CARD_SENSORS_DIGITAL = "SENSORS_DIGITAL";
    private static final String CARD_SENSORS_ANALOG = "SENSORS_ANALOG";
    private static final String CARD_SENSORS_IMU = "SENSORS_IMU";
    private static final String CARD_OTHER = "OTHER";

    private final JLabel statusLabel = new JLabel("Checking Connection...", SwingConstants.CENTER);
    private final JTextArea logArea = new JTextArea();

    private final CardLayout mainCardLayout = new CardLayout();
    private final JPanel mainContentPanel = new JPanel(mainCardLayout);

    
    
    
    private final JPanel navHeader = new JPanel(new BorderLayout());
    private final JButton navBackButton = createStyledButton("\u2190 Back");
    private final JLabel navTitleLabel = new JLabel();
    private final Deque<String[]> navStack = new ArrayDeque<>(); 
    private String currentCard = CARD_ROOT;

    private final JLabel[] motorPosLabels = new JLabel[4];
    private final JSlider[] motorSliders = new JSlider[4];

    private final JSlider[] servoSliders = new JSlider[4];
    private final JToggleButton[] servoEnableToggles = new JToggleButton[4];
    private boolean servoPositionsLoaded = false;

    private final JLabel[] digitalStateLabels = new JLabel[10];

    private static final int MAX_SAMPLES = 200;
    private final List<Double> sensor1Data = Collections.synchronizedList(new ArrayList<>());
    private final List<Double> sensor2Data = Collections.synchronizedList(new ArrayList<>());
    private JComboBox<String> sensor1Select;
    private JComboBox<String> sensor2Select;
    private AnalogGraphPanel analogGraphPanel;

    private final List<Double> gyroXData = Collections.synchronizedList(new ArrayList<>());
    private final List<Double> gyroYData = Collections.synchronizedList(new ArrayList<>());
    private final List<Double> gyroZData = Collections.synchronizedList(new ArrayList<>());
    private final List<Double> accelXData = Collections.synchronizedList(new ArrayList<>());
    private final List<Double> accelYData = Collections.synchronizedList(new ArrayList<>());
    private final List<Double> accelZData = Collections.synchronizedList(new ArrayList<>());

    private final JLabel[] gyroLabels = new JLabel[3];
    private final JLabel[] accelLabels = new JLabel[3];

    private JCheckBox chkX, chkY, chkZ;
    private JComboBox<String> imuDisplaySelect;
    private final CardLayout imuCardLayout = new CardLayout();
    private final JPanel imuGraphCardPanel = new JPanel(imuCardLayout);
    private ImuGraphPanel gyroGraphPanel;
    private ImuGraphPanel accelGraphPanel;

    private volatile boolean isReachable = false;
    private volatile boolean isServerOnline = false;
    private volatile boolean threadsRunning = true;
    private Thread telemetryThread;

    
    private static final float MIN_SCALE = 0.7f;
    private static final float MAX_SCALE = 2.2f;
    private static final float SCALE_STEP = 0.1f;
    private float uiScale = 1.0f;

    public RobotDashboard() {
        setLayout(new BorderLayout());
        setBackground(BG_DARK);
        setMinimumSize(new Dimension(800, 500));
        setPreferredSize(new Dimension(1200, 800));

        
        JPanel topHeader = new JPanel(new BorderLayout());
        topHeader.setBackground(PANEL_BG);
        topHeader.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel titleLabel = new JLabel("RIDAR ROBOTICS CONTROL");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        statusLabel.setOpaque(true);
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        statusLabel.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(4, 10, 4, 10)
        ));
        updateStatusBadge(false, false, "INITIALIZING");

        JButton btnScaleDown = createScaleButton("\u2212"); 
        btnScaleDown.setToolTipText("Zoom out (Ctrl/Cmd -)");
        btnScaleDown.addActionListener(e -> adjustScale(-SCALE_STEP));

        JButton btnScaleReset = createScaleButton("=");
        btnScaleReset.setToolTipText("Reset zoom");
        btnScaleReset.addActionListener(e -> resetScale());

        JButton btnScaleUp = createScaleButton("+");
        btnScaleUp.setToolTipText("Zoom in (Ctrl/Cmd +)");
        btnScaleUp.addActionListener(e -> adjustScale(SCALE_STEP));

        JPanel scaleGroup = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        scaleGroup.setBackground(PANEL_BG);
        scaleGroup.add(btnScaleDown);
        scaleGroup.add(btnScaleReset);
        scaleGroup.add(btnScaleUp);

        JButton btnResetUi = createStyledButton("Reset UI");
        btnResetUi.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnResetUi.setBackground(WARN_ORANGE);
        btnResetUi.setForeground(Color.WHITE);
        btnResetUi.addActionListener(e -> resetUiState());

        JPanel headerRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        headerRightPanel.setBackground(PANEL_BG);
        headerRightPanel.add(statusLabel);
        headerRightPanel.add(scaleGroup);
        headerRightPanel.add(btnResetUi);

        topHeader.add(titleLabel, BorderLayout.WEST);
        topHeader.add(headerRightPanel, BorderLayout.EAST);

        
        navHeader.setBackground(PANEL_BG);
        navHeader.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        navBackButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        navBackButton.addActionListener(e -> goBack());
        navBackButton.setVisible(false);

        navTitleLabel.setForeground(Color.WHITE);
        navTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        navTitleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        navTitleLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

        navHeader.add(navBackButton, BorderLayout.WEST);
        navHeader.add(navTitleLabel, BorderLayout.CENTER);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(topHeader, BorderLayout.NORTH);
        topContainer.add(navHeader, BorderLayout.SOUTH);

        
        
        initRootView();
        initEffectorsViews();
        initSensorsViews();
        initOtherView();

        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(BG_DARK);
        contentWrapper.add(mainContentPanel, BorderLayout.CENTER);
        mainContentPanel.setMinimumSize(new Dimension(300, 200));

        
        logArea.setEditable(false);
        logArea.setBackground(new Color(10, 10, 10));
        logArea.setForeground(new Color(169, 183, 198));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JScrollPane logScroll = createStyledScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR), "Telemetry & Debug Log", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 11), TEXT_COLOR
        ));
        logScroll.setMinimumSize(new Dimension(200, 10));

        
        EmbeddedTerminalPanel embeddedTerminal = new EmbeddedTerminalPanel();
        embeddedTerminal.setMinimumSize(new Dimension(250, 200));

        
        JSplitPane leftVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        leftVerticalSplit.setBackground(BG_DARK);
        leftVerticalSplit.setTopComponent(contentWrapper);
        leftVerticalSplit.setBottomComponent(logScroll);
        leftVerticalSplit.setResizeWeight(0.70);
        leftVerticalSplit.setContinuousLayout(true);
        leftVerticalSplit.setMinimumSize(new Dimension(300, 300));

        
        JSplitPane mainHorizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainHorizontalSplit.setBackground(BG_DARK);
        mainHorizontalSplit.setLeftComponent(leftVerticalSplit);
        mainHorizontalSplit.setRightComponent(embeddedTerminal);
        mainHorizontalSplit.setResizeWeight(0.60);
        mainHorizontalSplit.setDividerSize(6);
        mainHorizontalSplit.setContinuousLayout(true);

        add(topContainer, BorderLayout.NORTH);
        add(mainHorizontalSplit, BorderLayout.CENTER);

        showCard(CARD_ROOT, "Overview");
        setupScaleKeyBindings();
        startBackgroundServices();
    }

    

    /** Push the current screen onto the back-stack and show a new one. */
    private void navigateTo(String cardName, String title) {
        navStack.push(new String[]{currentCard, navTitleLabel.getText()});
        showCard(cardName, title);
    }

    /** Pop the back-stack, if any, and show the previous screen. */
    private void goBack() {
        if (!navStack.isEmpty()) {
            String[] prev = navStack.pop();
            showCard(prev[0], prev[1]);
        }
    }

    /** Reset all the way back to the root menu (used by Reset UI). */
    private void goToRoot() {
        navStack.clear();
        showCard(CARD_ROOT, "Overview");
    }

    private void showCard(String cardName, String title) {
        currentCard = cardName;
        mainCardLayout.show(mainContentPanel, cardName);
        navTitleLabel.setText(title);
        navBackButton.setVisible(!navStack.isEmpty());
    }

    

    private void initRootView() {
        JPanel homePanel = new JPanel();
        homePanel.setLayout(new BoxLayout(homePanel, BoxLayout.Y_AXIS));
        homePanel.setBackground(BG_DARK);
        homePanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel cardNav = createCardPanel("Quick Navigation");
        cardNav.setLayout(new GridLayout(3, 1, 8, 8));

        JButton quickEffectors = createStyledButton("Open Effector Controls (Motors & Servos)");
        JButton quickSensors = createStyledButton("Open Sensor Monitor (Digital, Analog, IMU)");
        JButton quickTools = createStyledButton("Open Diagnostic Tools");

        quickEffectors.addActionListener(e -> navigateTo(CARD_EFFECTORS_MENU, "Effectors"));
        quickSensors.addActionListener(e -> navigateTo(CARD_SENSORS_MENU, "Sensors"));
        quickTools.addActionListener(e -> navigateTo(CARD_OTHER, "Tools"));

        cardNav.add(quickEffectors);
        cardNav.add(quickSensors);
        cardNav.add(quickTools);

        JPanel cardInfo = createCardPanel("Target Device Info");
        cardInfo.setLayout(new GridLayout(4, 1, 4, 4));
        cardInfo.add(new JLabel("Target IP: " + ROBOT_IP, SwingConstants.LEFT));
        cardInfo.add(new JLabel("Target Port: " + ROBOT_PORT, SwingConstants.LEFT));
        cardInfo.add(new JLabel("Bridge Framework: RIDAR_BRIDGE C++", SwingConstants.LEFT));
        cardInfo.add(new JLabel("Motor Range: -1500 to +1500", SwingConstants.LEFT));

        for (Component c : cardInfo.getComponents()) {
            c.setForeground(TEXT_COLOR);
            c.setFont(new Font("SansSerif", Font.PLAIN, 12));
        }

        homePanel.add(cardNav);
        homePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        homePanel.add(cardInfo);

        mainContentPanel.add(createStyledScrollPane(homePanel), CARD_ROOT);
    }

    /** A simple full-width "menu item" button used by the submenu screens. */
    private JButton createMenuItemButton(String label) {
        JButton btn = createStyledButton(label);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(12, 14, 12, 14)
        ));
        return btn;
    }

    private void initEffectorsViews() {
        
        JPanel menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBackground(BG_DARK);
        menuPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JButton btnMotorsItem = createMenuItemButton("Motors (Ports 0-3)");
        JButton btnServosItem = createMenuItemButton("Servos (Ports 0-3)");

        btnMotorsItem.addActionListener(e -> navigateTo(CARD_EFFECTORS_MOTORS, "Motors"));
        btnServosItem.addActionListener(e -> {
            navigateTo(CARD_EFFECTORS_SERVOS, "Servos");
            if (!servoPositionsLoaded) {
                fetchInitialServoPositions();
                servoPositionsLoaded = true;
            }
        });

        menuPanel.add(btnMotorsItem);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        menuPanel.add(btnServosItem);

        mainContentPanel.add(menuPanel, CARD_EFFECTORS_MENU);

        
        JPanel motorsListContainer = new JPanel();
        motorsListContainer.setLayout(new BoxLayout(motorsListContainer, BoxLayout.Y_AXIS));
        motorsListContainer.setBackground(BG_DARK);
        motorsListContainer.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (int i = 0; i <= 3; i++) {
            final int port = i;
            JPanel mCard = createCardPanel("Motor Port " + port);
            mCard.setLayout(new BoxLayout(mCard, BoxLayout.Y_AXIS));

            JSlider speedSlider = new JSlider(-1500, 1500, 0);
            speedSlider.setBackground(PANEL_BG);
            speedSlider.setForeground(TEXT_COLOR);
            speedSlider.setMajorTickSpacing(750);
            speedSlider.setPaintTicks(true);
            speedSlider.setPaintLabels(true);
            motorSliders[port] = speedSlider;

            JLabel speedValLbl = new JLabel("Speed: 0", SwingConstants.CENTER);
            speedValLbl.setForeground(ACCENT_BLUE);
            speedValLbl.setFont(new Font("Monospaced", Font.BOLD, 12));
            speedValLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            speedSlider.addChangeListener(e -> {
                int val = speedSlider.getValue();
                speedValLbl.setText("Speed: " + val);
                if (speedSlider.isEnabled()) {
                    sendSocketCommand("effector", "motor", "set_speed", port, val);
                }
            });

            JPanel ctrlRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
            ctrlRow.setBackground(PANEL_BG);

            JButton btnStop = createStyledButton("STOP");
            btnStop.setBackground(DANGER_RED);
            btnStop.addActionListener(e -> {
                speedSlider.setValue(0);
                sendSocketCommand("effector", "motor", "off", port, 0);
            });

            JToggleButton toggleActiveBrake = new JToggleButton("Active Brake");
            toggleActiveBrake.setBackground(BUTTON_BG);
            toggleActiveBrake.setForeground(TEXT_COLOR);
            toggleActiveBrake.setFocusPainted(false);
            toggleActiveBrake.addActionListener(e -> {
                boolean brakeOn = toggleActiveBrake.isSelected();
                speedSlider.setEnabled(!brakeOn);
                if (brakeOn) {
                    speedSlider.setValue(0);
                    toggleActiveBrake.setBackground(WARN_ORANGE);
                    sendSocketCommand("effector", "motor", "active_brake", port, 0);
                } else {
                    toggleActiveBrake.setBackground(BUTTON_BG);
                    sendSocketCommand("effector", "motor", "off", port, 0);
                }
            });

            JLabel posLabel = new JLabel("Encoder: 0", SwingConstants.CENTER);
            posLabel.setForeground(TEXT_COLOR);
            motorPosLabels[port] = posLabel;

            JButton btnClearPos = createStyledButton("Clear Pos");
            btnClearPos.addActionListener(e -> {
                sendSocketCommand("effector", "motor", "clear_pos", port, 0);
                motorPosLabels[port].setText("Encoder: 0");
            });

            ctrlRow.add(btnStop);
            ctrlRow.add(toggleActiveBrake);
            ctrlRow.add(btnClearPos);

            mCard.add(speedSlider);
            mCard.add(speedValLbl);
            mCard.add(ctrlRow);

            motorsListContainer.add(mCard);
            motorsListContainer.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        mainContentPanel.add(createStyledScrollPane(motorsListContainer), CARD_EFFECTORS_MOTORS);

        
        JPanel servosListContainer = new JPanel();
        servosListContainer.setLayout(new BoxLayout(servosListContainer, BoxLayout.Y_AXIS));
        servosListContainer.setBackground(BG_DARK);
        servosListContainer.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (int i = 0; i <= 3; i++) {
            final int port = i;
            JPanel sCard = createCardPanel("Servo Port " + port);
            sCard.setLayout(new BoxLayout(sCard, BoxLayout.Y_AXIS));

            JSlider angleSlider = new JSlider(0, 180, 90);
            angleSlider.setBackground(PANEL_BG);
            angleSlider.setForeground(TEXT_COLOR);
            angleSlider.setMajorTickSpacing(45);
            angleSlider.setPaintTicks(true);
            angleSlider.setPaintLabels(true);
            servoSliders[port] = angleSlider;

            JLabel angleLbl = new JLabel("Angle: 90°", SwingConstants.CENTER);
            angleLbl.setForeground(ACCENT_BLUE);
            angleLbl.setFont(new Font("Monospaced", Font.BOLD, 12));
            angleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JPanel ctrlRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
            ctrlRow.setBackground(PANEL_BG);

            JToggleButton toggleEnable = new JToggleButton("Enabled");
            toggleEnable.setSelected(true);
            toggleEnable.setBackground(OK_GREEN);
            toggleEnable.setForeground(Color.WHITE);
            toggleEnable.setFocusPainted(false);
            servoEnableToggles[port] = toggleEnable;

            toggleEnable.addActionListener(e -> {
                boolean isEnabled = toggleEnable.isSelected();
                angleSlider.setEnabled(isEnabled);
                if (isEnabled) {
                    toggleEnable.setText("Enabled");
                    toggleEnable.setBackground(OK_GREEN);
                    sendSocketCommand("effector", "servo", "enable", port, 0);
                } else {
                    toggleEnable.setText("Disabled");
                    toggleEnable.setBackground(BUTTON_BG);
                    sendSocketCommand("effector", "servo", "disable", port, 0);
                }
            });

            angleSlider.addChangeListener(e -> {
                int deg = angleSlider.getValue();
                angleLbl.setText("Angle: " + deg + "°");
                if (angleSlider.isEnabled()) {
                    sendSocketCommand("effector", "servo", "set_position", port, deg);
                }
            });

            JButton btnCutPower = createStyledButton("Disable Servo");
            btnCutPower.setBackground(DANGER_RED);
            btnCutPower.addActionListener(e -> {
                toggleEnable.setSelected(false);
                angleSlider.setEnabled(false);
                toggleEnable.setText("Disabled");
                toggleEnable.setBackground(BUTTON_BG);
                sendSocketCommand("effector", "servo", "disable", port, 0);
            });

            ctrlRow.add(toggleEnable);
            ctrlRow.add(btnCutPower);

            sCard.add(angleSlider);
            sCard.add(angleLbl);
            sCard.add(ctrlRow);

            servosListContainer.add(sCard);
            servosListContainer.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        mainContentPanel.add(createStyledScrollPane(servosListContainer), CARD_EFFECTORS_SERVOS);
    }

    private void fetchInitialServoPositions() {
        appendLog("[SYSTEM] Fetching initial servo positions...");
        for (int i = 0; i < servoSliders.length; i++) {
            if (servoSliders[i] != null) servoSliders[i].setValue(90);
        }
    }

    private void initSensorsViews() {
        
        JPanel menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBackground(BG_DARK);
        menuPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JButton btnDigitalItem = createMenuItemButton("Digital I/O");
        JButton btnAnalogItem = createMenuItemButton("Analog Sensors");
        JButton btnImuItem = createMenuItemButton("IMU Waveforms");

        btnDigitalItem.addActionListener(e -> navigateTo(CARD_SENSORS_DIGITAL, "Digital"));
        btnAnalogItem.addActionListener(e -> navigateTo(CARD_SENSORS_ANALOG, "Analog"));
        btnImuItem.addActionListener(e -> navigateTo(CARD_SENSORS_IMU, "IMU Waveforms"));

        menuPanel.add(btnDigitalItem);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        menuPanel.add(btnAnalogItem);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        menuPanel.add(btnImuItem);

        mainContentPanel.add(menuPanel, CARD_SENSORS_MENU);

        
        JPanel digitalPanel = new JPanel(new GridLayout(0, 1, 6, 6));
        digitalPanel.setBackground(BG_DARK);
        digitalPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (int i = 0; i <= 9; i++) {
            JPanel p = new JPanel(new BorderLayout(8, 0));
            p.setBackground(PANEL_BG);
            p.setBorder(new CompoundBorder(new LineBorder(BORDER_COLOR), new EmptyBorder(6, 10, 6, 10)));

            JLabel name = new JLabel("Digital Port " + i);
            name.setForeground(TEXT_COLOR);

            JLabel state = new JLabel("0 (OFF)", SwingConstants.CENTER);
            state.setOpaque(true);
            state.setBackground(DANGER_RED);
            state.setForeground(Color.WHITE);
            state.setFont(new Font("Monospaced", Font.BOLD, 11));
            state.setBorder(new EmptyBorder(2, 8, 2, 8));

            digitalStateLabels[i] = state;

            p.add(name, BorderLayout.WEST);
            p.add(state, BorderLayout.EAST);
            digitalPanel.add(p);
        }

        mainContentPanel.add(createStyledScrollPane(digitalPanel), CARD_SENSORS_DIGITAL);

        
        JPanel analogPanel = new JPanel(new BorderLayout());
        analogPanel.setBackground(BG_DARK);

        JPanel selectorBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        selectorBar.setBackground(PANEL_BG);

        String[] sensorOptions = {"Analog 0", "Analog 1", "Analog 2", "Analog 3", "None"};
        sensor1Select = new JComboBox<>(sensorOptions);
        sensor2Select = new JComboBox<>(sensorOptions);
        sensor1Select.setSelectedIndex(0);
        sensor2Select.setSelectedIndex(1);

        styleComboBox(sensor1Select);
        styleComboBox(sensor2Select);

        JLabel l1 = new JLabel("S1 (Blue):"); l1.setForeground(TEXT_COLOR);
        JLabel l2 = new JLabel("S2 (Red):"); l2.setForeground(TEXT_COLOR);

        selectorBar.add(l1);
        selectorBar.add(sensor1Select);
        selectorBar.add(l2);
        selectorBar.add(sensor2Select);

        analogGraphPanel = new AnalogGraphPanel();
        analogPanel.add(selectorBar, BorderLayout.NORTH);
        analogPanel.add(analogGraphPanel, BorderLayout.CENTER);

        mainContentPanel.add(analogPanel, CARD_SENSORS_ANALOG);

        
        JPanel imuMasterPanel = new JPanel(new BorderLayout());
        imuMasterPanel.setBackground(BG_DARK);

        JPanel imuControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        imuControls.setBackground(PANEL_BG);
        imuControls.setBorder(new LineBorder(BORDER_COLOR, 1));

        imuDisplaySelect = new JComboBox<>(new String[]{"Gyroscope (deg/s)", "Accelerometer (m/s²)"});
        styleComboBox(imuDisplaySelect);

        chkX = new JCheckBox("X", true);
        chkY = new JCheckBox("Y", true);
        chkZ = new JCheckBox("Z", true);

        styleCheckBox(chkX, ACCENT_BLUE);
        styleCheckBox(chkY, ACCENT_RED);
        styleCheckBox(chkZ, ACCENT_GREEN);

        imuDisplaySelect.addActionListener(e -> {
            if (imuDisplaySelect.getSelectedIndex() == 0) {
                imuCardLayout.show(imuGraphCardPanel, "GYRO");
            } else {
                imuCardLayout.show(imuGraphCardPanel, "ACCEL");
            }
        });

        chkX.addActionListener(e -> repaintImuGraphs());
        chkY.addActionListener(e -> repaintImuGraphs());
        chkZ.addActionListener(e -> repaintImuGraphs());

        imuControls.add(imuDisplaySelect);
        imuControls.add(chkX);
        imuControls.add(chkY);
        imuControls.add(chkZ);

        JPanel imuBadges = new JPanel(new GridLayout(2, 1, 6, 6));
        imuBadges.setBackground(BG_DARK);
        imuBadges.setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel gyroBox = createCardPanel("Gyroscope Live Readout");
        gyroBox.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 2));

        JPanel accelBox = createCardPanel("Accelerometer Live Readout");
        accelBox.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 2));

        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            gyroLabels[i] = new JLabel(axes[i] + ": 0.00 deg/s ");
            gyroLabels[i].setForeground(TEXT_COLOR);
            gyroLabels[i].setFont(new Font("Monospaced", Font.BOLD, 11));
            gyroBox.add(gyroLabels[i]);

            accelLabels[i] = new JLabel(axes[i] + ": 0.00 m/s² ");
            accelLabels[i].setForeground(TEXT_COLOR);
            accelLabels[i].setFont(new Font("Monospaced", Font.BOLD, 11));
            accelBox.add(accelLabels[i]);
        }

        imuBadges.add(gyroBox);
        imuBadges.add(accelBox);

        gyroGraphPanel = new ImuGraphPanel(gyroXData, gyroYData, gyroZData, "deg/s", true);
        accelGraphPanel = new ImuGraphPanel(accelXData, accelYData, accelZData, "m/s²", false);

        imuGraphCardPanel.add(gyroGraphPanel, "GYRO");
        imuGraphCardPanel.add(accelGraphPanel, "ACCEL");

        imuMasterPanel.add(imuControls, BorderLayout.NORTH);
        imuMasterPanel.add(imuGraphCardPanel, BorderLayout.CENTER);
        imuMasterPanel.add(imuBadges, BorderLayout.SOUTH);

        mainContentPanel.add(imuMasterPanel, CARD_SENSORS_IMU);
    }

    private void styleCheckBox(JCheckBox chk, Color c) {
        chk.setBackground(PANEL_BG);
        chk.setForeground(c);
        chk.setFont(new Font("SansSerif", Font.BOLD, 11));
        chk.setFocusPainted(false);
    }

    private void repaintImuGraphs() {
        if (gyroGraphPanel != null) gyroGraphPanel.repaint();
        if (accelGraphPanel != null) accelGraphPanel.repaint();
    }

    private void initOtherView() {
        JPanel otherPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        otherPanel.setBackground(BG_DARK);

        JLabel infoLabel = new JLabel("Use the right terminal pane to run interactive shell commands or SSH into user@" + ROBOT_IP);
        infoLabel.setForeground(TEXT_COLOR);
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        otherPanel.add(infoLabel);
        mainContentPanel.add(otherPanel, CARD_OTHER);
    }

    

    private JScrollPane createStyledScrollPane(Component content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setBackground(BG_DARK);
        scrollPane.getViewport().setBackground(BG_DARK);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JPanel createCardPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL_BG);
        panel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(BORDER_COLOR), title, TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 11), TEXT_COLOR),
                new EmptyBorder(6, 6, 6, 6)
        ));
        return panel;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBackground(BUTTON_BG);
        button.setForeground(TEXT_COLOR);
        button.setFont(new Font("SansSerif", Font.PLAIN, 11));
        button.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(5, 10, 5, 10)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createScaleButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBackground(BUTTON_BG);
        button.setForeground(TEXT_COLOR);
        button.setFont(new Font("Monospaced", Font.BOLD, 12));
        button.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(3, 8, 3, 8)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void styleComboBox(JComboBox<String> box) {
        box.setBackground(BUTTON_BG);
        box.setForeground(TEXT_COLOR);
        box.setFont(new Font("SansSerif", Font.PLAIN, 11));
    }

    

    private void setupScaleKeyBindings() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuMask), "scaleIncrease");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuMask | KeyEvent.SHIFT_DOWN_MASK), "scaleIncrease");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, menuMask), "scaleIncrease");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menuMask), "scaleDecrease");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, menuMask), "scaleDecrease");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, menuMask), "scaleReset");

        am.put("scaleIncrease", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                adjustScale(SCALE_STEP);
            }
        });
        am.put("scaleDecrease", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                adjustScale(-SCALE_STEP);
            }
        });
        am.put("scaleReset", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetScale();
            }
        });
    }

    private void adjustScale(float delta) {
        setUiScale(uiScale + delta);
    }

    private void resetScale() {
        setUiScale(1.0f);
    }

    private void setUiScale(float newScale) {
        uiScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        applyScale(this);
        revalidate();
        repaint();
    }

    /**
     * Recursively rescales font sizes (component fonts, and any TitledBorder titles)
     * relative to each component's original ("1.0x") size, which is lazily captured
     * the first time scaling is applied.
     */
    private void applyScale(Component comp) {
        if (comp instanceof JComponent) {
            JComponent jc = (JComponent) comp;

            Font f = jc.getFont();
            if (f != null) {
                Object baseObj = jc.getClientProperty("baseFontSize");
                float baseSize;
                if (baseObj instanceof Float) {
                    baseSize = (Float) baseObj;
                } else {
                    baseSize = f.getSize2D();
                    jc.putClientProperty("baseFontSize", baseSize);
                }
                jc.setFont(f.deriveFont(baseSize * uiScale));
            }

            Border border = jc.getBorder();
            if (border instanceof CompoundBorder) {
                Border outer = ((CompoundBorder) border).getOutsideBorder();
                if (outer instanceof TitledBorder) {
                    TitledBorder tb = (TitledBorder) outer;
                    Font tf = tb.getTitleFont();
                    if (tf != null) {
                        Object baseTitleObj = jc.getClientProperty("baseTitleFontSize");
                        float baseTitleSize;
                        if (baseTitleObj instanceof Float) {
                            baseTitleSize = (Float) baseTitleObj;
                        } else {
                            baseTitleSize = tf.getSize2D();
                            jc.putClientProperty("baseTitleFontSize", baseTitleSize);
                        }
                        tb.setTitleFont(tf.deriveFont(baseTitleSize * uiScale));
                    }
                }
            }
        }

        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                applyScale(child);
            }
        }
    }

    

    private void resetUiState() {
        appendLog("[SYSTEM] Resetting UI state and reconnecting client loops...");

        sensor1Data.clear();
        sensor2Data.clear();
        gyroXData.clear(); gyroYData.clear(); gyroZData.clear();
        accelXData.clear(); accelYData.clear(); accelZData.clear();

        for (int i = 0; i < 4; i++) {
            if (motorSliders[i] != null) motorSliders[i].setValue(0);
            if (servoSliders[i] != null) {
                servoSliders[i].setValue(90);
                servoSliders[i].setEnabled(true);
            }
            if (servoEnableToggles[i] != null) {
                servoEnableToggles[i].setSelected(true);
                servoEnableToggles[i].setText("Enabled");
                servoEnableToggles[i].setBackground(OK_GREEN);
            }
        }

        for (JLabel label : digitalStateLabels) {
            if (label != null) {
                label.setText("0 (OFF)");
                label.setBackground(DANGER_RED);
            }
        }

        stopBackgroundServices();
        startBackgroundServices();

        repaintImuGraphs();
        if (analogGraphPanel != null) analogGraphPanel.repaint();

        goToRoot();

        appendLog("[SYSTEM] Reset complete.");
    }

    private void startBackgroundServices() {
        threadsRunning = true;
        startUnifiedClientConnection();
    }

    private void stopBackgroundServices() {
        threadsRunning = false;
        if (telemetryThread != null) telemetryThread.interrupt();
    }

    private synchronized void updateStatusBadge(boolean reachable, boolean serverOnline, String statusMsg) {
        isReachable = reachable;
        isServerOnline = serverOnline;
        if (reachable && serverOnline) {
            statusLabel.setBackground(OK_GREEN);
            statusLabel.setForeground(Color.WHITE);
            statusLabel.setText(" ONLINE | " + statusMsg + " ");
        } else if (reachable) {
            statusLabel.setBackground(WARN_ORANGE);
            statusLabel.setForeground(Color.WHITE);
            statusLabel.setText(" HOST REACHABLE | SERVER DOWN ");
        } else {
            statusLabel.setBackground(DANGER_RED);
            statusLabel.setForeground(Color.WHITE);
            statusLabel.setText(" OFFLINE | " + statusMsg + " ");
        }
    }

    private void sendSocketCommand(String category, String device, String action, int port, int value) {
        String json = String.format("{\"category\":\"%s\",\"device\":\"%s\",\"action\":\"%s\",\"port\":%d,\"value\":%d}\n",
                category, device, action, port, value);

        appendLog("[CMD OUT] " + json.trim());

        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ROBOT_IP, ROBOT_PORT), 1000);
                try (OutputStream out = socket.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Exception ex) {
                appendLog("[CMD ERROR] Failed to send command: " + ex.getMessage());
            }
        }).start();
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timeStamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            logArea.append("[" + timeStamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private boolean executePing(String ip) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            List<String> command = new ArrayList<>();
            command.add("ping");
            command.add(isWindows ? "-n" : "-c");
            command.add("1");
            command.add(ip);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void startUnifiedClientConnection() {
        telemetryThread = new Thread(() -> {
            while (threadsRunning) {
                try {
                    boolean reachable = executePing(ROBOT_IP);

                    if (!reachable) {
                        SwingUtilities.invokeLater(() -> updateStatusBadge(false, false, "UNREACHABLE"));
                        Thread.sleep(2000);
                        continue;
                    }

                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ROBOT_IP, ROBOT_PORT), 2000);
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                            SwingUtilities.invokeLater(() -> updateStatusBadge(true, true, ROBOT_IP));
                            appendLog("[SYSTEM] Connected to server debugger stream at " + ROBOT_IP + ":" + ROBOT_PORT);

                            String line;
                            while (threadsRunning && (line = reader.readLine()) != null) {
                                if (!isServerOnline) {
                                    SwingUtilities.invokeLater(() -> updateStatusBadge(true, true, ROBOT_IP));
                                }

                                final String rawLine = line;
                                if (rawLine.contains("\"telemetry\"")) {
                                    parseAndApplyTelemetry(rawLine);
                                } else {
                                    appendLog("[SERVER DEBUG] " + rawLine);
                                }
                            }
                        }
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> updateStatusBadge(true, false, "PORT CLOSED"));
                        Thread.sleep(1500);
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> updateStatusBadge(false, false, "UNREACHABLE"));
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) { break; }
                }
            }
        });
        telemetryThread.setDaemon(true);
        telemetryThread.start();
    }

    private double extractJsonNumber(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\":");
        if (keyIdx == -1) return 0.0;
        int start = keyIdx + key.length() + 3;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void parseAndApplyTelemetry(String json) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (json.contains("\"imu\"")) {
                    double gx = extractJsonNumber(json, "gx");
                    double gy = extractJsonNumber(json, "gy");
                    double gz = extractJsonNumber(json, "gz");

                    double ax = extractJsonNumber(json, "ax");
                    double ay = extractJsonNumber(json, "ay");
                    double az = extractJsonNumber(json, "az");

                    gyroLabels[0].setText(String.format("X: %.2f deg/s", gx));
                    gyroLabels[1].setText(String.format("Y: %.2f deg/s", gy));
                    gyroLabels[2].setText(String.format("Z: %.2f deg/s", gz));

                    accelLabels[0].setText(String.format("X: %.2f m/s²", ax));
                    accelLabels[1].setText(String.format("Y: %.2f m/s²", ay));
                    accelLabels[2].setText(String.format("Z: %.2f m/s²", az));

                    synchronized (gyroXData) {
                        gyroXData.add(gx); if (gyroXData.size() > MAX_SAMPLES) gyroXData.remove(0);
                        gyroYData.add(gy); if (gyroYData.size() > MAX_SAMPLES) gyroYData.remove(0);
                        gyroZData.add(gz); if (gyroZData.size() > MAX_SAMPLES) gyroZData.remove(0);
                    }

                    synchronized (accelXData) {
                        accelXData.add(ax); if (accelXData.size() > MAX_SAMPLES) accelXData.remove(0);
                        accelYData.add(ay); if (accelYData.size() > MAX_SAMPLES) accelYData.remove(0);
                        accelZData.add(az); if (accelZData.size() > MAX_SAMPLES) accelZData.remove(0);
                    }

                    repaintImuGraphs();
                }

                int digIdx = json.indexOf("\"digital\":[");
                if (digIdx != -1) {
                    int endIdx = json.indexOf("]", digIdx);
                    if (endIdx != -1) {
                        String digStr = json.substring(digIdx + 11, endIdx);
                        String[] parts = digStr.split(",");
                        for (int i = 0; i < parts.length && i < digitalStateLabels.length; i++) {
                            int val = Integer.parseInt(parts[i].trim());
                            if (val == 1) {
                                digitalStateLabels[i].setText("1 (HIGH)");
                                digitalStateLabels[i].setBackground(OK_GREEN);
                            } else {
                                digitalStateLabels[i].setText("0 (LOW)");
                                digitalStateLabels[i].setBackground(DANGER_RED);
                            }
                        }
                    }
                }

                int anaIdx = json.indexOf("\"analog\":[");
                if (anaIdx != -1) {
                    int endIdx = json.indexOf("]", anaIdx);
                    if (endIdx != -1) {
                        String anaStr = json.substring(anaIdx + 10, endIdx);
                        String[] parts = anaStr.split(",");
                        int s1Idx = sensor1Select.getSelectedIndex();
                        int s2Idx = sensor2Select.getSelectedIndex();

                        synchronized (sensor1Data) {
                            if (s1Idx >= 0 && s1Idx < parts.length) {
                                sensor1Data.add(Double.parseDouble(parts[s1Idx].trim()));
                                if (sensor1Data.size() > MAX_SAMPLES) sensor1Data.remove(0);
                            }
                        }
                        synchronized (sensor2Data) {
                            if (s2Idx >= 0 && s2Idx < parts.length) {
                                sensor2Data.add(Double.parseDouble(parts[s2Idx].trim()));
                                if (sensor2Data.size() > MAX_SAMPLES) sensor2Data.remove(0);
                            }
                        }
                        if (analogGraphPanel != null) analogGraphPanel.repaint();
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    private class EmbeddedTerminalPanel extends JPanel {
        private JediTermWidget termWidget;
        private Process shellProcess;

        public EmbeddedTerminalPanel() {
            setLayout(new BorderLayout());
            setBackground(new Color(10, 10, 10));
            setBorder(BorderFactory.createTitledBorder(
                    new LineBorder(BORDER_COLOR), "Interactive Shell", TitledBorder.LEFT, TitledBorder.TOP,
                    new Font("SansSerif", Font.BOLD, 11), TEXT_COLOR
            ));

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            toolbar.setBackground(PANEL_BG);

            JButton btnSsh = createStyledButton("SSH...");
            btnSsh.addActionListener(e -> openSshSession());

            JButton btnRestart = createStyledButton("Restart Shell");
            btnRestart.addActionListener(e -> restartShell());

            toolbar.add(btnSsh);
            toolbar.add(btnRestart);

            add(toolbar, BorderLayout.NORTH);

            termWidget = buildTerminalWidget();
            add(termWidget, BorderLayout.CENTER);

            startShell(termWidget, 100, 30);

            
            Runtime.getRuntime().addShutdownHook(new Thread(this::disposeShell));
        }

        private JediTermWidget buildTerminalWidget() {
            JediTermWidget widget = new StyledJediTermWidget(100, 30, new DarkTerminalSettingsProvider());
            widget.setBackground(Color.BLACK);
            widget.getTerminalPanel().setCursorShape(CursorShape.BLINK_BLOCK);
            return widget;
        }

        private void startShell(JediTermWidget widget, int cols, int rows) {
            new Thread(() -> {
                try {
                    String shell = resolveLoginShell();
                    String initCommand = String.format(
                            "stty rows %d columns %d >/dev/null 2>&1; exec %s -l", rows, cols, shell);

                    ProcessBuilder pb = isMac()
                            ? new ProcessBuilder("script", "-q", "/dev/null", "/bin/sh", "-c", initCommand)
                            : new ProcessBuilder("script", "-qc", initCommand, "/dev/null");

                    Map<String, String> env = pb.environment();
                    env.put("TERM", "xterm-256color");
                    env.put("COLORTERM", "truecolor");
                    //working dir
                    pb.directory(new File(System.getProperty("user.dir")));
                    pb.redirectErrorStream(true);

                    shellProcess = pb.start();
                    TtyConnector connector = new ScriptPtyConnector(shellProcess);

                    SwingUtilities.invokeLater(() -> {
                        widget.setTtyConnector(connector);
                        widget.start();
                        widget.requestFocusInWindow();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            appendLog("[ERROR] Failed to spawn shell: " + ex.getMessage()));
                }
            }, "Ridar-Script-Shell-Thread").start();
        }

        private boolean isMac() {
            return System.getProperty("os.name", "").toLowerCase().contains("mac");
        }

        private String resolveLoginShell() {
            String envShell = System.getenv("SHELL");
            if (envShell != null && new File(envShell).exists()) return envShell;
            if (new File("/bin/zsh").exists()) return "/bin/zsh";
            return "/bin/bash";
        }

        private void openSshSession() {
            String target = JOptionPane.showInputDialog(
                    this, "SSH target (e.g. user@192.168.1.50):", "New SSH Session", JOptionPane.PLAIN_MESSAGE);
            if (target != null && !target.trim().isEmpty()) {
                sendRaw("ssh " + target.trim() + "\n");
            }
            termWidget.requestFocusInWindow();
        }

        private void sendRaw(String text) {
            try {
                TtyConnector connector = termWidget.getTtyConnector();
                if (connector != null) connector.write(text);
            } catch (IOException ex) {
                appendLog("[ERROR] Failed to send to shell: " + ex.getMessage());
            }
        }

        private void restartShell() {
            disposeShell();
            remove(termWidget);
            termWidget = buildTerminalWidget();
            add(termWidget, BorderLayout.CENTER);
            revalidate();
            repaint();
            startShell(termWidget, 100, 30);
        }

        private void disposeShell() {
            try {
                if (shellProcess != null && shellProcess.isAlive()) {
                    shellProcess.destroy();
                }
            } catch (Exception ignored) {}
        }
    }

    
    
    
    
    
    private static class DarkTerminalSettingsProvider extends DefaultSettingsProvider {
        @Override
        public TextStyle getDefaultStyle() {
            return new TextStyle(TerminalColor.WHITE, TerminalColor.BLACK);
        }

        @Override
        public TextStyle getSelectionColor() {
            return new TextStyle(TerminalColor.BLACK, TerminalColor.rgb(100, 130, 190));
        }
    }

    
    
    
    private static class StyledJediTermWidget extends JediTermWidget {
        StyledJediTermWidget(int columns, int lines, SettingsProvider settingsProvider) {
            super(columns, lines, settingsProvider);
        }

        @Override
        protected JScrollBar createScrollBar() {
            JScrollBar bar = super.createScrollBar();
            bar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
            bar.setOpaque(true);
            bar.setBackground(new Color(15, 15, 15));
            bar.setUI(new DarkScrollBarUI());
            return bar;
        }
    }

    private static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(90, 90, 90);
            thumbDarkShadowColor = new Color(20, 20, 20);
            thumbHighlightColor = new Color(110, 110, 110);
            thumbLightShadowColor = new Color(70, 70, 70);
            trackColor = new Color(15, 15, 15);
            trackHighlightColor = new Color(15, 15, 15);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroSizeButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroSizeButton();
        }

        private JButton zeroSizeButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }

    
    
    private static class ScriptPtyConnector implements TtyConnector {
        private final Process process;
        private final Reader reader;
        private final Writer writer;

        ScriptPtyConnector(Process process) {
            this.process = process;
            this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
            this.writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        }

        @Override
        public boolean init(Questioner questioner) {
            return true;
        }

        @Override
        public void close() {
            process.destroy();
        }

        @Override
        public String getName() {
            return "local-shell";
        }

        @Override
        public int read(char[] buf, int offset, int length) throws IOException {
            return reader.read(buf, offset, length);
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            writer.write(new String(bytes, StandardCharsets.UTF_8));
            writer.flush();
        }

        @Override
        public void write(String string) throws IOException {
            writer.write(string);
            writer.flush();
        }

        @Override
        public boolean isConnected() {
            return process.isAlive();
        }

        @Override
        public int waitFor() throws InterruptedException {
            return process.waitFor();
        }

        @Override
        public boolean ready() throws IOException {
            return reader.ready();
        }

        @Override
        public void resize(Dimension termWinSize) {
            
            
            
        }
    }

    private class AnalogGraphPanel extends JPanel {
        public AnalogGraphPanel() {
            setBackground(BG_DARK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(40, 40, 40));
            for (int i = 0; i < w; i += 40) g2.drawLine(i, 0, i, h);
            for (int i = 0; i < h; i += 40) g2.drawLine(0, i, w, i);

            double minVal = Double.MAX_VALUE;
            double maxVal = -Double.MAX_VALUE;

            synchronized (sensor1Data) {
                if (!sensor1Data.isEmpty()) {
                    minVal = Math.min(minVal, Collections.min(sensor1Data));
                    maxVal = Math.max(maxVal, Collections.max(sensor1Data));
                }
            }
            synchronized (sensor2Data) {
                if (!sensor2Data.isEmpty()) {
                    minVal = Math.min(minVal, Collections.min(sensor2Data));
                    maxVal = Math.max(maxVal, Collections.max(sensor2Data));
                }
            }

            if (minVal == Double.MAX_VALUE || maxVal == -Double.MAX_VALUE) {
                minVal = 0;
                maxVal = 1024;
            } else if (minVal == maxVal) {
                minVal -= 10;
                maxVal += 10;
            } else {
                double margin = (maxVal - minVal) * 0.1;
                minVal -= margin;
                maxVal += margin;
            }

            synchronized (sensor1Data) {
                drawSeries(g2, sensor1Data, ACCENT_BLUE, w, h, minVal, maxVal);
            }
            synchronized (sensor2Data) {
                drawSeries(g2, sensor2Data, ACCENT_RED, w, h, minVal, maxVal);
            }

            g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
            g2.setColor(TEXT_COLOR);
            g2.drawString(String.format("Max: %.1f", maxVal), w - 85, 15);
            g2.drawString(String.format("Min: %.1f", minVal), w - 85, h - 10);
        }

        private void drawSeries(Graphics2D g2, List<Double> data, Color color, int w, int h, double minVal, double maxVal) {
            if (data.size() < 2) return;
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.0f));

            double range = maxVal - minVal;
            int pts = data.size();

            for (int i = 0; i < pts - 1; i++) {
                int x1 = (int) ((double) i / MAX_SAMPLES * w);
                int y1 = (int) (h - ((data.get(i) - minVal) / range * h));
                int x2 = (int) ((double) (i + 1) / MAX_SAMPLES * w);
                int y2 = (int) (h - ((data.get(i + 1) - minVal) / range * h));
                g2.drawLine(x1, y1, x2, y2);
            }
        }
    }

    private class ImuGraphPanel extends JPanel {
        private final List<Double> dataX;
        private final List<Double> dataY;
        private final List<Double> dataZ;
        private final String unit;
        private final boolean isGyro;

        public ImuGraphPanel(List<Double> x, List<Double> y, List<Double> z, String unit, boolean isGyro) {
            this.dataX = x;
            this.dataY = y;
            this.dataZ = z;
            this.unit = unit;
            this.isGyro = isGyro;
            setBackground(BG_DARK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(40, 40, 40));
            for (int i = 0; i < w; i += 40) g2.drawLine(i, 0, i, h);
            for (int i = 0; i < h; i += 40) g2.drawLine(0, i, w, i);

            double minVal = Double.MAX_VALUE;
            double maxVal = -Double.MAX_VALUE;

            synchronized (dataX) {
                if (chkX != null && chkX.isSelected() && !dataX.isEmpty()) {
                    minVal = Math.min(minVal, Collections.min(dataX));
                    maxVal = Math.max(maxVal, Collections.max(dataX));
                }
                if (chkY != null && chkY.isSelected() && !dataY.isEmpty()) {
                    minVal = Math.min(minVal, Collections.min(dataY));
                    maxVal = Math.max(maxVal, Collections.max(dataY));
                }
                if (chkZ != null && chkZ.isSelected() && !dataZ.isEmpty()) {
                    minVal = Math.min(minVal, Collections.min(dataZ));
                    maxVal = Math.max(maxVal, Collections.max(dataZ));
                }
            }

            if (minVal == Double.MAX_VALUE || maxVal == -Double.MAX_VALUE) {
                minVal = isGyro ? -250.0 : -10.0;
                maxVal = isGyro ? 250.0 : 10.0;
            } else if (minVal == maxVal) {
                minVal -= 1.0;
                maxVal += 1.0;
            } else {
                double margin = Math.abs(maxVal - minVal) * 0.15;
                minVal -= margin;
                maxVal += margin;
            }

            if (minVal < 0 && maxVal > 0) {
                int zeroY = (int) (h - ((0.0 - minVal) / (maxVal - minVal) * h));
                g2.setColor(new Color(90, 90, 90));
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f}, 0.0f));
                g2.drawLine(0, zeroY, w, zeroY);
            }

            synchronized (dataX) {
                if (chkX != null && chkX.isSelected()) drawSeries(g2, dataX, ACCENT_BLUE, w, h, minVal, maxVal);
                if (chkY != null && chkY.isSelected()) drawSeries(g2, dataY, ACCENT_RED, w, h, minVal, maxVal);
                if (chkZ != null && chkZ.isSelected()) drawSeries(g2, dataZ, ACCENT_GREEN, w, h, minVal, maxVal);
            }

            g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
            g2.setColor(TEXT_COLOR);
            g2.drawString(String.format("Max: %.2f %s", maxVal, unit), w - 120, 15);
            g2.drawString(String.format("Min: %.2f %s", minVal, unit), w - 120, h - 10);
        }

        private void drawSeries(Graphics2D g2, List<Double> data, Color color, int w, int h, double minVal, double maxVal) {
            if (data.size() < 2) return;
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.0f));

            double range = maxVal - minVal;
            int pts = data.size();

            for (int i = 0; i < pts - 1; i++) {
                int x1 = (int) ((double) i / MAX_SAMPLES * w);
                int y1 = (int) (h - ((data.get(i) - minVal) / range * h));
                int x2 = (int) ((double) (i + 1) / MAX_SAMPLES * w);
                int y2 = (int) (h - ((data.get(i + 1) - minVal) / range * h));
                g2.drawLine(x1, y1, x2, y2);
            }
        }
    }
}