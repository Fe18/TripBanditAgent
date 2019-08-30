package UserExamples;

import COMSETsystem.CityMap;
import COMSETsystem.Intersection;
import COMSETsystem.Link;
import COMSETsystem.Vertex;
import DataParsing.KdTree;

import java.util.*;

public class Trip {

    private CityMap map;
    private KdTree index = new KdTree();
    private List<Long> intersectionIDs;
    private HashMap<Long, Double> weights;
    private int id;


    public Trip(int id, List<Long> intersectionIDs, CityMap map) {
        this.id = id;
        this.map = map;
        this.weights = new HashMap<>();
        this.intersectionIDs = createTrip(intersectionIDs);
        buildIndex();
    }

    private void buildIndex() {
        // create Vertices
        List<Vertex> tripVertices = new ArrayList<>();
        for (Long intersectionID : intersectionIDs) {
            tripVertices.add(new Vertex(
                    map.intersections().get(intersectionID).longitude,
                    map.intersections().get(intersectionID).latitude,
                    map.intersections().get(intersectionID).xy.getX(),
                    map.intersections().get(intersectionID).xy.getY(),
                    intersectionID
                    ));
        }
        // create Links
        List<Link> tripLinks = new ArrayList<>();
        for (int i = 0; i < tripVertices.size()-1; i++) {
            tripLinks.add(new Link(tripVertices.get(i), tripVertices.get(i+1), 0.0, 0.0));
        }
        // fill index
        for (Link link : tripLinks) {
            this.index.insert(link);
        }
    }

    private List<Long> createTrip(List<Long> convexHull) {
        List<Long> intersectionIDs = new ArrayList<>(convexHull);
        // close the trip if not closed yet
        if (!intersectionIDs.get(0).equals(intersectionIDs.get(intersectionIDs.size()-1))) {
            intersectionIDs.add(intersectionIDs.get(0));
        }
        // calculate the entire trip (first != last)
        LinkedList<Long> trip = new LinkedList<>();
        for (int i = 0; i < intersectionIDs.size()-1; i++) {
            Long from = intersectionIDs.get(i);
            Long to = intersectionIDs.get(i+1);

            LinkedList<Intersection> path = map.shortestTravelTimePath(
                    map.intersections().get(from),
                    map.intersections().get(to)
            );

            if (i == 0) {
                for (Intersection intersection : path) {
                    trip.add(intersection.id);
                }
            } else if (i == intersectionIDs.size() - 2) {
                for (int idx = 1; idx < path.size()-1; idx++) {
                    trip.add(path.get(idx).id);
                }
            } else {
                for (int idx = 1; idx < path.size(); idx++) {
                    trip.add(path.get(idx).id);
                }
            }
        }

        return trip;
    }


    public Intersection findClosest(Intersection intersection) {
        return map.intersections().get(this.index.nearest(intersection.xy).from.id);
    }

    public int getTravelDuration() {
        int duration = 0;
        for (int idx = 0; idx < intersectionIDs.size()-1; idx++) {
            Intersection from = map.intersections().get(intersectionIDs.get(idx));
            Intersection to = map.intersections().get(intersectionIDs.get(idx+1));

            duration += map.travelTimeBetween(from, to);
        }

        if (!intersectionIDs.get(0).equals(intersectionIDs.get(intersectionIDs.size()-1))) {
            duration += map.travelTimeBetween(
                    map.intersections().get(intersectionIDs.get(intersectionIDs.size()-1)),
                    map.intersections().get(intersectionIDs.get(0)));
        }
        return duration;
    }

    public List<Long> getIntersectionIDs() {
        return intersectionIDs;
    }

    public int size() {
        return intersectionIDs.size();
    }

    public HashMap<Long, Double> getWeights() {
        return weights;
    }

    public int getId() {
        return id;
    }
}
