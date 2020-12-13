package massim.javaagents.agents;

import eis.iilang.*;
import massim.javaagents.MailService;

import java.util.*;

/**
 * A very basic agent.
 */
public class PickupTruck extends Agent {
    private Boolean ready = false;
    private Boolean available = false;
    private final Location hq = new Location();
    private final Location center = new Location();
    private Location current = new Location();
    private Location target = new Location();
    private Queue<Action> actionQueue = new LinkedList<>();
    private Map<String, Percept> storages = new HashMap<>();
    private String targetVolume = "";
    private String targetItem = "";
    private String currentLoad = "";
    private String maxLoad = "";
    private String lastAction = "";
    private Map<String, Percept> items = new HashMap<>();

    /**
     * Constructor.
     * @param name    the agent's name
     * @param mailbox the mail facility
     */
    public PickupTruck(String name, MailService mailbox) {
        super(name, mailbox);
    }

    @Override
    public void handlePercept(Percept percept) {}

    @Override
    public Action step() {
        //
        for (Percept p: getPercepts()) {
            //System.out.println(p.getName());
            switch (p.getName()) {
                case "storage":
                    if (!ready){
                        storages.putIfAbsent(String.valueOf(p.getParameters().get(0)),p);
                    }
                    break;
                case "item":
                    if (!ready){
                        items.putIfAbsent(String.valueOf(p.getParameters().get(0)),p);
                    } 
                    break;
                case "centerLon":
                    center.lon = String.valueOf(p.getParameters().get(0));
                    break;
                case "centerLat":
                    center.lat = String.valueOf(p.getParameters().get(0));
                    break;
                case "lon":
                    current.lon = String.valueOf(p.getParameters().get(0));
                    break;
                case "lat":
                    current.lat = String.valueOf(p.getParameters().get(0));
                    break;
                case "load":
                    currentLoad = String.valueOf(p.getParameters().get(0));
                    break;
                case "maxLoad":
                    maxLoad = String.valueOf(p.getParameters().get(0));
                    break;
                case "lastAction":
                    lastAction = String.valueOf(p.getParameters().get(0));
                    break;
                    
            }
        }

        if (!ready) {
            System.out.println("First step, finding most central storage. Center Lat " + center.lat + " center Lon " + center.lon);
            Float minDif = Float.MAX_VALUE;
            for (Percept s: storages.values()){
                Float newLat = Float.parseFloat(String.valueOf(s.getParameters().get(1)));
                Float newLon = Float.parseFloat(String.valueOf(s.getParameters().get(2)));
                Float newDif = Math.abs(Float.parseFloat(center.lat) - newLat) + Math.abs(Float.parseFloat(center.lon) - newLon);
                if (newDif<minDif) {
                    minDif = newDif;
                    hq.id = String.valueOf(s.getParameters().get(0));
                    hq.lat = String.valueOf(s.getParameters().get(1));
                    hq.lon = String.valueOf(s.getParameters().get(2));
                }
            }
            System.out.println("HQ will be " + hq + " at " + hq.lat + ";" + hq.lon);
            ready = true;
            goHome();

        }

        if (!available) {
            if (current.lat.equals(hq.lat) && current.lon.equals(hq.lon) && actionQueue.isEmpty() && !lastAction.equals("goto")) {
                if (currentLoad.equals("0")) {
                    available = true;
                    target.lon = "";
                    target.lat = "";
                    target.id = "";
                    targetVolume = "";
                    // say("Reached HQ, sending available message.");
                    Percept message = new Percept("TruckReady", new Identifier(getName()));
                    broadcast(message, getName());
                } else {
                    actionQueue.add(new Action("store", new Identifier(targetItem), new Numeral(Integer.valueOf(currentLoad)/Integer.valueOf(targetVolume))));
                }
            } else if (current.lat.equals(target.lat) && current.lon.equals(target.lon)) {
                // say("Reached target!");
                if (actionQueue.isEmpty()) {
                    if(Float.parseFloat(maxLoad) > Float.parseFloat(currentLoad) + Float.parseFloat(targetVolume)) {
                        // say("Capacity left, loading!");
                        actionQueue.add(new Action("gather"));
                    } else {
                        goHome();
                    }
                }
            }
    
        } 
        return actionQueue.peek() != null? actionQueue.poll() : new Action("continue");
    }

    @Override
    public void handleMessage(Percept message, String sender) {
        switch (message.getName()){
            case "gatherFromNode":
                // say("Got command to pick up");
                if(available){
                    available = false;
                    target.id = String.valueOf(message.getParameters().get(2));
                    targetItem = String.valueOf(message.getParameters().get(3));
                    targetVolume = String.valueOf(items.get(targetItem).getParameters().get(1));
                    // say(targetVolume.toString());
                    target.lat = String.valueOf(message.getParameters().get(1));
                    target.lon = String.valueOf(message.getParameters().get(0));
                    // say("Target lat:" + target.lat + " target lon: " + target.lon);
                    actionQueue.add(new Action("goto", new Numeral(Float.parseFloat(target.lat)), new Numeral(Float.parseFloat(target.lon))));
                    break;
                }
        }
    }
    private void goHome() {
        actionQueue.add(new Action("goto", new Identifier(hq.id)));
    }
}
