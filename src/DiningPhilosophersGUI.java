import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * DiningPhilosophersGUI provides an interactive, real-time visual representation of the Dining Philosophers MAS.
 * 
 * FEATURES:
 * - Dynamic Hunger & Priority System:
 *   - Each philosopher has a dynamic hunger level (0 to 100).
 *   - Priority categories: LOW (0–39), MEDIUM (40–74), HIGH (75–100).
 *   - Dynamic color-coded progress bars on every philosopher.
 * - Peer-to-Peer Communication & Live ACL Event Log:
 *   - Displays philosopher priority updates and allocation events.
 * - Eating Animation:
 *   - Clean visual plate and eating indicators during dining.
 * - Decoupled Observer:
 *   - Implements TableStateListener. Safe EDT updates via SwingUtilities.invokeLater().
 */
public class DiningPhilosophersGUI extends JFrame implements TableStateListener {

    private final int numPhilosophers;
    private final String[] philosopherNames;
    private final String[] philosopherStates;
    private final int[] philosopherMeals;
    private final int[] philosopherHunger;
    private final String[] philosopherPriority;

    private final boolean[] forkInUse;
    private final int[] forkOwner;

    private final TableCanvas tableCanvas;
    private final JTextArea logArea;
    private final JButton btnStart;
    private final JButton btnPause;
    private final JButton btnStop;
    private final JButton btnReset;
    private final JButton btnSpeed;
    private double currentSpeed = 1.0;
    private final JLabel lblStatus;

    private final Random rand = new Random();
    private final javax.swing.Timer animationTimer;
    private int animTick = 0;

    // Classical Philosopher Roster
    public static final String[] DEFAULT_NAMES = {
        "Socrates", "Plato", "Aristotle", "Descartes", "Kant",
        "Nietzsche", "Confucius", "Epicurus", "Seneca", "Marcus Aurelius",
        "Cicero", "Hume", "Locke", "Spinoza", "Rousseau"
    };

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public DiningPhilosophersGUI(int numPhilosophers) {
        super("Dining Philosophers Multi-Agent System (JADE) — Hunger & Priority System");
        this.numPhilosophers = numPhilosophers;

        this.philosopherNames = new String[numPhilosophers];
        this.philosopherStates = new String[numPhilosophers];
        this.philosopherMeals = new int[numPhilosophers];
        this.philosopherHunger = new int[numPhilosophers];
        this.philosopherPriority = new String[numPhilosophers];

        for (int i = 0; i < numPhilosophers; i++) {
            this.philosopherNames[i] = (i < DEFAULT_NAMES.length) ? DEFAULT_NAMES[i] : "Phil-" + i;
            this.philosopherStates[i] = "Thinking";
            this.philosopherMeals[i] = 0;
            this.philosopherHunger[i] = (i * 18 + 10) % 70;
            this.philosopherPriority[i] = (this.philosopherHunger[i] >= 75) ? "HIGH" : 
                                          (this.philosopherHunger[i] >= 40) ? "MEDIUM" : "LOW";
        }

        this.forkInUse = new boolean[numPhilosophers];
        this.forkOwner = new int[numPhilosophers];
        for (int i = 0; i < numPhilosophers; i++) {
            this.forkOwner[i] = -1;
        }

        // Setup Window
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 760);
        setMinimumSize(new Dimension(950, 640));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // -------------------------------------------------------------
        // 1. TOP HEADER & CONTROL PANEL
        // -------------------------------------------------------------
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(30, 41, 59)); // Slate dark
        topPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel("Dining Philosophers MAS — " + numPhilosophers + " Philosophers with Hunger Priority");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleLabel.setForeground(Color.WHITE);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlsPanel.setOpaque(false);

        lblStatus = new JLabel("Status: RUNNING");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblStatus.setForeground(new Color(52, 211, 153)); // Emerald green

        btnSpeed = createStyledButton("Speed: 1×", new Color(99, 102, 241)); // Indigo
        btnStart = createStyledButton("Start", new Color(16, 185, 129));
        btnPause = createStyledButton("Pause", new Color(245, 158, 11));
        btnStop  = createStyledButton("Stop", new Color(239, 68, 68));
        btnReset = createStyledButton("Reset Log", new Color(100, 116, 139));

        // Initialize logArea early
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(241, 245, 249));
        logArea.setForeground(new Color(30, 41, 59));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        btnSpeed.addActionListener(e -> {
            if (currentSpeed == 1.0) {
                currentSpeed = 0.5;
                PhilosopherAgent.setSpeedMultiplier(0.5);
                btnSpeed.setText("Speed: 0.5×");
                appendLog("--- Simulation speed set to 0.5× (Half Speed) ---");
            } else {
                currentSpeed = 1.0;
                PhilosopherAgent.setSpeedMultiplier(1.0);
                btnSpeed.setText("Speed: 1×");
                appendLog("--- Simulation speed set to 1× (Normal Speed) ---");
            }
        });

        btnStart.addActionListener(e -> {
            EnvironmentAgent.setPaused(false);
            lblStatus.setText("Status: RUNNING");
            lblStatus.setForeground(new Color(52, 211, 153));
            btnPause.setText("Pause");
            appendLog("--- Simulation Started / Resumed by User ---");
        });

        btnPause.addActionListener(e -> {
            boolean currentlyPaused = EnvironmentAgent.isPaused();
            boolean newPausedState = !currentlyPaused;
            EnvironmentAgent.setPaused(newPausedState);
            if (newPausedState) {
                lblStatus.setText("Status: PAUSED");
                lblStatus.setForeground(new Color(251, 191, 36));
                btnPause.setText("Resume");
                appendLog("--- Simulation PAUSED by User ---");
            } else {
                lblStatus.setText("Status: RUNNING");
                lblStatus.setForeground(new Color(52, 211, 153));
                btnPause.setText("Pause");
                appendLog("--- Simulation RESUMED by User ---");
            }
        });

        btnStop.addActionListener(e -> {
            EnvironmentAgent.setPaused(true);
            lblStatus.setText("Status: STOPPED");
            lblStatus.setForeground(new Color(248, 113, 113));
            appendLog("--- Simulation STOPPED by User ---");
        });

        btnReset.addActionListener(e -> {
            logArea.setText("");
            appendLog("--- Log area cleared ---");
        });

        controlsPanel.add(lblStatus);
        controlsPanel.add(Box.createHorizontalStrut(10));
        controlsPanel.add(btnSpeed);
        controlsPanel.add(btnStart);
        controlsPanel.add(btnPause);
        controlsPanel.add(btnStop);
        controlsPanel.add(btnReset);

        topPanel.add(titleLabel, BorderLayout.WEST);
        topPanel.add(controlsPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // -------------------------------------------------------------
        // 2. CENTER: TABLE CANVAS
        // -------------------------------------------------------------
        tableCanvas = new TableCanvas();
        tableCanvas.setBackground(new Color(248, 250, 252));

        // -------------------------------------------------------------
        // 3. RIGHT: EVENT LOG PANEL
        // -------------------------------------------------------------
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setPreferredSize(new Dimension(430, 0));
        rightPanel.setBorder(new EmptyBorder(0, 0, 10, 12));

        JLabel logHeader = new JLabel("Live Event Log (ACL & Priority Communications)");
        logHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        logHeader.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logScrollPane.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));

        rightPanel.add(logHeader, BorderLayout.NORTH);
        rightPanel.add(logScrollPane, BorderLayout.CENTER);

        // Comprehensive Legend at bottom of log panel
        JPanel legendPanel = createLegendPanel();
        rightPanel.add(legendPanel, BorderLayout.SOUTH);

        add(tableCanvas, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // Lightweight animation timer (ticking every 90ms for eating & steam animations)
        animationTimer = new javax.swing.Timer(90, e -> {
            animTick++;
            tableCanvas.repaint();
        });
        animationTimer.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (animationTimer != null) {
                    animationTimer.stop();
                }
            }
        });
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.darker(), 1),
            new EmptyBorder(6, 11, 6, 11)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel createLegendPanel() {
        JPanel container = new JPanel(new GridLayout(2, 1, 4, 4));
        container.setBackground(Color.WHITE);
        container.setBorder(BorderFactory.createTitledBorder("Simulation Legend"));

        // States legend
        JPanel statePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statePanel.setOpaque(false);
        statePanel.add(new JLabel("States:"));
        statePanel.add(createLegendDot("Thinking", new Color(59, 130, 246)));
        statePanel.add(createLegendDot("Waiting", new Color(245, 158, 11)));
        statePanel.add(createLegendDot("Holding 1", new Color(234, 88, 12)));
        statePanel.add(createLegendDot("Eating", new Color(16, 185, 129)));

        // Priority legend
        JPanel prioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        prioPanel.setOpaque(false);
        prioPanel.add(new JLabel("Priority:"));
        prioPanel.add(createLegendDot("LOW (0-39)", new Color(16, 185, 129)));
        prioPanel.add(createLegendDot("MED (40-74)", new Color(245, 158, 11)));
        prioPanel.add(createLegendDot("HIGH (75-100)", new Color(239, 68, 68)));

        container.add(statePanel);
        container.add(prioPanel);
        return container;
    }

    private JPanel createLegendDot(String label, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        p.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setFont(new Font("SansSerif", Font.BOLD, 13));
        dot.setForeground(color);
        JLabel txt = new JLabel(label);
        txt.setFont(new Font("SansSerif", Font.PLAIN, 10));
        p.add(dot);
        p.add(txt);
        return p;
    }

    private void appendLog(String message) {
        String timestamp = timeFormat.format(new Date());
        logArea.append("[" + timestamp + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // =========================================================================
    // TableStateListener Implementation (Thread-safe via SwingUtilities)
    // =========================================================================

    @Override
    public void onPhilosopherStateChanged(int philId, String state) {
        SwingUtilities.invokeLater(() -> {
            if (philId >= 0 && philId < numPhilosophers) {
                philosopherStates[philId] = state;
                tableCanvas.repaint();
            }
        });
    }

    @Override
    public void onForkStateChanged(int forkId, boolean inUse, int ownerId) {
        SwingUtilities.invokeLater(() -> {
            if (forkId >= 0 && forkId < numPhilosophers) {
                forkInUse[forkId] = inUse;
                forkOwner[forkId] = ownerId;
                tableCanvas.repaint();
            }
        });
    }

    @Override
    public void onLogEvent(String event) {
        SwingUtilities.invokeLater(() -> appendLog(event));
    }

    @Override
    public void onMealCompleted(int philId, int totalMeals) {
        SwingUtilities.invokeLater(() -> {
            if (philId >= 0 && philId < numPhilosophers) {
                philosopherMeals[philId] = totalMeals;
                tableCanvas.repaint();
            }
        });
    }

    @Override
    public void onHungerChanged(int philId, int hunger, String priority) {
        SwingUtilities.invokeLater(() -> {
            if (philId >= 0 && philId < numPhilosophers) {
                philosopherHunger[philId] = hunger;
                philosopherPriority[philId] = priority;
                tableCanvas.repaint();
            }
        });
    }

    // =========================================================================
    // 2D Custom Table Canvas
    // =========================================================================
    private class TableCanvas extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int centerX = width / 2;
            int centerY = height / 2;

            int minDim = Math.min(width, height);
            int tableRadius = (int) (minDim * 0.25);
            int philosopherOrbit = (int) (minDim * 0.35);
            int forkOrbit = (int) (minDim * 0.23);

            // 1. Draw Dining Table (Round wooden aesthetic)
            g2.setColor(new Color(229, 231, 235)); // Outer rim
            g2.fillOval(centerX - tableRadius - 8, centerY - tableRadius - 8, (tableRadius + 8) * 2, (tableRadius + 8) * 2);

            g2.setColor(new Color(254, 243, 199)); // Table top (warm beige)
            g2.fillOval(centerX - tableRadius, centerY - tableRadius, tableRadius * 2, tableRadius * 2);

            g2.setColor(new Color(217, 119, 6)); // Wooden border
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(centerX - tableRadius, centerY - tableRadius, tableRadius * 2, tableRadius * 2);

            // Center Plate / Bowl
            int bowlRadius = (int) (minDim * 0.08);
            g2.setColor(new Color(254, 202, 202)); // Spaghetti plate
            g2.fillOval(centerX - bowlRadius, centerY - bowlRadius, bowlRadius * 2, bowlRadius * 2);
            g2.setColor(new Color(220, 38, 38));
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(centerX - bowlRadius, centerY - bowlRadius, bowlRadius * 2, bowlRadius * 2);

            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            String bowlText = "Spaghetti";
            g2.drawString(bowlText, centerX - fm.stringWidth(bowlText) / 2, centerY + 4);

            // 2. Draw connecting lines from table center to philosophers
            double angleStep = 2 * Math.PI / numPhilosophers;
            for (int i = 0; i < numPhilosophers; i++) {
                double angle = i * angleStep - Math.PI / 2;
                int philX = (int) (centerX + philosopherOrbit * Math.cos(angle));
                int philY = (int) (centerY + philosopherOrbit * Math.sin(angle));

                g2.setColor(new Color(226, 232, 240));
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4}, 0));
                g2.drawLine(centerX, centerY, philX, philY);
            }

            // 3. Draw Forks (positioned at half-angles between philosophers)
            for (int i = 0; i < numPhilosophers; i++) {
                double forkAngle = (i + 0.5) * angleStep - Math.PI / 2;
                int forkX = (int) (centerX + forkOrbit * Math.cos(forkAngle));
                int forkY = (int) (centerY + forkOrbit * Math.sin(forkAngle));

                drawFork(g2, i, forkX, forkY, forkInUse[i], forkOwner[i]);
            }

            // 4. Draw Eating Animations (Plate, noodles, steam, moving fork) for Eating philosophers
            int philRadius = (numPhilosophers > 7) ? 36 : 46;
            for (int i = 0; i < numPhilosophers; i++) {
                double angle = i * angleStep - Math.PI / 2;
                int philX = (int) (centerX + philosopherOrbit * Math.cos(angle));
                int philY = (int) (centerY + philosopherOrbit * Math.sin(angle));

                // Check eating state and fork ownership
                boolean isEating = "Eating".equalsIgnoreCase(philosopherStates[i]);
                int leftFork = i;
                int rightFork = (i + 1) % numPhilosophers;
                boolean holdsBothForks = (forkOwner[leftFork] == i && forkOwner[rightFork] == i);

                if (isEating && holdsBothForks) {
                    drawEatingAnimation(g2, i, centerX, centerY, tableRadius, angle, philX, philY, philRadius);
                }
            }

            // 5. Draw Philosophers
            for (int i = 0; i < numPhilosophers; i++) {
                double angle = i * angleStep - Math.PI / 2;
                int philX = (int) (centerX + philosopherOrbit * Math.cos(angle));
                int philY = (int) (centerY + philosopherOrbit * Math.sin(angle));

                drawPhilosopher(g2, i, philX, philY, philRadius);
            }

        }

        private void drawEatingAnimation(Graphics2D g2, int id, int centerX, int centerY, int tableRadius, 
                                        double angle, int philX, int philY, int philRadius) {
            // Personal eating plate at the table rim in front of the philosopher
            int plateDist = tableRadius - 16;
            int plateX = centerX + (int) (plateDist * Math.cos(angle));
            int plateY = centerY + (int) (plateDist * Math.sin(angle));
            int plateR = (numPhilosophers > 7) ? 13 : 16;

            // White porcelain personal plate
            g2.setColor(Color.WHITE);
            g2.fillOval(plateX - plateR, plateY - plateR, plateR * 2, plateR * 2);
            g2.setColor(new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(plateX - plateR, plateY - plateR, plateR * 2, plateR * 2);

            // Tomato marinara sauce
            int sauceR = (int) (plateR * 0.75);
            g2.setColor(new Color(239, 68, 68));
            g2.fillOval(plateX - sauceR, plateY - sauceR, sauceR * 2, sauceR * 2);

            // Animated yellow spaghetti swirl
            int spin = (animTick * 12) % 360;
            g2.setColor(new Color(251, 191, 36));
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawArc(plateX - sauceR / 2, plateY - sauceR / 2, sauceR, sauceR, spin, 240);

            // Animated steam wisps rising above plate
            for (int s = 0; s < 2; s++) {
                int steamAge = (animTick * 2 + s * 12) % 24;
                float steamAlpha = Math.max(0f, 1f - (steamAge / 24f));
                int steamY = plateY - plateR - steamAge;
                int steamX = plateX + (int) (Math.sin((animTick + s * 6) * 0.35) * 4);
                g2.setColor(new Color(156, 163, 175, (int) (steamAlpha * 140)));
                g2.fillOval(steamX - 2, steamY - 2, 4, 4);
            }

            // Animated fork moving back and forth between plate and philosopher
            double progress = 0.5 + 0.5 * Math.sin(animTick * 0.35); // 0.0 to 1.0
            double forkDistFraction = 0.22 + 0.48 * progress;
            int forkX = (int) (plateX + (philX - plateX) * forkDistFraction);
            int forkY = (int) (plateY + (philY - plateY) * forkDistFraction);

            drawForkWithBite(g2, forkX, forkY, angle);
        }

        private void drawForkWithBite(Graphics2D g2, int fx, int fy, double angle) {
            Graphics2D gFork = (Graphics2D) g2.create();
            gFork.translate(fx, fy);
            gFork.rotate(angle + Math.PI / 2);

            // Metallic silver fork handle
            gFork.setColor(new Color(148, 163, 184));
            gFork.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gFork.drawLine(0, 9, 0, -3);

            // Fork prongs
            gFork.drawLine(-3, -3, -3, -9);
            gFork.drawLine(0, -3, 0, -10);
            gFork.drawLine(3, -3, 3, -9);

            // Spaghetti bite on the fork tips!
            gFork.setColor(new Color(245, 158, 11)); // Amber noodle bite
            gFork.fillOval(-4, -11, 8, 5);

            gFork.dispose();
        }

        private void drawPhilosopher(Graphics2D g2, int id, int x, int y, int radius) {
            String state = philosopherStates[id];
            Color stateColor = getStateColor(state);
            boolean isEating = "Eating".equalsIgnoreCase(state);
            int hunger = philosopherHunger[id];
            String priority = philosopherPriority[id];

            // 1. Draw outer shadow or pulsating eating halo
            if (isEating) {
                float pulse = (float) (0.5 + 0.5 * Math.sin(animTick * 0.28));
                int glow = (int) (6 * pulse + 4);
                g2.setColor(new Color(16, 185, 129, (int) (35 + 50 * pulse)));
                g2.fillOval(x - radius - glow, y - radius - glow, (radius + glow) * 2, (radius + glow) * 2);
            } else {
                g2.setColor(new Color(stateColor.getRed(), stateColor.getGreen(), stateColor.getBlue(), 45));
                g2.fillOval(x - radius - 5, y - radius - 5, (radius + 5) * 2, (radius + 5) * 2);
            }

            // 2. Draw body circle
            g2.setColor(Color.WHITE);
            g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);

            g2.setColor(stateColor);
            g2.setStroke(new BasicStroke(isEating ? 5 : 4));
            g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);

            // 3. Information inside philosopher circle
            // Philosopher Name
            g2.setColor(new Color(15, 23, 42));
            g2.setFont(new Font("SansSerif", Font.BOLD, (numPhilosophers > 7) ? 10 : 12));
            FontMetrics fm = g2.getFontMetrics();
            String name = philosopherNames[id];
            g2.drawString(name, x - fm.stringWidth(name) / 2, y - 14);

            // Meals or Eating indicator
            if (isEating) {
                g2.setColor(new Color(5, 150, 105)); // Emerald
                g2.setFont(new Font("SansSerif", Font.BOLD, (numPhilosophers > 7) ? 8 : 10));
                fm = g2.getFontMetrics();
                String eatingIndicator = "EATING";
                g2.drawString(eatingIndicator, x - fm.stringWidth(eatingIndicator) / 2, y - 2);
            } else {
                g2.setColor(new Color(100, 116, 139));
                g2.setFont(new Font("SansSerif", Font.PLAIN, (numPhilosophers > 7) ? 8 : 9));
                fm = g2.getFontMetrics();
                String mealsText = "Meals: " + philosopherMeals[id];
                g2.drawString(mealsText, x - fm.stringWidth(mealsText) / 2, y - 2);
            }

            // Hunger & Priority label
            Color prioColor;
            if (hunger >= 75) {
                prioColor = new Color(239, 68, 68); // Red
            } else if (hunger >= 40) {
                prioColor = new Color(245, 158, 11); // Amber
            } else {
                prioColor = new Color(16, 185, 129); // Green
            }

            g2.setColor(prioColor);
            g2.setFont(new Font("SansSerif", Font.BOLD, (numPhilosophers > 7) ? 8 : 9));
            fm = g2.getFontMetrics();
            String hungerLabel = "H: " + hunger + " (" + priority + ")";
            g2.drawString(hungerLabel, x - fm.stringWidth(hungerLabel) / 2, y + 10);

            // Sleek Horizontal Progress Bar for Hunger
            int barW = (numPhilosophers > 7) ? 44 : 54;
            int barH = 5;
            int barX = x - barW / 2;
            int barY = y + 16;

            // Bar background
            g2.setColor(new Color(226, 232, 240));
            g2.fillRoundRect(barX, barY, barW, barH, 3, 3);

            // Bar fill
            int fillW = (int) (barW * (Math.max(0, Math.min(100, hunger)) / 100.0));
            g2.setColor(prioColor);
            g2.fillRoundRect(barX, barY, fillW, barH, 3, 3);

            // Bar border
            g2.setColor(new Color(148, 163, 184));
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawRoundRect(barX, barY, barW, barH, 3, 3);

            // 4. State badge below circle
            g2.setFont(new Font("SansSerif", Font.BOLD, (numPhilosophers > 7) ? 9 : 10));
            fm = g2.getFontMetrics();
            String badgeText = state;
            int badgeW = fm.stringWidth(badgeText) + 12;
            int badgeH = 16;
            int badgeX = x - badgeW / 2;
            int badgeY = y + radius - 4;

            g2.setColor(stateColor);
            g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 8, 8);

            g2.setColor(Color.WHITE);
            g2.drawString(badgeText, badgeX + 6, badgeY + 12);
        }

        private void drawFork(Graphics2D g2, int forkId, int x, int y, boolean inUse, int ownerId) {
            int forkBoxW = (numPhilosophers > 7) ? 56 : 64;
            int forkBoxH = 26;
            int boxX = x - forkBoxW / 2;
            int boxY = y - forkBoxH / 2;

            if (inUse) {
                // Held by an agent: Golden Amber badge
                g2.setColor(new Color(254, 240, 138));
                g2.fillRoundRect(boxX, boxY, forkBoxW, forkBoxH, 6, 6);

                g2.setColor(new Color(202, 138, 4));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(boxX, boxY, forkBoxW, forkBoxH, 6, 6);

                g2.setColor(new Color(133, 77, 14));
                g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                String ownerName = (ownerId >= 0 && ownerId < philosopherNames.length) ? 
                    philosopherNames[ownerId] : "P" + ownerId;
                String text = "F" + forkId + " (" + ownerName + ")";
                g2.drawString(text, x - fm.stringWidth(text) / 2, y + 4);
            } else {
                // Available: Clean neutral badge
                g2.setColor(new Color(241, 245, 249));
                g2.fillRoundRect(boxX, boxY, forkBoxW, forkBoxH, 6, 6);

                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(boxX, boxY, forkBoxW, forkBoxH, 6, 6);

                g2.setColor(new Color(71, 85, 105));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                FontMetrics fm = g2.getFontMetrics();
                String text = "F" + forkId + " [Free]";
                g2.drawString(text, x - fm.stringWidth(text) / 2, y + 4);
            }
        }

        private Color getStateColor(String state) {
            if (state == null) return Color.GRAY;
            switch (state) {
                case "Thinking":
                    return new Color(59, 130, 246); // Dodger Blue
                case "Waiting for left fork":
                case "Waiting for right fork":
                    return new Color(245, 158, 11); // Amber / Gold
                case "Holding left fork":
                case "Holding right fork":
                    return new Color(234, 88, 12);  // Dark Orange
                case "Eating":
                    return new Color(16, 185, 129); // Emerald Green
                default:
                    return new Color(100, 116, 139);
            }
        }
    }

    /**
     * Captures a high-resolution PNG snapshot of the GUI window.
     */
    public void captureSnapshot(String filePath) {
        try {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                w = 1240;
                h = 760;
            }
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            paint(g2);
            g2.dispose();
            javax.imageio.ImageIO.write(img, "png", new java.io.File(filePath));
            System.out.println("[GUI] Snapshot saved successfully to " + filePath);
        } catch (Exception e) {
            System.err.println("[GUI] Failed to capture snapshot: " + e.getMessage());
        }
    }
}
