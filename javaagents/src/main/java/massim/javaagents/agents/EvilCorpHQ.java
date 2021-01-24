package massim.javaagents.agents;

import eis.iilang.*;
import massim.javaagents.MailService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class EvilCorpHQ extends Agent {

    static private class eCNPInstance {
        static private class Bid implements Comparable<Bid>{
            public Bid(String agent, float bid) {
                this.agent = agent;
                this.bid = bid;
            }

            public Bid(String agent, float bid, boolean definitive) {
                this.agent = agent;
                this.bid = bid;
                this.definitive = definitive;
            }

            String agent;
            float bid;
            boolean definitive = false;

            @Override
            public int compareTo(Bid o) {
                return Float.compare(this.bid, o.bid);
            }
        }

        public eCNPInstance(String jobId, EvilCorpHQ initiator) {
            this.jobId = jobId;
            this.initiator = initiator;
            bids = new LinkedList<>();
        }
        String jobId;
        EvilCorpHQ initiator;
        int start = -1;
        // stores a list of bids per round
        LinkedList<LinkedList<Bid>> bids;

        public void announceJob(int step) {
            this.start = step;
            Percept message = new Percept("announceJob", new Identifier(jobId));
            initiator.broadcast(message, initiator.getName());
        }

        public void handleBidMessage(String participantName, float bid, int currStep) {
            addBiddingRoundIfNeeded(currStep);
            bids.get(getBiddingRound(currStep)).add(new Bid(participantName, bid));
        }

        public void handleDefinitiveBidMessage(String participantName, float bid, int currStep) {
            addBiddingRoundIfNeeded(currStep);
            bids.get(getBiddingRound(currStep)).add(new Bid(participantName, bid, true));
        }

        public void handleCurrentBids(int currStep) {
            // handle the bids that came in the last step
            LinkedList<Bid> currBids;
            try {
                currBids = bids.get(getBiddingRound(currStep - 1));
            } catch (IndexOutOfBoundsException e) {
                // no bid came in for this job, abort
                initiator.say("No bid received for " + jobId + " ... removing contract.");
                initiator.closeECNPInstance(jobId, false);
                return;
            }

            Bid definiteBid = getDefinitiveBid(currBids);
            if (currBids.isEmpty()) initiator.say("eCNP Error: no bids given for " + jobId);

            currBids.sort((o1, o2) -> Float.compare(o1.bid, o2.bid));

            // (STATE 5 in slides) // check if definitive bid is still highest
            if (definiteBid != null) {
                // yes, bid is still highest, send def. accept & def. reject to all others
                if (currBids.getFirst() == definiteBid) {
                    sendDefinitiveAccept(definiteBid.agent);
                    // collect all bidders (except finalist) of this bidding and send def reject
                    HashSet<String> allBidders = getAllBidders();
                    allBidders.remove(definiteBid.agent);
                    allBidders.forEach(this::sendDefinitiveReject);
                    initiator.closeECNPInstance(jobId, true);
                }
                // no, bid is no longer the best, continue with normal procedure (def bidder will get a reject)
                else {
                    acceptAndRejectBids(currBids);
                }
            }
            // (STATE 2 in slides) //
            else {
                acceptAndRejectBids(currBids);
            }
        }

        private HashSet<String> getAllBidders() {
            HashSet<String> allBidders = new HashSet<>();
            for (LinkedList<Bid> round : bids) {
                for (Bid bid : round) {
                    allBidders.add(bid.agent);
                }
            }
            return allBidders;
        }

        private void acceptAndRejectBids(LinkedList<Bid> currBids) {
            // send accept to highest bidder
            sendAccept(currBids.getFirst().agent);

            // send reject to others
            for (int i = 1; i < currBids.size(); i++)
                sendReject(currBids.get(i).agent);
        }

        private Bid getDefinitiveBid(LinkedList<Bid> currBids) {
            for (Bid bid : currBids) {
                if (bid.definitive)
                    return bid;
            }
            return null;
        }

        private void sendDefinitiveReject(String participantName) {
            Percept message = new Percept("definitiveReject", new Identifier(jobId));
            initiator.sendMessage(message, participantName, initiator.getName());
        }

        private void sendReject(String participantName) {
            Percept message = new Percept("reject", new Identifier(jobId), new Numeral(bids.getLast().getFirst().bid));
            initiator.sendMessage(message, participantName, initiator.getName());
        }

        private void sendDefinitiveAccept(String participantName) {
            Percept message = new Percept("definitiveAccept", new Identifier(jobId));
            initiator.sendMessage(message, participantName, initiator.getName());
        }

        private void sendAccept(String participantName) {
            Percept message = new Percept("accept", new Identifier(jobId), new Numeral(bids.getLast().getFirst().bid));
            initiator.sendMessage(message, participantName, initiator.getName());
        }

        private void addBiddingRoundIfNeeded(int currStep) {
            if (bids.size() < getBiddingRound(currStep) + 1)
                bids.add(new LinkedList<>());
        }

        private int getBiddingRound(int currStep) {
            return currStep - start;
        }
    }

    private enum Protocol {
        delegation,
        CNP,
        eCNP
    }

    // guess that class could be shared between agents
    static private class Point {
        double lat;
        double lon;
        public Point(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
        public Point add(Point p2) {
            return new Point(lat + p2.lat, lon + p2.lon);
        }
        public Point sub(Point p2) {
            return new Point(lat - p2.lat, lon - p2.lon);
        }
        public Point mult(double scalar) {
            return new Point(scalar * lat, scalar * lon);
        }
        public Point div(double scalar) {
            return new Point(lat / scalar, lon / scalar);
        }
        public Point rot(double angleInRad) {
            return new Point(
                    Math.cos(angleInRad) * lat - Math.sin(angleInRad) * lon,
                    Math.sin(angleInRad) * lat + Math.cos(angleInRad) * lon);
        }
        public Point clamp(double paddedMinLat, double paddedMaxLat, double paddedMinLon, double paddedMaxLon) {
            return new Point(
                    Math.max(paddedMinLat, Math.min(paddedMaxLat, lat)),
                    Math.max(paddedMinLon, Math.min(paddedMaxLon, lon)));
        }
        /**
         * element-wise multiplication of both vectors
         */
        public Point mult(Point p2) {
            return new Point(lat * p2.lat, lon * p2.lon);
        }
    }

    static private class ResourceNode {
        String item;
        String name;
        Point location;
        public ResourceNode(String item, String name, Point location) {
            this.item = item;
            this.name = name;
            this.location = location;
        }
        public ResourceNode(String item, String name, double lat, double lon) {
            this.item = item;
            this.name = name;
            this.location = new Point(lon, lat);
        }
    }

    static private class Job implements Comparable<Job>{
        public Job(String id, float reward) {
            this.id = id;
            this.reward = reward;
        }
        String id;
        float reward;
        boolean contracted = false;

        @Override
        public int compareTo(Job o) {
            if (this.id.equals(o.id)) return 0;
            else return 1;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if ((obj == null) || (obj.getClass() != this.getClass())) {
                return false;
            }
            return ((Job) obj).id.equals(this.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    // relevant fields per agent
    private String leader = "";
    private double lat;
    private double lon;
    private double visionRange;
    static public Protocol protocol = Protocol.eCNP;


    private List<String> availableTrucks = new LinkedList<>();
    private List<ResourceNode> resourceNodes = new LinkedList<>();
    private HashMap<String, Job> allJobs = new HashMap<>();
    private HashMap<String, eCNPInstance> eCNPInstances = new HashMap<>();
    private LinkedList<String> finishedECNPs = new LinkedList<>();
//    private LinkedList<CNPInstance> CNPInstances = new LinkedList<>();

    // sim env vars
    private int step = 0;
    private double maxLat;
    private double minLat;
    private double maxLon;
    private double minLon;
    private double centerLon;
    private double centerLat;

    // rough ratios for Berlin scenario
    private static final double lonToMetersRatio = 68000;
    private static final double latToMetersRatio = 111000.32;

    /**
     * Constructor
     *
     * @param name    the agent's name
     * @param mailbox the mail facility
     */
    public EvilCorpHQ(String name, MailService mailbox) {
        super(name, mailbox);
    }

    @Override
    public void handlePercept(Percept percept) {

    }

    @Override
    public Action step() {
        electLeader();

        List<Percept> percepts = getPercepts();

        // for whatever reason it seems the simStart percept doesn't work correctly
        // but all subitems of the simStart percept are their own percepts. Weird.
        // ideally the function below would only be called in the first simulation step
        // but for now, its every step
        extractInfoFromStartPercept(percepts);

        // extract values from step percept (e.g. lat and lon of agent, resource nodes)
        extractInfoFromStepPercept(percepts);

        // try commanding trucks to pick up some resources
        if (leader.equals(getName())) {
            tryOrderTrucksToResourceNodes();
        }

        // process (e)CNP instances
        processCNPInstances();

        // try starting job announcements
        startJobAnnouncement();

        Action action = getAction();
        // say(action.toProlog());
        return action;
    }

    private void electLeader() {
        // "elect" a leader (first one wins)
        if(leader.equals("")) {
            broadcast(new Percept("leader"), getName());
            this.leader = getName();
        }
    }

    private void processCNPInstances() {
        // process eCNP instances
        for (eCNPInstance inst : eCNPInstances.values())
            inst.handleCurrentBids(step);

        // remove finished instances
        for (String jobId : finishedECNPs) {
            eCNPInstances.remove(jobId);
        }
        finishedECNPs.clear();

        // TODO: handle CNP instances
    }

    @Override
    public void handleMessage(Percept message, String sender) {
        switch(message.getName()) {
            case "leader":
                this.leader = sender;
                say("I agree to " + sender + " being the group leader.");
                break;
            case "TruckReady":
                availableTrucks.add(sender);
                break;
            case "resourceNodeFound":
                addResourceNodeUnique(message);
                break;
            case "bid":
                String jobId = String.valueOf(message.getParameters().get(0));
                float bidAmount = ((Numeral) message.getParameters().get(1)).getValue().floatValue();
                eCNPInstances.get(jobId).handleBidMessage(sender, bidAmount, step);
                break;
            case "definitiveBid":
                jobId = String.valueOf(message.getParameters().get(0));
                bidAmount = ((Numeral) message.getParameters().get(1)).getValue().floatValue();
                eCNPInstances.get(jobId).handleDefinitiveBidMessage(sender, bidAmount, step);
                break;
            default:
                say("I cannot handle a message of type " + message.getName());
        }
    }

    /**
     * will look though percepts and get the min and max lon and latitude
     * after that it will calculate the center of the simulation area
     * all values will be stored in the attributes of the agent
     */
    private void extractInfoFromStartPercept(List<Percept> percepts) {
        // check for minLat percept
        percepts.stream()
                .filter(p -> p.getName().equals("minLat"))
                .findAny()
                .ifPresent(p -> {
                    // this syntax ... ouf
                    minLat = ((Numeral) p.getParameters().getFirst()).getValue().doubleValue();
                });

        // check for maxLat percept
        percepts.stream()
                .filter(p -> p.getName().equals("maxLat"))
                .findAny()
                .ifPresent(p -> {
                    // this syntax ... ouf
                    maxLat = ((Numeral) p.getParameters().getFirst()).getValue().doubleValue();
                });

        // check for minLon percept
        percepts.stream()
                .filter(p -> p.getName().equals("minLon"))
                .findAny()
                .ifPresent(p -> {
                    // this syntax ... ouf
                    minLon = ((Numeral) p.getParameters().getFirst()).getValue().doubleValue();
                });

        // check for maxLon percept
        percepts.stream()
                .filter(p -> p.getName().equals("maxLon"))
                .findAny()
                .ifPresent(p -> {
                    // this syntax ... ouf
                    maxLon = ((Numeral) p.getParameters().getFirst()).getValue().doubleValue();
                });

        // calculate the center of the simulation environment
        centerLat = (maxLat + minLat) / 2;
        centerLon = (maxLon + minLon) / 2;
    }

    private void extractInfoFromStepPercept(List<Percept> percepts) {
        // check for vision percept
        percepts.stream()
                .filter(p -> p.getName().equals("vision"))
                .findAny()
                .ifPresent(p -> {
                    // this syntax ... ouf
                    visionRange = ((Numeral) p.getParameters().getFirst()).getValue().doubleValue();
                });

        // update lat
        percepts.stream()
                .filter(p -> p.getName().equals("lat"))
                .findAny()
                .ifPresent(p -> {
                    // this syntax ... ouf
                    lat = ((Numeral) p.getParameters().getFirst()).getValue().doubleValue();
                });

        // update long
        percepts.stream()
                .filter(p -> p.getName().equals("lon"))
                .findAny()
                .ifPresent(p -> {
                    // this syntax ... ouf
                    lon = ((Numeral) p.getParameters().getFirst()).getValue().doubleValue();
                });

        // update step
        percepts.stream()
                .filter(p -> p.getName().equals("step"))
                .findAny()
                .ifPresent(p -> {
                    // this syntax ... ouf
                    step = ((Numeral) p.getParameters().getFirst()).getValue().intValue();
                });

        // update jobs
        percepts.stream()
                .filter(p -> p.getName().equals("job"))
                .forEach(p -> {
                    // this syntax ... ouf
                    String id = String.valueOf(p.getParameters().get(0));
                    int reward = ((Numeral) p.getParameters().get(2)).getValue().intValue();
                    Job job = new Job(id, reward);
                    allJobs.put(id, job);
                });
    }

    private void addResourceNodeUnique(Percept p) {
        String name = ((Identifier) p.getParameters().get(0)).getValue();
        if (resourceNodes.stream().anyMatch(node -> node.name.equals(name))) return;

        double resLat = ((Numeral) p.getParameters().get(1)).getValue().doubleValue();
        double resLon = ((Numeral) p.getParameters().get(2)).getValue().doubleValue();
        String item = ((Identifier) p.getParameters().get(3)).getValue();
        resourceNodes.add(new ResourceNode(item, name, resLat, resLon));
        say(String.format("New resource node added to list: %s", p.toProlog()));
    }

    private Action getAction() {
        return new Action("noAction");
    }

    /**
     * return euclidean dist between p1 and p2
     * @return euclidean distance between (x1, y1) and (x2, y2)
     */
    private double get2dDist(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    private void tryOrderTrucksToResourceNodes() {
        if (resourceNodes.size() == 0) {
            say("No resource node located yet."); return;
        }
        while (availableTrucks.size() > 0)
            orderTruckToResourceNode();
    }

    private void orderTruckToResourceNode() {
        // check if trucks are available
        if (availableTrucks.isEmpty()) return;

        // get first available truck
        String truckName = availableTrucks.get(0);

        // get resource node to pick up from
        ResourceNode node = resourceNodes.get(0);

        // create message
        // name = gatherFromNode, parameters = [lat, lon, resourceNodeName, itemName]
        Percept message = new Percept("gatherFromNode", new Numeral(node.location.lat),
                new Numeral(node.location.lon), new Identifier(node.name), new Identifier(node.item));

        // send message to truck
        // we imply that message will come through and truck doesn't need to send an answer
        // sendMessage(message, truckName, getName());
        say(String.format("Commanding %s to pick up %s from %s.", truckName, node.item, node.name));

        // truck is not available any longer
        availableTrucks.remove(0);
        // push resource node to the back of the list, so we goto other ones first
        resourceNodes.remove(0);
        resourceNodes.add(node);
    }

    private void startJobAnnouncement() {
        // TODO: gather jobs to announce // for now all jobs that arent contracted yet
        List<Job> jobsWOContract = allJobs.values().stream()
                .filter(job -> !job.contracted).collect(Collectors.toList());

        // for each job start a new eCNP instance
        for (Job job : jobsWOContract) {
            eCNPInstance inst = new eCNPInstance(job.id, this);
            eCNPInstances.put(job.id, inst);
            inst.announceJob(step);
            job.contracted = true;
        }
    }

    public void closeECNPInstance(String jobId, boolean success) {
        if (!success) {
            // TODO: readd to joblist or sth? for now, not contracted jobs will be contracted again
            allJobs.get(jobId).contracted = false;
        }
        finishedECNPs.add(jobId);
    }
}
