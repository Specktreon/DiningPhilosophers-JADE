import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.Random;

/**
 * PhilosopherAgent represents an autonomous dining philosopher with a dynamic hunger/priority system.
 * 
 * PHILOSOPHER ROSTER:
 * - 0: Socrates, 1: Plato, 2: Aristotle, 3: Descartes, 4: Kant, etc.
 * 
 * HUNGER & PRIORITY SYSTEM:
 * - Hunger level: 0 (satiated) to 100 (starving).
 * - Priority scale:
 *     0–39:  LOW PRIORITY
 *    40–74:  MEDIUM PRIORITY
 *    75–100: HIGH PRIORITY
 * - Dynamics:
 *    - Thinking: hunger gradually increases.
 *    - Waiting for fork: hunger accelerates (+4 per 300ms).
 *    - Eating: hunger decreases to 0.
 * 
 * AGENT-TO-AGENT ACL COMMUNICATION:
 * - When reaching HIGH priority (>= 75), the philosopher sends a FIPA-ACL INFORM message
 *   to neighboring philosophers competing for shared forks:
 *     HUNGER_PRIORITY:id=0:name=Socrates:hunger=85:priority=HIGH
 * - Receiving philosophers reply with:
 *     PRIORITY_ACK:id=1:name=Plato
 * 
 * STRICT DEADLOCK PREVENTION (DIJKSTRA HIERARCHY):
 * - One-fork-at-a-time acquisition: min(left, right) first, then max(left, right).
 * - Hunger priority NEVER violates resource ordering; it only resolves contention
 *   when multiple philosophers are queued for the same fork.
 */
public class PhilosopherAgent extends Agent {

    public static final String[] NAMES = {
        "Socrates", "Plato", "Aristotle", "Descartes", "Kant",
        "Nietzsche", "Confucius", "Epicurus", "Seneca", "Marcus Aurelius",
        "Cicero", "Hume", "Locke", "Spinoza", "Rousseau"
    };

    private int id = 0;
    private int totalPhilosophers = 5;
    private String philosopherName = "Socrates";
    private int leftFork = 0;
    private int rightFork = 0;

    // Dijkstra's ordered forks
    private int firstFork = 0;
    private int secondFork = 0;

    private String environmentAgentName = "Environment";
    private int mealsEaten = 0;
    private final Random rand = new Random();

    // Dynamic Hunger System (0 to 100)
    private int hunger = 10;
    private long lastPriorityBroadcast = 0;

    // Reference to optional GUI listener
    private static TableStateListener stateListener = null;

    // Simulation speed factor (1.0 = normal, 0.5 = half speed)
    private static volatile double speedMultiplier = 1.0;

    public static void setTableStateListener(TableStateListener listener) {
        stateListener = listener;
    }

    public static void setSpeedMultiplier(double speed) {
        speedMultiplier = (speed > 0) ? speed : 1.0;
    }

    public static double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public String getPriorityLevel() {
        if (hunger >= 75) return "HIGH";
        if (hunger >= 40) return "MEDIUM";
        return "LOW";
    }

    public int getHunger() {
        return hunger;
    }

    private void setHunger(int newHunger) {
        this.hunger = Math.max(0, Math.min(100, newHunger));
        notifyHunger();
    }

    private void addHunger(int delta) {
        setHunger(this.hunger + delta);
        if (this.hunger >= 75) {
            checkAndBroadcastPriority();
        }
    }

    private void notifyHunger() {
        if (stateListener != null) {
            stateListener.onHungerChanged(id, hunger, getPriorityLevel());
        }
    }

    private void notifyLog(String message) {
        if (stateListener != null) {
            stateListener.onLogEvent(message);
        }
    }

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            try {
                id = Integer.parseInt(args[0].toString());
                totalPhilosophers = Integer.parseInt(args[1].toString());
                if (args.length >= 3) {
                    environmentAgentName = args[2].toString();
                }
            } catch (Exception e) {
                id = 0;
                totalPhilosophers = 5;
            }
        }

        philosopherName = (id < NAMES.length) ? NAMES[id] : "Philosopher-" + id;
        leftFork = id;
        rightFork = (id + 1) % totalPhilosophers;

        // Dijkstra's Resource Hierarchy ordering [min, max]
        firstFork = Math.min(leftFork, rightFork);
        secondFork = Math.max(leftFork, rightFork);

        String firstSide = (firstFork == leftFork) ? "left" : "right";
        String secondSide = (secondFork == leftFork) ? "left" : "right";

        // Initial staggered hunger so philosophers have diverse starting priorities
        this.hunger = (id * 18 + 10) % 70;
        notifyHunger();

        System.out.println(String.format(
            "[%s (ID: %d)] Seated. Left Fork: %d, Right Fork: %d. Order: [First: %d (%s) -> Second: %d (%s)] Initial Hunger: %d (%s)",
            philosopherName, id, leftFork, rightFork, firstFork, firstSide, secondFork, secondSide, hunger, getPriorityLevel()
        ));

        addBehaviour(new PhilosopherLifecycleBehaviour());
    }

    private class PhilosopherLifecycleBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            // Check if simulation is paused from GUI
            while (EnvironmentAgent.isPaused()) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
            }

            // =============================================================
            // STEP 1: THINKING (Hunger gradually increases)
            // =============================================================
            updateState("Thinking");
            System.out.println(String.format("[%s (ID: %d)] Thinking (Hunger: %d, Priority: %s)",
                philosopherName, id, hunger, getPriorityLevel()));
            
            simulateThinking(1400, 2600);

            // =============================================================
            // STEP 2: REQUEST FIRST FORK (ONE FORK AT A TIME)
            // =============================================================
            updateState("Waiting for Fork " + firstFork);
            System.out.println(String.format(
                "[%s (ID: %d)] Requesting Fork %d (Hunger: %d, Priority: %s)...",
                philosopherName, id, firstFork, hunger, getPriorityLevel()
            ));

            String reqId1 = sendForkRequest(firstFork);

            // Wait for first fork (Hunger continues to accumulate, preventing starvation)
            waitForForkGrant(firstFork, reqId1);

            // First fork acquired!
            updateState("Holding Fork " + firstFork);
            System.out.println(String.format(
                "[%s (ID: %d)] Acquired Fork %d (Hunger: %d, Priority: %s)",
                philosopherName, id, firstFork, hunger, getPriorityLevel()
            ));
            notifyLog(String.format("%s acquired Fork %d", philosopherName, firstFork));

            // Physical delay grabbing fork
            simulateShortPause(300, 500);

            // =============================================================
            // STEP 3: REQUEST SECOND FORK (ONE FORK AT A TIME)
            // =============================================================
            updateState("Waiting for Fork " + secondFork);
            System.out.println(String.format(
                "[%s (ID: %d)] Requesting Fork %d (Hunger: %d, Priority: %s)...",
                philosopherName, id, secondFork, hunger, getPriorityLevel()
            ));

            String reqId2 = sendForkRequest(secondFork);

            // Wait for second fork
            waitForForkGrant(secondFork, reqId2);

            // Both forks acquired!
            updateState("Holding Fork " + secondFork);
            notifyLog(String.format("%s acquired Fork %d", philosopherName, secondFork));

            // =============================================================
            // STEP 4: EATING (Hunger actively resets / decreases)
            // =============================================================
            mealsEaten++;
            updateState("Eating");
            System.out.println(String.format(
                "[%s (ID: %d)] Eating (Meal #%d, Pre-meal Hunger: %d, Priority: %s)",
                philosopherName, id, mealsEaten, hunger, getPriorityLevel()
            ));
            notifyLog(String.format("%s is eating", philosopherName));

            // Inform Environment that eating has started
            ACLMessage eatingMsg = new ACLMessage(ACLMessage.INFORM);
            eatingMsg.addReceiver(new AID(environmentAgentName, AID.ISLOCALNAME));
            eatingMsg.setConversationId("dining-philosophers");
            eatingMsg.setContent("STARTED_EATING:" + id);
            send(eatingMsg);

            simulateEating(1800, 3200);

            // Post-eating: hunger completely satisfied (reset to 0)
            setHunger(0);

            // =============================================================
            // STEP 5: RELEASE FORKS (SEPARATELY)
            // =============================================================
            System.out.println(String.format(
                "[%s (ID: %d)] Finished meal #%d. Releasing forks...",
                philosopherName, id, mealsEaten
            ));

            // Release first fork
            sendForkRelease(firstFork);
            simulateShortPause(150, 300);

            // Release second fork
            sendForkRelease(secondFork);

            // Loop back to THINKING!
        }
    }

    private String sendForkRequest(int forkId) {
        String reqId = "fork-request-" + id + "-" + forkId + "-" + System.currentTimeMillis();
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(new AID(environmentAgentName, AID.ISLOCALNAME));
        msg.setConversationId("dining-philosophers");
        msg.setReplyWith(reqId);
        // Transmit hunger level and philosopher name as metadata
        msg.addUserDefinedParameter("philId", String.valueOf(id));
        msg.addUserDefinedParameter("hunger", String.valueOf(hunger));
        msg.addUserDefinedParameter("philosopherName", philosopherName);
        msg.setContent("FORK_REQUEST:" + forkId);
        send(msg);
        return reqId;
    }

    private void sendForkRelease(int forkId) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(environmentAgentName, AID.ISLOCALNAME));
        msg.setConversationId("dining-philosophers");
        msg.addUserDefinedParameter("philId", String.valueOf(id));
        msg.addUserDefinedParameter("philosopherName", philosopherName);
        msg.setContent("RELEASE_FORK:" + forkId);
        send(msg);
    }

    private void waitForForkGrant(int expectedForkId, String reqId) {
        // MessageTemplate matching conversationId and inReplyTo correlation
        MessageTemplate mtDining = MessageTemplate.and(
            MessageTemplate.MatchConversationId("dining-philosophers"),
            MessageTemplate.and(
                MessageTemplate.MatchInReplyTo(reqId),
                MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.AGREE),
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                )
            )
        );

        boolean granted = false;
        while (!granted) {
            // 1. Process any incoming priority communications from peer philosophers
            processIncomingPriorityMessages();

            // 2. Check for resource grant from Environment matching this specific request
            ACLMessage reply = receive(mtDining);
            if (reply != null) {
                if (reply.getPerformative() == ACLMessage.AGREE 
                        && reply.getContent().equals("FORK_GRANTED:" + expectedForkId)) {
                    granted = true;
                } else if (reply.getPerformative() == ACLMessage.INFORM 
                        && reply.getContent().startsWith("WAIT:")) {
                    // Fork is busy; remain waiting
                }
            }

            if (!granted) {
                // Sleep for ~250ms (0% CPU active waiting)
                int sleepSlice = (int) (250 / speedMultiplier);
                try {
                    Thread.sleep(Math.max(50, sleepSlice));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // While waiting for a contested fork, hunger increases faster (+4)
                // This ensures aging: a waiting philosopher will NEVER starve!
                addHunger(4);
            }
        }
    }

    /**
     * Broadcasts priority to competing neighbor philosophers when hunger reaches HIGH.
     */
    private void checkAndBroadcastPriority() {
        long now = System.currentTimeMillis();
        if (now - lastPriorityBroadcast < 4000) {
            return; // Cooldown of 4 seconds to avoid spamming the log
        }
        lastPriorityBroadcast = now;

        int leftNeighbor = (id - 1 + totalPhilosophers) % totalPhilosophers;
        int rightNeighbor = (id + 1) % totalPhilosophers;

        for (int nId : new int[]{leftNeighbor, rightNeighbor}) {
            String nName = (nId < NAMES.length) ? NAMES[nId] : "Phil-" + nId;

            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID("Philosopher-" + nId, AID.ISLOCALNAME));
            msg.setConversationId("hunger-priority");
            msg.setContent(String.format("HUNGER_PRIORITY:id=%d:name=%s:hunger=%d:priority=%s",
                id, philosopherName, hunger, getPriorityLevel()));
            send(msg);

            String logMsg = String.format("%s → %s: priority update (hunger=%d)",
                philosopherName, nName, hunger);
            notifyLog(logMsg);
            System.out.println(String.format("[%s (ID: %d)] -> [%s (ID: %d)]: priority update (hunger=%d, priority=%s)",
                philosopherName, id, nName, nId, hunger, getPriorityLevel()));
        }
    }

    /**
     * Handles incoming peer-to-peer priority messages from other philosophers.
     */
    private void processIncomingPriorityMessages() {
        MessageTemplate mt = MessageTemplate.MatchConversationId("hunger-priority");
        ACLMessage msg = receive(mt);
        while (msg != null) {
            String content = msg.getContent();
            if (content != null && content.startsWith("HUNGER_PRIORITY:")) {
                String senderName = "Philosopher";
                int senderHunger = 0;
                String[] parts = content.split(":");
                for (String p : parts) {
                    if (p.startsWith("name=")) senderName = p.substring(5);
                    if (p.startsWith("hunger=")) {
                        try { senderHunger = Integer.parseInt(p.substring(7)); } catch (Exception ignored) {}
                    }
                }

                // Send peer acknowledgment
                ACLMessage ack = msg.createReply();
                ack.setPerformative(ACLMessage.INFORM);
                ack.setConversationId("hunger-priority");
                ack.setContent(String.format("PRIORITY_ACK:id=%d:name=%s", id, philosopherName));
                send(ack);
            }
            msg = receive(mt);
        }
    }

    private void simulateThinking(int minMs, int maxMs) {
        long totalDuration = minMs + rand.nextInt(maxMs - minMs + 1);
        long stepMs = (long) (300 / speedMultiplier);
        long elapsed = 0;

        while (elapsed < totalDuration) {
            long thisStep = Math.min(stepMs, totalDuration - elapsed);
            try {
                Thread.sleep(thisStep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            elapsed += thisStep;
            // Hunger steadily grows while contemplating (+2 every slice)
            addHunger(2);
            processIncomingPriorityMessages();
        }
    }

    private void simulateEating(int minMs, int maxMs) {
        long totalDuration = minMs + rand.nextInt(maxMs - minMs + 1);
        long stepMs = (long) (300 / speedMultiplier);
        long elapsed = 0;

        while (elapsed < totalDuration) {
            long thisStep = Math.min(stepMs, totalDuration - elapsed);
            try {
                Thread.sleep(thisStep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            elapsed += thisStep;
            // Hunger visibly drains during meal
            addHunger(-12);
            processIncomingPriorityMessages();
        }
    }

    private void simulateShortPause(int minMs, int maxMs) {
        try {
            long baseDuration = minMs + rand.nextInt(maxMs - minMs + 1);
            long actualDuration = (long) (baseDuration / speedMultiplier);
            Thread.sleep(actualDuration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateState(String stateName) {
        if (stateListener != null) {
            stateListener.onPhilosopherStateChanged(id, stateName);
        }
    }

    @Override
    protected void takeDown() {
        System.out.println(String.format("[%s (ID: %d)] Left the table. Final hunger: %d", philosopherName, id, hunger));
    }
}
