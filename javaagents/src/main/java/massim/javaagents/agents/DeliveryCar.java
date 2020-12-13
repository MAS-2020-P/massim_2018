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

    private Boolean ready = false;
    private Location hq = new Location();
    private Location center = new Location();
    private Location current = new Location();
    private Location target = new Location();
    private Queue<Action> actionQueue = new LinkedList<>();
    private String currentJob = "";
    private Map<String,Job> activeJobs = new HashMap<>();
    private Map<String, Integer> hqStorage = new HashMap<>();
    private Map<String, Percept> storages = new HashMap<>();

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
    public void handleMessage(Percept message, String sender) {}

    @Override
    public Action step() {


        for (Percept p: getPercepts()){
            //System.out.println(p.getName());
            switch(p.getName()) {
                case "job":
                    //System.out.println(p);
                    Job newJob = new Job();
                    newJob.target = String.valueOf(p.getParameters().get(1));
                    ParameterList requiredItems = listParam(p,5);
                    for (Parameter i: requiredItems) {
                        String itemName = stringParam(((Function) i).getParameters(), 0);
                        int amount = intParam(((Function) i).getParameters(), 1);
                        newJob.items.putIfAbsent(itemName, amount);
                    }
                    activeJobs.putIfAbsent(String.valueOf(p.getParameters().get(0)), newJob);
                    break;
                case "storage":
                    if (!ready){
                        storages.putIfAbsent(String.valueOf(p.getParameters().get(0)),p);
                    } else if (String.valueOf(p.getParameters().get(0)).equals(hq.id)){
                        ParameterList storedItems = listParam(p,5);
                        for (Parameter i: storedItems) {
                            String itemName = stringParam(((Function) i).getParameters(), 0);
                            int amount = intParam(((Function) i).getParameters(), 1);
                            hqStorage.put(itemName, amount);
                        }
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
        if (currentJob.equals("") && current.lat.equals(hq.lat) && current.lon.equals(hq.lon)) {
            for (Map.Entry<String, Job> job : activeJobs.entrySet()) {
                Boolean jobPossible = true;
                for (Map.Entry<String, Integer> item: job.getValue().items.entrySet()) {
                    if (hqStorage.containsKey(item.getKey())) {
                            if (hqStorage.get(item.getKey()) < item.getValue()){
                                System.out.println("Job not possible!");
                                jobPossible = false;
                            } else {
                                say("Item " + item.getKey() + " is available in enough quantity!");
                            }
                    } else {
                        //System.out.println("Job not possible!");
                        jobPossible = false;
                    }
                }
                if (jobPossible) {
                    currentJob = job.getKey();
                    for (Map.Entry<String, Integer> item: job.getValue().items.entrySet()) {
                        actionQueue.add(new Action("retrieve", new Identifier(item.getKey()), new Numeral(item.getValue())));
                    }
                    break;
                }
            }

        } else {
            if (!activeJobs.containsKey(currentJob)) {
                say("Job no longer available, returning to base!");
                goHome();
                currentJob = "";
            } else if (actionQueue.isEmpty()){
                target.id = activeJobs.get(currentJob).target;
                say(String.valueOf(activeJobs.get(currentJob).items.entrySet()));
                target.lat = String.valueOf(storages.get(target.id).getParameters().get(1));
                target.lon = String.valueOf(storages.get(target.id).getParameters().get(2));
                if (current.lat.equals(target.lat) && current.lon.equals(target.lon)) {
                    actionQueue.add(new Action("deliver_job", new Identifier(currentJob)));
                } else {
                    actionQueue.add(new Action("goto", new Identifier(target.id)));
                }
            }
        }

        activeJobs.clear();
        hqStorage.clear();
        return actionQueue.peek() != null? actionQueue.poll() : new Action("continue");
    }

    private void goHome() {
        actionQueue.add(new Action("goto", new Identifier(hq.id)));
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
