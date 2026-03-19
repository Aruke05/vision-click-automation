package com.example.autoscript.gui;

import com.example.autoscript.model.AppConfig;
import com.example.autoscript.model.CaptureMode;
import com.example.autoscript.model.ConditionConfig;
import com.example.autoscript.model.MonitorRegion;
import com.example.autoscript.model.WindowInfo;
import com.example.autoscript.script.ClickActionStep;
import com.example.autoscript.script.ImageTemplateCondition;
import com.example.autoscript.script.MonitorRule;
import com.example.autoscript.service.ActionExecutor;
import com.example.autoscript.service.CaptureService;
import com.example.autoscript.service.ConfigService;
import com.example.autoscript.service.GlobalHotkeyService;
import com.example.autoscript.service.ImageMatcher;
import com.example.autoscript.service.MonitoringService;
import com.example.autoscript.service.OpenCvTemplateMatcher;
import com.example.autoscript.service.RobotCaptureService;
import com.example.autoscript.service.WindowClickExecutor;
import com.example.autoscript.service.WindowService;
import com.example.autoscript.service.WindowsWindowService;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class MainFrame extends JFrame {

    private final WindowService windowService;
    private final ConfigService configService;
    private final ImageMatcher imageMatcher;
    private final ActionExecutor actionExecutor;
    private final CaptureService captureService;
    private final MonitoringService monitoringService;
    private final GlobalHotkeyService hotkeyService;
    private final boolean runningAsAdmin;

    private final WindowTableModel windowTableModel = new WindowTableModel();
    private final JTable windowTable = new JTable(windowTableModel);
    private final ConditionTableModel conditionTableModel = new ConditionTableModel();
    private final JTable conditionTable = new JTable(conditionTableModel);
    private final JTextArea logArea = new JTextArea();
    private final JLabel bindStatusLabel = new JLabel("当前未绑定窗口");
    private final JButton toggleLogPanelButton = new JButton("隐藏日志");

    private final JTextField conditionNameField = new JTextField("条件1");
    private final JSpinner regionOffsetXSpinner = new JSpinner(new SpinnerNumberModel(0, -10000, 10000, 1));
    private final JSpinner regionOffsetYSpinner = new JSpinner(new SpinnerNumberModel(0, -10000, 10000, 1));
    private final JSpinner regionWidthSpinner = new JSpinner(new SpinnerNumberModel(320, 1, 10000, 1));
    private final JSpinner regionHeightSpinner = new JSpinner(new SpinnerNumberModel(180, 1, 10000, 1));
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(500, 50, 60000, 50));
    private final JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(90, 1, 100, 1));
    private final JCheckBox repeatTriggerCheckBox = new JCheckBox("连续命中重复触发");
    private final JCheckBox backgroundClickModeCheckBox = new JCheckBox("后台点击模式(不抢前台/不移动鼠标)");
    private final JComboBox<CaptureMode> captureModeComboBox = new JComboBox<>(CaptureMode.values());
    private final JTextField startHotkeyField = new JTextField("F9");
    private final JTextField stopHotkeyField = new JTextField("F10");
    private final JSpinner clickXSpinner = new JSpinner(new SpinnerNumberModel(100, -10000, 10000, 1));
    private final JSpinner clickYSpinner = new JSpinner(new SpinnerNumberModel(100, -10000, 10000, 1));
    private final JTextArea templatePathsArea = new JTextArea(4, 24);
    private final JTextField conditionExpressionField = new JTextField();

    private final RegionOverlayWindow regionOverlay;
    private final javax.swing.Timer regionOverlayTimer;
    private final JButton togglePreviewOverlayButton = new JButton("显示区域与点击预览");
    private final ClickOverlayWindow clickOverlay;
    private final javax.swing.Timer clickOverlayTimer;

    private WindowInfo boundWindow;
    private AppConfig currentConfig;
    private boolean previewOverlayEnabled = false;
    private boolean regionOverlayErrorLogged = false;
    private boolean regionOverlayPositionLogged = false;
    private boolean clickOverlayErrorLogged = false;
    private boolean clickOverlayPositionLogged = false;
    private boolean syncingConditionEditor = false;
    private JSplitPane configVerticalSplitPane;
    private boolean logPanelCollapsed = false;
    private int lastLogDividerLocation = -1;

    public MainFrame() throws Exception {
        super("Java 桌面脚本化自动化工具（Windows 示例）");
        this.windowService = new WindowsWindowService();
        this.configService = new ConfigService();
        this.imageMatcher = new OpenCvTemplateMatcher();
        this.actionExecutor = new WindowClickExecutor(windowService);
        this.captureService = new RobotCaptureService(windowService);
        this.monitoringService = new MonitoringService(windowService, this.captureService, this::log);
        this.hotkeyService = new GlobalHotkeyService();
        this.runningAsAdmin = detectRunningAsAdmin();
        this.currentConfig = configService.load();

        this.regionOverlay = new RegionOverlayWindow(this);
        this.regionOverlayTimer = new javax.swing.Timer(220, e -> refreshRegionOverlayQuietly());
        this.clickOverlay = new ClickOverlayWindow(this);
        this.clickOverlayTimer = new javax.swing.Timer(220, e -> refreshClickOverlayQuietly());

        initLookAndFeel();
        initComponents();
        installRegionPreviewListeners();
        loadConfigToForm(currentConfig);
        try {
            registerGlobalHotkeys(currentConfig, true);
        } catch (Exception e) {
            log("全局热键注册失败: " + e.getMessage());
        }
        refreshWindows();
        tryRestoreBinding();
        log("当前进程权限: " + (runningAsAdmin ? "管理员" : "普通用户"));
    }

    private void initLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equalsIgnoreCase(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private void initComponents() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(1380, 980));
        setSize(1380, 1020);

        JPanel leftPanel = buildWindowPanel();
        JPanel rightPanel = buildConfigPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(520);
        add(splitPane, BorderLayout.CENTER);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown();
            }
        });
    }

    private void installRegionPreviewListeners() {
        ChangeListener listener = e -> refreshRegionOverlayQuietly();
        regionOffsetXSpinner.addChangeListener(listener);
        regionOffsetYSpinner.addChangeListener(listener);
        regionWidthSpinner.addChangeListener(listener);
        regionHeightSpinner.addChangeListener(listener);

        ChangeListener clickListener = e -> refreshClickOverlayQuietly();
        clickXSpinner.addChangeListener(clickListener);
        clickYSpinner.addChangeListener(clickListener);
    }

    private JPanel buildWindowPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("刷新窗口列表");
        JButton bindButton = new JButton("绑定选中窗口");
        JButton unbindButton = new JButton("解绑");

        refreshButton.addActionListener(e -> refreshWindows());
        bindButton.addActionListener(e -> bindSelectedWindow());
        unbindButton.addActionListener(e -> unbindWindow());

        topBar.add(refreshButton);
        topBar.add(bindButton);
        topBar.add(unbindButton);

        windowTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        windowTable.setRowHeight(24);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(windowTable), BorderLayout.CENTER);
        panel.add(bindStatusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("基础设置", buildGeneralConfigTab());
        tabbedPane.addTab("条件轮询", buildConditionConfigTab());

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("运行日志"));
        logScrollPane.setMinimumSize(new Dimension(100, 38));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(tabbedPane, BorderLayout.CENTER);
        topPanel.add(buildActionBar(), BorderLayout.SOUTH);

        this.configVerticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, logScrollPane);
        this.configVerticalSplitPane.setResizeWeight(0.90D);
        this.configVerticalSplitPane.setOneTouchExpandable(true);
        this.configVerticalSplitPane.setDividerLocation(0.90D);
        panel.add(this.configVerticalSplitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildGeneralConfigTab() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("运行参数"));
        GridBagConstraints gbc = createDefaultGbc();
        int row = 0;
        addFormRow(form, gbc, row++, "轮询间隔(ms)", intervalSpinner);
        addFormRow(form, gbc, row++, "重复触发", repeatTriggerCheckBox);
        addFormRow(form, gbc, row++, "点击模式", backgroundClickModeCheckBox);
        addFormRow(form, gbc, row++, "截图模式", captureModeComboBox);
        addFormRow(form, gbc, row++, "启动热键", startHotkeyField);
        addFormRow(form, gbc, row++, "停止热键", stopHotkeyField);
        addFormRow(form, gbc, row++, "热键示例", new JLabel("F9 / F10 / CTRL+F9"));
        return form;
    }

    private JPanel buildConditionConfigTab() {
        conditionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conditionTable.setRowHeight(24);
        conditionTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onConditionSelectionChanged();
            }
        });

        JPanel tablePanel = new JPanel(new BorderLayout(6, 6));
        tablePanel.setBorder(BorderFactory.createTitledBorder("轮询条件顺序（命中即停止本轮）"));
        tablePanel.add(new JScrollPane(conditionTable), BorderLayout.CENTER);

        JPanel manageButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addButton = new JButton("新增条件");
        JButton duplicateButton = new JButton("复制条件");
        JButton applyButton = new JButton("更新当前条件");
        JButton removeButton = new JButton("删除条件");
        JButton moveUpButton = new JButton("上移");
        JButton moveDownButton = new JButton("下移");
        addButton.addActionListener(e -> addConditionFromEditor());
        duplicateButton.addActionListener(e -> duplicateSelectedCondition());
        applyButton.addActionListener(e -> applyEditorToSelectedCondition());
        removeButton.addActionListener(e -> removeSelectedCondition());
        moveUpButton.addActionListener(e -> moveSelectedCondition(-1));
        moveDownButton.addActionListener(e -> moveSelectedCondition(1));
        manageButtons.add(addButton);
        manageButtons.add(duplicateButton);
        manageButtons.add(applyButton);
        manageButtons.add(removeButton);
        manageButtons.add(moveUpButton);
        manageButtons.add(moveDownButton);
        tablePanel.add(manageButtons, BorderLayout.SOUTH);

        JPanel editorPanel = buildConditionEditorPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, editorPanel);
        splitPane.setResizeWeight(0.43D);
        splitPane.setDividerLocation(230);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.add(splitPane, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildConditionEditorPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("条件编辑"));
        GridBagConstraints gbc = createDefaultGbc();
        int row = 0;
        addFormRow(form, gbc, row++, "条件名称", conditionNameField);
        addFormRow(form, gbc, row++, "区域 X(client)", regionOffsetXSpinner);
        addFormRow(form, gbc, row++, "区域 Y(client)", regionOffsetYSpinner);
        addFormRow(form, gbc, row++, "区域宽度", regionWidthSpinner);
        addFormRow(form, gbc, row++, "区域高度", regionHeightSpinner);
        addFormRow(form, gbc, row++, "相似度阈值(%)", thresholdSpinner);
        addFormRow(form, gbc, row++, "点击坐标 X(client)", clickXSpinner);
        addFormRow(form, gbc, row++, "点击坐标 Y(client)", clickYSpinner);

        JPanel templatePanel = new JPanel(new BorderLayout(6, 6));
        templatePathsArea.setLineWrap(false);
        templatePathsArea.setWrapStyleWord(false);
        templatePanel.add(new JScrollPane(templatePathsArea), BorderLayout.CENTER);
        JPanel templateButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton chooseTemplateButton = new JButton("导入模板(可多选)");
        JButton clearTemplateButton = new JButton("清空");
        JButton autoExprButton = new JButton("自动AND表达式");
        chooseTemplateButton.addActionListener(e -> chooseTemplates());
        clearTemplateButton.addActionListener(e -> clearTemplates());
        autoExprButton.addActionListener(e -> autoGenerateExpression());
        templateButtons.add(chooseTemplateButton);
        templateButtons.add(clearTemplateButton);
        templateButtons.add(autoExprButton);
        templatePanel.add(templateButtons, BorderLayout.NORTH);
        addFormRow(form, gbc, row++, "条件模板路径", templatePanel);

        addFormRow(form, gbc, row++, "条件表达式", conditionExpressionField);
        addFormRow(form, gbc, row++, "表达式示例", new JLabel("C1 AND (NOT C2)；也支持 && || !"));
        return form;
    }

    private JPanel buildActionBar() {
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton saveConfigButton = new JButton("保存配置");
        JButton saveProfileButton = new JButton("导出方案");
        JButton loadProfileButton = new JButton("载入方案");
        JButton startButton = new JButton("启动监控");
        JButton stopButton = new JButton("停止监控");
        JButton captureRegionButton = new JButton("截图判断区");

        saveConfigButton.addActionListener(e -> saveConfig());
        saveProfileButton.addActionListener(e -> exportProfile());
        loadProfileButton.addActionListener(e -> importProfile());
        startButton.addActionListener(e -> startMonitoring());
        stopButton.addActionListener(e -> stopMonitoring());
        captureRegionButton.addActionListener(e -> captureCurrentRegion());
        togglePreviewOverlayButton.addActionListener(e -> togglePreviewOverlay());
        toggleLogPanelButton.addActionListener(e -> toggleLogPanel());

        actionBar.add(saveConfigButton);
        actionBar.add(saveProfileButton);
        actionBar.add(loadProfileButton);
        actionBar.add(startButton);
        actionBar.add(stopButton);
        actionBar.add(captureRegionButton);
        actionBar.add(togglePreviewOverlayButton);
        actionBar.add(toggleLogPanelButton);
        return actionBar;
    }

    private GridBagConstraints createDefaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, java.awt.Component comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(comp, gbc);
    }

    private void refreshWindows() {
        try {
            List<WindowInfo> windows = windowService.listWindows();
            windowTableModel.setWindows(windows);
            log("窗口列表已刷新，数量=" + windows.size());
        } catch (Exception e) {
            showError("刷新窗口列表失败", e);
        }
    }

    private void bindSelectedWindow() {
        int selectedRow = windowTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个窗口。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        WindowInfo selected = windowTableModel.getWindowAt(selectedRow);
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "未找到选中的窗口。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boundWindow = selected;
        updateBindStatus();

        currentConfig.setLastWindowPid(selected.getProcessId());
        currentConfig.setLastWindowTitle(selected.getTitle());
        currentConfig.setLastWindowClassName(selected.getClassName());
        configService.save(currentConfig);
        log("已绑定窗口: " + selected.toDisplayText());
        refreshRegionOverlayQuietly();
        refreshClickOverlayQuietly();
    }

    private void unbindWindow() {
        if (monitoringService.isRunning()) {
            stopMonitoring();
        }
        boundWindow = null;
        updateBindStatus();
        previewOverlayEnabled = false;
        regionOverlayErrorLogged = false;
        regionOverlayPositionLogged = false;
        togglePreviewOverlayButton.setText("显示区域与点击预览");
        regionOverlayTimer.stop();
        regionOverlay.hideOverlay();
        clickOverlayErrorLogged = false;
        clickOverlayPositionLogged = false;
        clickOverlayTimer.stop();
        clickOverlay.hide();
        log("已解绑当前窗口");
    }

    private void updateBindStatus() {
        if (boundWindow == null) {
            bindStatusLabel.setText("当前未绑定窗口");
        } else {
            bindStatusLabel.setText("当前绑定: " + boundWindow.toDisplayText());
        }
    }

    private void chooseTemplates() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("图片文件", "png", "jpg", "jpeg", "bmp"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File[] files = chooser.getSelectedFiles();
        List<String> paths = new ArrayList<>();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file != null) {
                    paths.add(file.getAbsolutePath());
                }
            }
        } else if (chooser.getSelectedFile() != null) {
            paths.add(chooser.getSelectedFile().getAbsolutePath());
        }

        if (paths.isEmpty()) {
            return;
        }

        templatePathsArea.setText(String.join(System.lineSeparator(), paths));
        if (conditionExpressionField.getText().isBlank()) {
            conditionExpressionField.setText(buildDefaultConditionExpression(paths.size()));
        }
        applyEditorToSelectedConditionQuietly();
        log("已导入模板条件数量=" + paths.size());
    }

    private void clearTemplates() {
        templatePathsArea.setText("");
        conditionExpressionField.setText("");
        applyEditorToSelectedConditionQuietly();
        log("模板条件已清空");
    }

    private void autoGenerateExpression() {
        List<String> templatePaths = parseTemplatePaths(templatePathsArea.getText());
        if (templatePaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先导入模板图片。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String expression = buildDefaultConditionExpression(templatePaths.size());
        conditionExpressionField.setText(expression);
        applyEditorToSelectedConditionQuietly();
        log("已自动生成表达式: " + expression);
    }

    private void onConditionSelectionChanged() {
        if (syncingConditionEditor) {
            return;
        }
        if (previewOverlayEnabled) {
            hidePreviewOverlays();
            regionOverlayPositionLogged = false;
            clickOverlayPositionLogged = false;
        }
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        ConditionConfig condition = conditionTableModel.getConditionAt(selectedRow);
        if (condition == null) {
            return;
        }
        loadConditionToEditor(condition);
    }

    private void addConditionFromEditor() {
        applyEditorToSelectedConditionQuietly();
        ConditionConfig condition = readConditionFromEditor();
        if (condition.getName().isBlank()) {
            condition.setName("条件" + (conditionTableModel.getConditionCount() + 1));
        }
        int index = conditionTableModel.addCondition(condition);
        conditionTable.getSelectionModel().setSelectionInterval(index, index);
        log("已新增条件: " + condition.getName());
    }

    private void duplicateSelectedCondition() {
        applyEditorToSelectedConditionQuietly();
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个条件。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ConditionConfig condition = conditionTableModel.getConditionAt(selectedRow);
        if (condition == null) {
            return;
        }
        if (condition.getName().isBlank()) {
            condition.setName("条件" + (conditionTableModel.getConditionCount() + 1));
        } else {
            condition.setName(condition.getName() + "-副本");
        }
        int index = conditionTableModel.addCondition(condition);
        conditionTable.getSelectionModel().setSelectionInterval(index, index);
        log("已复制条件: " + condition.getName());
    }

    private void applyEditorToSelectedCondition() {
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个条件再更新。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ConditionConfig condition = readConditionFromEditor();
        if (condition.getName().isBlank()) {
            condition.setName("条件" + (selectedRow + 1));
        }
        conditionTableModel.updateCondition(selectedRow, condition);
        log("已更新条件: " + condition.getName());
    }

    private void applyEditorToSelectedConditionQuietly() {
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        ConditionConfig condition = readConditionFromEditor();
        if (condition.getName().isBlank()) {
            condition.setName("条件" + (selectedRow + 1));
        }
        conditionTableModel.updateCondition(selectedRow, condition);
    }

    private void removeSelectedCondition() {
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个条件。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ConditionConfig condition = conditionTableModel.getConditionAt(selectedRow);
        conditionTableModel.removeCondition(selectedRow);
        int count = conditionTableModel.getConditionCount();
        if (count > 0) {
            int next = Math.min(selectedRow, count - 1);
            conditionTable.getSelectionModel().setSelectionInterval(next, next);
        } else {
            loadConditionToEditor(new ConditionConfig());
            conditionNameField.setText("条件1");
        }
        log("已删除条件: " + (condition == null ? "" : condition.getName()));
    }

    private void moveSelectedCondition(int offset) {
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个条件。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int target = conditionTableModel.moveCondition(selectedRow, offset);
        conditionTable.getSelectionModel().setSelectionInterval(target, target);
    }

    private void loadConditionToEditor(ConditionConfig condition) {
        ConditionConfig source = condition == null ? new ConditionConfig() : condition;
        MonitorRegion region = source.getMonitorRegion() == null ? new MonitorRegion() : source.getMonitorRegion();
        syncingConditionEditor = true;
        try {
            conditionNameField.setText(source.getName());
            regionOffsetXSpinner.setValue(region.getX());
            regionOffsetYSpinner.setValue(region.getY());
            regionWidthSpinner.setValue(region.getWidth());
            regionHeightSpinner.setValue(region.getHeight());
            thresholdSpinner.setValue((int) Math.round(source.getThreshold() * 100.0D));
            clickXSpinner.setValue(source.getClickX());
            clickYSpinner.setValue(source.getClickY());
            templatePathsArea.setText(String.join(System.lineSeparator(), source.getTemplatePaths()));
            String expression = source.getConditionExpression();
            if ((expression == null || expression.isBlank()) && !source.getTemplatePaths().isEmpty()) {
                expression = buildDefaultConditionExpression(source.getTemplatePaths().size());
            }
            conditionExpressionField.setText(expression == null ? "" : expression);
        } finally {
            syncingConditionEditor = false;
        }
        refreshRegionOverlayQuietly();
        refreshClickOverlayQuietly();
    }

    private ConditionConfig readConditionFromEditor() {
        ConditionConfig condition = new ConditionConfig();
        condition.setName(conditionNameField.getText().trim());
        condition.setThreshold(((Number) thresholdSpinner.getValue()).doubleValue() / 100.0D);
        condition.setClickX(((Number) clickXSpinner.getValue()).intValue());
        condition.setClickY(((Number) clickYSpinner.getValue()).intValue());
        condition.setTemplatePaths(parseTemplatePaths(templatePathsArea.getText()));
        condition.setConditionExpression(conditionExpressionField.getText().trim());
        MonitorRegion region = new MonitorRegion();
        region.setX(((Number) regionOffsetXSpinner.getValue()).intValue());
        region.setY(((Number) regionOffsetYSpinner.getValue()).intValue());
        region.setWidth(((Number) regionWidthSpinner.getValue()).intValue());
        region.setHeight(((Number) regionHeightSpinner.getValue()).intValue());
        applyReferenceSizeToEditorRegion(region);
        condition.setMonitorRegion(region);
        return condition;
    }

    private void applyReferenceSizeToEditorRegion(MonitorRegion region) {
        if (region == null) {
            return;
        }
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow >= 0) {
            ConditionConfig selected = conditionTableModel.getConditionAt(selectedRow);
            MonitorRegion selectedRegion = selected == null ? null : selected.getMonitorRegion();
            if (selectedRegion != null && selectedRegion.hasReferenceSize()) {
                region.setReferenceWidth(selectedRegion.getReferenceWidth());
                region.setReferenceHeight(selectedRegion.getReferenceHeight());
                return;
            }
        }

        Rectangle clientRect = tryGetBoundClientRect();
        if (clientRect != null) {
            region.setReferenceWidth(clientRect.width);
            region.setReferenceHeight(clientRect.height);
        }
    }

    private Rectangle tryGetBoundClientRect() {
        try {
            if (boundWindow == null || !windowService.isAlive(boundWindow) || windowService.isMinimized(boundWindow)) {
                return null;
            }
            return windowService.getClientRectOnScreen(boundWindow);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void togglePreviewOverlay() {
        if (!previewOverlayEnabled) {
            if (boundWindow == null) {
                JOptionPane.showMessageDialog(this, "请先绑定窗口，再显示预览。", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            previewOverlayEnabled = true;
            regionOverlayErrorLogged = false;
            regionOverlayPositionLogged = false;
            clickOverlayErrorLogged = false;
            clickOverlayPositionLogged = false;
            togglePreviewOverlayButton.setText("隐藏区域与点击预览");
            regionOverlayTimer.start();
            clickOverlayTimer.start();
            hidePreviewOverlays();
            refreshRegionOverlayQuietly();
            refreshClickOverlayQuietly();
            log("已显示区域与点击预览");
        } else {
            previewOverlayEnabled = false;
            regionOverlayErrorLogged = false;
            regionOverlayPositionLogged = false;
            clickOverlayErrorLogged = false;
            clickOverlayPositionLogged = false;
            togglePreviewOverlayButton.setText("显示区域与点击预览");
            regionOverlayTimer.stop();
            clickOverlayTimer.stop();
            hidePreviewOverlays();
            log("已隐藏区域与点击预览");
        }
    }

    private void hidePreviewOverlays() {
        regionOverlay.hideOverlay();
        clickOverlay.hide();
    }

    private void toggleLogPanel() {
        if (configVerticalSplitPane == null) {
            return;
        }
        if (!logPanelCollapsed) {
            lastLogDividerLocation = configVerticalSplitPane.getDividerLocation();
            configVerticalSplitPane.setDividerLocation(0.99D);
            logPanelCollapsed = true;
            toggleLogPanelButton.setText("显示日志");
        } else {
            if (lastLogDividerLocation > 0) {
                configVerticalSplitPane.setDividerLocation(lastLogDividerLocation);
            } else {
                configVerticalSplitPane.setDividerLocation(0.90D);
            }
            logPanelCollapsed = false;
            toggleLogPanelButton.setText("隐藏日志");
        }
    }

    private void refreshRegionOverlayQuietly() {
        if (!previewOverlayEnabled) {
            return;
        }
        try {
            refreshRegionOverlay();
            regionOverlayErrorLogged = false;
        } catch (Exception ignored) {
            regionOverlay.hideOverlay();
            if (!regionOverlayErrorLogged) {
                regionOverlayErrorLogged = true;
                log("刷新判断区域框失败: " + ignored.getMessage());
            }
        }
    }

    private void refreshClickOverlayQuietly() {
        if (!previewOverlayEnabled) {
            return;
        }
        try {
            refreshClickOverlay();
            clickOverlayErrorLogged = false;
        } catch (Exception e) {
            clickOverlay.hide();
            if (!clickOverlayErrorLogged) {
                clickOverlayErrorLogged = true;
                log("刷新点击位置标记失败: " + e.getMessage());
            }
        }
    }

    private void refreshRegionOverlay() {
        if (!previewOverlayEnabled) {
            regionOverlay.hideOverlay();
            return;
        }
        if (boundWindow == null || !windowService.isAlive(boundWindow) || windowService.isMinimized(boundWindow)) {
            regionOverlay.hideOverlay();
            return;
        }

        AppConfig config = mergeGlobalWithCondition(readGlobalConfigFromForm(), readConditionFromEditor());
        Rectangle clientRect = windowService.getClientRectOnScreen(boundWindow);
        normalizeRegionCoordinateModeIfNeeded(config, clientRect, true);
        ensureReferenceSizeIfMissing(config, clientRect, false);
        Rectangle targetRect = config.getMonitorRegion().resolveWithin(clientRect);
        Rectangle overlayRect = toOverlayCoordinates(targetRect);
        regionOverlay.showAt(overlayRect);
        if (!regionOverlayPositionLogged) {
            regionOverlayPositionLogged = true;
            log("判断区域框坐标(native)=" + targetRect + ", overlay=" + overlayRect);
        }
    }

    private void refreshClickOverlay() {
        if (!previewOverlayEnabled) {
            clickOverlay.hide();
            return;
        }
        if (boundWindow == null || !windowService.isAlive(boundWindow) || windowService.isMinimized(boundWindow)) {
            clickOverlay.hide();
            return;
        }

        AppConfig config = mergeGlobalWithCondition(readGlobalConfigFromForm(), readConditionFromEditor());
        Rectangle clientRect = windowService.getClientRectOnScreen(boundWindow);
        normalizeClickCoordinateModeIfNeeded(config, clientRect, true);
        ensureReferenceSizeIfMissing(config, clientRect, false);
        Point clickPoint = config.resolveClickPoint(clientRect);
        Point nativePoint = windowService.clientToScreen(boundWindow, clickPoint.x, clickPoint.y);
        Point overlayPoint = toOverlayPoint(nativePoint);
        clickOverlay.showAt(overlayPoint);

        if (!clickOverlayPositionLogged) {
            clickOverlayPositionLogged = true;
            log("点击位置坐标(native)=" + nativePoint + ", overlay=" + overlayPoint);
        }
    }

    /**
     * JNA 拿到的是 native 像素坐标；Swing 顶层窗体在高 DPI/多屏下使用逻辑坐标。
     * 这里按命中屏幕的缩放进行转换，避免区域框跑到双屏中间。
     */
    private Rectangle toOverlayCoordinates(Rectangle nativeRect) {
        if (nativeRect == null) {
            return null;
        }
        GraphicsConfiguration gc = pickGraphicsConfig(nativeRect);
        if (gc == null) {
            return new Rectangle(nativeRect);
        }
        Rectangle logicalBounds = gc.getBounds();
        AffineTransform tx = gc.getDefaultTransform();
        double sx = tx.getScaleX() <= 0.0D ? 1.0D : tx.getScaleX();
        double sy = tx.getScaleY() <= 0.0D ? 1.0D : tx.getScaleY();

        int nativeOriginX = (int) Math.round(logicalBounds.x * sx);
        int nativeOriginY = (int) Math.round(logicalBounds.y * sy);

        int x = logicalBounds.x + (int) Math.round((nativeRect.x - nativeOriginX) / sx);
        int y = logicalBounds.y + (int) Math.round((nativeRect.y - nativeOriginY) / sy);
        int w = Math.max(1, (int) Math.round(nativeRect.width / sx));
        int h = Math.max(1, (int) Math.round(nativeRect.height / sy));
        Rectangle converted = new Rectangle(x, y, w, h);
        if (!intersectsAnyScreen(converted)) {
            return new Rectangle(nativeRect);
        }
        return converted;
    }

    private Point toOverlayPoint(Point nativePoint) {
        if (nativePoint == null) {
            return null;
        }
        Rectangle mapped = toOverlayCoordinates(new Rectangle(nativePoint.x, nativePoint.y, 1, 1));
        return mapped == null ? null : new Point(mapped.x, mapped.y);
    }

    private boolean ensureReferenceSizeIfMissing(AppConfig config,
                                                 Rectangle clientRect,
                                                 boolean logWhenApplied) {
        if (config == null || clientRect == null || clientRect.width <= 0 || clientRect.height <= 0) {
            return false;
        }
        MonitorRegion region = config.getMonitorRegion();
        if (region == null) {
            region = new MonitorRegion();
            config.setMonitorRegion(region);
        }
        if (region.hasReferenceSize()) {
            return false;
        }
        region.setReferenceWidth(clientRect.width);
        region.setReferenceHeight(clientRect.height);
        if (logWhenApplied) {
            log("该条件缺少基准分辨率，已自动记录为 " + clientRect.width + "x" + clientRect.height + "（后续按比例缩放）");
        }
        return true;
    }

    private boolean normalizeRegionCoordinateModeIfNeeded(AppConfig config,
                                                          Rectangle clientRect,
                                                          boolean applyToForm) {
        if (config == null || clientRect == null || config.getMonitorRegion() == null) {
            return false;
        }
        MonitorRegion region = config.getMonitorRegion();
        if (region.hasReferenceSize()) {
            return false;
        }
        Rectangle relativeRect = region.resolveWithin(clientRect);
        Rectangle absoluteRect = new Rectangle(
                region.getX(),
                region.getY(),
                Math.max(1, region.getWidth()),
                Math.max(1, region.getHeight())
        );

        boolean relativeVisible = hasPositiveIntersection(relativeRect, clientRect);
        boolean absoluteVisible = hasPositiveIntersection(absoluteRect, clientRect);

        if (!relativeVisible && absoluteVisible) {
            int clientX = region.getX() - clientRect.x;
            int clientY = region.getY() - clientRect.y;
            region.setX(clientX);
            region.setY(clientY);
            if (applyToForm) {
                regionOffsetXSpinner.setValue(clientX);
                regionOffsetYSpinner.setValue(clientY);
            }
            log("检测到区域坐标是屏幕坐标，已自动转换为 client 坐标: x=" + clientX + ", y=" + clientY);
            return true;
        }
        return false;
    }

    private boolean normalizeClickCoordinateModeIfNeeded(AppConfig config,
                                                         Rectangle clientRect,
                                                         boolean applyToForm) {
        if (config == null || clientRect == null) {
            return false;
        }
        MonitorRegion region = config.getMonitorRegion();
        if (region != null && region.hasReferenceSize()) {
            return false;
        }
        int clickX = config.getClickX();
        int clickY = config.getClickY();

        boolean relativeInside = clickX >= 0 && clickY >= 0
                && clickX < clientRect.width && clickY < clientRect.height;
        boolean absoluteInside = clickX >= clientRect.x && clickY >= clientRect.y
                && clickX < clientRect.x + clientRect.width
                && clickY < clientRect.y + clientRect.height;

        if (!relativeInside && absoluteInside) {
            int clientX = clickX - clientRect.x;
            int clientY = clickY - clientRect.y;
            config.setClickX(clientX);
            config.setClickY(clientY);
            if (applyToForm) {
                clickXSpinner.setValue(clientX);
                clickYSpinner.setValue(clientY);
            }
            log("检测到点击坐标是屏幕坐标，已自动转换为 client 坐标: x=" + clientX + ", y=" + clientY);
            return true;
        }
        return false;
    }

    private boolean hasPositiveIntersection(Rectangle a, Rectangle b) {
        if (a == null || b == null) {
            return false;
        }
        Rectangle c = a.intersection(b);
        return c.width > 0 && c.height > 0;
    }

    private GraphicsConfiguration pickGraphicsConfig(Rectangle nativeRect) {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (devices == null || devices.length == 0) {
            return getGraphicsConfiguration();
        }
        Point center = new Point(
                nativeRect.x + Math.max(0, nativeRect.width / 2),
                nativeRect.y + Math.max(0, nativeRect.height / 2)
        );
        GraphicsConfiguration best = null;
        long bestDistance = Long.MAX_VALUE;
        for (GraphicsDevice device : devices) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle nativeBounds = toNativeBounds(gc);
            if (nativeBounds.contains(center)) {
                return gc;
            }
            long distance = distanceToRect(center, nativeBounds);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = gc;
            }
        }
        return best != null ? best : getGraphicsConfiguration();
    }

    private Rectangle toNativeBounds(GraphicsConfiguration gc) {
        Rectangle logical = gc.getBounds();
        AffineTransform tx = gc.getDefaultTransform();
        double sx = tx.getScaleX() <= 0.0D ? 1.0D : tx.getScaleX();
        double sy = tx.getScaleY() <= 0.0D ? 1.0D : tx.getScaleY();
        return new Rectangle(
                (int) Math.round(logical.x * sx),
                (int) Math.round(logical.y * sy),
                Math.max(1, (int) Math.round(logical.width * sx)),
                Math.max(1, (int) Math.round(logical.height * sy))
        );
    }

    private long distanceToRect(Point point, Rectangle rect) {
        int dx = 0;
        if (point.x < rect.x) {
            dx = rect.x - point.x;
        } else if (point.x > rect.x + rect.width) {
            dx = point.x - (rect.x + rect.width);
        }

        int dy = 0;
        if (point.y < rect.y) {
            dy = rect.y - point.y;
        } else if (point.y > rect.y + rect.height) {
            dy = point.y - (rect.y + rect.height);
        }
        return (long) dx * dx + (long) dy * dy;
    }

    private boolean intersectsAnyScreen(Rectangle rect) {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (devices == null || devices.length == 0) {
            return true;
        }
        for (GraphicsDevice device : devices) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            if (gc != null && rect.intersects(gc.getBounds())) {
                return true;
            }
        }
        return false;
    }

    private void startMonitoring() {
        startMonitoring(false);
    }

    private void startMonitoring(boolean triggeredByHotkey) {
        if (monitoringService.isRunning()) {
            if (triggeredByHotkey) {
                log("热键触发启动: 监控已在运行中");
            } else {
                log("监控已经在运行中");
            }
            return;
        }
        if (boundWindow == null) {
            if (triggeredByHotkey) {
                log("热键触发启动失败: 请先绑定窗口");
            } else {
                JOptionPane.showMessageDialog(this, "请先绑定一个窗口。", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        if (!runningAsAdmin) {
            log("警告: 当前进程非管理员。若目标窗口/游戏为管理员权限，点击和按键可能不会生效。");
        }
        try {
            disableOverlaysForCapture("启动监控前已自动隐藏判断框/点击标记，避免干扰匹配。");
            currentConfig = readConfigFromForm();
            registerGlobalHotkeys(currentConfig, false);
            List<ConditionConfig> rawConditions = currentConfig.getConditions();
            if (rawConditions.isEmpty()) {
                if (triggeredByHotkey) {
                    log("热键触发启动失败: 请先配置至少一个轮询条件");
                } else {
                    JOptionPane.showMessageDialog(this, "请先新增至少一个轮询条件。", "提示", JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }

            Rectangle clientRect = windowService.getClientRectOnScreen(boundWindow);
            List<ConditionConfig> normalizedConditions = new ArrayList<>();
            List<MonitoringService.PollingCondition> pollingConditions = new ArrayList<>();

            for (int i = 0; i < rawConditions.size(); i++) {
                ConditionConfig rawCondition = rawConditions.get(i);
                if (rawCondition == null) {
                    continue;
                }
                String conditionName = resolveConditionName(rawCondition, i);
                AppConfig runtimeConfig = mergeGlobalWithCondition(currentConfig, rawCondition);
                normalizeRegionCoordinateModeIfNeeded(runtimeConfig, clientRect, false);
                normalizeClickCoordinateModeIfNeeded(runtimeConfig, clientRect, false);
                ensureReferenceSizeIfMissing(runtimeConfig, clientRect, true);

                List<LoadedTemplate> loadedTemplates = loadTemplateImages(runtimeConfig.getTemplatePaths());
                if (loadedTemplates.isEmpty()) {
                    throw new IllegalArgumentException("条件 [" + conditionName + "] 未配置模板图片");
                }

                int regionWidth = runtimeConfig.getMonitorRegion().getWidth();
                int regionHeight = runtimeConfig.getMonitorRegion().getHeight();
                for (LoadedTemplate template : loadedTemplates) {
                    int tw = template.image().getWidth();
                    int th = template.image().getHeight();
                    if (tw > regionWidth || th > regionHeight) {
                        String message = String.format(
                                "条件[%s]的模板 %s 尺寸为 %dx%d，大于检测区域 %dx%d。\n将尝试自动缩放模板匹配（可能降低精度）。",
                                conditionName, template.conditionName(), tw, th, regionWidth, regionHeight
                        );
                        log("提示: " + message.replace("\n", " "));
                    }
                }

                if (runtimeConfig.getConditionExpression() == null || runtimeConfig.getConditionExpression().isBlank()) {
                    runtimeConfig.setConditionExpression(buildDefaultConditionExpression(loadedTemplates.size()));
                }

                MonitorRule rule = new MonitorRule("image-match-click-" + (i + 1))
                        .setConditionExpression(runtimeConfig.getConditionExpression())
                        .addAction(new ClickActionStep(actionExecutor, windowService));
                for (LoadedTemplate template : loadedTemplates) {
                    rule.addCondition(new ImageTemplateCondition(template.conditionName(), imageMatcher, template.image()));
                }

                String conditionKey = "COND_" + i;
                pollingConditions.add(new MonitoringService.PollingCondition(
                        conditionKey,
                        conditionName,
                        runtimeConfig,
                        loadedTemplates.get(0).image(),
                        rule
                ));
                normalizedConditions.add(buildConditionConfigFromRuntime(runtimeConfig, conditionName));
                logConditionMapping(conditionName, loadedTemplates, runtimeConfig.getConditionExpression());
            }

            if (pollingConditions.isEmpty()) {
                throw new IllegalArgumentException("没有可用的轮询条件");
            }

            currentConfig.setConditions(normalizedConditions);
            conditionTableModel.setConditions(normalizedConditions);
            if (conditionTableModel.getConditionCount() > 0) {
                int selected = Math.min(Math.max(conditionTable.getSelectedRow(), 0), conditionTableModel.getConditionCount() - 1);
                conditionTable.getSelectionModel().setSelectionInterval(selected, selected);
            }

            currentConfig.setLastWindowPid(boundWindow.getProcessId());
            currentConfig.setLastWindowTitle(boundWindow.getTitle());
            currentConfig.setLastWindowClassName(boundWindow.getClassName());
            configService.save(currentConfig);

            monitoringService.start(boundWindow, currentConfig, pollingConditions);
            log("重复触发: " + (currentConfig.isRepeatTrigger() ? "开启" : "关闭"));
            log("点击模式: " + (currentConfig.isBackgroundClickMode() ? "后台消息点击" : "前台真实点击"));
            log("热键: 启动=" + currentConfig.getStartHotkey() + ", 停止=" + currentConfig.getStopHotkey());
            if (currentConfig.isBackgroundClickMode() && isLikelyForegroundInputWindow(boundWindow)) {
                log("提示: 当前窗口类名为 " + boundWindow.getClassName()
                        + "，这类窗口常忽略后台消息点击；若无效可关闭后台点击模式。");
            }
            if (triggeredByHotkey) {
                log("热键触发: 已启动监控");
            }
            log("已启动监控任务");
        } catch (Exception e) {
            if (triggeredByHotkey) {
                log("热键触发启动失败: " + e.getMessage());
            } else {
                showError("启动监控失败", e);
            }
        }
    }

    private void logConditionMapping(String conditionName, List<LoadedTemplate> loadedTemplates, String expression) {
        log("条件[" + conditionName + "] 表达式: " + expression);
        for (LoadedTemplate template : loadedTemplates) {
            log("  " + template.conditionName() + " => " + template.path());
        }
    }

    private List<LoadedTemplate> loadTemplateImages(List<String> templatePaths) {
        List<LoadedTemplate> templates = new ArrayList<>();
        int index = 1;
        for (String path : templatePaths) {
            File file = new File(path);
            if (!file.exists()) {
                throw new IllegalArgumentException("模板文件不存在: " + file.getAbsolutePath());
            }
            BufferedImage image;
            try {
                image = ImageIO.read(file);
            } catch (Exception e) {
                throw new IllegalArgumentException("模板图读取失败: " + file.getAbsolutePath(), e);
            }
            if (image == null) {
                throw new IllegalArgumentException("无法读取图片: " + file.getAbsolutePath());
            }
            templates.add(new LoadedTemplate("C" + index, file.getAbsolutePath(), image));
            index++;
        }
        return templates;
    }

    private void stopMonitoring() {
        stopMonitoring(false);
    }

    private void stopMonitoring(boolean triggeredByHotkey) {
        if (!monitoringService.isRunning()) {
            if (triggeredByHotkey) {
                log("热键触发停止: 当前未在监控");
            }
            return;
        }
        monitoringService.stop();
        if (triggeredByHotkey) {
            log("热键触发: 已停止监控");
        }
    }

    private void captureCurrentRegion() {
        if (boundWindow == null) {
            JOptionPane.showMessageDialog(this, "请先绑定一个窗口。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        OverlayState overlayState = suspendOverlaysForCapture();
        try {
            AppConfig config = mergeGlobalWithCondition(readGlobalConfigFromForm(), readConditionFromEditor());
            Rectangle clientRect = windowService.getClientRectOnScreen(boundWindow);
            normalizeRegionCoordinateModeIfNeeded(config, clientRect, true);
            ensureReferenceSizeIfMissing(config, clientRect, false);

            try {
                Thread.sleep(80L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            BufferedImage image = captureService.capture(boundWindow, config.getMonitorRegion(), config.getCaptureMode());

            Path projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path captureDir = projectRoot.resolve("captures");
            Files.createDirectories(captureDir);
            String filename = "region-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".png";
            Path output = captureDir.resolve(filename);

            ImageIO.write(image, "png", output.toFile());
            log("已截图判断区: " + output + "，size=" + image.getWidth() + "x" + image.getHeight());
            if (isMostlyWhite(image)) {
                log("警告: 截图几乎全白，目标窗口可能为独占全屏/受保护渲染。建议切换为窗口化或无边框窗口化后再试。");
            }
            JOptionPane.showMessageDialog(this, "截图已保存:\n" + output, "完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showError("截图判断区失败", e);
        } finally {
            restoreOverlaysAfterCapture(overlayState);
        }
    }

    private void disableOverlaysForCapture(String logMessage) {
        boolean hadOverlay = previewOverlayEnabled;
        previewOverlayEnabled = false;
        regionOverlayErrorLogged = false;
        regionOverlayPositionLogged = false;
        clickOverlayErrorLogged = false;
        clickOverlayPositionLogged = false;
        regionOverlayTimer.stop();
        clickOverlayTimer.stop();
        hidePreviewOverlays();
        togglePreviewOverlayButton.setText("显示区域与点击预览");

        if (hadOverlay && logMessage != null && !logMessage.isBlank()) {
            log(logMessage);
        }
    }

    private OverlayState suspendOverlaysForCapture() {
        OverlayState state = new OverlayState(previewOverlayEnabled);
        disableOverlaysForCapture(null);
        return state;
    }

    private void restoreOverlaysAfterCapture(OverlayState state) {
        if (state == null) {
            return;
        }
        if (state.previewEnabled()) {
            previewOverlayEnabled = true;
            regionOverlayErrorLogged = false;
            regionOverlayPositionLogged = false;
            clickOverlayErrorLogged = false;
            clickOverlayPositionLogged = false;
            togglePreviewOverlayButton.setText("隐藏区域与点击预览");
            regionOverlayTimer.start();
            clickOverlayTimer.start();
            hidePreviewOverlays();
            refreshRegionOverlayQuietly();
            refreshClickOverlayQuietly();
        }
    }

    private boolean isMostlyWhite(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        long total = 0L;
        long white = 0L;
        int step = Math.max(1, Math.min(width, height) / 80);
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r >= 245 && g >= 245 && b >= 245) {
                    white++;
                }
                total++;
            }
        }
        if (total <= 0) {
            return false;
        }
        return (double) white / (double) total >= 0.98D;
    }

    private void saveConfig() {
        saveConfig(true);
    }

    private void saveConfig(boolean applyHotkeys) {
        try {
            applyEditorToSelectedConditionQuietly();
            currentConfig = readConfigFromForm();
            if (applyHotkeys) {
                registerGlobalHotkeys(currentConfig, false);
            }
            if (boundWindow != null) {
                Rectangle clientRect = windowService.getClientRectOnScreen(boundWindow);
                List<ConditionConfig> normalizedConditions = new ArrayList<>();
                List<ConditionConfig> conditions = currentConfig.getConditions();
                for (int i = 0; i < conditions.size(); i++) {
                    ConditionConfig condition = conditions.get(i);
                    AppConfig runtimeConfig = mergeGlobalWithCondition(currentConfig, condition);
                    normalizeRegionCoordinateModeIfNeeded(runtimeConfig, clientRect, false);
                    normalizeClickCoordinateModeIfNeeded(runtimeConfig, clientRect, false);
                    ensureReferenceSizeIfMissing(runtimeConfig, clientRect, true);
                    normalizedConditions.add(buildConditionConfigFromRuntime(runtimeConfig, resolveConditionName(condition, i)));
                }
                currentConfig.setConditions(normalizedConditions);
                conditionTableModel.setConditions(normalizedConditions);
                if (conditionTableModel.getConditionCount() > 0) {
                    int selected = Math.min(Math.max(conditionTable.getSelectedRow(), 0), conditionTableModel.getConditionCount() - 1);
                    conditionTable.getSelectionModel().setSelectionInterval(selected, selected);
                }
                currentConfig.setLastWindowPid(boundWindow.getProcessId());
                currentConfig.setLastWindowTitle(boundWindow.getTitle());
                currentConfig.setLastWindowClassName(boundWindow.getClassName());
            }
            configService.save(currentConfig);
            log("配置已保存到: " + configService.getConfigPath());
        } catch (Exception e) {
            if (applyHotkeys) {
                showError("保存配置失败", e);
            } else {
                log("保存配置失败: " + e.getMessage());
            }
        }
    }

    private AppConfig readConfigFromForm() {
        applyEditorToSelectedConditionQuietly();
        AppConfig config = readGlobalConfigFromForm();
        List<ConditionConfig> conditions = conditionTableModel.getConditions();
        if (conditions.isEmpty()) {
            ConditionConfig editor = readConditionFromEditor();
            if (!editor.getTemplatePaths().isEmpty() || !editor.getConditionExpression().isBlank()) {
                if (editor.getName().isBlank()) {
                    editor.setName("条件1");
                }
                conditions.add(editor);
            }
        }
        config.setConditions(conditions);
        return config;
    }

    private AppConfig readGlobalConfigFromForm() {
        AppConfig config = new AppConfig();
        config.setIntervalMs(((Number) intervalSpinner.getValue()).intValue());
        config.setRepeatTrigger(repeatTriggerCheckBox.isSelected());
        config.setBackgroundClickMode(backgroundClickModeCheckBox.isSelected());
        config.setCaptureMode((CaptureMode) captureModeComboBox.getSelectedItem());
        config.setStartHotkey(GlobalHotkeyService.normalizeHotkeyText(startHotkeyField.getText(), "F9"));
        config.setStopHotkey(GlobalHotkeyService.normalizeHotkeyText(stopHotkeyField.getText(), "F10"));
        return config;
    }

    private List<String> parseTemplatePaths(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ArrayList<>();
        }
        String[] parts = rawText.split("[\\r\\n;]+");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : parts) {
            String normalized = part == null ? "" : part.trim();
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    private String buildDefaultConditionExpression(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append(" AND ");
            }
            builder.append("C").append(i);
        }
        return builder.toString();
    }

    private void loadConfigToForm(AppConfig config) {
        if (config == null) {
            return;
        }
        intervalSpinner.setValue(config.getIntervalMs());
        repeatTriggerCheckBox.setSelected(config.isRepeatTrigger());
        backgroundClickModeCheckBox.setSelected(config.isBackgroundClickMode());
        captureModeComboBox.setSelectedItem(config.getCaptureMode());
        String normalizedStart = "F9";
        String normalizedStop = "F10";
        try {
            normalizedStart = GlobalHotkeyService.normalizeHotkeyText(config.getStartHotkey(), "F9");
        } catch (Exception e) {
            log("启动热键配置无效，已回退为 F9: " + e.getMessage());
        }
        try {
            normalizedStop = GlobalHotkeyService.normalizeHotkeyText(config.getStopHotkey(), "F10");
        } catch (Exception e) {
            log("停止热键配置无效，已回退为 F10: " + e.getMessage());
        }
        startHotkeyField.setText(normalizedStart);
        stopHotkeyField.setText(normalizedStop);

        List<ConditionConfig> conditions = config.getConditions();
        if (conditions.isEmpty()) {
            ConditionConfig defaultCondition = new ConditionConfig();
            defaultCondition.setName("条件1");
            conditions.add(defaultCondition);
        }
        conditionTableModel.setConditions(conditions);
        if (conditionTableModel.getConditionCount() > 0) {
            conditionTable.getSelectionModel().setSelectionInterval(0, 0);
            ConditionConfig condition = conditionTableModel.getConditionAt(0);
            loadConditionToEditor(condition);
        } else {
            loadConditionToEditor(new ConditionConfig());
            conditionNameField.setText("条件1");
        }
    }

    private String resolveConditionName(ConditionConfig condition, int index) {
        if (condition != null && condition.getName() != null && !condition.getName().isBlank()) {
            return condition.getName().trim();
        }
        return "条件" + (index + 1);
    }

    private AppConfig mergeGlobalWithCondition(AppConfig globalConfig, ConditionConfig condition) {
        AppConfig merged = new AppConfig();
        AppConfig sourceGlobal = globalConfig == null ? new AppConfig() : globalConfig;
        ConditionConfig sourceCondition = condition == null ? new ConditionConfig() : condition;
        merged.setIntervalMs(sourceGlobal.getIntervalMs());
        merged.setRepeatTrigger(sourceGlobal.isRepeatTrigger());
        merged.setBackgroundClickMode(sourceGlobal.isBackgroundClickMode());
        merged.setCaptureMode(sourceGlobal.getCaptureMode());
        merged.setStartHotkey(sourceGlobal.getStartHotkey());
        merged.setStopHotkey(sourceGlobal.getStopHotkey());
        merged.setThreshold(sourceCondition.getThreshold());
        merged.setClickX(sourceCondition.getClickX());
        merged.setClickY(sourceCondition.getClickY());
        merged.setTemplatePaths(sourceCondition.getTemplatePaths());
        merged.setConditionExpression(sourceCondition.getConditionExpression());
        merged.setMonitorRegion(cloneRegion(sourceCondition.getMonitorRegion()));
        return merged;
    }

    private ConditionConfig buildConditionConfigFromRuntime(AppConfig config, String conditionName) {
        ConditionConfig condition = new ConditionConfig();
        condition.setName(conditionName);
        condition.setThreshold(config.getThreshold());
        condition.setClickX(config.getClickX());
        condition.setClickY(config.getClickY());
        condition.setTemplatePaths(config.getTemplatePaths());
        condition.setConditionExpression(config.getConditionExpression());
        condition.setMonitorRegion(cloneRegion(config.getMonitorRegion()));
        return condition;
    }

    private MonitorRegion cloneRegion(MonitorRegion source) {
        MonitorRegion region = new MonitorRegion();
        if (source != null) {
            region.setAnchor(source.getAnchor());
            region.setX(source.getX());
            region.setY(source.getY());
            region.setWidth(source.getWidth());
            region.setHeight(source.getHeight());
            region.setReferenceWidth(source.getReferenceWidth());
            region.setReferenceHeight(source.getReferenceHeight());
        }
        return region;
    }

    private void exportProfile() {
        try {
            applyEditorToSelectedConditionQuietly();
            AppConfig config = readConfigFromForm();
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("导出方案");
            chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件", "json"));
            chooser.setSelectedFile(new File("auto-script-profile.json"));
            int result = chooser.showSaveDialog(this);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return;
            }
            File selectedFile = chooser.getSelectedFile();
            Path outputPath = selectedFile.toPath();
            if (!outputPath.getFileName().toString().toLowerCase().endsWith(".json")) {
                outputPath = outputPath.resolveSibling(outputPath.getFileName() + ".json");
            }
            configService.save(outputPath, config);
            log("方案已导出: " + outputPath);
        } catch (Exception e) {
            showError("导出方案失败", e);
        }
    }

    private void importProfile() {
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("载入方案");
            chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件", "json"));
            int result = chooser.showOpenDialog(this);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return;
            }
            Path path = chooser.getSelectedFile().toPath();
            AppConfig loaded = configService.load(path);
            currentConfig = loaded;
            loadConfigToForm(loaded);
            try {
                registerGlobalHotkeys(currentConfig, false);
            } catch (Exception e) {
                log("方案载入后热键注册失败: " + e.getMessage());
            }
            log("方案已载入: " + path);
        } catch (Exception e) {
            showError("载入方案失败", e);
        }
    }

    private void tryRestoreBinding() {
        try {
            List<WindowInfo> windows = windowService.listWindows();
            Optional<WindowInfo> restored = windowService.tryRestoreBinding(
                    windows,
                    currentConfig.getLastWindowPid(),
                    currentConfig.getLastWindowTitle(),
                    currentConfig.getLastWindowClassName()
            );
            restored.ifPresent(window -> {
                boundWindow = window;
                updateBindStatus();
                log("已根据配置自动恢复绑定: " + window.toDisplayText());
                refreshRegionOverlayQuietly();
                refreshClickOverlayQuietly();
            });
        } catch (Exception e) {
            log("恢复绑定失败: " + e.getMessage());
        }
    }

    private void registerGlobalHotkeys(AppConfig config, boolean logSuccess) {
        AppConfig target = config == null ? new AppConfig() : config;
        String startHotkey = GlobalHotkeyService.normalizeHotkeyText(target.getStartHotkey(), "F9");
        String stopHotkey = GlobalHotkeyService.normalizeHotkeyText(target.getStopHotkey(), "F10");
        target.setStartHotkey(startHotkey);
        target.setStopHotkey(stopHotkey);

        hotkeyService.registerHotkeys(startHotkey, stopHotkey,
                () -> SwingUtilities.invokeLater(() -> {
                    if (isDisplayable()) {
                        startMonitoring(true);
                    }
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    if (isDisplayable()) {
                        stopMonitoring(true);
                    }
                }));

        startHotkeyField.setText(startHotkey);
        stopHotkeyField.setText(stopHotkey);
        if (logSuccess) {
            log("全局热键已注册: 启动=" + startHotkey + ", 停止=" + stopHotkey);
        }
    }

    private void log(String message) {
        String line = String.format("[%s] %s",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                message);
        System.out.println(line);
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showError(String title, Exception e) {
        e.printStackTrace();
        log(title + ": " + e.getMessage());
        JOptionPane.showMessageDialog(this, title + "\n" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
    }

    private void shutdown() {
        try {
            regionOverlayTimer.stop();
            regionOverlay.hideOverlay();
            clickOverlayTimer.stop();
            clickOverlay.hide();
            if (monitoringService.isRunning()) {
                monitoringService.stop();
            }
            saveConfig(false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            hotkeyService.close();
            regionOverlay.dispose();
            clickOverlay.dispose();
        }
    }

    private record LoadedTemplate(String conditionName, String path, BufferedImage image) {
    }

    private record OverlayState(boolean previewEnabled) {
    }

    private boolean detectRunningAsAdmin() {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase().contains("win")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("cmd", "/c", "net session")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLikelyForegroundInputWindow(WindowInfo window) {
        if (window == null || window.getClassName() == null) {
            return false;
        }
        String className = window.getClassName().trim().toLowerCase();
        return className.contains("unreal")
                || className.contains("unity")
                || className.contains("chrome_widgetwin");
    }
}
