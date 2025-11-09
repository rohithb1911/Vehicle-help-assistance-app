import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class Location {
    double lat, lon;
    Location(double lat, double lon){ this.lat=lat; this.lon=lon; }
    double distanceTo(Location o){
        double dx = lat - o.lat, dy = lon - o.lon;
        return Math.sqrt(dx*dx + dy*dy);
    }
    public String toString(){ return String.format("(%.4f, %.4f)", lat, lon); }
}

enum RequestType { BREAKDOWN, FUEL }
enum RequestStatus { PENDING, DISPATCHED, RESOLVED }

class Vehicle {
    String regNo;
    String model;
    String fuelType; // e.g., Petrol/Diesel
    Vehicle(String regNo, String model, String fuelType){
        this.regNo=regNo; this.model=model; this.fuelType=fuelType;
    }
    public String toString(){ return regNo + " | " + model + " | " + fuelType; }
}

class AssistanceRequest {
    static final AtomicInteger COUNTER = new AtomicInteger(1);
    final int id;
    final RequestType type;
    final Vehicle vehicle;
    final Location location;
    RequestStatus status;
    Helper assignedHelper;
    double litersNeeded; // only for fuel requests

    AssistanceRequest(RequestType type, Vehicle vehicle, Location location){
        this.id = COUNTER.getAndIncrement();
        this.type = type;
        this.vehicle = vehicle;
        this.location = location;
        this.status = RequestStatus.PENDING;
    }

    public String toString(){
        return String.format("Req#%d [%s] %s at %s -> %s%s",
            id, type, vehicle, location, status,
            (assignedHelper==null ? "" : " | Helper: "+assignedHelper.name));
    }
}

class Helper {
    String id;
    String name;
    String serviceType; // MECHANIC, TOW, FUEL
    Location location;
    double rating;
    Helper(String id, String name, String serviceType, Location loc, double rating){
        this.id=id; this.name=name; this.serviceType=serviceType; this.location=loc; this.rating=rating;
    }
    public String toString(){ return String.format("%s (%s) @ %s r=%.1f", name, serviceType, location, rating); }
}

class DataStore {
    List<Helper> helpers = new ArrayList<>();
    List<AssistanceRequest> history = new ArrayList<>();

    DataStore(){
        // Seed some helpers
        helpers.add(new Helper("H1","Speedy Tow","TOW", new Location(12.9712,77.5936), 4.5));
        helpers.add(new Helper("H2","FuelBuddy","FUEL", new Location(12.9720,77.5900), 4.2));
        helpers.add(new Helper("H3","Ramesh Mechanic","MECHANIC", new Location(12.9700,77.5950), 4.7));
        helpers.add(new Helper("H4","Express Fuel","FUEL", new Location(12.9750,77.5920), 4.0));
    }

    void addRequest(AssistanceRequest r){ history.add(r); }
}

class NotificationService {
    void notifyUser(String msg){
        System.out.println("[NOTIFICATION] " + msg);
    }
}

class AssistanceService {
    DataStore db;
    NotificationService notifier;

    AssistanceService(DataStore db, NotificationService notifier){
        this.db = db; this.notifier = notifier;
    }

    AssistanceRequest createRequest(RequestType type, Vehicle vehicle, Location loc, double litersNeeded){
        AssistanceRequest r = new AssistanceRequest(type, vehicle, loc);
        r.litersNeeded = litersNeeded;
        db.addRequest(r);
        notifier.notifyUser("Request created: " + r);
        dispatch(r);
        return r;
    }

    void dispatch(AssistanceRequest r){
        // choose helper matching type
        String neededService = (r.type==RequestType.BREAKDOWN) ? "MECHANIC" : "FUEL";
        Helper best = null;
        double bestDist = Double.MAX_VALUE;
        for(Helper h : db.helpers){
            if(!h.serviceType.equalsIgnoreCase(neededService)) continue;
            double d = h.location.distanceTo(r.location);
            if(d < bestDist){
                bestDist = d; best = h;
            }
             }
        if(best!=null){
            r.assignedHelper = best;
            r.status = RequestStatus.DISPATCHED;
            notifier.notifyUser(String.format("Dispatched %s to request #%d (dist=%.4f)", best.name, r.id, bestDist));
        } else {
            notifier.notifyUser("No helper available for request #" + r.id);
        }
    }

    void resolveRequest(int requestId){
        for(AssistanceRequest r : db.history){
            if(r.id==requestId){
                r.status = RequestStatus.RESOLVED;
                notifier.notifyUser("Request resolved: " + r);
                return;
            }
        }
        System.out.println("Request not found: " + requestId);
    }

    void printHistory(){
        System.out.println("=== Request History ===");
        for(AssistanceRequest r : db.history) System.out.println(r);
    }

    List<Helper> listHelpers(){ return db.helpers; }
}

public class AssistanceApp {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        DataStore db = new DataStore();
        NotificationService notifier = new NotificationService();
        AssistanceService svc = new AssistanceService(db, notifier);

        System.out.println("Welcome to Onroad Assistance Helper (console prototype)");
        while(true){
            System.out.println("\nOptions: 1=Create Request  2=List Helpers  3=History  4=Resolve  0=Exit");
            System.out.print("Choice: ");
            int ch = -1;
            try { ch = Integer.parseInt(sc.nextLine().trim()); } catch(Exception e){ continue; }
            if(ch==0) break;
            switch(ch){
                case 1:
                    System.out.print("Type (BREAKDOWN/FUEL): ");
                    String t = sc.nextLine().trim().toUpperCase();
                    RequestType type = t.equals("FUEL") ? RequestType.FUEL : RequestType.BREAKDOWN;
                    System.out.print("Vehicle regNo: "); String reg = sc.nextLine().trim();
                    System.out.print("Vehicle model: "); String model = sc.nextLine().trim();
                    System.out.print("Fuel type (Petrol/Diesel): "); String fuel = sc.nextLine().trim();
                    System.out.print("Latitude: "); double lat = Double.parseDouble(sc.nextLine().trim());
                    System.out.print("Longitude: "); double lon = Double.parseDouble(sc.nextLine().trim());
                    double liters = 0;
                    if(type==RequestType.FUEL){
                        System.out.print("Approx liters needed: "); liters = Double.parseDouble(sc.nextLine().trim());
                    }
                    Vehicle v = new Vehicle(reg, model, fuel);
                    svc.createRequest(type, v, new Location(lat, lon), liters);
                    break;
                case 2:
                    System.out.println("Available helpers:");
                    for(Helper h : svc.listHelpers()) System.out.println(h);
                    break;
                case 3:
                    svc.printHistory();
                    break;
                case 4:
                    System.out.print("Enter request id to resolve: ");
                    int id = Integer.parseInt(sc.nextLine().trim());
                    svc.resolveRequest(id);
                    break;
                default:
                    System.out.println("Invalid choice.");
            }
        }
        System.out.println("Goodbye!");
        sc.close();
    }
}