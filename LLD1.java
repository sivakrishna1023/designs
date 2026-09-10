import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

enum VehicleType {
    TWO, FOUR, HEAVY
}

enum PaymentType {
    CASH, CARD, UPI
}

class Vehicle {
    int regNum;
    VehicleType vType;

    public Vehicle(int regNum, VehicleType vType) {
        this.regNum = regNum;
        this.vType = vType;
    }
}

class ParkingSpace {
    int spaceId;
    int floorId;
    Vehicle vehicle;
    double price;
    VehicleType type;
    boolean isEmpty;

    public ParkingSpace(int spaceId, int floorId, double price, VehicleType type) {
        this.spaceId = spaceId;
        this.floorId = floorId;
        this.price = price;
        this.type = type;
        this.isEmpty = true;
    }

    public void parkVehicle(Vehicle v) {
        isEmpty = false;
        vehicle = v;
    }

    public void removeVehicle() {
        vehicle = null;
        isEmpty = true;
    }
}

class TwoWheelerSpace extends ParkingSpace {
    public TwoWheelerSpace(int spaceId, int floorId) {
        super(spaceId, floorId, 10, VehicleType.TWO);
    }
}

class FourWheelerSpace extends ParkingSpace {
    public FourWheelerSpace(int spaceId, int floorId) {
        super(spaceId, floorId, 20, VehicleType.FOUR);
    }
}

class HeavyVehicleSpace extends ParkingSpace {
    public HeavyVehicleSpace(int spaceId, int floorId) {
        super(spaceId, floorId, 40, VehicleType.HEAVY);
    }
}

class ParkingSpaceFactory {
    public ParkingSpace getParkingSpace(VehicleType type, int spaceId, int floorId) {
        switch (type) {
            case TWO:   return new TwoWheelerSpace(spaceId, floorId);
            case FOUR:  return new FourWheelerSpace(spaceId, floorId);
            case HEAVY: return new HeavyVehicleSpace(spaceId, floorId);
            default:    throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }
}

class Floor {
    int floorId;
    List<ParkingSpace> pSpaces;

    public Floor(int floorId) {
        this.floorId = floorId;
        this.pSpaces = new ArrayList<>();
    }

    public void addParkingSpace(ParkingSpace p) {
        pSpaces.add(p);
    }

    public void removeParkingSpace(ParkingSpace p) {
        pSpaces.remove(p);
    }
}

class ParkingLot {
    List<Floor> floorList;
    ParkingSpaceFactory pSpaceFact;

    public ParkingLot() {
        floorList = new ArrayList<>();
        pSpaceFact = new ParkingSpaceFactory();
    }

    public ParkingSpace createSpace(VehicleType type, int spaceId, int floorId) {
        return pSpaceFact.getParkingSpace(type, spaceId, floorId);
    }

    public void addFloor(Floor f, List<ParkingSpace> spaces) {
        for (ParkingSpace p : spaces) {
            f.addParkingSpace(p);
        }
        floorList.add(f);
    }

    public void removeFloor(Floor f) {
        floorList.remove(f);
    }

    // floorList is kept ordered by proximity to the entry gates, so the
    // first empty, type-matching space found is also the closest one.
    public ParkingSpace findParkingSpace(EntryGate entry, Vehicle v) {
        for (Floor f : floorList) {
            for (ParkingSpace p : f.pSpaces) {
                if (p.isEmpty && p.type == v.vType) {
                    return p;
                }
            }
        }
        return null; // lot full for this vehicle type
    }
}

class GateManager {
    List<EntryGate> entries;
    List<ExitGate> exits;

    public GateManager() {
        entries = new ArrayList<>();
        exits = new ArrayList<>();
    }

    public void addEntryGate(EntryGate entry) { entries.add(entry); }
    public void removeEntryGate(EntryGate entry) { entries.remove(entry); }
    public void addExitGate(ExitGate exit) { exits.add(exit); }
    public void removeExitGate(ExitGate exit) { exits.remove(exit); }
}

class EntryGate {
    int gateId;
    ParkingLot pLot;

    public EntryGate(int gateId, ParkingLot pLot) {
        this.gateId = gateId;
        this.pLot = pLot;
    }

    public Vehicle getVehicle(Vehicle v) {
        System.out.println("Gate " + gateId + ": vehicle " + v.regNum + " arrived");
        return v;
    }

    public ParkingSpace findSpace(Vehicle v) {
        return pLot.findParkingSpace(this, v);
    }

    public void updateParkingSpace(ParkingSpace pSpace, Vehicle v) {
        pSpace.parkVehicle(v);
    }

    public Ticket generateTicket(Vehicle v) {
        v = getVehicle(v);
        ParkingSpace space = findSpace(v);
        if (space == null) {
            System.out.println("Sorry, parking full for vehicle type: " + v.vType);
            return null;
        }
        updateParkingSpace(space, v);
        return new Ticket(v, space);
    }
}

class Ticket {
    private static int counter = 1;

    int ticketId;
    LocalDateTime entryTime;
    LocalDateTime exitTime;
    Vehicle v;
    ParkingSpace pSpace;

    public Ticket(Vehicle v, ParkingSpace pSpace) {
        this.ticketId = counter++;
        this.entryTime = LocalDateTime.now();
        this.v = v;
        this.pSpace = pSpace;
    }

    public void setExitTime(LocalDateTime time) {
        this.exitTime = time;
    }
}

class PaymentInfo {
    int transactionId;
    double amount;
    LocalDateTime date;
    Ticket ticket;
    PaymentType pType;
    PaymentStrategy pStrategy;

    public PaymentInfo(double amount, Ticket ticket, PaymentType pType, PaymentStrategy pStrategy) {
        this.transactionId = new Random().nextInt(1_000_000);
        this.amount = amount;
        this.date = LocalDateTime.now();
        this.ticket = ticket;
        this.pType = pType;
        this.pStrategy = pStrategy;
    }
}

class ExitGate {
    int gateId;
    Ticket t;

    public ExitGate(int gateId) {
        this.gateId = gateId;
    }

    public void setTicket(Ticket t) {
        this.t = t;
    }

    public double calculatePrice(PaymentStrategy pStrategy) {
        return pStrategy.calculateCost(t);
    }

    public PaymentInfo generatePaymentInfo(PaymentType pType, PaymentStrategy pStrategy) {
        t.setExitTime(LocalDateTime.now());
        double amount = calculatePrice(pStrategy);
        PaymentInfo info = new PaymentInfo(amount, t, pType, pStrategy);
        freeParkingSpace();
        return info;
    }

    public void freeParkingSpace() {
        t.pSpace.removeVehicle();
    }
}

abstract class PaymentStrategy {
    public abstract double calculateCost(Ticket t);
}

class HourlyPaymentStrategy extends PaymentStrategy {
    public double calculateCost(Ticket t) {
        long minutesParked = Duration.between(t.entryTime, t.exitTime).toMinutes();
        long hours = (long) Math.ceil(minutesParked / 60.0);
        if (hours == 0) hours = 1; // minimum charge
        return hours * t.pSpace.price;
    }
}

class MinutesPaymentStrategy extends PaymentStrategy {
    public double calculateCost(Ticket t) {
        long minutesParked = Duration.between(t.entryTime, t.exitTime).toMinutes();
        if (minutesParked == 0) minutesParked = 1; // minimum charge
        return minutesParked * t.pSpace.price;
    }
}

public class ParkingLotDemo {
    public static void main(String[] args) throws InterruptedException {
        ParkingLot lot = new ParkingLot();

        Floor floor1 = new Floor(1);
        List<ParkingSpace> floor1Spaces = new ArrayList<>();
        floor1Spaces.add(lot.createSpace(VehicleType.TWO, 101, 1));
        floor1Spaces.add(lot.createSpace(VehicleType.FOUR, 102, 1));
        floor1Spaces.add(lot.createSpace(VehicleType.HEAVY, 103, 1));
        lot.addFloor(floor1, floor1Spaces);

        GateManager gateManager = new GateManager();
        EntryGate entryGate = new EntryGate(1, lot);
        ExitGate exitGate = new ExitGate(1);
        gateManager.addEntryGate(entryGate);
        gateManager.addExitGate(exitGate);

        Vehicle car = new Vehicle(1234, VehicleType.FOUR);
        Ticket ticket = entryGate.generateTicket(car);

        if (ticket != null) {
            System.out.println("Ticket #" + ticket.ticketId
                    + " | Space " + ticket.pSpace.spaceId
                    + " | Entry: " + ticket.entryTime);

            Thread.sleep(2000); // simulate time parked

            exitGate.setTicket(ticket);
            PaymentInfo payment = exitGate.generatePaymentInfo(PaymentType.UPI, new MinutesPaymentStrategy());
            System.out.println("Paid " + payment.amount
                    + " via " + payment.pType
                    + " | Transaction #" + payment.transactionId);
        }
    }
}
