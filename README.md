# Dining Philosophers Multi-Agent System (JADE) + Graphical Interface (GUI)
**Course:** Foundations of Agent-based Systems  
**Institution:** Université Paris-Saclay — M1 DKAI  
**Academic Year:** 2026  

---

## 1. Project Overview

This project implements the classical **Dining Philosophers Problem** as a **Multi-Agent System (MAS)** in **JADE**, featuring an interactive, real-time **Java Swing Graphical User Interface (GUI)**.

### Core Requirements Met:
1. **One-Fork-at-a-Time Allocation:** Each philosopher requests the **left fork and right fork separately** via individual FIPA-ACL messages. No request ever asks for both forks simultaneously.
2. **Provable Deadlock Freedom (Dijkstra's Resource Hierarchy):** Forks are ordered $0 \dots N-1$. Each philosopher requests $\min(\text{left}, \text{right})$ first, then $\max(\text{left}, \text{right})$ second. For the last philosopher, $\min(N-1, 0) = 0$, breaking the circular wait condition.
3. **Environment as an Authoritative Agent:** An [EnvironmentAgent](src/EnvironmentAgent.java) manages all shared forks and maintains per-fork FIFO waiting queues.
4. **Zero Active / Busy Waiting:** Philosophers use JADE's native `blockingReceive()` to wait for fork grants. The Environment uses `receive() + block()`. Idle/waiting states consume **0% CPU**.
5. **Decoupled Graphical User Interface (GUI):** A clean Java Swing window displaying the round table, real-time philosopher states, fork occupancy badges, live event log, and simulation controls (**Start**, **Pause / Resume**, **Stop**, **Reset Log**).

---

## 2. Multi-Agent Architecture

```text
Main (Bootstrap & Launcher)
 ├── DiningPhilosophersGUI (Swing Observer Window)
 │    ├── 2D Table Canvas: Philosophers (Circles) & Forks (Badges)
 │    ├── Control Bar: Start, Pause / Resume, Stop, Reset Log
 │    └── Live Event Log with timestamps
 │
 ├── EnvironmentAgent (Authority for Resources)
 │    ├── Fork occupancy & ownership arrays (size N)
 │    ├── Per-fork FIFO waiting queues
 │    └── Event-driven CyclicBehaviour (receive() + block())
 │
 └── PhilosopherAgent × N (Philosophers 0 to N-1)
      ├── Dijkstra Order: firstFork = min(left, right), secondFork = max(left, right)
      ├── Step-by-Step Lifecycle:
      │    THINKING ──> REQUEST FIRST FORK ──> WAITING ──> HOLDING FIRST FORK
      │             ──> REQUEST SECOND FORK ──> WAITING ──> EATING
      │             ──> RELEASE FIRST FORK ──> RELEASE SECOND FORK ──> repeat
      └── Event-driven WAITING via JADE's blockingReceive()
```

---

## 3. FIPA-ACL Message Protocol

All agent communication uses standardized **FIPA-ACL messages**:

| Message | Sender | Receiver | Performative | Conversation ID | Content | Purpose & Action |
|:---|:---|:---|:---|:---|:---|:---|
| **`REQUEST_FORK`** | `Philosopher` | `Environment` | `REQUEST` | `dining-philosophers` | `REQUEST_FORK:<id>:<forkId>` | Requests a single fork. If free, granted immediately; if busy, enqueued. |
| **`FORK_GRANTED`** | `Environment` | `Philosopher` | `AGREE` | `dining-philosophers` | `FORK_GRANTED:<forkId>` | Environment confirms ownership of this fork. Philosopher wakes from `blockingReceive()`. |
| **`WAIT`** | `Environment` | `Philosopher` | `INFORM` | `dining-philosophers` | `WAIT:<forkId>` | Acknowledges request; fork is busy, philosopher is placed in FIFO queue. |
| **`STARTED_EATING`**| `Philosopher` | `Environment` | `INFORM` | `dining-philosophers` | `STARTED_EATING:<id>` | Informs Environment that both forks are held and eating has started (updates stats & GUI). |
| **`RELEASE_FORK`** | `Philosopher` | `Environment` | `INFORM` | `dining-philosophers` | `RELEASE_FORK:<id>:<forkId>` | Returns a held fork. Environment marks it free and wakes the first queued waiter. |

---

## 4. Philosopher Lifecycle States (Visible on GUI)

```text
1. Thinking                 (Blue badge, contemplates philosophical ideas)
   ↓
2. Waiting for left/right fork (Amber badge, requested first fork, blocked in blockingReceive())
   ↓
3. Holding left/right fork  (Orange badge, acquired first fork, requesting second fork)
   ↓
4. Waiting for other fork   (Amber badge, blocked waiting for second fork)
   ↓
5. Eating                   (Emerald Green badge, holds both forks, enjoying spaghetti)
   ↓
6. Release Forks Separately (Releases first fork, then releases second fork)
   ↓
(Cycles back to Thinking)
```

---

## 5. Why Deadlock is Impossible (Dijkstra's Resource Hierarchy)

A classical deadlock occurs when every philosopher holds their left fork and waits forever for their right fork.

By applying **Dijkstra's Resource Hierarchy**:
- All forks are numbered $0, 1, \dots, N-1$.
- Every philosopher requests $\min(\text{left}, \text{right})$ first, then $\max(\text{left}, \text{right})$ second.
- For philosophers $0$ to $N-2$: $\min(\text{left}, \text{right}) = \text{left}$ (Fork $i$), and $\max = \text{right}$ (Fork $i+1$).
- For the last philosopher $N-1$: Left fork is $N-1$, Right fork is $0$. Therefore, $\min(N-1, 0) = 0$ (their right fork), and $\max = N-1$ (their left fork)!
- **Result:** Philosopher $0$ and Philosopher $N-1$ both compete for **Fork 0 as their first fork**. One of them wins Fork 0; the other cannot acquire any fork and waits. At most $N-1$ philosophers can hold a single fork at any time, guaranteeing that at least one philosopher will always acquire their second fork and eat.
- **Circular Wait is broken mathematically.** Deadlock cannot occur.

---

## 6. How to Compile and Run

### Compilation:
- **Windows Command Prompt:**
  ```cmd
  compile.bat
  ```
- **PowerShell:**
  ```powershell
  .\compile.ps1
  ```
- **Manual Command:**
  ```cmd
  javac -encoding UTF-8 -cp "lib/jade.jar" -d bin src/*.java
  ```

### Running with Graphical Interface (Default):
```cmd
run.bat
```
*(Runs with 5 philosophers and opens the Swing GUI).*

### Configurable Number of Philosophers:
```cmd
run.bat 3
run.bat 7
run.bat 10
```

### Headless / Console-Only Mode:
```cmd
java -cp "bin;lib/jade.jar" Main 5 -nogui
```

---

## 7. Dynamic Hunger & Priority System

### 7.1 Dynamic Hunger & Priority Levels
Every `PhilosopherAgent` maintains a dynamic hunger level ($0 \dots 100$):
- **0–39 (LOW):** Green indicator. Normal thinking or newly satiated.
- **40–74 (MEDIUM):** Amber indicator. Hungry, actively looking for forks.
- **75–100 (HIGH):** Red indicator / Warning. Starving; triggers agent-to-agent communication.

**Hunger Dynamics:**
- **Thinking:** Hunger increases gradually (+1 to +2 per thinking step).
- **Waiting for Forks:** Hunger continues increasing while blocked (+4 per wait slice).
- **Eating:** Hunger drains back to 0 during the meal, resetting priority to LOW.

### 7.2 Agent-to-Agent FIPA-ACL Priority Communication
When a philosopher reaches **HIGH priority** ($\ge 75$ hunger), it sends peer-to-peer JADE ACL messages to notify its neighbors:
- **`HUNGER_PRIORITY` (`INFORM`):**
  ```text
  HUNGER_PRIORITY:id=0:name=Socrates:hunger=85:priority=HIGH
  ```
- **`PRIORITY_ACK` (`INFORM`):**
  The receiving philosopher acknowledges the neighbor's high priority:
  ```text
  PRIORITY_ACK:id=1:name=Plato
  ```

### 7.3 Priority Fork Allocation with Starvation Prevention (Aging)
When a fork is contested in `EnvironmentAgent`:
1. Waiting requests are evaluated using an **Effective Priority**:
   $$\text{effectivePriority} = \text{initialHunger} + (\text{waitSeconds} \times 8.0)$$
2. The waiting philosopher with the highest effective priority is granted the fork first.
3. If priorities are equal, the lower philosopher ID serves as a deterministic tie-breaker.
4. **Starvation Prevention:** Because waiting philosophers continuously accumulate priority over time ($+8.0/\text{sec}$), even a low-hunger philosopher who waits is guaranteed to surpass new high-hunger arrivals and receive the fork.
5. **Deadlock Freedom Preserved:** Prioritizing within a fork's waiting queue does not modify the global resource hierarchy order ($\min(\text{left}, \text{right}) \rightarrow \max(\text{left}, \text{right})$). The resource dependency graph remains strictly acyclic.

---

## 8. UML Architecture & Sequence Diagrams

All UML diagrams are available in [`docs/uml/`](docs/uml/) in `.puml`, `.png`, and `.svg` formats:

1. **[agent-architecture](docs/uml/agent-architecture.png):** Component and class architecture showing `Main`, `EnvironmentAgent`, `PhilosopherAgent`, behaviours, and decoupled GUI observer.
2. **[dining-communication](docs/uml/dining-communication.png):** Sequence diagram of a complete dining cycle using JADE FIPA-ACL messages (`REQUEST`, `AGREE`, `INFORM`).
3. **[hunger-priority-communication](docs/uml/hunger-priority-communication.png):** Sequence diagram showing peer-to-peer priority broadcasting and starvation-free queue resolution.
4. **[deadlock-prevention](docs/uml/deadlock-prevention.png):** Sequence diagram illustrating Dijkstra's resource hierarchy and symmetry breaking.

To re-render the diagrams at any time:
```cmd
python docs/uml/render_uml.py
```
