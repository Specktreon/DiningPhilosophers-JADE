import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import javax.swing.SwingUtilities;

/**
 * Main is the entry point and bootstrapper for the Dining Philosophers
 * Multi-Agent System.
 * 
 * TESTING INSTRUCTIONS FOR THE PROFESSOR / EVALUATOR:
 * --------------------------------------------------
 * 1. Default execution with GUI (5 philosophers):
 * java -cp "bin;lib/jade.jar" Main
 * or: run.bat
 * 
 * 2. Configurable number of philosophers with GUI (any N >= 2):
 * java -cp "bin;lib/jade.jar" Main 3
 * java -cp "bin;lib/jade.jar" Main 7
 * java -cp "bin;lib/jade.jar" Main 10
 * 
 * 3. Headless / Terminal-only mode (No GUI):
 * java -cp "bin;lib/jade.jar" Main 5 -nogui
 * 
 * ARCHITECTURE OVERVIEW:
 * ----------------------
 * Main
 * ├── DiningPhilosophersGUI (Swing Observer Interface - Completely Decoupled)
 * │
 * ├── EnvironmentAgent (Authority for N shared forks)
 * │ ├── One-fork-at-a-time allocation with individual FIFO queues
 * │ └── Non-busy waiting (receive() + block())
 * │
 * └── PhilosopherAgent × N (Philosophers 0 to N-1)
 * ├── One-fork-at-a-time acquisition: min(left, right) then max(left, right)
 * ├── States: Thinking -> Waiting for fork -> Holding fork -> Eating -> Release
 * └── JADE blockingReceive() for dormant waiting (0% CPU usage)
 */
public class Main {

    public static void main(String[] args) {
        int numPhilosophers = 5;
        boolean enableGui = true;
        String snapshotPath = null;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-nogui".equalsIgnoreCase(arg) || "--no-gui".equalsIgnoreCase(arg)
                    || "-headless".equalsIgnoreCase(arg)) {
                enableGui = false;
            } else if ("-snapshot".equalsIgnoreCase(arg) && i + 1 < args.length) {
                snapshotPath = args[++i];
            } else if ("-n".equalsIgnoreCase(arg) && i + 1 < args.length) {
                try {
                    numPhilosophers = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    numPhilosophers = Integer.parseInt(arg);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (numPhilosophers < 2) {
            System.out.println("[Warning] At least 2 philosophers are required. Defaulting to 5.");
            numPhilosophers = 5;
        }

        printBanner(numPhilosophers, enableGui);

        final int totalPhils = numPhilosophers;
        final DiningPhilosophersGUI[] guiHolder = new DiningPhilosophersGUI[1];
        final String snapFile = snapshotPath;

        try {
            // 1. Launch GUI if enabled (completely decoupled observer)
            if (enableGui) {
                final int philsCount = totalPhils;
                SwingUtilities.invokeAndWait(() -> {
                    guiHolder[0] = new DiningPhilosophersGUI(philsCount);
                    EnvironmentAgent.setTableStateListener(guiHolder[0]);
                    PhilosopherAgent.setTableStateListener(guiHolder[0]);
                    guiHolder[0].setVisible(true);
                });
                System.out.println("[Main] Graphical User Interface (Swing) launched successfully.");

                if (snapFile != null) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(7000);
                            if (guiHolder[0] != null) {
                                guiHolder[0].captureSnapshot(snapFile);
                            }
                        } catch (Exception ignored) {
                        }
                    }).start();
                }
            }

            // 2. Initialize the JADE Runtime singleton
            Runtime rt = Runtime.instance();

            // 3. Configure platform container profile with a dynamically allocated free
            // port
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.MAIN_HOST, "localhost");
            profile.setParameter(Profile.MAIN_PORT, String.valueOf(findFreePort()));
            profile.setParameter(Profile.CONTAINER_NAME, "DiningPlatform");
            profile.setParameter(Profile.GUI, "false"); // No JADE RMA GUI needed

            // 4. Create Main-Container
            AgentContainer container = rt.createMainContainer(profile);
            System.out.println("[Main] JADE Main-Container created successfully.");

            // 5. Launch the Environment Agent
            String envName = "Environment";
            AgentController envController = container.createNewAgent(
                    envName,
                    "EnvironmentAgent",
                    new Object[] { totalPhils });
            envController.start();
            System.out.println("[Main] EnvironmentAgent ('" + envName + "') spawned.");

            // Brief pause to allow EnvironmentAgent to initialize
            Thread.sleep(300);

            // 6. Launch N Philosopher Agents
            for (int i = 0; i < totalPhils; i++) {
                String philAgentName = "Philosopher-" + i;
                AgentController philController = container.createNewAgent(
                        philAgentName,
                        "PhilosopherAgent",
                        new Object[] { i, totalPhils, envName });
                philController.start();
            }
            System.out.println("[Main] All " + totalPhils + " PhilosopherAgents have been seated.\n");
            System.out.println("------------------------- SIMULATION RUNNING -------------------------\n");

        } catch (Exception e) {
            System.err.println("[Main] Fatal error initializing JADE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printBanner(int numPhilosophers, boolean enableGui) {
        System.out.println("================================================================================");
        System.out.println("           UNIVERSITÉ PARIS-SACLAY — M1 DKAI — PRACTICAL SESSION 3              ");
        System.out.println("               DINING PHILOSOPHERS MULTI-AGENT SYSTEM (JADE)                    ");
        System.out.println("================================================================================");
        System.out.println(" Configured Philosophers: " + numPhilosophers);
        System.out.println(" Configured Forks:        " + numPhilosophers + " (Fork i is between Phil i and Phil (i+1)%"
                + numPhilosophers + ")");
        System.out.println(" Fork Acquisition:        ONE FORK AT A TIME (Left and Right requested separately)");
        System.out.println(" Deadlock Prevention:     Dijkstra's Resource Hierarchy Ordering [min, max]");
        System.out.println(" Active Waiting / CPU:    Zero busy waiting (JADE blockingReceive)");
        System.out.println(" User Interface:          "
                + (enableGui ? "Swing Graphical Window Enabled" : "Headless Console Mode"));
        System.out.println(" Mode:                    Continuous simulation (Press Ctrl+C or Close Window to stop)");
        System.out.println("================================================================================\n");
    }

    private static int findFreePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 1099;
        }
    }
}
