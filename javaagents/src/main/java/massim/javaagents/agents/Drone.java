package massim.javaagents.agents;

import eis.iilang.*;
import massim.javaagents.MailService;

import java.util.LinkedList;
import java.util.List;

public class Drone extends Agent{

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

    private enum DroneState {
        GOTO_CENTER,
        EXPLORE,
        FINISHED
    }

    // relevant fields per agent
    private String leader = "";
    private DroneState state;
    private double lat;
    private double lon;
    private double visionRange;

    private List<Point> waypoints = new LinkedList<>();
    private List<String> availableTrucks = new LinkedList<>();
    private List<ResourceNode> resourceNodes = new LinkedList<>();

    private static double WAYPOINT_ACC_DIST = 0.0001;

    // sim env vars
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
    public Drone(String name, MailService mailbox) {
        super(name, mailbox);
        state = DroneState.GOTO_CENTER;
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

        // check for vision percept
        // <resourceNode lat="51.478" lon="-0.03632" name="resourceNode1" resource="item7"/>
        // add to found resource nodes if its not already inside
        percepts.stream()
                .filter(p -> p.getName().equals("resourceNode"))
                .forEach(p -> {
                    if (leader.equals(getName()))
                        addResourceNodeUnique(p);
                    else {
                        say(String.format("I found a resource node - forwarding to leader: %s", p.toProlog()));
                        sendMessage(new Percept("resourceNodeFound", p.getParameters()), leader, getName());
                    }
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
        if (state == DroneState.GOTO_CENTER) {
            // check if drone reached center point
            if (get2dDist(lat, lon, centerLat, centerLon) < WAYPOINT_ACC_DIST) {
                state = DroneState.EXPLORE;
                return getAction();
            }

            // send action GOTO (center)
            return new Action("goto", new Numeral(centerLat), new Numeral(centerLon));
        }

        if (state == DroneState.EXPLORE) {
            // generate waypoints
            // after generation just goto next waypoint
            if (waypoints.isEmpty()) generateSpiralExploreWaypoints();

            Point wp = waypoints.get(0);
            // check if we reached curr waypoint
            if (get2dDist(lat, lon, wp.lat, wp.lon) < WAYPOINT_ACC_DIST) {
                waypoints.remove(0);
                return getAction();
            } else {
                // goto next waypoint
                return new Action("goto", new Numeral(wp.lat), new Numeral(wp.lon));
            }
        }

        // dummy action
        return new Action("noAction");
    }

    /**
     * generates waypoints for exploring the sim env and puts them into the waypoints list
     */
    private void generateExploreWaypoints() {
        // for now explore in rows bottom to top
        double edgePadding = Math.sqrt(2 * Math.pow(visionRange, 2));
        double paddedLatSize = maxLat - minLat - 2 * edgePadding / latToMetersRatio;
        double paddedMaxLat = maxLat - edgePadding / 2 / latToMetersRatio;
        double paddedMinLat = minLat + edgePadding / 2 / latToMetersRatio;
        double paddedMaxLon = maxLon - edgePadding / 2 / lonToMetersRatio;
        double paddedMinLon = minLon + edgePadding / 2 / lonToMetersRatio;

        // num left to right lines the drone will need to scan the complete padded field
        int numRows = (int) Math.ceil((maxLat - minLat) / (2 * visionRange / latToMetersRatio));
        for (int i = 0; i < numRows; i++) {
            double rowProgress = (numRows == 1) ? 0 : i / (double) (numRows - 1);
            double wpLat = paddedMinLat + Math.min(paddedMaxLat, rowProgress * paddedLatSize);
            waypoints.add(new Point(wpLat, (i % 2 == 0) ? paddedMinLon : paddedMaxLon));
            waypoints.add(new Point(wpLat, (i % 2 == 0) ? paddedMaxLon : paddedMinLon));
        }
    }

    private void generateSpiralExploreWaypoints() {
        // for now explore in rows bottom to top
        double edgePadding = Math.sqrt(2 * Math.pow(visionRange, 2));
        double adjViewRange = edgePadding;  // if rectangular path, we need some more overlap in the edges
        double paddedMaxLat = maxLat - edgePadding / 2 / latToMetersRatio;
        double paddedMinLat = minLat + edgePadding / 2 / latToMetersRatio;
        double paddedMaxLon = maxLon - edgePadding / 2 / lonToMetersRatio;
        double paddedMinLon = minLon + edgePadding / 2 / lonToMetersRatio;

        // num left to right lines the drone will need to scan the complete padded field
        Point currPt = new Point(centerLat, centerLon);
        boolean leftReached, rightReached, topReached, botReached;
        int extraIterations = 2;
        leftReached = rightReached = topReached = botReached = false;
        int i = 0;
        while (extraIterations > 0) {
            double lenToAdd = adjViewRange * (double) ((i + 2) / 2);
            Point directionInMeters = new Point(lenToAdd, 0).rot(i * Math.PI / 2);

            currPt = currPt.add(directionInMeters.mult(new Point(1 / latToMetersRatio, 1 / lonToMetersRatio)));
            // check if reached boundaries
            if (currPt.lat <= paddedMinLat) topReached = true;
            if (currPt.lat >= paddedMaxLat) botReached = true;
            if (currPt.lon <= paddedMinLon) leftReached = true;
            if (currPt.lon >= paddedMaxLon) rightReached = true;

            // clamp to boundaries
            currPt = currPt.clamp(paddedMinLat, paddedMaxLat, paddedMinLon, paddedMaxLon);
            waypoints.add(currPt);
            if (leftReached && rightReached && topReached && botReached)
                extraIterations--;
            i++;
        }
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
        sendMessage(message, truckName, getName());
        say(String.format("Commanding %s to pick up %s from %s.", truckName, node.item, node.name));

        // truck is not available any longer
        availableTrucks.remove(0);
        // push resource node to the back of the list, so we goto other ones first
        resourceNodes.remove(0);
        resourceNodes.add(node);
    }
}
