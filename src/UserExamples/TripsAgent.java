package UserExamples;

import COMSETsystem.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TripsAgent extends BaseAgent {

    // search route stored as a list of intersections.
    LinkedList<Intersection> route = new LinkedList<Intersection>();

    // a static singleton object of a data model, shared by all agents
    static TripsDataModel dataModel = null;

    /**
     * TripsAgent constructor.
     *
     * @param id An id that is unique among all agents and resources
     * @param map The map
     */
    public TripsAgent(long id, CityMap map) {
        super(id, map);
        if (dataModel == null) {
            dataModel = new TripsDataModel(map);
        }
    }

    /**
     * From all the trips choose one (wrt to its temporal weight) to go.
     */
    @Override
    public void planSearchRoute(LocationOnRoad currentLocation, long currentTime) {
        route.clear();
        Intersection currentIntersection = currentLocation.road.to;

        // sample trip w/o journey
        Trip trip = dataModel.sampleTrip(currentTime);

        // calculate the nearest point on the trip
        Intersection firstTripIntersection = trip.findClosest(currentIntersection);
        // if the current intersection is not a trip intersection drive from current intersection to the trip
        if (!currentIntersection.equals(firstTripIntersection)) {
            LinkedList<Intersection> initPath = this.map.shortestTravelTimePath(currentIntersection, firstTripIntersection);
            // add this way to the planned route
            route.addAll(initPath.subList(1, initPath.size())); // without the first entry as it equals to currentLocation.road.to
        }

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
    }

    /**
     * This method polls the first intersection in the current route and returns this intersection.
     *
     * This method is a callback method which is called when the agent reaches an intersection. The Simulator
     * will move the agent to the returned intersection and then call this method again, and so on.
     * This is how a planned route (in this case randomly planned) is executed by the Simulator.
     *
     * @return Intersection that the Agent is going to move to.
     */

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
     * A dummy implementation of the assignedTo callback function which does nothing but clearing the current route.
     * assignedTo is called when the agent is assigned to a resource.
     */

    @Override
    public void assignedTo(LocationOnRoad currentLocation, long currentTime, long resourceId, LocationOnRoad resourcePikcupLocation, LocationOnRoad resourceDropoffLocation) {
        // Clear the current route.
        route.clear();

        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Agent " + this.id + " assigned to resource " + resourceId);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "currentLocation = " + currentLocation);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "currentTime = " + currentTime);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "resourcePickupLocation = " + resourcePikcupLocation);
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "resourceDropoffLocation = " + resourceDropoffLocation);
    }


}
