import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * EnvironmentAgent represents the central table authority in the Dining Philosophers system.
 * 
 * RESOURCE MANAGEMENT:
 * - Manages N shared forks on the table.
 * - Philosophers request ONE FORK AT A TIME (left fork and right fork requested separately).
 * - For each fork, the Environment maintains ownership state and an individual FIFO waiting queue.
 * - When a fork is available, it is granted immediately.
 * - When a fork is occupied, the philosopher is placed into that fork's FIFO queue and sent WAIT.
 * - When a fork is released, the first waiting philosopher in that fork's queue is granted the resource.
 * 
 * DEADLOCK AVOIDANCE:
 * - Guaranteed by Dijkstra's Resource Hierarchy applied by the philosophers (requesting min(left, right)
 *   then max(left, right)).
 * 
 * CPU EFFICIENCY (NO BUSY WAITING):
 * - Uses CyclicBehaviour with blocking message reception (receive() + block()).
 * - Zero polling loops or CPU consumption.
 */
public class EnvironmentAgent extends Agent {

    private int numPhilosophers = 5;

    // forkInUse[i] is true if fork i is currently held, false if free on table
    private boolean[] forkInUse;

    // forkOwner[i] stores the ID of the philosopher holding fork i (-1 if free)
    private int[] forkOwner;

    // Total meals completed by each philosopher
    private int[] mealsCount;

    // Per-fork FIFO waiting queues
    private List<ForkWaitingRequest>[] forkQueues;

    // GUI/Telemetry observer listener
    private static TableStateListener stateListener = null;

    // Simulation pause flag controlled by GUI
    private static volatile boolean isPaused = false;

    public static void setTableStateListener(TableStateListener listener) {
        stateListener = listener;
    }

    public static void setPaused(boolean paused) {
        isPaused = paused;
    }

    public static boolean isPaused() {
        return isPaused;
    }

    /**
     * Inner helper class representing a queued request for a specific fork,
     * including initial hunger and entry timestamp for starvation-preventing aging.
     */
    public static class ForkWaitingRequest {
        final AID philosopherAID;
        final int philId;
        final String philName;
        final int forkId;
        final int initialHunger;
        final long requestTime;
        final String replyWith;

        public ForkWaitingRequest(AID aid, int philId, String philName, int forkId, int initialHunger, String replyWith) {
            this.philosopherAID = aid;
            this.philId = philId;
            this.philName = (philName != null && !philName.isEmpty()) ? philName : "Phil-" + philId;
            this.forkId = forkId;
            this.initialHunger = initialHunger;
            this.requestTime = System.currentTimeMillis();
            this.replyWith = replyWith;
        }

        /**
         * Effective Priority = initialHunger + (waitSeconds * 8.0).
         * This deterministic aging factor guarantees that a waiting philosopher
         * with lower initial hunger steadily increases in priority and will NEVER starve.
         */
        public double getEffectivePriority() {
            double waitSeconds = (System.currentTimeMillis() - requestTime) / 1000.0;
            return initialHunger + (waitSeconds * 8.0);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                numPhilosophers = Integer.parseInt(args[0].toString());
            } catch (Exception e) {
                numPhilosophers = 5;
            }
        }

        forkInUse = new boolean[numPhilosophers];
        forkOwner = new int[numPhilosophers];
        Arrays.fill(forkOwner, -1);
        mealsCount = new int[numPhilosophers];

        forkQueues = new List[numPhilosophers];
        for (int i = 0; i < numPhilosophers; i++) {
            forkQueues[i] = new ArrayList<>();
        }

        System.out.println("[Environment] Table initialized with " + numPhilosophers + " forks.");
        System.out.println("[Environment] Resource authority active (One-fork-at-a-time + Hunger Priority allocation).");
        printTableState();

        addBehaviour(new ResourceAllocationBehaviour());
    }

    private class ResourceAllocationBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();

            if (msg != null) {
                String content = msg.getContent();

                if (msg.getPerformative() == ACLMessage.REQUEST && (content.startsWith("FORK_REQUEST:") || content.startsWith("REQUEST_FORK:"))) {
                    handleRequestFork(msg);
                } else if (msg.getPerformative() == ACLMessage.INFORM && content.startsWith("RELEASE_FORK:")) {
                    handleReleaseFork(msg);
                } else if (msg.getPerformative() == ACLMessage.INFORM && content.startsWith("STARTED_EATING:")) {
                    handleStartedEating(msg);
                }
            } else {
                // If there are no messages, put behaviour to sleep (0% CPU active waiting).
                block();
            }
        }
    }

    /**
     * Handles an individual fork request using standard JADE metadata and correlation:
     * - Performative: REQUEST
     * - Conversation-ID: dining-philosophers
     * - Correlation: replyWith -> inReplyTo
     * - Content: FORK_REQUEST:<forkId>
     * - User-defined parameters: hunger, philId, philosopherName
     */
    private void handleRequestFork(ACLMessage msg) {
        String content = msg.getContent();
        int philId = -1;
        int forkId = -1;
        int hunger = 50;
        String philName = null;
        String reqId = msg.getReplyWith();

        // 1. Extract metadata from user-defined parameters if present
        String philIdParam = msg.getUserDefinedParameter("philId");
        if (philIdParam != null) {
            try { philId = Integer.parseInt(philIdParam); } catch (Exception ignored) {}
        }
        String hungerParam = msg.getUserDefinedParameter("hunger");
        if (hungerParam != null) {
            try { hunger = Integer.parseInt(hungerParam); } catch (Exception ignored) {}
        }
        philName = msg.getUserDefinedParameter("philosopherName");

        // 2. Parse content: supports "FORK_REQUEST:<forkId>" or legacy "REQUEST_FORK:<id>:<forkId>:<hunger>:<name>"
        String[] parts = content.split(":");
        if (parts[0].equals("FORK_REQUEST") && parts.length > 1) {
            forkId = Integer.parseInt(parts[1].trim());
        } else if (parts[0].equals("REQUEST_FORK") && parts.length > 2) {
            if (philId == -1) philId = Integer.parseInt(parts[1].trim());
            forkId = Integer.parseInt(parts[2].trim());
            if (hungerParam == null && parts.length > 3) hunger = Integer.parseInt(parts[3].trim());
            if (philName == null && parts.length > 4) philName = parts[4].trim();
        }

        // 3. Fallback resolution for philosopher identity from sender AID
        if (philId == -1 && msg.getSender() != null) {
            String localName = msg.getSender().getLocalName();
            if (localName != null && localName.startsWith("Philosopher-")) {
                try { philId = Integer.parseInt(localName.substring("Philosopher-".length())); } catch (Exception ignored) {}
            }
        }
        if (philName == null) {
            philName = (philId >= 0 && philId < PhilosopherAgent.NAMES.length) ? PhilosopherAgent.NAMES[philId] : "Philosopher-" + philId;
        }

        String forkSide = getForkSideName(philId, forkId);
        notifyListenerLog(String.format("%s requested Fork %d", philName, forkId));

        if (!forkInUse[forkId] && !isPaused) {
            // Fork is available -> grant immediately with correlated inReplyTo
            forkInUse[forkId] = true;
            forkOwner[forkId] = philId;

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.AGREE);
            if (reqId != null) {
                reply.setInReplyTo(reqId);
            }
            reply.setContent("FORK_GRANTED:" + forkId);
            send(reply);

            System.out.println(String.format(
                "[Environment] -> GRANTED Fork %d (%s) to %s (ID: %d) [Immediate | Hunger: %d | inReplyTo: %s]",
                forkId, forkSide, philName, philId, hunger, reqId
            ));
            printTableState();

            notifyListenerFork(forkId, true, philId);
            notifyListenerLog(String.format("Fork %d granted to %s", forkId, philName));
        } else {
            // Fork is occupied or simulation is paused -> queue request with replyWith correlation ID
            forkQueues[forkId].add(new ForkWaitingRequest(msg.getSender(), philId, philName, forkId, hunger, reqId));

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            if (reqId != null) {
                reply.setInReplyTo(reqId);
            }
            reply.setContent("WAIT:" + forkId);
            send(reply);

            int currentHolder = forkOwner[forkId];
            String reason = (currentHolder >= 0) ? "held by P" + currentHolder : "paused";
            System.out.println(String.format(
                "[Environment] %s (%d) must WAIT for Fork %d (%s) [%s] -> Enqueued with Hunger %d (Queue: %d, reqId: %s)",
                philName, philId, forkId, forkSide, reason, hunger, forkQueues[forkId].size(), reqId
            ));
        }
    }

    /**
     * Handles an individual fork release: RELEASE_FORK:<forkId> or RELEASE_FORK:<philId>:<forkId>
     */
    private void handleReleaseFork(ACLMessage msg) {
        String content = msg.getContent();
        int philId = -1;
        int forkId = -1;

        String philIdParam = msg.getUserDefinedParameter("philId");
        if (philIdParam != null) {
            try { philId = Integer.parseInt(philIdParam); } catch (Exception ignored) {}
        }

        String[] parts = content.split(":");
        if (parts.length == 2) {
            forkId = Integer.parseInt(parts[1].trim());
            if (philId == -1 && msg.getSender() != null) {
                String localName = msg.getSender().getLocalName();
                if (localName != null && localName.startsWith("Philosopher-")) {
                    try { philId = Integer.parseInt(localName.substring("Philosopher-".length())); } catch (Exception ignored) {}
                }
            }
        } else if (parts.length >= 3) {
            philId = Integer.parseInt(parts[1].trim());
            forkId = Integer.parseInt(parts[2].trim());
        }

        String forkSide = getForkSideName(philId, forkId);

        // Free this specific fork
        forkInUse[forkId] = false;
        forkOwner[forkId] = -1;

        System.out.println(String.format(
            "[Environment] <- RELEASED Fork %d (%s) by Philosopher %d",
            forkId, forkSide, philId
        ));
        printTableState();

        String philName = (philId >= 0 && philId < PhilosopherAgent.NAMES.length) ? PhilosopherAgent.NAMES[philId] : "Philosopher-" + philId;
        notifyListenerFork(forkId, false, -1);
        notifyListenerLog(String.format("%s released Fork %d", philName, forkId));

        // Check if any philosopher is waiting for THIS fork
        if (!forkQueues[forkId].isEmpty() && !isPaused) {
            // Select the waiting request with the highest effective hunger priority (including aging)
            ForkWaitingRequest bestReq = selectBestWaitingRequest(forkQueues[forkId]);

            forkInUse[forkId] = true;
            forkOwner[forkId] = bestReq.philId;

            ACLMessage grantMsg = new ACLMessage(ACLMessage.AGREE);
            grantMsg.addReceiver(bestReq.philosopherAID);
            grantMsg.setConversationId("dining-philosophers");
            if (bestReq.replyWith != null) {
                grantMsg.setInReplyTo(bestReq.replyWith);
            }
            grantMsg.setContent("FORK_GRANTED:" + forkId);
            send(grantMsg);

            String nextSide = getForkSideName(bestReq.philId, forkId);
            System.out.println(String.format(
                "[Environment] -> GRANTED Fork %d (%s) to prioritized %s (ID: %d) [Hunger: %d, EffectivePriority: %.1f, inReplyTo: %s, Queue left: %d]",
                forkId, nextSide, bestReq.philName, bestReq.philId, bestReq.initialHunger, bestReq.getEffectivePriority(), bestReq.replyWith, forkQueues[forkId].size()
            ));
            printTableState();

            notifyListenerFork(forkId, true, bestReq.philId);
            notifyListenerLog(String.format("Fork %d granted to %s (Hunger: %d)", forkId, bestReq.philName, bestReq.initialHunger));
        }
    }

    /**
     * Selects and removes the waiting request with the highest effective hunger priority.
     * Uses a deterministic tie-breaker (earlier requestTime, then smaller philId) to guarantee fairness.
     */
    private ForkWaitingRequest selectBestWaitingRequest(List<ForkWaitingRequest> queue) {
        if (queue == null || queue.isEmpty()) return null;
        int bestIdx = 0;
        double bestScore = queue.get(0).getEffectivePriority();

        for (int i = 1; i < queue.size(); i++) {
            double score = queue.get(i).getEffectivePriority();
            if (score > bestScore + 1.5) {
                bestScore = score;
                bestIdx = i;
            } else if (Math.abs(score - bestScore) <= 1.5) {
                // Tie-breaker: request arrival order
                if (queue.get(i).requestTime < queue.get(bestIdx).requestTime) {
                    bestScore = score;
                    bestIdx = i;
                } else if (queue.get(i).requestTime == queue.get(bestIdx).requestTime
                        && queue.get(i).philId < queue.get(bestIdx).philId) {
                    bestScore = score;
                    bestIdx = i;
                }
            }
        }
        return queue.remove(bestIdx);
    }

    private void handleStartedEating(ACLMessage msg) {
        String[] parts = msg.getContent().split(":");
        int philId = Integer.parseInt(parts[1].trim());
        mealsCount[philId]++;

        notifyListenerMeal(philId, mealsCount[philId]);
    }

    /**
     * Determines whether a given fork is the left or right fork for a philosopher.
     */
    private String getForkSideName(int philId, int forkId) {
        int leftFork = philId;
        return (forkId == leftFork) ? "left" : "right";
    }

    private void printTableState() {
        StringBuilder sb = new StringBuilder();
        sb.append("      [Table State] Forks: ");
        for (int i = 0; i < numPhilosophers; i++) {
            sb.append("F").append(i).append(":");
            if (forkInUse[i]) {
                sb.append("[P").append(forkOwner[i]).append("]");
            } else {
                sb.append("[FREE]");
            }
            if (i < numPhilosophers - 1) sb.append(" | ");
        }
        System.out.println(sb.toString());
    }

    // Observer notification helpers
    private void notifyListenerFork(int forkId, boolean inUse, int ownerId) {
        if (stateListener != null) {
            stateListener.onForkStateChanged(forkId, inUse, ownerId);
        }
    }

    private void notifyListenerLog(String message) {
        if (stateListener != null) {
            stateListener.onLogEvent(message);
        }
    }

    private void notifyListenerMeal(int philId, int meals) {
        if (stateListener != null) {
            stateListener.onMealCompleted(philId, meals);
        }
    }

    @Override
    protected void takeDown() {
        System.out.println("[Environment] Table closed. Final meals: " + Arrays.toString(mealsCount));
    }
}
