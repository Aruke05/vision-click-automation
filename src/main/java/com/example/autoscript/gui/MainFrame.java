package com.example.autoscript.gui;

import com.example.autoscript.model.AppConfig;
import com.example.autoscript.model.CaptureMode;
import com.example.autoscript.model.ConditionConfig;
import com.example.autoscript.model.ConditionTriggerActionConfig;
import com.example.autoscript.model.ConditionTriggerActionType;
import com.example.autoscript.model.MonitorRegion;
import com.example.autoscript.model.WindowInfo;
import com.example.autoscript.script.ClickActionStep;
import com.example.autoscript.script.ImageTemplateCondition;
import com.example.autoscript.script.MonitorRule;
import com.example.autoscript.script.StopMonitoringActionStep;
import com.example.autoscript.service.ActionExecutor;
import com.example.autoscript.service.ApplicationRelauncher;
import com.example.autoscript.service.CaptureService;
import com.example.autoscript.service.ConfigService;
import com.example.autoscript.service.GlobalHotkeyService;
import com.example.autoscript.service.ImageMatcher;
import com.example.autoscript.service.JavaImageTemplateMatcher;
import com.example.autoscript.service.MonitoringService;
import com.example.autoscript.service.OpenCvTemplateMatcher;
import com.example.autoscript.service.OpenCvRecoveryStateStore;
import com.example.autoscript.service.RobotCaptureService;
import com.example.autoscript.service.VcRedistributableInstaller;
import com.example.autoscript.service.WindowClickExecutor;
import com.example.autoscript.service.WindowService;
import com.example.autoscript.service.WindowsWindowService;

import javax.imageio.ImageIO;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.DropMode;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainFrame extends JFrame {

    private static final CaptureMode DEFAULT_CAPTURE_MODE = CaptureMode.WINDOW_HANDLE_FALLBACK_SCREEN;
    private static final int DEFAULT_BIND_PANEL_WIDTH = 520;
    private static final Dimension CONDITION_EDITOR_DIALOG_SIZE = new Dimension(780, 760);

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
    private final JLabel bindStatusLabel = new JLabel("当前未绑定窗口，请先打开绑定栏并绑定窗口");
    private final JButton showBindPanelButton = new JButton("打开绑定栏");
    private final JButton hideBindPanelButton = new JButton("收起绑定栏");
    private final JButton toggleLogPanelButton = new JButton("隐藏日志");

    private final JTextField conditionNameField = new JTextField("条件1");
    private final JSpinner regionOffsetXSpinner = new JSpinner(new SpinnerNumberModel(0, -10000, 10000, 1));
    private final JSpinner regionOffsetYSpinner = new JSpinner(new SpinnerNumberModel(0, -10000, 10000, 1));
    private final JSpinner regionWidthSpinner = new JSpinner(new SpinnerNumberModel(320, 1, 10000, 1));
    private final JSpinner regionHeightSpinner = new JSpinner(new SpinnerNumberModel(180, 1, 10000, 1));
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(500, 50, 60000, 50));
    private final JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(90, 1, 100, 1));
    private final JCheckBox repeatTriggerCheckBox = new JCheckBox("连续命中重复触发");
    private final JCheckBox moveWindowToBackAfterTriggerCheckBox = new JCheckBox("触发后将绑定窗口移到最底层");
    private final JCheckBox backgroundClickModeCheckBox = new JCheckBox("后台点击模式(不抢前台/不移动鼠标)");
    private final JTextField startHotkeyField = new JTextField("F9");
    private final JTextField stopHotkeyField = new JTextField("F10");
    private final JSpinner clickXSpinner = new JSpinner(new SpinnerNumberModel(100, -10000, 10000, 1));
    private final JSpinner clickYSpinner = new JSpinner(new SpinnerNumberModel(100, -10000, 10000, 1));
    private final JComboBox<ConditionTriggerActionType> triggerActionTypeComboBox =
            new JComboBox<>(ConditionTriggerActionType.values());
    private final JTextArea templatePathsArea = new JTextArea(4, 24);
    private final JTextField conditionExpressionField = new JTextField();
    private FormRow clickXRow;
    private FormRow clickYRow;
    private FormRow clickPickerRow;

    private final RegionOverlayWindow regionOverlay;
    private final javax.swing.Timer regionOverlayTimer;
    private final JButton togglePreviewOverlayButton = new JButton("显示区域与点击预览");
    private final ClickOverlayWindow clickOverlay;
    private final javax.swing.Timer clickOverlayTimer;
    private final SelectionMaskOverlayWindow selectionMaskOverlay;
    private final javax.swing.Timer basicConfigAutoSaveTimer;
    private final javax.swing.Timer boundWindowWatchTimer;

    private WindowInfo boundWindow;
    private CaptureMode boundCaptureMode = DEFAULT_CAPTURE_MODE;
    private AppConfig currentConfig;
    private boolean previewOverlayEnabled = false;
    private boolean regionOverlayErrorLogged = false;
    private boolean regionOverlayPositionLogged = false;
    private boolean clickOverlayErrorLogged = false;
    private boolean clickOverlayPositionLogged = false;
    private boolean syncingConditionEditor = false;
    private int editingConditionRow = -1;
    private int previewConditionRow = -1;
    private JPanel bindPanelContainer;
    private JSplitPane configVerticalSplitPane;
    private JDialog conditionEditorDialog;
    private boolean bindPanelCollapsed = false;
    private boolean logPanelCollapsed = false;
    private int lastLogDividerLocation = -1;
    private boolean suppressBasicConfigAutoSave = false;
    private ConditionConfig copiedConditionClipboard;
    private String imageMatcherStartupWarning;
    private String imageMatcherStartupDiagnostic;
    private boolean imageMatcherUsingOpenCv;
    private final AtomicBoolean vcRuntimeInstallInProgress = new AtomicBoolean(false);
    private final OpenCvRecoveryStateStore openCvRecoveryStateStore = new OpenCvRecoveryStateStore();
    private final Path projectRootPath = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    private final Path scriptDirPath = projectRootPath.resolve("script").toAbsolutePath().normalize();
    private final Path capturesDirPath = projectRootPath.resolve("captures").toAbsolutePath().normalize();

    public MainFrame() throws Exception {
        super("Java 桌面脚本化自动化工具");
        this.windowService = new WindowsWindowService();
        this.configService = new ConfigService();
        this.imageMatcher = createImageMatcher();
        this.actionExecutor = new WindowClickExecutor(windowService);
        this.captureService = new RobotCaptureService(windowService, this::log);
        this.monitoringService = new MonitoringService(windowService, this.captureService, this::log, this::showMonitoringStoppedReminder);
        this.hotkeyService = new GlobalHotkeyService();
        this.runningAsAdmin = detectRunningAsAdmin();
        this.currentConfig = configService.load();

        this.regionOverlay = new RegionOverlayWindow(this);
        this.regionOverlayTimer = new javax.swing.Timer(220, e -> refreshRegionOverlayQuietly());
        this.clickOverlay = new ClickOverlayWindow(this);
        this.clickOverlayTimer = new javax.swing.Timer(220, e -> refreshClickOverlayQuietly());
        this.selectionMaskOverlay = new SelectionMaskOverlayWindow(this);
        this.basicConfigAutoSaveTimer = new javax.swing.Timer(180, e -> autoSaveBasicConfigNow());
        this.basicConfigAutoSaveTimer.setRepeats(false);
        this.boundWindowWatchTimer = new javax.swing.Timer(1000, e -> ensureBoundWindowStillValid());

        initLookAndFeel();
        initComponents();
        collapseLogPanelByDefault();
        showImageMatcherStartupWarningIfNeeded();
        installRegionPreviewListeners();
        installBasicConfigAutoSaveListeners();
        installConditionEditorAutoApplyListeners();
        loadConfigToForm(currentConfig);
        try {
            registerGlobalHotkeys(currentConfig, true);
        } catch (Exception e) {
            log("全局热键注册失败: " + e.getMessage());
        }
        refreshWindows();
        tryRestoreBinding();
        ensureBindPanelState();
        boundWindowWatchTimer.start();
        log("当前进程权限: " + (runningAsAdmin ? "管理员" : "普通用户"));
    }

    private ImageMatcher createImageMatcher() {
        try {
            OpenCvTemplateMatcher matcher = new OpenCvTemplateMatcher();
            imageMatcherUsingOpenCv = true;
            imageMatcherStartupWarning = null;
            imageMatcherStartupDiagnostic = null;
            return matcher;
        } catch (Throwable firstFailure) {
            String firstDetail = resolveRootCauseMessage(firstFailure);
            String firstSummary = summarizeDiagnosticDetail(firstDetail);
            String firstDiagnostic = buildOpenCvFailureDiagnostic(firstFailure);
            imageMatcherUsingOpenCv = false;
            boolean cacheCleared = clearJavaCppOpenCvCache();
            if (cacheCleared) {
                log("OpenCV 首次加载失败，已自动清理 .javacpp OpenCV 缓存并重试一次。"
                        + (firstDetail.isBlank() ? "" : " 首次失败详情: " + firstDetail));
                try {
                    OpenCvTemplateMatcher matcher = new OpenCvTemplateMatcher();
                    imageMatcherUsingOpenCv = true;
                    imageMatcherStartupWarning = null;
                    imageMatcherStartupDiagnostic = null;
                    log("OpenCV 缓存清理后重试成功，已恢复使用 OpenCV 模板匹配");
                    return matcher;
                } catch (Throwable retryFailure) {
                    String retryDetail = resolveRootCauseMessage(retryFailure);
                    String retrySummary = summarizeDiagnosticDetail(retryDetail);
                    String retryDiagnostic = buildOpenCvFailureDiagnostic(retryFailure);
                    imageMatcherStartupWarning = "OpenCV 本地库加载失败，已自动清理 .javacpp 缓存并重试一次，但仍未恢复。"
                            + " 已切换为纯 Java 模板匹配。"
                            + (retrySummary.isBlank() ? "" : "\n重试后摘要: " + retrySummary)
                            + "\n完整错误详情已写入运行日志。";
                    imageMatcherStartupDiagnostic = "首次加载失败诊断:\n"
                            + firstDiagnostic
                            + "\n\n清理 .javacpp OpenCV 缓存后再次重试，仍然失败。\n\n重试失败诊断:\n"
                            + retryDiagnostic;
                    log(imageMatcherStartupWarning.replace(System.lineSeparator(), " "));
                    if (!retryDetail.isBlank()) {
                        log("OpenCV 重试后完整错误详情: " + retryDetail);
                    }
                    return new JavaImageTemplateMatcher();
                }
            }
            imageMatcherStartupWarning = "OpenCV 本地库加载失败，已自动切换为纯 Java 模板匹配。"
                    + " 当前仍可运行，但匹配速度可能变慢。"
                    + (firstSummary.isBlank() ? "" : "\n错误摘要: " + firstSummary)
                    + "\n完整错误详情已写入运行日志。";
            imageMatcherStartupDiagnostic = firstDiagnostic;
            log(imageMatcherStartupWarning.replace(System.lineSeparator(), " "));
            if (!firstDetail.isBlank()) {
                log("OpenCV 完整错误详情: " + firstDetail);
            }
            return new JavaImageTemplateMatcher();
        }
    }

    private boolean clearJavaCppOpenCvCache() {
        Path cacheRoot = resolveJavaCppCacheRoot();
        if (cacheRoot == null || Files.notExists(cacheRoot) || !Files.isDirectory(cacheRoot)) {
            return false;
        }
        List<Path> targets = new ArrayList<>();
        try (var children = Files.list(cacheRoot)) {
            children.filter(Files::isDirectory)
                    .filter(path -> {
                        Path fileName = path.getFileName();
                        return fileName != null && fileName.toString().toLowerCase().startsWith("opencv-");
                    })
                    .forEach(targets::add);
        } catch (Exception e) {
            log("扫描 .javacpp 缓存失败，无法自动清理 OpenCV 缓存: " + e.getMessage());
            return false;
        }
        if (targets.isEmpty()) {
            return false;
        }
        boolean deletedAny = false;
        for (Path target : targets) {
            try {
                deleteDirectoryRecursively(target);
                deletedAny = true;
                log("已清理 OpenCV 缓存目录: " + target);
            } catch (Exception e) {
                log("清理 OpenCV 缓存目录失败: " + target + "，详情: " + e.getMessage());
            }
        }
        return deletedAny;
    }

    private Path resolveJavaCppCacheRoot() {
        String userHome = System.getProperty("user.home", "").trim();
        if (userHome.isBlank()) {
            return null;
        }
        return Paths.get(userHome, ".javacpp", "cache").toAbsolutePath().normalize();
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (directory == null || Files.notExists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void showImageMatcherStartupWarningIfNeeded() {
        Optional<OpenCvRecoveryStateStore.RecoveryState> recoveryState = loadOpenCvRecoveryState();
        if (recoveryState.isPresent()) {
            SwingUtilities.invokeLater(() -> handleOpenCvRecoveryStateOnStartup(recoveryState.get()));
            return;
        }
        if (imageMatcherStartupWarning == null || imageMatcherStartupWarning.isBlank()) {
            return;
        }
        String message = imageMatcherStartupWarning;
        SwingUtilities.invokeLater(() -> {
            java.awt.Component parent = isDisplayable() || isShowing() ? this : null;
            String prompt = message
                    + "\n\n检测到 OpenCV 本地依赖缺失。通常是 Microsoft Visual C++ x64 运行库未安装。"
                    + "\n是否现在自动下载安装并安装运行库？"
                    + "\n安装完成后程序会自动重启，并在下次启动时自动验证 OpenCV 是否恢复。";
            Object[] options = {"立即自动安装", "继续纯 Java 模式"};
            int choice = showScrollableOptionDialog(parent, prompt, "OpenCV 依赖缺失",
                    JOptionPane.WARNING_MESSAGE, options, options[0]);
            if (choice == JOptionPane.YES_OPTION) {
                installVcRuntimeAsync();
            }
        });
    }

    private void installVcRuntimeAsync() {
        if (!vcRuntimeInstallInProgress.compareAndSet(false, true)) {
            JOptionPane.showMessageDialog(this, "运行库安装任务已在进行中，请稍候。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                log("开始自动安装 Microsoft Visual C++ x64 运行库");
                VcRedistributableInstaller installer = new VcRedistributableInstaller(projectRootPath, runningAsAdmin, this::log);
                VcRedistributableInstaller.InstallResult result = installer.install();
                log(result.message());
                SwingUtilities.invokeLater(() -> handleVcRuntimeInstallCompletion(result));
            } finally {
                vcRuntimeInstallInProgress.set(false);
            }
        }, "vc-redist-installer");
        worker.setDaemon(true);
        worker.start();
    }

    private void handleVcRuntimeInstallCompletion(VcRedistributableInstaller.InstallResult result) {
        if (result == null) {
            showScrollableMessageDialog(this,
                    "运行库安装结果未知，当前继续使用纯 Java 匹配。",
                    "运行库安装",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        switch (result.status()) {
            case INSTALLED -> restartApplicationAfterVcInstall(result);
            case RESTART_REQUIRED -> {
                persistOpenCvRecoveryState(result);
                showScrollableMessageDialog(this,
                        buildVcInstallRestartRequiredMessage(result),
                        "运行库安装",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            case CANCELED -> showScrollableMessageDialog(this,
                    buildVcInstallCanceledMessage(result),
                    "运行库安装",
                    JOptionPane.WARNING_MESSAGE);
            case FAILED -> showScrollableMessageDialog(this,
                    buildVcInstallFailedMessage(result),
                    "运行库安装",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restartApplicationAfterVcInstall(VcRedistributableInstaller.InstallResult result) {
        persistOpenCvRecoveryState(result);
        try {
            boolean cacheCleared = clearJavaCppOpenCvCache();
            if (cacheCleared) {
                log("安装完成后已再次清理 .javacpp OpenCV 缓存，准备自动重启验证。");
            }
            ApplicationRelauncher relauncher = new ApplicationRelauncher(projectRootPath);
            ApplicationRelauncher.RelaunchPlan plan = relauncher.relaunch();
            log("运行库安装完成，已启动新进程验证 OpenCV: " + plan.description());
            shutdown();
            dispose();
            System.exit(0);
        } catch (Exception e) {
            String message = buildVcInstallRestartFailureMessage(result, e);
            log("自动重启失败: " + e.getMessage());
            showScrollableMessageDialog(this, message, "自动重启失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Optional<OpenCvRecoveryStateStore.RecoveryState> loadOpenCvRecoveryState() {
        try {
            return openCvRecoveryStateStore.load();
        } catch (Exception e) {
            log("读取 OpenCV 自动恢复状态失败: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void persistOpenCvRecoveryState(VcRedistributableInstaller.InstallResult result) {
        if (result == null) {
            return;
        }
        try {
            openCvRecoveryStateStore.save(new OpenCvRecoveryStateStore.RecoveryState(
                    System.currentTimeMillis(),
                    result.status().name(),
                    result.exitCode(),
                    safeText(result.message()),
                    result.installerPath() == null ? "" : result.installerPath().toString(),
                    result.logPath() == null ? "" : result.logPath().toString(),
                    safeText(imageMatcherStartupWarning),
                    safeText(imageMatcherStartupDiagnostic)
            ));
            log("已写入 OpenCV 自动恢复状态: " + openCvRecoveryStateStore.getStatePath());
        } catch (Exception e) {
            log("写入 OpenCV 自动恢复状态失败: " + e.getMessage());
        }
    }

    private void clearOpenCvRecoveryStateQuietly() {
        try {
            openCvRecoveryStateStore.clear();
        } catch (Exception e) {
            log("清理 OpenCV 自动恢复状态失败: " + e.getMessage());
        }
    }

    private void handleOpenCvRecoveryStateOnStartup(OpenCvRecoveryStateStore.RecoveryState state) {
        java.awt.Component parent = isDisplayable() || isShowing() ? this : null;
        if (imageMatcherUsingOpenCv) {
            clearOpenCvRecoveryStateQuietly();
            log("OpenCV 自动恢复验证通过，当前已恢复为 OpenCV 模板匹配。");
            return;
        }
        if (VcRedistributableInstaller.InstallStatus.RESTART_REQUIRED.name().equalsIgnoreCase(state.installStatus())) {
            showScrollableMessageDialog(parent,
                    buildOpenCvRecoveryFailureMessage(state, true),
                    "OpenCV 仍未恢复",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Object[] options = {"再次自动安装", "继续纯 Java 模式"};
        int choice = showScrollableOptionDialog(parent,
                buildOpenCvRecoveryFailureMessage(state, false),
                "OpenCV 自动安装后仍未恢复",
                JOptionPane.ERROR_MESSAGE,
                options,
                options[0]);
        if (choice == JOptionPane.YES_OPTION) {
            installVcRuntimeAsync();
        }
    }

    private String buildOpenCvFailureDiagnostic(Throwable throwable) {
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("Java 版本: ").append(System.getProperty("java.version", "")).append('\n');
        diagnostic.append("Java Home: ").append(System.getProperty("java.home", "")).append('\n');
        diagnostic.append("操作系统: ").append(System.getProperty("os.name", "")).append(' ')
                .append(System.getProperty("os.version", "")).append('\n');
        diagnostic.append("系统架构: ").append(System.getProperty("os.arch", "")).append('\n');
        diagnostic.append("运行目录: ").append(projectRootPath).append('\n');
        Path cacheRoot = resolveJavaCppCacheRoot();
        if (cacheRoot != null) {
            diagnostic.append(".javacpp 缓存: ").append(cacheRoot).append('\n');
        }
        diagnostic.append('\n').append("异常链:").append('\n');

        int index = 1;
        Set<Throwable> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            diagnostic.append(index++).append(". ").append(current.getClass().getName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                diagnostic.append(": ").append(message.trim());
            }
            diagnostic.append('\n');
            current = current.getCause();
        }
        return diagnostic.toString().trim();
    }

    private String buildVcInstallRestartRequiredMessage(VcRedistributableInstaller.InstallResult result) {
        StringBuilder message = new StringBuilder();
        message.append(result.message());
        message.append("\n\n程序已经记录本次安装结果。");
        message.append("\n完成系统重启后，再重新打开程序时会自动验证 OpenCV 是否恢复。");
        message.append("\n如果仍有问题，会自动弹出可复制的诊断信息。");
        appendInstallResultDetails(message, result, false);
        return message.toString();
    }

    private String buildVcInstallCanceledMessage(VcRedistributableInstaller.InstallResult result) {
        StringBuilder message = new StringBuilder();
        message.append(result.message());
        appendInstallResultDetails(message, result, false);
        return message.toString();
    }

    private String buildVcInstallFailedMessage(VcRedistributableInstaller.InstallResult result) {
        StringBuilder message = new StringBuilder();
        message.append(result.message());
        message.append("\n\n如需手动安装，请打开: ").append(VcRedistributableInstaller.OFFICIAL_DOWNLOAD_URI);
        appendInstallResultDetails(message, result, true);
        return message.toString();
    }

    private String buildVcInstallRestartFailureMessage(VcRedistributableInstaller.InstallResult result, Exception restartError) {
        StringBuilder message = new StringBuilder();
        message.append("运行库安装已完成，但自动重启程序失败。");
        message.append("\n请先复制下面信息发给开发者，然后手动重新打开程序。");
        appendInstallResultDetails(message, result, false);
        message.append("\n\n自动重启失败详情:\n").append(restartError.getClass().getName());
        if (restartError.getMessage() != null && !restartError.getMessage().isBlank()) {
            message.append(": ").append(restartError.getMessage().trim());
        }
        return message.toString();
    }

    private String buildOpenCvRecoveryFailureMessage(OpenCvRecoveryStateStore.RecoveryState state, boolean rebootStillPending) {
        StringBuilder message = new StringBuilder();
        if (rebootStillPending) {
            message.append("上一次自动安装已经完成，但安装器要求先重启系统。");
            message.append("\n如果你还没有重启系统，这次 OpenCV 仍未恢复是预期现象。");
            message.append("\n请重启系统后再次打开程序；如果仍失败，再把下面信息复制给开发者。");
        } else {
            message.append("自动安装和自动重启已经执行，但这次启动 OpenCV 仍未恢复。");
            message.append("\n程序已继续使用纯 Java 模板匹配。");
            message.append("\n下面文本可直接复制给开发者排查。");
        }
        message.append("\n\n安装时间: ").append(formatEpochMillis(state.createdAtEpochMillis()));
        if (!state.installStatus().isBlank()) {
            message.append("\n安装状态: ").append(state.installStatus());
        }
        if (state.installExitCode() >= 0) {
            message.append("\n安装退出码: ").append(state.installExitCode());
        }
        if (!state.installMessage().isBlank()) {
            message.append("\n安装结果: ").append(state.installMessage());
        }
        if (!state.installerPath().isBlank()) {
            message.append("\n安装包: ").append(state.installerPath());
        }
        if (!state.installerLogPath().isBlank()) {
            message.append("\n安装日志: ").append(state.installerLogPath());
        }
        if (!state.startupWarning().isBlank()) {
            message.append("\n\n安装前启动告警:\n").append(state.startupWarning());
        }
        if (!state.startupDiagnostic().isBlank()) {
            message.append("\n\n安装前启动诊断:\n").append(state.startupDiagnostic());
        }
        if (!safeText(imageMatcherStartupWarning).isBlank()) {
            message.append("\n\n本次启动告警:\n").append(imageMatcherStartupWarning);
        }
        if (!safeText(imageMatcherStartupDiagnostic).isBlank()) {
            message.append("\n\n本次启动诊断:\n").append(imageMatcherStartupDiagnostic);
        }
        return message.toString();
    }

    private void appendInstallResultDetails(StringBuilder message,
                                            VcRedistributableInstaller.InstallResult result,
                                            boolean copyableHint) {
        if (message == null || result == null) {
            return;
        }
        if (copyableHint) {
            message.append("\n\n下面文本可直接复制给开发者排查。");
        }
        message.append("\n安装状态: ").append(result.status());
        message.append("\n安装退出码: ").append(result.exitCode());
        if (result.installerPath() != null) {
            message.append("\n安装包: ").append(result.installerPath());
        }
        if (result.logPath() != null) {
            message.append("\n安装日志: ").append(result.logPath());
        }
    }

    private String formatEpochMillis(long epochMillis) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            return String.valueOf(epochMillis);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? "" : message.trim();
    }

    private String summarizeDiagnosticDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        String compact = detail.replaceAll("\\s+", " ").trim();
        int libraryPathIndex = compact.toLowerCase().indexOf("java.library.path:");
        if (libraryPathIndex >= 0) {
            compact = compact.substring(0, libraryPathIndex).trim();
        }
        int maxLength = 220;
        if (compact.length() > maxLength) {
            return compact.substring(0, maxLength) + "...";
        }
        return compact;
    }

    private int showScrollableOptionDialog(java.awt.Component parent,
                                           String message,
                                           String title,
                                           int messageType,
                                           Object[] options,
                                           Object initialValue) {
        return JOptionPane.showOptionDialog(
                parent,
                createScrollableMessageComponent(message),
                title,
                JOptionPane.YES_NO_OPTION,
                messageType,
                null,
                options,
                initialValue
        );
    }

    private void showScrollableMessageDialog(java.awt.Component parent,
                                             String message,
                                             String title,
                                             int messageType) {
        JOptionPane.showMessageDialog(parent, createScrollableMessageComponent(message), title, messageType);
    }

    private JScrollPane createScrollableMessageComponent(String message) {
        JTextArea textArea = new JTextArea(message == null ? "" : message);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(560, 260));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
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
        this.bindPanelContainer = new JPanel(new BorderLayout());
        this.bindPanelContainer.setPreferredSize(new Dimension(DEFAULT_BIND_PANEL_WIDTH, 10));
        this.bindPanelContainer.add(leftPanel, BorderLayout.CENTER);

        JPanel contentPanel = new JPanel(new BorderLayout(8, 0));
        contentPanel.add(buildBindPanelToolbar(), BorderLayout.NORTH);
        contentPanel.add(bindPanelContainer, BorderLayout.WEST);
        contentPanel.add(rightPanel, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown();
            }
        });
    }

    private JPanel buildBindPanelToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        showBindPanelButton.addActionListener(e -> {
            showBindPanel();
            refreshWindows();
        });
        hideBindPanelButton.addActionListener(e -> hideBindPanel());
        toolbar.add(showBindPanelButton);
        toolbar.add(hideBindPanelButton);
        return toolbar;
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

    private void installBasicConfigAutoSaveListeners() {
        intervalSpinner.addChangeListener(e -> scheduleBasicConfigAutoSave());
        repeatTriggerCheckBox.addActionListener(e -> scheduleBasicConfigAutoSave());
        moveWindowToBackAfterTriggerCheckBox.addActionListener(e -> scheduleBasicConfigAutoSave());
        backgroundClickModeCheckBox.addActionListener(e -> scheduleBasicConfigAutoSave());

        startHotkeyField.addActionListener(e -> scheduleBasicConfigAutoSave());
        stopHotkeyField.addActionListener(e -> scheduleBasicConfigAutoSave());
        FocusAdapter hotkeyFocusListener = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                scheduleBasicConfigAutoSave();
            }
        };
        startHotkeyField.addFocusListener(hotkeyFocusListener);
        stopHotkeyField.addFocusListener(hotkeyFocusListener);
    }

    private void installConditionEditorAutoApplyListeners() {
        ChangeListener spinnerListener = e -> autoApplyEditorToEditingCondition();
        regionOffsetXSpinner.addChangeListener(spinnerListener);
        regionOffsetYSpinner.addChangeListener(spinnerListener);
        regionWidthSpinner.addChangeListener(spinnerListener);
        regionHeightSpinner.addChangeListener(spinnerListener);
        thresholdSpinner.addChangeListener(spinnerListener);
        clickXSpinner.addChangeListener(spinnerListener);
        clickYSpinner.addChangeListener(spinnerListener);
        triggerActionTypeComboBox.addActionListener(e -> {
            updateClickActionEditorVisibility();
            clickOverlayPositionLogged = false;
            autoApplyEditorToEditingCondition();
            refreshClickOverlayQuietly();
        });

        DocumentListener textListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                autoApplyEditorToEditingCondition();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                autoApplyEditorToEditingCondition();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                autoApplyEditorToEditingCondition();
            }
        };
        conditionNameField.getDocument().addDocumentListener(textListener);
        templatePathsArea.getDocument().addDocumentListener(textListener);
        conditionExpressionField.getDocument().addDocumentListener(textListener);
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
        addFormRow(form, gbc, row++, "触发后窗口置底", moveWindowToBackAfterTriggerCheckBox);
        addFormRow(form, gbc, row++, "点击模式", backgroundClickModeCheckBox);
        addFormRow(form, gbc, row++, "截图策略", new JLabel("绑定窗口时自动探测并固定模式"));
        addFormRow(form, gbc, row++, "启动热键", startHotkeyField);
        addFormRow(form, gbc, row++, "停止热键", stopHotkeyField);
        addFormRow(form, gbc, row++, "热键示例", new JLabel("F9 / F10 / CTRL+F9"));
        return form;
    }

    private JPanel buildConditionConfigTab() {
        conditionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conditionTable.setRowHeight(28);
        conditionTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onConditionSelectionChanged();
            }
        });
        installConditionTableContextMenuAndDragReorder();
        installConditionPreviewButtonColumn();

        JPanel tablePanel = new JPanel(new BorderLayout(6, 6));
        tablePanel.setBorder(BorderFactory.createTitledBorder("轮询条件顺序（命中即停止本轮）"));
        tablePanel.add(new JScrollPane(conditionTable), BorderLayout.CENTER);

        JPanel manageButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addButton = new JButton("新增条件");
        JButton copyButton = new JButton("复制条件");
        JButton pasteButton = new JButton("粘贴条件");
        JButton editButton = new JButton("编辑选中条件");
        JButton removeButton = new JButton("删除条件");
        JButton moveUpButton = new JButton("上移");
        JButton moveDownButton = new JButton("下移");
        addButton.addActionListener(e -> addConditionFromEditor());
        copyButton.addActionListener(e -> copySelectedConditionToClipboard());
        pasteButton.addActionListener(e -> pasteConditionFromClipboard());
        editButton.addActionListener(e -> beginEditSelectedCondition());
        removeButton.addActionListener(e -> removeSelectedCondition());
        moveUpButton.addActionListener(e -> moveSelectedCondition(-1));
        moveDownButton.addActionListener(e -> moveSelectedCondition(1));
        manageButtons.add(addButton);
        manageButtons.add(copyButton);
        manageButtons.add(pasteButton);
        manageButtons.add(editButton);
        manageButtons.add(removeButton);
        manageButtons.add(moveUpButton);
        manageButtons.add(moveDownButton);
        tablePanel.add(manageButtons, BorderLayout.SOUTH);
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.add(tablePanel, BorderLayout.CENTER);
        return root;
    }

    private void installConditionPreviewButtonColumn() {
        TableColumn previewColumn = conditionTable.getColumnModel().getColumn(ConditionTableModel.PREVIEW_COLUMN_INDEX);
        previewColumn.setMinWidth(96);
        previewColumn.setPreferredWidth(96);
        previewColumn.setMaxWidth(110);
        previewColumn.setCellRenderer(new ConditionPreviewButtonRenderer());
        previewColumn.setCellEditor(new ConditionPreviewButtonEditor());
    }

    private void installConditionTableContextMenuAndDragReorder() {
        conditionTable.setDragEnabled(true);
        conditionTable.setDropMode(DropMode.INSERT_ROWS);
        conditionTable.setTransferHandler(new ConditionRowTransferHandler());

        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制条件");
        JMenuItem pasteItem = new JMenuItem("粘贴条件");
        JMenuItem deleteItem = new JMenuItem("删除条件");
        copyItem.addActionListener(e -> copySelectedConditionToClipboard());
        pasteItem.addActionListener(e -> pasteConditionFromClipboard());
        deleteItem.addActionListener(e -> removeSelectedCondition());
        menu.add(copyItem);
        menu.add(pasteItem);
        menu.add(deleteItem);

        MouseAdapter popupListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowConditionPopupMenu(e, menu, copyItem, pasteItem, deleteItem);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowConditionPopupMenu(e, menu, copyItem, pasteItem, deleteItem);
            }
        };
        conditionTable.addMouseListener(popupListener);
    }

    private void maybeShowConditionPopupMenu(
            MouseEvent event,
            JPopupMenu menu,
            JMenuItem copyItem,
            JMenuItem pasteItem,
            JMenuItem deleteItem
    ) {
        if (event == null || !event.isPopupTrigger()) {
            return;
        }
        int row = conditionTable.rowAtPoint(event.getPoint());
        if (row >= 0) {
            conditionTable.getSelectionModel().setSelectionInterval(row, row);
        }
        boolean hasSelected = conditionTable.getSelectedRow() >= 0;
        copyItem.setEnabled(hasSelected);
        deleteItem.setEnabled(hasSelected);
        pasteItem.setEnabled(copiedConditionClipboard != null);
        menu.show(event.getComponent(), event.getX(), event.getY());
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
        JPanel regionPickerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton selectRegionByMaskButton = new JButton("蒙版框选区域");
        selectRegionByMaskButton.addActionListener(e -> selectRegionByMask());
        regionPickerPanel.add(selectRegionByMaskButton);
        addFormRow(form, gbc, row++, "区域框选", regionPickerPanel);
        addFormRow(form, gbc, row++, "相似度阈值(%)", thresholdSpinner);
        addFormRow(form, gbc, row++, "触发后动作", triggerActionTypeComboBox);
        clickXRow = addFormRow(form, gbc, row++, "点击坐标 X(client)", clickXSpinner);
        clickYRow = addFormRow(form, gbc, row++, "点击坐标 Y(client)", clickYSpinner);
        JPanel clickPickerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton selectClickByMaskButton = new JButton("蒙版框选点击中心");
        selectClickByMaskButton.addActionListener(e -> selectClickPointByMask());
        clickPickerPanel.add(selectClickByMaskButton);
        clickPickerRow = addFormRow(form, gbc, row++, "点击框选", clickPickerPanel);

        JPanel templatePanel = new JPanel(new BorderLayout(6, 6));
        templatePathsArea.setLineWrap(false);
        templatePathsArea.setWrapStyleWord(false);
        templatePanel.add(new JScrollPane(templatePathsArea), BorderLayout.CENTER);
        JPanel templateButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton chooseTemplateButton = new JButton("导入模板(可多选)");
        JButton captureTemplateButton = new JButton("判断区截图设为模板");
        JButton clearTemplateButton = new JButton("清空");
        JButton autoExprButton = new JButton("自动AND表达式");
        chooseTemplateButton.addActionListener(e -> chooseTemplates());
        captureTemplateButton.addActionListener(e -> captureRegionAsConditionTemplate());
        clearTemplateButton.addActionListener(e -> clearTemplates());
        autoExprButton.addActionListener(e -> autoGenerateExpression());
        templateButtons.add(chooseTemplateButton);
        templateButtons.add(captureTemplateButton);
        templateButtons.add(clearTemplateButton);
        templateButtons.add(autoExprButton);
        templatePanel.add(templateButtons, BorderLayout.NORTH);
        addFormRow(form, gbc, row++, "条件模板路径", templatePanel);

        addFormRow(form, gbc, row++, "条件表达式", conditionExpressionField);
        addFormRow(form, gbc, row++, "表达式示例", new JLabel("C1 AND (NOT C2)；也支持 && || !"));
        updateClickActionEditorVisibility();
        return form;
    }

    private JPanel buildActionBar() {
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton saveProfileButton = new JButton("导出方案");
        JButton loadProfileButton = new JButton("载入方案");
        JButton startButton = new JButton("启动监控");
        JButton stopButton = new JButton("停止监控");
        JButton captureRegionButton = new JButton("截图判断区");

        saveProfileButton.addActionListener(e -> exportProfile());
        loadProfileButton.addActionListener(e -> importProfile());
        startButton.addActionListener(e -> startMonitoring());
        stopButton.addActionListener(e -> stopMonitoring());
        captureRegionButton.addActionListener(e -> captureCurrentRegion());
        togglePreviewOverlayButton.addActionListener(e -> togglePreviewOverlay());
        toggleLogPanelButton.addActionListener(e -> toggleLogPanel());

        actionBar.add(saveProfileButton);
        actionBar.add(loadProfileButton);
        actionBar.add(startButton);
        actionBar.add(stopButton);
        actionBar.add(captureRegionButton);
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

    private FormRow addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, java.awt.Component comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel label = new JLabel(labelText);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(comp, gbc);
        return new FormRow(label, comp);
    }

    private void updateClickActionEditorVisibility() {
        boolean visible = resolveSelectedTriggerActionType() == ConditionTriggerActionType.CLICK_REGION;
        setFormRowVisible(clickXRow, visible);
        setFormRowVisible(clickYRow, visible);
        setFormRowVisible(clickPickerRow, visible);
        if (clickXRow != null && clickXRow.label.getParent() != null) {
            clickXRow.label.getParent().revalidate();
            clickXRow.label.getParent().repaint();
        }
    }

    private void setFormRowVisible(FormRow row, boolean visible) {
        if (row == null) {
            return;
        }
        row.label.setVisible(visible);
        row.field.setVisible(visible);
    }

    private boolean usesClickTriggerAction(ConditionConfig condition) {
        return condition != null && condition.getPrimaryTriggerActionType() == ConditionTriggerActionType.CLICK_REGION;
    }

    private static final class FormRow {
        private final JLabel label;
        private final Component field;

        private FormRow(JLabel label, Component field) {
            this.label = label;
            this.field = field;
        }
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
        detectCaptureModeForBoundWindow();
        updateBindStatus();

        currentConfig.setLastWindowPid(selected.getProcessId());
        currentConfig.setLastWindowTitle(selected.getTitle());
        currentConfig.setLastWindowClassName(selected.getClassName());
        configService.save(currentConfig);
        log("已绑定窗口: " + selected.toDisplayText());
        refreshRegionOverlayQuietly();
        refreshClickOverlayQuietly();
        hideBindPanel();
    }

    private void unbindWindow() {
        if (monitoringService.isRunning()) {
            stopMonitoring();
        }
        boundWindow = null;
        boundCaptureMode = DEFAULT_CAPTURE_MODE;
        updateBindStatus();
        resetPreviewOverlayState();
        showBindPanel();
        log("已解绑当前窗口");
    }

    private void updateBindStatus() {
        if (boundWindow == null) {
            bindStatusLabel.setText("当前未绑定窗口，请先打开绑定栏并绑定窗口");
        } else {
            bindStatusLabel.setText("当前绑定: " + boundWindow.toDisplayText() + " | 截图策略=" + captureModeLabel(boundCaptureMode));
        }
        updateBindPanelButtons();
    }

    private void detectCaptureModeForBoundWindow() {
        if (boundWindow == null) {
            boundCaptureMode = DEFAULT_CAPTURE_MODE;
            return;
        }
        try {
            Rectangle clientRect = windowService.getClientRectOnScreen(boundWindow);
            if (clientRect.width <= 0 || clientRect.height <= 0) {
                boundCaptureMode = CaptureMode.SCREEN;
                log("绑定探测: client 区域无效，已使用屏幕截图。");
                return;
            }

            MonitorRegion probeRegion = new MonitorRegion();
            probeRegion.setX(0);
            probeRegion.setY(0);
            probeRegion.setWidth(clientRect.width);
            probeRegion.setHeight(clientRect.height);
            probeRegion.setReferenceWidth(clientRect.width);
            probeRegion.setReferenceHeight(clientRect.height);

            BufferedImage probeImage = captureService.capture(boundWindow, probeRegion, CaptureMode.WINDOW_HANDLE);
            if (isMostlyBlack(probeImage)) {
                boundCaptureMode = CaptureMode.SCREEN;
                log("绑定探测: 句柄截图近乎全黑，后续使用屏幕截图。");
            } else {
                boundCaptureMode = CaptureMode.WINDOW_HANDLE;
                log("绑定探测: 句柄截图可用，后续使用后台句柄截图。");
            }
        } catch (Exception e) {
            boundCaptureMode = CaptureMode.SCREEN;
            log("绑定探测: 句柄截图不可用，后续使用屏幕截图。详情: " + e.getMessage());
        }
    }

    private String captureModeLabel(CaptureMode mode) {
        if (mode == CaptureMode.WINDOW_HANDLE) {
            return "后台句柄截图";
        }
        if (mode == CaptureMode.SCREEN) {
            return "屏幕截图";
        }
        return mode == null ? DEFAULT_CAPTURE_MODE.toString() : mode.toString();
    }

    private void chooseTemplates() {
        JFileChooser chooser = createCapturesChooser();
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
                    paths.add(normalizeTemplatePath(file.getAbsolutePath()));
                }
            }
        } else if (chooser.getSelectedFile() != null) {
            paths.add(normalizeTemplatePath(chooser.getSelectedFile().getAbsolutePath()));
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

    private void captureRegionAsConditionTemplate() {
        if (!ensureBoundWindowReady("截图设模板")) {
            return;
        }
        OverlayState overlayState = suspendOverlaysForCapture();
        try {
            CapturedRegion captured = captureSelectedConditionRegionToFile("template");
            String normalizedPath = normalizeTemplatePath(captured.outputPath().toString());
            templatePathsArea.setText(normalizedPath);
            conditionExpressionField.setText("C1");
            applyEditorToSelectedConditionQuietly();
            log("已将判断区域截图设为条件模板: " + normalizedPath);
            logCaptureQualityHints(captured.image(), captured.captureMode());
            JOptionPane.showMessageDialog(this,
                    "已截图并设置为当前条件模板:\n" + captured.outputPath(),
                    "完成",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showError("判断区域截图设模板失败", e);
        } finally {
            restoreOverlaysAfterCapture(overlayState);
        }
    }

    private void selectRegionByMask() {
        if (!ensureBoundWindowReady("蒙版框选区域")) {
            return;
        }

        final Rectangle clientRect;
        try {
            clientRect = requireBoundClientRect("蒙版框选区域");
        } catch (Exception e) {
            showError("蒙版框选区域失败", new IllegalStateException(e.getMessage(), e));
            return;
        }

        final OverlayState overlayState = suspendOverlaysForCapture();
        try {
            BufferedImage snapshot = tryCaptureClientSnapshotForMask(clientRect);
            selectionMaskOverlay.start(
                    clientRect,
                    snapshot,
                    "拖动鼠标框选判断区域，松开确认；ESC 或右键取消",
                    new SelectionMaskOverlayWindow.SelectionCallback() {
                        @Override
                        public void onSelected(Rectangle selectionRect) {
                            try {
                                pinSelectedConditionReferenceSize(clientRect.width, clientRect.height);
                                regionOffsetXSpinner.setValue(selectionRect.x);
                                regionOffsetYSpinner.setValue(selectionRect.y);
                                regionWidthSpinner.setValue(Math.max(1, selectionRect.width));
                                regionHeightSpinner.setValue(Math.max(1, selectionRect.height));
                                applyEditorToSelectedConditionQuietly();
                                log(String.format("已通过蒙版框选设置判断区域: x=%d, y=%d, w=%d, h=%d",
                                        selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height));
                            } finally {
                                restoreOverlaysAfterCapture(overlayState);
                            }
                        }

                        @Override
                        public void onCanceled() {
                            restoreOverlaysAfterCapture(overlayState);
                        }
                    }
            );
        } catch (Exception e) {
            restoreOverlaysAfterCapture(overlayState);
            showError("蒙版框选区域失败", new IllegalStateException(e.getMessage(), e));
        }
    }

    private void selectClickPointByMask() {
        if (!ensureBoundWindowReady("蒙版框选点击")) {
            return;
        }

        final Rectangle clientRect;
        try {
            clientRect = requireBoundClientRect("蒙版框选点击");
        } catch (Exception e) {
            showError("蒙版框选点击失败", new IllegalStateException(e.getMessage(), e));
            return;
        }

        final OverlayState overlayState = suspendOverlaysForCapture();
        try {
            BufferedImage snapshot = tryCaptureClientSnapshotForMask(clientRect);
            selectionMaskOverlay.start(
                    clientRect,
                    snapshot,
                    "拖动鼠标框选点击区域，松开后取中心点；ESC 或右键取消",
                    new SelectionMaskOverlayWindow.SelectionCallback() {
                        @Override
                        public void onSelected(Rectangle selectionRect) {
                            try {
                                pinSelectedConditionReferenceSize(clientRect.width, clientRect.height);
                                int clickX = selectionRect.x + selectionRect.width / 2;
                                int clickY = selectionRect.y + selectionRect.height / 2;
                                clickXSpinner.setValue(clickX);
                                clickYSpinner.setValue(clickY);
                                applyEditorToSelectedConditionQuietly();
                                log(String.format("已通过蒙版框选设置点击坐标: x=%d, y=%d", clickX, clickY));
                            } finally {
                                restoreOverlaysAfterCapture(overlayState);
                            }
                        }

                        @Override
                        public void onCanceled() {
                            restoreOverlaysAfterCapture(overlayState);
                        }
                    }
            );
        } catch (Exception e) {
            restoreOverlaysAfterCapture(overlayState);
            showError("蒙版框选点击失败", new IllegalStateException(e.getMessage(), e));
        }
    }

    private BufferedImage tryCaptureClientSnapshotForMask(Rectangle clientRect) {
        if (clientRect == null || clientRect.width <= 0 || clientRect.height <= 0) {
            return null;
        }
        try {
            MonitorRegion fullRegion = new MonitorRegion();
            fullRegion.setX(0);
            fullRegion.setY(0);
            fullRegion.setWidth(clientRect.width);
            fullRegion.setHeight(clientRect.height);
            fullRegion.setReferenceWidth(clientRect.width);
            fullRegion.setReferenceHeight(clientRect.height);
            return captureService.capture(boundWindow, fullRegion, CaptureMode.SCREEN);
        } catch (Exception e) {
            log("蒙版背景截图失败，已回退纯色蒙版: " + e.getMessage());
            BufferedImage fallback = new BufferedImage(
                    Math.max(1, clientRect.width),
                    Math.max(1, clientRect.height),
                    BufferedImage.TYPE_INT_RGB
            );
            java.awt.Graphics2D g2 = fallback.createGraphics();
            try {
                g2.setColor(new java.awt.Color(46, 46, 46));
                g2.fillRect(0, 0, fallback.getWidth(), fallback.getHeight());
            } finally {
                g2.dispose();
            }
            return fallback;
        }
    }

    private void pinSelectedConditionReferenceSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        ConditionConfig selected = conditionTableModel.getConditionAt(selectedRow);
        if (selected == null) {
            return;
        }
        MonitorRegion region = selected.getMonitorRegion();
        if (region == null) {
            region = new MonitorRegion();
        }
        region.setReferenceWidth(width);
        region.setReferenceHeight(height);
        selected.setMonitorRegion(region);
        conditionTableModel.updateCondition(selectedRow, selected);
    }

    private boolean ensureBoundWindowReady(String actionName) {
        if (boundWindow == null) {
            showBindPanel();
            JOptionPane.showMessageDialog(this, "请先绑定一个窗口。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        if (!isBoundWindowAliveQuietly()) {
            handleInvalidBoundWindow("目标窗口已失效，已自动展开绑定栏，请重新绑定。", true);
            JOptionPane.showMessageDialog(this, "目标窗口已失效，请重新绑定后再" + actionName + "。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        try {
            requireBoundClientRect(actionName);
            return true;
        } catch (Exception e) {
            showError(actionName + "失败", new IllegalStateException(e.getMessage(), e));
            return false;
        }
    }

    private Rectangle requireBoundClientRect(String actionName) {
        if (boundWindow == null) {
            showBindPanel();
            throw new IllegalStateException("请先绑定窗口后再" + actionName + "。");
        }
        if (!windowService.isAlive(boundWindow)) {
            handleInvalidBoundWindow("目标窗口已失效，已自动展开绑定栏，请重新绑定。", true);
            throw new IllegalStateException("目标窗口已失效，请重新绑定后再" + actionName + "。");
        }
        if (windowService.isMinimized(boundWindow)) {
            throw new IllegalStateException("目标窗口已最小化，请先恢复窗口后再" + actionName + "。");
        }
        Rectangle clientRect = windowService.getClientRectOnScreen(boundWindow);
        if (clientRect.width <= 0 || clientRect.height <= 0) {
            throw new IllegalStateException("目标窗口 client 区域无效: " + clientRect);
        }
        return clientRect;
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
        if (syncingConditionEditor || !previewOverlayEnabled || previewConditionRow < 0) {
            return;
        }
        if (conditionTable.getSelectedRow() == previewConditionRow) {
            regionOverlayPositionLogged = false;
            refreshRegionOverlayQuietly();
        }
    }

    private void beginEditSelectedCondition() {
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个条件。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ConditionConfig condition = conditionTableModel.getConditionAt(selectedRow);
        if (condition == null) {
            JOptionPane.showMessageDialog(this, "未找到选中的条件。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        openConditionEditorDialog(selectedRow, condition);
    }

    private void autoApplyEditorToEditingCondition() {
        if (syncingConditionEditor || editingConditionRow < 0) {
            return;
        }
        if (editingConditionRow >= conditionTableModel.getConditionCount()) {
            showConditionEditorPlaceholder(null);
            return;
        }
        try {
            ConditionConfig condition = readConditionFromEditor();
            if (condition.getName().isBlank()) {
                condition.setName("条件" + (editingConditionRow + 1));
            }
            conditionTableModel.updateCondition(editingConditionRow, condition);
            if (conditionTable.getSelectedRow() != editingConditionRow) {
                conditionTable.getSelectionModel().setSelectionInterval(editingConditionRow, editingConditionRow);
            }
            updateEditingConditionLabel(editingConditionRow, condition);
            if (previewConditionRow == editingConditionRow && previewOverlayEnabled) {
                regionOverlayPositionLogged = false;
                refreshRegionOverlayQuietly();
            }
        } catch (Exception e) {
            log("条件编辑自动应用失败: " + e.getMessage());
        }
    }

    private void showConditionEditorPlaceholder(String ignoredStatusText) {
        editingConditionRow = -1;
        if (conditionEditorDialog != null && conditionEditorDialog.isDisplayable()) {
            conditionEditorDialog.setTitle("条件编辑");
        }
    }

    private void openConditionEditorDialog(int row, ConditionConfig condition) {
        if (conditionEditorDialog != null && conditionEditorDialog.isDisplayable()) {
            conditionEditorDialog.dispose();
        }
        editingConditionRow = row;
        loadConditionToEditor(condition);

        JDialog dialog = new JDialog(this, buildConditionEditorDialogTitle(row, condition), true);
        this.conditionEditorDialog = dialog;
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JScrollPane(buildConditionEditorPanel()), BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton closeButton = new JButton("完成");
        closeButton.addActionListener(e -> dialog.dispose());
        footer.add(closeButton);
        content.add(footer, BorderLayout.SOUTH);

        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.setSize(CONDITION_EDITOR_DIALOG_SIZE);
        dialog.setLocationRelativeTo(this);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (conditionEditorDialog == dialog) {
                    conditionEditorDialog = null;
                }
                showConditionEditorPlaceholder(null);
                if (previewConditionRow >= 0 && previewOverlayEnabled) {
                    regionOverlayPositionLogged = false;
                    refreshRegionOverlayQuietly();
                }
            }
        });
        log("进入条件编辑: " + resolveConditionName(condition, row));
        dialog.setVisible(true);
    }

    private String buildConditionEditorDialogTitle(int row, ConditionConfig condition) {
        return "编辑条件 - #" + (row + 1) + " " + resolveConditionName(condition, row);
    }

    private void updateEditingConditionLabel(int row, ConditionConfig condition) {
        if (conditionEditorDialog != null && conditionEditorDialog.isDisplayable()) {
            conditionEditorDialog.setTitle(buildConditionEditorDialogTitle(row, condition));
        }
    }

    private void addConditionFromEditor() {
        applyEditorToSelectedConditionQuietly();
        ConditionConfig condition = editingConditionRow >= 0 ? readConditionFromEditor() : new ConditionConfig();
        if (condition.getName().isBlank()) {
            condition.setName("条件" + (conditionTableModel.getConditionCount() + 1));
        }
        int index = conditionTableModel.addCondition(condition);
        conditionTable.getSelectionModel().setSelectionInterval(index, index);
        showConditionEditorPlaceholder("已新增条件，请点击“编辑选中条件”继续编辑");
        log("已新增条件: " + condition.getName());
    }

    private void copySelectedConditionToClipboard() {
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
        copiedConditionClipboard = new ConditionConfig(condition);
        copiedConditionClipboard.setTemplatePaths(normalizeTemplatePaths(copiedConditionClipboard.getTemplatePaths()));
        log("已复制条件到剪贴板: " + resolveConditionName(condition, selectedRow));
    }

    private void pasteConditionFromClipboard() {
        applyEditorToSelectedConditionQuietly();
        if (copiedConditionClipboard == null) {
            JOptionPane.showMessageDialog(this, "剪贴板为空，请先在任意方案中复制一个条件。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ConditionConfig pasted = new ConditionConfig(copiedConditionClipboard);
        pasted.setTemplatePaths(normalizeTemplatePaths(pasted.getTemplatePaths()));
        if (pasted.getName().isBlank()) {
            pasted.setName("条件" + (conditionTableModel.getConditionCount() + 1));
        } else {
            pasted.setName(pasted.getName() + "-副本");
        }
        int index = conditionTableModel.addCondition(pasted);
        conditionTable.getSelectionModel().setSelectionInterval(index, index);
        showConditionEditorPlaceholder("已粘贴条件，请点击“编辑选中条件”继续编辑");
        log("已粘贴条件: " + pasted.getName());
    }

    private void applyEditorToSelectedConditionQuietly() {
        int selectedRow = editingConditionRow;
        if (selectedRow < 0 || syncingConditionEditor) {
            return;
        }
        ConditionConfig condition = readConditionFromEditor();
        if (condition.getName().isBlank()) {
            condition.setName("条件" + (selectedRow + 1));
        }
        conditionTableModel.updateCondition(selectedRow, condition);
        if (editingConditionRow >= 0) {
            updateEditingConditionLabel(selectedRow, condition);
        }
    }

    private void removeSelectedCondition() {
        int selectedRow = conditionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个条件。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ConditionConfig condition = conditionTableModel.getConditionAt(selectedRow);
        conditionTableModel.removeCondition(selectedRow);
        updatePreviewConditionRowAfterRemoval(selectedRow);
        int count = conditionTableModel.getConditionCount();
        if (count > 0) {
            int next = Math.min(selectedRow, count - 1);
            conditionTable.getSelectionModel().setSelectionInterval(next, next);
            showConditionEditorPlaceholder("条件已删除，请点击“编辑选中条件”继续编辑");
        } else {
            showConditionEditorPlaceholder("暂无条件，请先新增条件");
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
        updatePreviewConditionRowAfterSwap(selectedRow, target);
        showConditionEditorPlaceholder("条件顺序已调整，请点击“编辑选中条件”继续编辑");
    }

    private boolean moveConditionByDragAndDrop(int fromRow, int dropInsertRow) {
        int rowCount = conditionTableModel.getConditionCount();
        if (fromRow < 0 || fromRow >= rowCount || rowCount <= 1) {
            return false;
        }
        int insertRow = Math.max(0, Math.min(dropInsertRow, rowCount));
        int targetRow = insertRow > fromRow ? insertRow - 1 : insertRow;
        if (targetRow < 0 || targetRow >= rowCount || targetRow == fromRow) {
            return false;
        }
        ConditionConfig moving = conditionTableModel.getConditionAt(fromRow);
        int movedTo = conditionTableModel.moveConditionTo(fromRow, targetRow);
        conditionTable.getSelectionModel().setSelectionInterval(movedTo, movedTo);
        updatePreviewConditionRowAfterMove(fromRow, movedTo);
        showConditionEditorPlaceholder("条件顺序已调整，请点击“编辑选中条件”继续编辑");
        log("已拖拽调整条件顺序: " + resolveConditionName(moving, movedTo) + " -> #" + (movedTo + 1));
        return true;
    }

    private final class ConditionRowTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            int selectedRow = conditionTable.getSelectedRow();
            if (selectedRow < 0) {
                return null;
            }
            return new StringSelection(Integer.toString(selectedRow));
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) {
                return false;
            }
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return false;
            }
            if (!(support.getDropLocation() instanceof JTable.DropLocation dropLocation)) {
                return false;
            }
            return dropLocation.getRow() >= 0;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                String rowText = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                int fromRow = Integer.parseInt(rowText.trim());
                JTable.DropLocation dropLocation = (JTable.DropLocation) support.getDropLocation();
                return moveConditionByDragAndDrop(fromRow, dropLocation.getRow());
            } catch (Exception e) {
                log("拖拽排序失败: " + e.getMessage());
                return false;
            }
        }
    }

    private void toggleConditionRegionPreview(int row) {
        if (row < 0 || row >= conditionTableModel.getConditionCount()) {
            return;
        }
        if (previewOverlayEnabled && previewConditionRow == row) {
            stopConditionRegionPreview(true);
            return;
        }
        if (!ensureBoundWindowReady("显示截图区域")) {
            return;
        }
        ConditionConfig condition = conditionTableModel.getConditionAt(row);
        if (condition == null) {
            return;
        }
        previewConditionRow = row;
        previewOverlayEnabled = true;
        regionOverlayErrorLogged = false;
        regionOverlayPositionLogged = false;
        clickOverlayErrorLogged = false;
        clickOverlayPositionLogged = false;
        regionOverlayTimer.start();
        clickOverlayTimer.stop();
        clickOverlay.hide();
        conditionTable.getSelectionModel().setSelectionInterval(row, row);
        updateConditionPreviewButtons();
        refreshRegionOverlayQuietly();
        log("已显示条件区域预览: " + resolveConditionName(condition, row));
    }

    private void stopConditionRegionPreview(boolean logAction) {
        int previousRow = previewConditionRow;
        ConditionConfig previousCondition = previousRow >= 0 ? conditionTableModel.getConditionAt(previousRow) : null;
        previewConditionRow = -1;
        previewOverlayEnabled = false;
        regionOverlayErrorLogged = false;
        regionOverlayPositionLogged = false;
        clickOverlayErrorLogged = false;
        clickOverlayPositionLogged = false;
        regionOverlayTimer.stop();
        clickOverlayTimer.stop();
        hidePreviewOverlays();
        updateConditionPreviewButtons();
        if (logAction && previousCondition != null) {
            log("已隐藏条件区域预览: " + resolveConditionName(previousCondition, previousRow));
        }
    }

    private void updateConditionPreviewButtons() {
        conditionTable.repaint();
    }

    private String previewButtonTextForRow(int row) {
        return previewOverlayEnabled && previewConditionRow == row ? "隐藏区域" : "显示区域";
    }

    private void updatePreviewConditionRowAfterRemoval(int removedRow) {
        if (previewConditionRow < 0) {
            return;
        }
        if (previewConditionRow == removedRow) {
            stopConditionRegionPreview(false);
            return;
        }
        if (previewConditionRow > removedRow) {
            previewConditionRow--;
            updateConditionPreviewButtons();
            if (previewOverlayEnabled) {
                regionOverlayPositionLogged = false;
                refreshRegionOverlayQuietly();
            }
        }
    }

    private void updatePreviewConditionRowAfterSwap(int fromRow, int targetRow) {
        if (previewConditionRow < 0 || fromRow == targetRow) {
            return;
        }
        if (previewConditionRow == fromRow) {
            previewConditionRow = targetRow;
        } else if (previewConditionRow == targetRow) {
            previewConditionRow = fromRow;
        } else {
            return;
        }
        updateConditionPreviewButtons();
        if (previewOverlayEnabled) {
            regionOverlayPositionLogged = false;
            refreshRegionOverlayQuietly();
        }
    }

    private void updatePreviewConditionRowAfterMove(int fromRow, int movedTo) {
        if (previewConditionRow < 0 || fromRow == movedTo) {
            return;
        }
        if (previewConditionRow == fromRow) {
            previewConditionRow = movedTo;
        } else if (fromRow < previewConditionRow && previewConditionRow <= movedTo) {
            previewConditionRow--;
        } else if (movedTo <= previewConditionRow && previewConditionRow < fromRow) {
            previewConditionRow++;
        } else {
            return;
        }
        updateConditionPreviewButtons();
        if (previewOverlayEnabled) {
            regionOverlayPositionLogged = false;
            refreshRegionOverlayQuietly();
        }
    }

    private final class ConditionPreviewButtonRenderer extends JButton implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            setText(previewButtonTextForRow(row));
            return this;
        }
    }

    private final class ConditionPreviewButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton();
        private int currentRow = -1;

        private ConditionPreviewButtonEditor() {
            button.addActionListener(e -> {
                int row = currentRow;
                fireEditingStopped();
                SwingUtilities.invokeLater(() -> toggleConditionRegionPreview(row));
            });
        }

        @Override
        public Object getCellEditorValue() {
            return previewButtonTextForRow(currentRow);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     int row,
                                                     int column) {
            currentRow = row;
            button.setText(previewButtonTextForRow(row));
            return button;
        }
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
            triggerActionTypeComboBox.setSelectedItem(source.getPrimaryTriggerActionType());
            templatePathsArea.setText(String.join(System.lineSeparator(), normalizeTemplatePaths(source.getTemplatePaths())));
            String expression = source.getConditionExpression();
            if ((expression == null || expression.isBlank()) && !source.getTemplatePaths().isEmpty()) {
                expression = buildDefaultConditionExpression(source.getTemplatePaths().size());
            }
            conditionExpressionField.setText(expression == null ? "" : expression);
        } finally {
            syncingConditionEditor = false;
        }
        updateClickActionEditorVisibility();
        refreshRegionOverlayQuietly();
        clickOverlayPositionLogged = false;
        refreshClickOverlayQuietly();
    }

    private ConditionConfig readConditionFromEditor() {
        ConditionConfig condition = new ConditionConfig();
        condition.setName(conditionNameField.getText().trim());
        condition.setThreshold(((Number) thresholdSpinner.getValue()).doubleValue() / 100.0D);
        condition.setClickX(((Number) clickXSpinner.getValue()).intValue());
        condition.setClickY(((Number) clickYSpinner.getValue()).intValue());
        condition.setPrimaryTriggerActionType(resolveSelectedTriggerActionType());
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
            if (!ensureBoundWindowReady("显示预览")) {
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

    private void collapseLogPanelByDefault() {
        if (configVerticalSplitPane == null || logPanelCollapsed) {
            return;
        }
        lastLogDividerLocation = configVerticalSplitPane.getDividerLocation();
        configVerticalSplitPane.setDividerLocation(0.99D);
        logPanelCollapsed = true;
        toggleLogPanelButton.setText("显示日志");
    }

    private void ensureBindPanelState() {
        if (boundWindow == null) {
            showBindPanel();
            return;
        }
        if (!isBoundWindowAliveQuietly()) {
            handleInvalidBoundWindow("检测到绑定窗口已失效，已自动展开绑定栏，请重新绑定。", true);
            return;
        }
        hideBindPanel();
    }

    private void ensureBoundWindowStillValid() {
        if (!isDisplayable()) {
            return;
        }
        if (boundWindow == null) {
            showBindPanel();
            return;
        }
        if (isBoundWindowAliveQuietly()) {
            updateBindPanelButtons();
            return;
        }
        handleInvalidBoundWindow("检测到绑定窗口已失效，已自动展开绑定栏，请重新绑定。", true);
    }

    private boolean isBoundWindowAliveQuietly() {
        if (boundWindow == null) {
            return false;
        }
        try {
            return windowService.isAlive(boundWindow);
        } catch (Exception e) {
            return false;
        }
    }

    private void showBindPanel() {
        setBindPanelCollapsed(false);
    }

    private void hideBindPanel() {
        if (boundWindow == null) {
            showBindPanel();
            return;
        }
        if (!isBoundWindowAliveQuietly()) {
            handleInvalidBoundWindow("检测到绑定窗口已失效，已自动展开绑定栏，请重新绑定。", true);
            return;
        }
        setBindPanelCollapsed(true);
    }

    private void setBindPanelCollapsed(boolean collapsed) {
        bindPanelCollapsed = collapsed;
        if (bindPanelContainer != null) {
            bindPanelContainer.setVisible(!collapsed);
            bindPanelContainer.revalidate();
            bindPanelContainer.repaint();
        }
        updateBindPanelButtons();
        revalidate();
        repaint();
    }

    private void updateBindPanelButtons() {
        boolean hasValidBinding = isBoundWindowAliveQuietly();
        showBindPanelButton.setEnabled(bindPanelCollapsed);
        hideBindPanelButton.setEnabled(!bindPanelCollapsed && hasValidBinding);
    }

    private void resetPreviewOverlayState() {
        previewConditionRow = -1;
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
        updateConditionPreviewButtons();
    }

    private void handleInvalidBoundWindow(String message, boolean refreshWindowList) {
        if (monitoringService.isRunning()) {
            monitoringService.stop();
        }
        boundWindow = null;
        boundCaptureMode = DEFAULT_CAPTURE_MODE;
        updateBindStatus();
        resetPreviewOverlayState();
        showBindPanel();
        if (refreshWindowList) {
            try {
                List<WindowInfo> windows = windowService.listWindows();
                windowTableModel.setWindows(windows);
            } catch (Exception e) {
                log("刷新窗口列表失败: " + e.getMessage());
            }
        }
        if (message != null && !message.isBlank()) {
            log(message);
        }
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
        if (!previewOverlayEnabled || previewConditionRow >= 0) {
            clickOverlay.hide();
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

        ConditionConfig selectedCondition = resolveSelectedConditionForPreview();
        if (selectedCondition == null) {
            regionOverlay.hideOverlay();
            return;
        }
        AppConfig config = mergeGlobalWithCondition(readGlobalConfigFromForm(), selectedCondition);
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
        if (!previewOverlayEnabled || previewConditionRow >= 0) {
            clickOverlay.hide();
            return;
        }
        if (boundWindow == null || !windowService.isAlive(boundWindow) || windowService.isMinimized(boundWindow)) {
            clickOverlay.hide();
            return;
        }

        ConditionConfig selectedCondition = resolveSelectedConditionForPreview();
        if (selectedCondition == null) {
            clickOverlay.hide();
            return;
        }
        if (!usesClickTriggerAction(selectedCondition)) {
            clickOverlay.hide();
            return;
        }
        AppConfig config = mergeGlobalWithCondition(readGlobalConfigFromForm(), selectedCondition);
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

    private ConditionConfig resolveSelectedConditionForPreview() {
        int targetRow = previewConditionRow >= 0 ? previewConditionRow : conditionTable.getSelectedRow();
        if (targetRow < 0) {
            return null;
        }
        if (editingConditionRow == targetRow && !syncingConditionEditor) {
            try {
                return readConditionFromEditor();
            } catch (Exception ignored) {
            }
        }
        return conditionTableModel.getConditionAt(targetRow);
    }

    private Rectangle toOverlayCoordinates(Rectangle nativeRect) {
        if (nativeRect == null) {
            return null;
        }
        return new Rectangle(nativeRect);
    }

    private Point toOverlayPoint(Point nativePoint) {
        if (nativePoint == null) {
            return null;
        }
        return new Point(nativePoint);
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
            showBindPanel();
            if (triggeredByHotkey) {
                log("热键触发启动失败: 请先绑定窗口");
            } else {
                JOptionPane.showMessageDialog(this, "请先绑定一个窗口。", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        if (!isBoundWindowAliveQuietly()) {
            handleInvalidBoundWindow("目标窗口已失效，已自动展开绑定栏，请重新绑定。", true);
            if (triggeredByHotkey) {
                log("热键触发启动失败: 目标窗口已失效，请重新绑定");
            } else {
                JOptionPane.showMessageDialog(this, "目标窗口已失效，请重新绑定后再启动监控。", "提示", JOptionPane.INFORMATION_MESSAGE);
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
                        .setConditionExpression(runtimeConfig.getConditionExpression());
                for (LoadedTemplate template : loadedTemplates) {
                    rule.addCondition(new ImageTemplateCondition(template.conditionName(), imageMatcher, template.image()));
                }
                appendConfiguredTriggerActions(rule, rawCondition);

                String conditionKey = "COND_" + i;
                pollingConditions.add(new MonitoringService.PollingCondition(
                        conditionKey,
                        conditionName,
                        runtimeConfig,
                        loadedTemplates.get(0).image(),
                        rule
                ));
                normalizedConditions.add(buildConditionConfigFromRuntime(runtimeConfig,
                        conditionName,
                        rawCondition.getTriggerActions()));
                logConditionMapping(conditionName,
                        loadedTemplates,
                        runtimeConfig.getConditionExpression(),
                        rawCondition.getTriggerActionsSummary());
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

            applyBoundWindowMetadata(currentConfig);
            configService.save(currentConfig);

            monitoringService.start(boundWindow, currentConfig, pollingConditions);
            log("重复触发: " + (currentConfig.isRepeatTrigger() ? "开启" : "关闭"));
            log("触发后窗口置底: " + (currentConfig.isMoveWindowToBackAfterTrigger() ? "开启" : "关闭"));
            log("点击模式: " + (currentConfig.isBackgroundClickMode() ? "后台消息点击" : "前台真实点击"));
            log("截图策略: " + captureModeLabel(currentConfig.getCaptureMode()));
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

    private void logConditionMapping(String conditionName,
                                     List<LoadedTemplate> loadedTemplates,
                                     String expression,
                                     String triggerActionSummary) {
        log("条件[" + conditionName + "] 表达式: " + expression
                + "；触发动作=" + triggerActionSummary);
        for (LoadedTemplate template : loadedTemplates) {
            log("  " + template.conditionName() + " => " + template.path());
        }
    }

    private List<LoadedTemplate> loadTemplateImages(List<String> templatePaths) {
        List<LoadedTemplate> templates = new ArrayList<>();
        int index = 1;
        for (String path : templatePaths) {
            Path resolvedPath = resolveTemplatePath(path);
            File file = resolvedPath.toFile();
            if (!file.exists()) {
                throw new IllegalArgumentException("模板文件不存在: " + resolvedPath);
            }
            BufferedImage image;
            try {
                image = ImageIO.read(file);
            } catch (Exception e) {
                throw new IllegalArgumentException("模板图读取失败: " + resolvedPath, e);
            }
            if (image == null) {
                throw new IllegalArgumentException("无法读取图片: " + resolvedPath);
            }
            templates.add(new LoadedTemplate("C" + index, normalizeTemplatePath(resolvedPath.toString()), image));
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
        if (!ensureBoundWindowReady("截图判断区")) {
            return;
        }
        OverlayState overlayState = suspendOverlaysForCapture();
        try {
            CapturedRegion captured = captureSelectedConditionRegionToFile("region");
            log("已截图判断区: " + captured.outputPath()
                    + "，size=" + captured.image().getWidth() + "x" + captured.image().getHeight());
            logCaptureQualityHints(captured.image(), captured.captureMode());
            JOptionPane.showMessageDialog(this, "截图已保存:\n" + captured.outputPath(), "完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showError("截图判断区失败", e);
        } finally {
            restoreOverlaysAfterCapture(overlayState);
        }
    }

    private CapturedRegion captureSelectedConditionRegionToFile(String filenamePrefix) throws Exception {
        AppConfig config = mergeGlobalWithCondition(readGlobalConfigFromForm(), readConditionFromEditor());
        Rectangle clientRect = requireBoundClientRect("截图判断区");
        normalizeRegionCoordinateModeIfNeeded(config, clientRect, true);
        ensureReferenceSizeIfMissing(config, clientRect, false);

        try {
            Thread.sleep(80L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        BufferedImage image = captureService.capture(boundWindow, config.getMonitorRegion(), config.getCaptureMode());
        Path captureDir = capturesDirPath;
        Files.createDirectories(captureDir);
        String filePrefix = (filenamePrefix == null || filenamePrefix.isBlank()) ? "region" : filenamePrefix.trim();
        String filename = filePrefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".png";
        Path output = captureDir.resolve(filename);
        ImageIO.write(image, "png", output.toFile());
        return new CapturedRegion(output, image, config.getCaptureMode());
    }

    private void logCaptureQualityHints(BufferedImage image, CaptureMode captureMode) {
        if (isMostlyWhite(image)) {
            log("警告: 截图几乎全白，目标窗口可能为独占全屏/受保护渲染。建议切换为窗口化或无边框窗口化后再试。");
        }
        if ((captureMode == CaptureMode.WINDOW_HANDLE
                || captureMode == CaptureMode.WINDOW_HANDLE_FALLBACK_SCREEN)
                && isMostlyBlack(image)) {
            log("警告: 句柄截图几乎全黑，目标窗口可能不支持 PrintWindow（如硬件加速/受保护渲染）。建议以管理员启动并重新绑定窗口触发截图模式探测。");
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

    private boolean isMostlyBlack(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        long total = 0L;
        long black = 0L;
        int step = Math.max(1, Math.min(width, height) / 80);
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r <= 10 && g <= 10 && b <= 10) {
                    black++;
                }
                total++;
            }
        }
        if (total <= 0) {
            return false;
        }
        return (double) black / (double) total >= 0.98D;
    }

    private void scheduleBasicConfigAutoSave() {
        if (suppressBasicConfigAutoSave) {
            return;
        }
        basicConfigAutoSaveTimer.restart();
    }

    private void autoSaveBasicConfigNow() {
        if (suppressBasicConfigAutoSave) {
            return;
        }
        try {
            applyEditorToSelectedConditionQuietly();
            AppConfig updated = readConfigFromForm();
            applyBoundWindowMetadata(updated);
            registerGlobalHotkeys(updated, false);
            currentConfig = updated;
            configService.save(currentConfig);
        } catch (Exception e) {
            log("基础设置自动保存失败: " + e.getMessage());
        }
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
                    normalizedConditions.add(buildConditionConfigFromRuntime(runtimeConfig,
                            resolveConditionName(condition, i),
                            condition.getTriggerActions()));
                }
                currentConfig.setConditions(normalizedConditions);
                conditionTableModel.setConditions(normalizedConditions);
                if (conditionTableModel.getConditionCount() > 0) {
                    int selected = Math.min(Math.max(conditionTable.getSelectedRow(), 0), conditionTableModel.getConditionCount() - 1);
                    conditionTable.getSelectionModel().setSelectionInterval(selected, selected);
                }
            }
            applyBoundWindowMetadata(currentConfig);
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

    private void applyBoundWindowMetadata(AppConfig config) {
        if (config == null) {
            return;
        }
        if (boundWindow != null) {
            config.setLastWindowPid(boundWindow.getProcessId());
            config.setLastWindowTitle(boundWindow.getTitle());
            config.setLastWindowClassName(boundWindow.getClassName());
            return;
        }
        if (currentConfig != null) {
            config.setLastWindowPid(currentConfig.getLastWindowPid());
            config.setLastWindowTitle(currentConfig.getLastWindowTitle());
            config.setLastWindowClassName(currentConfig.getLastWindowClassName());
        }
    }

    private AppConfig readConfigFromForm() {
        applyEditorToSelectedConditionQuietly();
        AppConfig config = readGlobalConfigFromForm();
        List<ConditionConfig> conditions = normalizeConditionTemplatePaths(conditionTableModel.getConditions());
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
        config.setMoveWindowToBackAfterTrigger(moveWindowToBackAfterTriggerCheckBox.isSelected());
        config.setBackgroundClickMode(backgroundClickModeCheckBox.isSelected());
        config.setCaptureMode(resolveBoundCaptureMode());
        config.setStartHotkey(GlobalHotkeyService.normalizeHotkeyText(startHotkeyField.getText(), "F9"));
        config.setStopHotkey(GlobalHotkeyService.normalizeHotkeyText(stopHotkeyField.getText(), "F10"));
        return config;
    }

    private CaptureMode resolveBoundCaptureMode() {
        if (boundWindow == null) {
            return DEFAULT_CAPTURE_MODE;
        }
        return boundCaptureMode == null ? DEFAULT_CAPTURE_MODE : boundCaptureMode;
    }

    private List<String> parseTemplatePaths(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ArrayList<>();
        }
        String[] parts = rawText.split("[\\r\\n;]+");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : parts) {
            String normalized = normalizeTemplatePath(part);
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

    private ConditionTriggerActionType resolveSelectedTriggerActionType() {
        Object selectedItem = triggerActionTypeComboBox.getSelectedItem();
        if (selectedItem instanceof ConditionTriggerActionType actionType) {
            return actionType;
        }
        return ConditionTriggerActionType.CLICK_REGION;
    }

    private void loadConfigToForm(AppConfig config) {
        if (config == null) {
            return;
        }
        boolean previousSuppress = suppressBasicConfigAutoSave;
        suppressBasicConfigAutoSave = true;
        try {
            resetPreviewOverlayState();
            showConditionEditorPlaceholder(null);
            intervalSpinner.setValue(config.getIntervalMs());
            repeatTriggerCheckBox.setSelected(config.isRepeatTrigger());
            moveWindowToBackAfterTriggerCheckBox.setSelected(config.isMoveWindowToBackAfterTrigger());
            backgroundClickModeCheckBox.setSelected(config.isBackgroundClickMode());
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

            List<ConditionConfig> conditions = normalizeConditionTemplatePaths(config.getConditions());
            if (conditions.isEmpty()) {
                ConditionConfig defaultCondition = new ConditionConfig();
                defaultCondition.setName("条件1");
                conditions.add(defaultCondition);
            }
            conditionTableModel.setConditions(conditions);
            if (conditionTableModel.getConditionCount() > 0) {
                conditionTable.getSelectionModel().setSelectionInterval(0, 0);
            }
        } finally {
            suppressBasicConfigAutoSave = previousSuppress;
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
        merged.setMoveWindowToBackAfterTrigger(sourceGlobal.isMoveWindowToBackAfterTrigger());
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

    private ConditionConfig buildConditionConfigFromRuntime(AppConfig config,
                                                            String conditionName,
                                                            List<ConditionTriggerActionConfig> triggerActions) {
        ConditionConfig condition = new ConditionConfig();
        condition.setName(conditionName);
        condition.setThreshold(config.getThreshold());
        condition.setClickX(config.getClickX());
        condition.setClickY(config.getClickY());
        condition.setTriggerActions(triggerActions);
        condition.setTemplatePaths(config.getTemplatePaths());
        condition.setConditionExpression(config.getConditionExpression());
        condition.setMonitorRegion(cloneRegion(config.getMonitorRegion()));
        return condition;
    }

    private void appendConfiguredTriggerActions(MonitorRule rule, ConditionConfig condition) {
        if (rule == null) {
            return;
        }
        ConditionConfig source = condition == null ? new ConditionConfig() : condition;
        for (ConditionTriggerActionConfig actionConfig : source.getTriggerActions()) {
            if (actionConfig == null) {
                continue;
            }
            switch (actionConfig.getType()) {
                case CLICK_REGION -> rule.addAction(new ClickActionStep(actionExecutor, windowService));
                case STOP_MONITORING -> rule.addAction(new StopMonitoringActionStep());
            }
        }
    }

    private void showMonitoringStoppedReminder(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!isDisplayable()) {
                return;
            }
            showScrollableMessageDialog(this, message, "监控已停止", JOptionPane.INFORMATION_MESSAGE);
        });
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
            JFileChooser chooser = createScriptChooser();
            chooser.setDialogTitle("导出方案");
            chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件", "json"));
            chooser.setSelectedFile(scriptDirPath.resolve("auto-script-profile.json").toFile());
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
            JFileChooser chooser = createScriptChooser();
            chooser.setDialogTitle("载入方案");
            chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件", "json"));
            int result = chooser.showOpenDialog(this);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return;
            }
            Path path = chooser.getSelectedFile().toPath();
            AppConfig loaded = configService.loadStrict(path);
            currentConfig = loaded;
            loadConfigToForm(loaded);
            try {
                registerGlobalHotkeys(currentConfig, false);
            } catch (Exception e) {
                log("方案载入后热键注册失败: " + e.getMessage());
            }
            log("方案已载入: " + path + "，条件数量=" + loaded.getConditions().size());
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
                detectCaptureModeForBoundWindow();
                updateBindStatus();
                log("已根据配置自动恢复绑定: " + window.toDisplayText());
                refreshRegionOverlayQuietly();
                refreshClickOverlayQuietly();
                hideBindPanel();
            });
            if (restored.isEmpty()) {
                showBindPanel();
            }
        } catch (Exception e) {
            log("恢复绑定失败: " + e.getMessage());
            showBindPanel();
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
        showScrollableMessageDialog(this, title + "\n" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
    }

    private void shutdown() {
        try {
            basicConfigAutoSaveTimer.stop();
            boundWindowWatchTimer.stop();
            if (conditionEditorDialog != null && conditionEditorDialog.isDisplayable()) {
                conditionEditorDialog.dispose();
            }
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
            selectionMaskOverlay.dispose();
        }
    }

    private JFileChooser createScriptChooser() {
        return createChooserAt(scriptDirPath);
    }

    private JFileChooser createCapturesChooser() {
        return createChooserAt(capturesDirPath);
    }

    private JFileChooser createChooserAt(Path targetDir) {
        Path directory = targetDir == null ? projectRootPath : targetDir;
        try {
            Files.createDirectories(directory);
        } catch (Exception ignored) {
            directory = projectRootPath;
        }
        JFileChooser chooser = new JFileChooser(directory.toFile());
        chooser.setCurrentDirectory(directory.toFile());
        return chooser;
    }

    private List<ConditionConfig> normalizeConditionTemplatePaths(List<ConditionConfig> source) {
        List<ConditionConfig> normalized = new ArrayList<>();
        if (source == null) {
            return normalized;
        }
        for (ConditionConfig condition : source) {
            if (condition == null) {
                continue;
            }
            ConditionConfig copied = new ConditionConfig(condition);
            copied.setTemplatePaths(normalizeTemplatePaths(copied.getTemplatePaths()));
            normalized.add(copied);
        }
        return normalized;
    }

    private List<String> normalizeTemplatePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String path : paths) {
            String normalized = normalizeTemplatePath(path);
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    private String normalizeTemplatePath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String value = rawPath.trim();
        if (value.isBlank()) {
            return "";
        }
        try {
            Path resolved = resolveTemplatePath(value);
            if (resolved.startsWith(projectRootPath)) {
                Path relative = projectRootPath.relativize(resolved);
                if (relative.getNameCount() == 0) {
                    return "./";
                }
                return "./" + relative.toString().replace('\\', '/');
            }
            return resolved.toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private Path resolveTemplatePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("模板路径不能为空");
        }
        Path path = Paths.get(rawPath.trim());
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        return projectRootPath.resolve(path).toAbsolutePath().normalize();
    }

    private record LoadedTemplate(String conditionName, String path, BufferedImage image) {
    }

    private record CapturedRegion(Path outputPath, BufferedImage image, CaptureMode captureMode) {
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
