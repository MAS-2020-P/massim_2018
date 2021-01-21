package massim.javaagents.agents;

import eis.iilang.*;
import massim.javaagents.MailService;

import java.util.*;

/**
 * A very basic agent.
 */

class Job {
    String target = "";
    Map<String, Integer> items = new HashMap<>();

    //public Job(String target, Map items) {target=}
}
class Location {
    String id = "";
    String lat = "";
    String lon = "";
}
public class DeliveryCar extends Agent {

    private Location center = new Location();
    private Location current = new Location();
    private Location target = new Location();
    private Queue<Action> actionQueue = new LinkedList<>();
    private Queue<Location> targetQueue = new LinkedList<>();
    private Queue<String> itemQueue = new LinkedList<>();
    private Queue<String> jobQueue = new LinkedList<>();
    private String currentJob = "";
    private Map<String,Job> activeJobs = new HashMap<>();
    private Map<String, Percept> storages = new HashMap<>();
    private Map<String, List<Location>> nodes = new HashMap<>();
    private Map<String, Float> load = new HashMap<>();
    private Boolean available = true;


    /**
     * Constructor.
     * @param name    the agent's name
     * @param mailbox the mail facility
     */
    public DeliveryCar(String name, MailService mailbox) {
        super(name, mailbox);
    }

    @Override
    public void handlePercept(Percept percept) {}

    @Override
    public Action step() {


        for (Percept p : getPercepts()) {
            //System.out.println(p.getName());
            switch (p.getName()) {
                case "job":
                    //System.out.println(p);
                    Job newJob = new Job();
                    newJob.target = String.valueOf(p.getParameters().get(1));
                    ParameterList requiredItems = listParam(p, 5);
                    for (Parameter i : requiredItems) {
                        String itemName = stringParam(((Function) i).getParameters(), 0);
                        int amount = intParam(((Function) i).getParameters(), 1);
                        newJob.items.putIfAbsent(itemName, amount);
                    }
                    activeJobs.putIfAbsent(String.valueOf(p.getParameters().get(0)), newJob);
                    break;
                case "storage":
                    storages.putIfAbsent(String.valueOf(p.getParameters().get(0)), p);
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
                case "item":
                    load.put(String.valueOf(p.getParameters().get(1)), Float.parseFloat(String.valueOf(p.getParameters().get(0))));
                    break;
            }

        }

        if (currentJob.equals("")) {
            if (getJob()) {
                goToTarget();
            }
        } else if (!activeJobs.containsKey(currentJob)) {
            say("Job no longer available!");
            reset();
        } else if (actionQueue.isEmpty() && atTarget()){
            if (targetQueue.size() == 0) {
                actionQueue.add(new Action("deliver_job", new Identifier(currentJob)));
            } else {
                if (activeJobs.get(currentJob).items.get(itemQueue.peek()) > load.get(itemQueue.peek())) {
                    actionQueue.add(new Action("gather"));
                } else {
                    if (targetQueue.size() > 1){
                        itemQueue.poll();
                    }
                    target = targetQueue.poll();
                    goToTarget();
                }
            }
        }

        activeJobs.clear();
        return actionQueue.peek() != null? actionQueue.poll() : new Action("continue");
    }
    @Override
    public void handleMessage(Percept message, String sender) {
        switch (message.getName()){
            case "gatherFromNode":
                // say("Got command to pick up");
                if(available){
                    jobQueue.add(String.valueOf(message.getParameters().get(2)));
                    break;
                }
            case "announceJob":
                if(available){
                    String newJob = String.valueOf(message.getParameters().get(2));
                    float cost = calculateCost(newJob);
                    Percept reply = new Percept("bid", new Identifier(getName()), new Identifier(String.valueOf(cost)));
                    broadcast(reply, getName());
                }
            case "accept":
                if(available){
                    String newJob = String.valueOf(message.getParameters().get(2));
                    float cost = calculateCost(newJob);
                    Percept reply = new Percept("definitiveBid", new Identifier(getName()), new Identifier(String.valueOf(cost)));
                    broadcast(reply, getName());
                }
            case "definitiveAccept":
                if (available){
                    jobQueue.add(String.valueOf(message.getParameters().get(2)));
                    break;
                }
        }
    }

    private Location findNearest(String item, Location start) {
        Location nearest = new Location();
        float minDif = Float.MAX_VALUE;
        for (Location l: nodes.get(item)) {
            float newLat = Float.parseFloat(l.lat);
            float newLon = Float.parseFloat(l.lon);
            float newDif = Math.abs(Float.parseFloat(start.lat) - newLat) + Math.abs(Float.parseFloat(start.lon) - newLon);
            if (newDif<minDif) {
                minDif = newDif;
                nearest = l;
            }
        }
        return nearest;
    }

    private float calculateCost(String job) {
        LinkedList<Location> targets = new LinkedList<>();
        targets.add(current);
        float total_distance = 0;
        for (String item: activeJobs.get(job).items.keySet()) {
            targetQueue.add(findNearest(item, targets.getLast()));
        }
        if (targets.size() == 0){
            return 0;
        }
        targets.add(getStorageForJob(job));
        Location cur = targets.poll();
        while (!targets.isEmpty()) {
            total_distance += calculateDistance(cur, targets.peek());
            cur = targets.poll();
        }


        return total_distance;
    }
    private float calculateDistance(Location loc1, Location loc2) {
        return Math.abs(Float.parseFloat(loc1.lat) - Float.parseFloat(loc2.lat) ) + Math.abs(Float.parseFloat(loc1.lon) - Float.parseFloat(loc2.lon));
    }
    private void goToTarget() {
        actionQueue.add(new Action("goto", new Numeral(Float.parseFloat(target.lat)), new Numeral(Float.parseFloat(target.lon))));
    }
    private boolean atTarget() {
        return target.lat.equals(current.lat) && target.lon.equals(current.lon);
    }
    private Location getStorageForJob(String job) {
        Location dropoff = new Location();
        dropoff.id = activeJobs.get(job).target;
        dropoff.lat = String.valueOf(storages.get(activeJobs.get(job).target).getParameters().get(1));
        dropoff.lon = String.valueOf(storages.get(activeJobs.get(job).target).getParameters().get(2));
        return dropoff;
    }
    private void reset() {
        targetQueue.clear();
        available = true;
        target = new Location();
        currentJob = "";
        actionQueue.clear();
        Percept message = new Percept("TruckReady", new Identifier(getName()));
        broadcast(message, getName());
    }
    private boolean getJob() {
        if (jobQueue.size() > 0) {
            currentJob = jobQueue.poll();
            for (String item: activeJobs.get(currentJob).items.keySet()){
                targetQueue.add(findNearest(item, current));
                itemQueue.add(item);
            }
            targetQueue.add(getStorageForJob(currentJob));
            target = targetQueue.poll();
            return true;
        }
        return false;
    }
    /**
     * Tries to extract a parameter from a list of parameters.
     * @param params the parameter list
     * @param index the index of the parameter
     * @return the string value of that parameter or an empty string if there is no parameter or it is not an identifier
     */
    public static String stringParam(List<Parameter> params, int index){
        if(params.size() < index + 1) return "";
        Parameter param = params.get(index);
        if(param instanceof Identifier) return ((Identifier) param).getValue();
        return "";
    }

    /**
     * Tries to extract an int parameter from a list of parameters.
     * @param params the parameter list
     * @param index the index of the parameter
     * @return the int value of that parameter or -1 if there is no parameter or it is not an identifier
     */
    private static int intParam(List<Parameter> params, int index){
        if(params.size() < index + 1) return -1;
        Parameter param = params.get(index);
        if(param instanceof Numeral) return ((Numeral) param).getValue().intValue();
        return -1;
    }

    /**
     * Tries to extract a parameter from a percept.
     * @param p the percept
     * @param index the index of the parameter
     * @return the string value of that parameter or an empty string if there is no parameter or it is not an identifier
     */
    private static ParameterList listParam(Percept p, int index){
        List<Parameter> params = p.getParameters();
        if(params.size() < index + 1) return new ParameterList();
        Parameter param = params.get(index);
        if(param instanceof ParameterList) return (ParameterList) param;
        return new ParameterList();
    }
}
