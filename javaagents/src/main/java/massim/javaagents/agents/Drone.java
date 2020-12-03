package massim.javaagents.agents;

import eis.iilang.*;
import massim.javaagents.MailService;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Drone extends Agent{

    /**
     * Constructor
     *
     * @param name    the agent's name
     * @param mailbox the mail facility
     */
    public Drone(String name, MailService mailbox) {
        super(name, mailbox);
    }

    @Override
    public void handlePercept(Percept percept) {

    }

    @Override
    public Action step() {
        List<Percept> percepts = getPercepts();
        percepts.stream()
                .filter(p -> p.getName().equals("step"))
                .findAny()
                .ifPresent(p -> {
                    Parameter param = p.getParameters().getFirst();
                    if(param instanceof Identifier) say("Step " + ((Identifier) param).getValue());
                });
        Action warp = new Action("goto", new Numeral(48.8424), new Numeral(2.3209));
        System.out.println(warp.toProlog());
        return warp;
    }

    @Override
    public void handleMessage(Percept message, String sender) {

    }
}
