package massim.javaagents.agents;

import eis.iilang.*;
import massim.javaagents.MailService;

import java.util.*;

/**
 * A very basic agent.
 */

class Job {
    String target = "";
    Map<String, Integer> items = new HashMap<String, Integer>();

    //public Job(String target, Map items) {target=}
}
public class DeliveryCar extends Agent {

    private Boolean ready = false;
    private String hq = "";
    private String hqLat = "";
    private String hqLon = "";
    private Queue<Action> actionQueue = new LinkedList<>();
    private String centerLat = "";
    private String centerLon = "";
    private String currentJob = "";
    private String currentLat = "";
    private String currentLon = "";
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
                    } else if (String.valueOf(p.getParameters().get(0)) == hq){
                        ParameterList storedItems = listParam(p,5);
                        for (Parameter i: storedItems) {
                            String itemName = stringParam(((Function) i).getParameters(), 0);
                            int amount = intParam(((Function) i).getParameters(), 1);
                            hqStorage.put(itemName, amount);
                        }
                    }
                    break;
                case "centerLon":
                    centerLon = String.valueOf(p.getParameters().get(0));
                    break;
                case "centerLat":
                    centerLat = String.valueOf(p.getParameters().get(0));
                    break;
                case "lon":
                    currentLon = String.valueOf(p.getParameters().get(0));
                    break;
                case "lat":
                    currentLat = String.valueOf(p.getParameters().get(0));
                    break;
                }


            }
        if (!ready) {
            System.out.println("First step, finding most central storage. Center Lat " + centerLat + " center Lon " + centerLon);
            Float minDif = Float.MAX_VALUE;
            for (Percept s: storages.values()){
                Float newLat = Float.parseFloat(String.valueOf(s.getParameters().get(1)));
                Float newLon = Float.parseFloat(String.valueOf(s.getParameters().get(2)));
                Float newDif = Math.abs(Float.parseFloat(centerLat) - newLat) + Math.abs(Float.parseFloat(centerLon) - newLon);
                actionQueue.add(new Action("goto", new Identifier(String.valueOf(s.getParameters().get(0)))));
                if (newDif<minDif) {
                    minDif = newDif;
                    hq = String.valueOf(s.getParameters().get(0));
                    hqLat = String.valueOf(s.getParameters().get(1));
                    hqLon = String.valueOf(s.getParameters().get(2));
                }
            }
            System.out.println("HQ will be " + hq + " at " + hqLat + ";" + hqLon);
            ready = true;
            goHome();

        }
        if (currentJob == "") {
            for (Map.Entry<String, Job> job : activeJobs.entrySet()) {
                Boolean jobPossible = true;
                for (Map.Entry<String, Integer> item: job.getValue().items.entrySet()) {
                    if (hqStorage.containsKey(item.getKey())) {
                        if (hqStorage.get(item.getKey()) < item.getValue()){
                            System.out.println("Job not possible!");
                            jobPossible = false;
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
                System.out.println("Job no longer available, returning to base!");
                goHome();
                currentJob = "";
            } else if (actionQueue.isEmpty()){
                String target = activeJobs.get(currentJob).target;
                String targetLat = String.valueOf(storages.get(target).getParameters().get(1));
                String targetLon = String.valueOf(storages.get(target).getParameters().get(2));
                if (currentLat == targetLat && currentLon == targetLon) {
                    actionQueue.add(new Action("deliver_job", new Identifier(currentJob)));
                } else {
                    actionQueue.add(new Action("goto", new Identifier(target)));
                }
            }
        }

        activeJobs.clear();
        hqStorage.clear();
        return actionQueue.peek() != null? actionQueue.poll() : new Action("continue");
    }

    private void goHome() {
        actionQueue.add(new Action("goto", new Identifier(hq)));
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
