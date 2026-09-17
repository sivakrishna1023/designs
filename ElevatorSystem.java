import java.util.*;

public class Main {
    public static void main(String[] args) {
        // Setup building with 5 floors and 2 elevators
        Building building = new Building(5, 2);

        // Simulate external requests:
        // Floor 0 pressing UP, Floor 3 pressing UP, Floor 4 pressing DOWN
        System.out.println("Simulating external requests...");
        building.floors.get(0).upButton.pressButton();
        building.floors.get(3).upButton.pressButton();
        building.floors.get(4).downButton.pressButton();

        // Let each elevator process its requests
        System.out.println("\nElevator movements:");
        for (ElevatorController controller : building.elevatorControllers) {
            controller.controlElevator();
        }

        // Simulate a passenger inside elevator 0 pressing internal button to floor 2
        System.out.println("\nSimulating internal request:");
        ElevatorController elevator0 = building.elevatorControllers.get(0);
        InternalButton internalButton = new InternalButton(building.internalDispatcher);
        internalButton.pressButton(2, elevator0);

        // Process internal request on elevator 0
        System.out.println("Elevator movements after internal request:");
        elevator0.controlElevator();
    }
}

enum ElevatorDirection {
    UP, DOWN, IDLE
}

enum DoorState {
    OPEN, CLOSED
}

class Door {
    DoorState state;
    public Door() {
        state = DoorState.CLOSED;
    }
    public void openDoor() {
        state = DoorState.OPEN;
    }
    public void closeDoor() {
        state = DoorState.CLOSED;
    }
}

class ElevatorCar {
    int id;
    int currentFloor;
    ElevatorDirection movingDirection;
    Door door;
    public ElevatorCar(int id) {
        this.id = id;
        currentFloor = 0;
        movingDirection = ElevatorDirection.IDLE;
        door = new Door();
    }
    public void showDisplay() {
        System.out.println(
            "Elevator " + id +
            " at floor: " + currentFloor +
            " moving: " + movingDirection
        );
    }
}

class ElevatorScheduler {
    private PriorityQueue<Integer> upQueue;
    private PriorityQueue<Integer> downQueue;
    public ElevatorScheduler() {
        upQueue = new PriorityQueue<>();
        downQueue = new PriorityQueue<>(Collections.reverseOrder());
    }
    public void addRequest(int floor, int currentFloor) {
        if (floor > currentFloor) {
            upQueue.offer(floor);
        } else if (floor < currentFloor) {
            downQueue.offer(floor);
        }
        // If floor == currentFloor, we could open door immediately (omitted for simplicity)
    }
    public int getPendingRequests() {
        return upQueue.size() + downQueue.size();
    }
    public void processRequests(ElevatorCar car) {
        // Process upward requests in ascending order
        if (!upQueue.isEmpty()) {
            car.movingDirection = ElevatorDirection.UP;
            while (!upQueue.isEmpty()) {
                int nextStop = upQueue.poll();
                car.currentFloor = nextStop;
                car.door.openDoor();
                car.showDisplay();
                car.door.closeDoor();
            }
        }
        // Process downward requests in descending order
        if (!downQueue.isEmpty()) {
            car.movingDirection = ElevatorDirection.DOWN;
            while (!downQueue.isEmpty()) {
                int nextStop = downQueue.poll();
                car.currentFloor = nextStop;
                car.door.openDoor();
                car.showDisplay();
                car.door.closeDoor();
            }
        }
        // Go idle when done
        car.movingDirection = ElevatorDirection.IDLE;
    }
}

class ElevatorController {
    ElevatorCar elevatorCar;
    ElevatorScheduler scheduler;
    public ElevatorController(int id) {
        elevatorCar = new ElevatorCar(id);
        scheduler = new ElevatorScheduler();
    }
    public void submitExternalRequest(int floor) {
        scheduler.addRequest(floor, elevatorCar.currentFloor);
    }
    public void submitInternalRequest(int floor) {
        scheduler.addRequest(floor, elevatorCar.currentFloor);
    }
    public void controlElevator() {
        scheduler.processRequests(elevatorCar);
    }
    public int getCurrentFloor() {
        return elevatorCar.currentFloor;
    }
    public int getPendingRequests() {
        return scheduler.getPendingRequests();
    }
}

interface ElevatorSelectionStrategy {
    ElevatorController selectElevator(List<ElevatorController> controllers,
                                      int floor,
                                      ElevatorDirection direction);
}

class NearestElevatorStrategy implements ElevatorSelectionStrategy {
    public ElevatorController selectElevator(List<ElevatorController> controllers, int floor, ElevatorDirection direction) {
        ElevatorController best = null;
        int minDistance = Integer.MAX_VALUE;
        for (ElevatorController controller : controllers) {
            int distance = Math.abs(controller.getCurrentFloor() - floor);
            if (distance < minDistance) {
                minDistance = distance;
                best = controller;
            }
        }
        return best;
    }
}

class LeastBusyStrategy implements ElevatorSelectionStrategy {
    public ElevatorController selectElevator(List<ElevatorController> controllers, int floor, ElevatorDirection direction) {
        ElevatorController best = null;
        int minQueue = Integer.MAX_VALUE;
        for (ElevatorController controller : controllers) {
            int queueSize = controller.getPendingRequests();
            if (queueSize < minQueue) {
                minQueue = queueSize;
                best = controller;
            }
        }
        return best;
    }
}

class ExternalDispatcher {
    List<ElevatorController> controllers;
    ElevatorSelectionStrategy strategy;
    public ExternalDispatcher(List<ElevatorController> controllers, ElevatorSelectionStrategy strategy) {
        this.controllers = controllers;
        this.strategy = strategy;
    }
    public void submitExternalRequest(int floor, ElevatorDirection direction) {
        ElevatorController controller = strategy.selectElevator(controllers, floor, direction);
        if (controller != null) {
            controller.submitExternalRequest(floor);
        }
    }
}

class ExternalButton {
    ExternalDispatcher dispatcher;
    int floor;
    ElevatorDirection direction;
    public ExternalButton(ExternalDispatcher dispatcher, int floor, ElevatorDirection direction) {
        this.dispatcher = dispatcher;
        this.floor = floor;
        this.direction = direction;
    }
    public void pressButton() {
        dispatcher.submitExternalRequest(floor, direction);
    }
}

class Floor {
    int floorNumber;
    ExternalButton upButton;
    ExternalButton downButton;
    public Floor(int floorNumber, ExternalDispatcher dispatcher) {
        this.floorNumber = floorNumber;
        upButton = new ExternalButton(dispatcher, floorNumber, ElevatorDirection.UP);
        downButton = new ExternalButton(dispatcher, floorNumber, ElevatorDirection.DOWN);
    }
}

class InternalDispatcher {
    public void submitInternalRequest(int floor, ElevatorController controller) {
        controller.submitInternalRequest(floor);
    }
}

class InternalButton {
    InternalDispatcher dispatcher;
    public InternalButton(InternalDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
    public void pressButton(int destinationFloor, ElevatorController controller) {
        dispatcher.submitInternalRequest(destinationFloor, controller);
    }
}

class Building {
    List<Floor> floors;
    List<ElevatorController> elevatorControllers;
    ExternalDispatcher externalDispatcher;
    InternalDispatcher internalDispatcher;
    ElevatorSelectionStrategy strategy;
    public Building(int floorsCount, int elevatorsCount) {
        floors = new ArrayList<>();
        elevatorControllers = new ArrayList<>();
        strategy = new NearestElevatorStrategy();  // default strategy
        externalDispatcher = new ExternalDispatcher(elevatorControllers, strategy);
        internalDispatcher = new InternalDispatcher();

        // Initialize elevators
        for (int i = 0; i < elevatorsCount; i++) {
            elevatorControllers.add(new ElevatorController(i));
        }
        // Initialize floors
        for (int i = 0; i < floorsCount; i++) {
            floors.add(new Floor(i, externalDispatcher));
        }
    }
}
