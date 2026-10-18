/**
 * TableStateListener is an observer interface for the Dining Philosophers system.
 * 
 * It allows the Graphical User Interface (GUI) or other telemetry monitors
 * to receive real-time state notifications without coupling the Multi-Agent System
 * to any UI framework.
 * 
 * Thread-safety note: Implementations updating Swing components should route
 * updates to the AWT Event Dispatch Thread via SwingUtilities.invokeLater().
 */
public interface TableStateListener {

    /**
     * Triggered when a philosopher's state changes.
     * @param philId Philosopher ID (0 to N-1)
     * @param state  State description (e.g., "Thinking", "Waiting for left fork",
     *               "Holding left fork", "Waiting for right fork", "Holding right fork", "Eating")
     */
    void onPhilosopherStateChanged(int philId, String state);

    /**
     * Triggered when the occupancy or ownership of a single fork changes.
     * @param forkId  The index of the fork (0 to N-1)
     * @param inUse   True if the fork is currently held, false if free on table
     * @param ownerId The ID of the philosopher holding it (-1 if free)
     */
    void onForkStateChanged(int forkId, boolean inUse, int ownerId);

    /**
     * Triggered when a significant simulation event occurs for the event log area.
     * @param event The event message text
     */
    void onLogEvent(String event);

    /**
     * Triggered when a philosopher completes a meal.
     * @param philId     Philosopher ID
     * @param totalMeals Total meals completed by this philosopher
     */
    void onMealCompleted(int philId, int totalMeals);

    /**
     * Triggered when a philosopher's hunger or priority level changes.
     * @param philId   Philosopher ID
     * @param hunger   Current hunger level (0 to 100)
     * @param priority Current priority ("LOW", "MEDIUM", "HIGH")
     */
    void onHungerChanged(int philId, int hunger, String priority);
}
