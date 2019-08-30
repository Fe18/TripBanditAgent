package UserExamples;

import COMSETsystem.*;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 * The TripsBanditAgent is an {@link BaseAgent Agent} for the {@link Simulator COMSET Simulator}.
 *
 * This agent is used for training only.
 * The base idea of this agent is to learn a distribution of {@link Trip trips} to sample from such that
 * the average search time is minimized.
 * To achieve this, we decided to use a multi-armed bandit (reinforcement learning) like approach.
 * Basically, an agent chooses a {@link Trip trip}, sampled from the (learned) distribution and drives around the trip
 * until it found a passenger.
 * The search time (costs/negative reward) will be used to update the sample distribution for the current time bin
 * (see {@link TripsDataModel}).
 */
public class TripsBanditAgent extends BaseAgent {

    /**
     * The current learning rate of all agents.
     * This might change (decay) over time.
     */
    private static double ALPHA = 1e-7;
    /**
     * A boolean that tells whether the average search time should contain the time for the approach to the trip.
     */
    private static final boolean WITH_APPROACH = false;

    /**
     * The route that is currently applied.
     */
    private LinkedList<Intersection> route = new LinkedList<>();

    /**
     * The data model.
     */
    static TripsDataModel dataModel = null;

    /**
     * The trip the agent is currently on.
     */
    private Trip trip;

    /**
     * The time when the agent started searching.
     */
    private long searchStart = -1;

    /**
     * TripsBanditAgent constructor.
     *
     * @param id An id that is unique among all agents and resources
     * @param map The map
     */
    public TripsBanditAgent(long id, CityMap map) {
        super(id, map);
        if (dataModel == null) {
            dataModel = new TripsDataModel(map);
        }
        //trip = sampleTrip();
    }


    @Override
    public void planSearchRoute(LocationOnRoad currentLocation, long currentTime) {
        route.clear();
        Intersection currentIntersection = currentLocation.road.to;

        if (trip == null) {
            trip = sampleTrip(currentTime);
        }

        // calculate the nearest point on the trip
        Intersection firstTripIntersection = trip.findClosest(currentIntersection);
        // if the current intersection is not a trip intersection drive from current intersection to the trip
        if (!currentIntersection.equals(firstTripIntersection)) {
            LinkedList<Intersection> initPath = this.map.shortestTravelTimePath(currentIntersection, firstTripIntersection);
            // add this way to the planned route
            route.addAll(initPath.subList(1, initPath.size())); // without the first entry as it equals to currentLocation.road.to
        }

        if (currentIntersection.equals(firstTripIntersection) || WITH_APPROACH) {

            // cut the trip at the firstTripIntersection and start from there (exclude the current intersection)
            int cut = trip.getIntersectionIDs().indexOf(firstTripIntersection.id);
            List<Long> firstPartIds = trip.getIntersectionIDs().subList(cut + 1, trip.size());
            for (Long identifier : firstPartIds) {
                route.add(map.intersections().get(identifier));
            }
            List<Long> secondPartIds = trip.getIntersectionIDs().subList(0, cut);
            for (Long identifier : secondPartIds) {
                route.add(map.intersections().get(identifier));
            }

            searchStart = currentTime;
        }
    }


    @Override
    public Intersection nextIntersection(LocationOnRoad currentLocation, long currentTime) {
        if (route.size() != 0) {
            // Route is not empty, take the next intersection.
            Intersection nextIntersection = route.poll();
            return nextIntersection;
        } else {
            // Finished the planned route. Plan a new route.
            planSearchRoute(currentLocation, currentTime);
            return route.poll();
        }
    }

    /**
     * This method samples a trip from the (learned) distribution.
     * @param time The current time. This parameter is used to obtain the time bin for the distributions
     *             (see {@link TripsDataModel}).
     * @return The sampled trip.
     */
    private Trip sampleTrip(long time) {
        double[] distribution = dataModel.distribution(time);

        double sample = dataModel.random.nextDouble();
        double sum = 0;
        List<Trip> trips = dataModel.getTrips();
        for (int i = 0; i < distribution.length; i++) {
            sum += distribution[i];
            if (sample <= sum) {
                return trips.get(i);
            }
        }
        return trips.get(trips.size()-1);
    }

    /**
     * Update (learn) the distribution for the current experience and time.
     * @param time The time when a resource has been picked up.
     */
    private void updateTheta(long time) {
        int startBin = dataModel.assignTimeIndex(searchStart);
        int endBin = dataModel.assignTimeIndex(time);
        List<Trip> trips = dataModel.getTrips();

        assert searchStart > 0;
        double averageSearchTime = time - searchStart;
        int chosenTrip = trips.indexOf(this.trip);

        int bin = startBin;
//        for (int bin = startBin; bin <= endBin; bin++) {
            double[] distribution = dataModel.distribution(bin);

            for (int j = 0; j < dataModel.theta[bin].length; j++) {
                // gradient descent (minimize costs)
                // theta <- theta - alpha * Reward * ∇_theta cross_entropy(softmax(theta), A)
                if (j != chosenTrip) {
                    dataModel.theta[bin][j] += ALPHA * distribution[j] * averageSearchTime;
                } else {
                    dataModel.theta[bin][j] += ALPHA * (distribution[j] - 1) * averageSearchTime;
                }
            }
//        }
    }


    @Override
    public void assignedTo(LocationOnRoad currentLocation, long currentTime, long resourceId, LocationOnRoad resourcePikcupLocation, LocationOnRoad resourceDropoffLocation) {
        // Clear the current route.
        route.clear();

        if (searchStart != -1) {
            updateTheta(currentTime + map.travelTimeBetween(currentLocation, resourcePikcupLocation));
            searchStart = -1;
        }
        trip = null;

        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Agent " + this.id + " assigned to resource " + resourceId);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "currentLocation = " + currentLocation);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "currentTime = " + currentTime);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "resourcePickupLocation = " + resourcePikcupLocation);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "resourceDropoffLocation = " + resourceDropoffLocation);
    }


    /**
     * A main method to learn the distribution of the trips.
     */
    public static void main(String[] args) throws IOException, ClassNotFoundException {

        if (args.length != 4) {
            throw new IllegalArgumentException("Call: experiment_name simulation_file.csv alpha_start alpha_decay");
        }
        String experimentName = args[0];
        String datasetFile = args[1];
        ALPHA = Double.parseDouble(args[2]);
        double alphaDecay = Double.parseDouble(args[3]);

        String configFile = "etc/config.properties";
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(configFile));

            //get the property values

            String mapJSONFile = prop.getProperty("comset.map_JSON_file").trim();

            String numberOfAgentsArg = prop.getProperty("comset.number_of_agents").trim();
            long numberOfAgents = Long.parseLong(numberOfAgentsArg);

            String boundingPolygonKMLFile = prop.getProperty("comset.bounding_polygon_KML_file").trim();

            String resourceMaximumLifeTimeArg = prop.getProperty("comset.resource_maximum_life_time").trim();
            long resourceMaximumLifeTime = Long.parseLong(resourceMaximumLifeTimeArg);

            String speedReductionArg = prop.getProperty("comset.speed_reduction").trim();
            double speedReduction = Double.parseDouble(speedReductionArg);

            String displayLoggingArg = prop.getProperty("comset.logging").trim();
            boolean displayLogging = Boolean.parseBoolean(displayLoggingArg);

            String agentPlacementSeedArg = prop.getProperty("comset.agent_placement_seed").trim();
            long agentPlacementSeed = Long.parseLong(agentPlacementSeedArg);
            if (agentPlacementSeed < 0) {
                Random random = new Random();
                agentPlacementSeed = random.nextLong();
            }

            Class<? extends BaseAgent> agentClass = TripsBanditAgent.class;

//            FileWriter file = new FileWriter("out/progress_" + experimentName + ".csv");

            for (int i = 0; i < 1000000; i++) {
                System.out.println("\n********** Experiment " + experimentName + " **********\n");
                System.out.println("\n********** Epoch " + i + " **********\n");
                System.out.println("\n********** ALPHA " + ALPHA + " **********\n");

                if (!displayLogging) {
                    LogManager.getLogManager().reset();
                }

                Simulator simulator = new Simulator(agentClass);
                simulator.configure(mapJSONFile, datasetFile, numberOfAgents, boundingPolygonKMLFile, resourceMaximumLifeTime, agentPlacementSeed, speedReduction);
                simulator.run();
//                file.write(simulator.run().getAverageAgentSearchTime() + "\n");
//                file.flush();

                if (i % 1 == 0) {
                    dataModel.writeData("out/theta_" + experimentName + "_" + i + ".bin");
                }

                ALPHA *= alphaDecay;
            }

//            file.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
