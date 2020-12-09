package massim.javaagents.agents;

import eis.iilang.*;
import jdk.jfr.Percentage;
import massim.javaagents.MailService;

import java.util.*;

/**
 * A very basic agent.
 */
public class PickupTruck extends Agent {
    private Boolean ready = false;
    private Boolean available = false;
    private String hq = "";
    private String hqLat = "";
    private String hqLon = "";
    private Queue<Action> actionQueue = new LinkedList<>();
    private String centerLat = "";
    private String centerLon = "";
    private String currentLat = "";
    private String currentLon = "";
    private Map<String, Percept> storages = new HashMap<>();
    private String targetLat = "";
    private String targetLon = "";
    private String target = "";
    private String targetVolume = "";
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

        if (!available) {
            say("I am not available");
            if (currentLat.equals(hqLat) && currentLon.equals(hqLon) && actionQueue.isEmpty() && lastAction != "goto") {
                available = true;
                targetLon = "";
                targetLat = "";
                target = "";
                targetVolume = "";
                say("Reached HQ, sending available message.");
                Percept message = new Percept("TruckReady", new Identifier(getName()));
                broadcast(message, getName());
            } else if (currentLat.equals(targetLat) && currentLon.equals(targetLon)) {
                say("Reached target!");
                if (actionQueue.isEmpty()) {
                    if(Float.parseFloat(maxLoad) > Float.parseFloat(currentLoad) + Float.parseFloat(targetVolume)) {
                        say("Capacity left, loading!");
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
                say("Got command to pick up");
                if(available){
                    available = false;
                    target = String.valueOf(message.getParameters().get(2));
                    String targetItem = String.valueOf(message.getParameters().get(3));
                    targetVolume = String.valueOf(items.get(targetItem).getParameters().get(1));
                    say(targetVolume.toString());
                    targetLat = String.valueOf(message.getParameters().get(1));
                    targetLon = String.valueOf(message.getParameters().get(0));
                    say("Target lat:" + targetLat + " target lon: " + targetLon);
                    actionQueue.add(new Action("goto", new Numeral(Float.parseFloat(targetLat)), new Numeral(Float.parseFloat(targetLon))));
                    break;
                }
        }
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
