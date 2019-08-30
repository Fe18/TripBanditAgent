package UserExamples;

import COMSETsystem.*;
import DataParsing.CSVNewYorkParser;
import DataParsing.Resource;
import me.tongfei.progressbar.ProgressBar;

import java.io.*;
import java.util.*;

public class TripsDataProcessor {

    public static final int SECONDS_IN_HOUR = 60 * 60;
    public static final int SECONDS_IN_DAY = 24 * SECONDS_IN_HOUR;
    public static final int SECONDS_IN_WEEK = 7 * SECONDS_IN_DAY;

    // Used training data (must be downloaded and put into the datasets folder in advance)
    final String[] data_files = new String[]{
            "datasets/yellow_tripdata_2016-01.csv",
            "datasets/yellow_tripdata_2016-02.csv",
            "datasets/yellow_tripdata_2016-03.csv",
            "datasets/yellow_tripdata_2016-04.csv",
            "datasets/yellow_tripdata_2016-05.csv",
            "datasets/yellow_tripdata_2016-06.csv",
            "datasets/yellow_tripdata_2015-07.csv",
            "datasets/yellow_tripdata_2015-08.csv",
            "datasets/yellow_tripdata_2015-09.csv",
            "datasets/yellow_tripdata_2015-10.csv",
            "datasets/yellow_tripdata_2015-11.csv",
            "datasets/yellow_tripdata_2015-12.csv"
    };


    // A reference to the map.
    CityMap map;

    // The map of trips.
    private HashMap<Intersection, Trip> trips = new HashMap<>();

    // data structures and params that are used for the discretization of time
    private int binSize = 60 * 60; // seconds per bin
    private int binStep = 60 * 60; // moving average step (unused if binStep == binSize)

    private List<Trip> tripList = new ArrayList<>();
    public double[][] theta;
    public Random random = new Random();

    // pickup count map <intersectionID -> <time -> count>
    private HashMap<Long, HashMap<Long, Integer>> pickupCounts;


    public TripsDataProcessor(CityMap map) {
        this.map = map;
    }

    public void computeData() {
        countEvents(binSize, binStep);  // calculates pickup counts per intersection within time bins
        computeIsoChroneTrips();

        tripList.addAll(trips.values());
        tripList.sort(Comparator.comparing(Trip::getId));

        initTheta();
    }

    private void initTheta() {
        assert (SECONDS_IN_WEEK % binSize) == 0;
        theta = new double[SECONDS_IN_WEEK / binSize][trips.size()];

        for (int bin = 0; bin < SECONDS_IN_WEEK / binSize; bin++) {
            for (int i = 0; i < theta[bin].length; i++) {
                theta[bin][i] = Math.log(tripList.get(i).getWeights().get((long)binSize*bin)+1e-10);
            }
        }
    }

    public void writeData(String file) throws IOException {
        writeData(file, trips, theta);
    }

    public static void writeData(String file, Map<Intersection, Trip> tripMap, double[][] theta) throws IOException {
        try (DataOutputStream os = new DataOutputStream(new FileOutputStream(file))) {
            List<Map.Entry<Intersection, Trip>> trips = new ArrayList<>(tripMap.entrySet());
            os.writeInt(trips.size()); // write number of trips (int)
            for (Map.Entry<Intersection, Trip> entry : trips) {
                Trip trip = entry.getValue();
                os.writeLong(entry.getKey().id); // write trip id (long)
                List<Long> intersectionIDs = trip.getIntersectionIDs();
                os.writeInt(intersectionIDs.size()); // write number of intersections in trip (int)
                for (int i = 0; i < intersectionIDs.size(); i++) {
                    os.writeLong(intersectionIDs.get(i)); // write intersection id (long)
                }
            }

            os.writeInt(theta.length);
            os.writeInt(theta[0].length);
            for (int i = 0; i < theta.length; i++) {
                for (int j = 0; j < theta[i].length; j++) {
                    os.writeDouble(theta[i][j]);
                }
            }
        }
    }

    public void loadData(String file) throws IOException {
        try (DataInputStream os = new DataInputStream(new FileInputStream(file))) {
            tripList.clear();
            trips.clear();
            int tripsCount = os.readInt(); // read number of trips (int)
            for (int i = 0; i < tripsCount; i++) {
                long id = os.readLong(); // read trip id (long)

                int numberOfIntersections = os.readInt(); // read number of intersections in trip (int)
                ArrayList<Long> intersections = new ArrayList<>(numberOfIntersections);
                for (int j = 0; j < numberOfIntersections; j++) {
                    intersections.add(os.readLong()); // read intersection id (long)
                }

                Trip trip = new Trip((int) id, intersections, map);
                trips.put(map.intersections().get(id), trip);
                tripList.add(trip);
            }

            int dim1 = os.readInt();
            int dim2 = os.readInt();
            theta = new double[dim1][dim2];
            for (int i = 0; i < theta.length; i++) {
                for (int j = 0; j < theta[i].length; j++) {
                    theta[i][j] = os.readDouble();
                }
            }

            binSize = SECONDS_IN_WEEK / dim1;
            if (SECONDS_IN_WEEK % dim1 != 0) {
                throw new IOException("File is not consistent.");
            }
        }
    }


    public Data getData() {
        return new Data(Collections.unmodifiableMap(trips), theta);
    }

    public void computeIsoChroneTrips() {

        Map<Intersection, Set<Long>> reachMap = new HashMap<>();

        for (Long intersectionID : ProgressBar.wrap(map.intersections().keySet(), "Trip Calculations")) {
            // path search: collect all intersection within 5 min radius for calculating the trip
            // use intersections that are reachable within 10mins for weighting the trip
            Set<Long> visited = new HashSet<>();
            Set<Long> forHull = new HashSet<>();
            PriorityQueue<QEntry> pq = new PriorityQueue<>();
            pq.add(new QEntry(intersectionID, 0));

            while (!pq.isEmpty()) {
                QEntry curr = pq.poll();
                if (!visited.add(curr.getIntersectionID()))
                    continue;

                if (curr.getTravelTime() < 300L) {  // for the hull, only consider intersections if travel time is less than 5min
                    forHull.add(curr.getIntersectionID());
                }

                Set<Intersection> neighbors = map.intersections().get(curr.getIntersectionID()).getAdjacentFrom();

                for (Intersection ngh : neighbors) {
                    if (!visited.contains(ngh.id)) {

                        long totalTravelTime;
                        totalTravelTime = curr.getTravelTime() +
                                    map.travelTimeBetween(map.intersections().get(curr.getIntersectionID()), ngh);

                        if (totalTravelTime < 600L) {  // add nodes within 10 mins radius to be considered for weight calculation
                            pq.add(new QEntry(ngh.id, totalTravelTime));
                        }
                    }
                }
            }
            // create convex hull (have to convert from IntersectionIds to Intersections and back...)
            List<Intersection> intersectionList = new ArrayList<>();
            for (Long identifier : forHull) {
                intersectionList.add(map.intersections().get(identifier));
            }
            List<Intersection> convexHullIntersections = ConvexHull.convexHull(new ArrayList<>(intersectionList));
            List<Long> convexHull = new LinkedList<>();
            for (Intersection i : convexHullIntersections) {
                convexHull.add(i.id);
            }
            // create full trip (shortest paths between hull points)
            Trip trip = new Trip(intersectionID.intValue(), convexHull, map);
            if (trip.getIntersectionIDs().size() < 1) {
                continue;  // skip empty trips
            }

            // add the current trip to trips
            trips.put(map.intersections().get(intersectionID), trip);
            reachMap.put(map.intersections().get(intersectionID), forHull);
        }

        // minimize trips (i.e., remove redundancy due to overlaps)
        minimizeTrips(reachMap);

        // calculate some statistics and weights for timestamps (wrt all intersections covered by the current trip)
        double avgTripDuration = 0.0;
        double maxTripDuration = -1.0;
        double minTripDuration = 1e10;
        ArrayList<Double> tripDurations = new ArrayList<>();
        HashMap<Long, Double> totalWeightPerTime = new HashMap<>();
        HashMap<Long, Double> minWeightPerTime = new HashMap<>();
        for (long time = 0; time < SECONDS_IN_WEEK; time+=binSize) {
            totalWeightPerTime.put(time, 0.0);
            minWeightPerTime.put(time, Double.MAX_VALUE);
        }

        for (Intersection id : ProgressBar.wrap(trips.keySet(), "Trip Weighting")) {
            Set<Long> visited = reachMap.get(id);
            Trip trip = trips.get(id);
            for (long time = 0; time < SECONDS_IN_WEEK; time+=binSize) {
                double pickupWeight = 0.0;
                for (Long i : visited) {
                    pickupWeight += pickupCounts.get(i).get(time);
                }

                double weight = pickupWeight;

                trip.getWeights().put(time, weight);
                totalWeightPerTime.put(time, totalWeightPerTime.get(time) + weight);
                if (minWeightPerTime.get(time) > weight) {
                    minWeightPerTime.put(time, weight);
                }
            }

            // trip statistics
            avgTripDuration += trip.getTravelDuration();
            tripDurations.add(trip.getTravelDuration() / 60.);
            maxTripDuration = maxTripDuration > trip.getTravelDuration() ? maxTripDuration : trip.getTravelDuration();
            minTripDuration = minTripDuration < trip.getTravelDuration() ? minTripDuration : trip.getTravelDuration();
        }

        // normalize weights per time
        for (Map.Entry<Intersection, Trip> entry : trips.entrySet()) {
            for (Map.Entry<Long, Double> weightMap : entry.getValue().getWeights().entrySet()) {
                weightMap.setValue(weightMap.getValue() / totalWeightPerTime.get(weightMap.getKey()));
            }
        }

        // print trip statistics
        avgTripDuration = avgTripDuration / trips.size() / 60;
        System.out.println(String.format("Average Trip Duration: %.2f mins.", avgTripDuration));
        System.out.println(String.format("Minimum Trip Duration: %.2f mins.", (minTripDuration / 60)));
        System.out.println(String.format("Maximum Trip Duration: %.2f mins.", (maxTripDuration / 60)));
        double sumOfSquaredDiffs = 0.0;
        for (Double duration : tripDurations) {
            sumOfSquaredDiffs += Math.pow(duration - avgTripDuration, 2.);
        }
        double std = Math.sqrt(sumOfSquaredDiffs / tripDurations.size());
        System.out.println(String.format("Standard Deviation for Trip Durations: %.6f", std));
    }


    private void minimizeTrips(Map<Intersection, Set<Long>> reachMap) {
        List<Intersection> keys = new ArrayList<>(trips.keySet());
        keys.sort(Comparator.comparingInt(k -> reachMap.get(k).size()).thenComparing(k -> trips.get(k).getId()));

        Set<Intersection> removableTrips = new HashSet<>();
        for (int i = keys.size() - 1; i >= 0; i--) {
            Intersection tripId = keys.get(i);
            Set<Long> reachCopy = new HashSet<>(reachMap.get(tripId));

            for (int j = keys.size() - 1; j >= 0; j--) {
                if (i != j && !removableTrips.contains(keys.get(j))) {
                    reachCopy.removeAll(reachMap.get(keys.get(j)));
                    if (reachCopy.isEmpty()) {
                        removableTrips.add(tripId);
                        break;
                    }
                }
            }
        }

        for (Intersection remove : removableTrips) {
            trips.remove(remove);
        }
        System.out.println("After optimizing trips, there are " + trips.size() + " trips left.");
    }

    private ArrayList<Resource> parseResources(String data_file) {
        CSVNewYorkParser parser = new CSVNewYorkParser(data_file, map.computeZoneId());
        return parser.parse(); // Resources with unixTimestamps
    }

    /**
     * Precompute the pickup and drop off counts per intersection and time bin.
     * Those are going to be used for weighting the trips (temporally).
     *
     * @param binSize in minutes
     */
    private void countEvents(long binSize, long binStep) {
        assert (binSize >= binStep);
        // Load the data file such that we get a list of the resources as we'll have them in the map
        ArrayList<Resource> resources = new ArrayList<>();
        for (String data_file : data_files) {
            resources.addAll(parseResources(data_file));
        }

        // make sure that the list of resources is sorted according to pickup time
        resources.sort(Comparator.comparingLong(Resource::getTime));

        // init pick up and drop off count maps (recall: <intersectionID -> <time -> count>>)
        pickupCounts = new HashMap<>();
        for (Long intersectionID : map.intersections().keySet()) {
            HashMap<Long, Integer> timeToCount = new HashMap<>();
            for (long timestamp = 0; timestamp < SECONDS_IN_WEEK; timestamp+=binSize) {
                timeToCount.put(timestamp, 0);
            }
            pickupCounts.put(intersectionID, timeToCount);
        }
        // fill the map
        for (Resource res : resources) {
            assignResourceToIntersection(res);
        }
    }

    private void assignResourceToIntersection(Resource res) {
        HashMap<Long, HashMap<Long, Integer>> targetMap = pickupCounts;
        double lat = res.getPickupLat();
        double lon = res.getPickupLon();
        // get the nearest link of the current resource
        Link nearestLink = map.getNearestLink(lon, lat);
        // get the resource's xy projection
        double[] xyProjection = map.projector().fromLatLon(lat, lon);
        // get this resource's associated time bin
        long time = res.getTime();

        long associatedBinLatest = assignToTimeBin(time);
        // assign resource to closest intersection
        if (nearestLink.road.from.xy.distance(xyProjection[0], xyProjection[1]) <
                nearestLink.road.to.xy.distance(xyProjection[0], xyProjection[1])) {
            // the from intersection of the nearest link is closer
            HashMap<Long, Integer> counts = targetMap.get(nearestLink.road.from.id);
            counts.put(associatedBinLatest, counts.get(associatedBinLatest) + 1);
            targetMap.put(nearestLink.road.from.id, counts);
        } else {
            // the to intersection of the nearest link is closer
            HashMap<Long, Integer> counts = targetMap.get(nearestLink.road.to.id);
            counts.put(associatedBinLatest, counts.get(associatedBinLatest) + 1);
            targetMap.put(nearestLink.road.to.id, counts);
        }
    }


    public int assignTimeIndex(Long time) {
        return (int)((time % SECONDS_IN_WEEK) / binSize);
    }

    long assignToTimeBin(Long time){
        int idx = assignTimeIndex(time);
        return (idx * binSize);
    }

    public List<Trip> getTrips() {
        return Collections.unmodifiableList(tripList);
    }

    private class QEntry implements Comparable<QEntry> {

        private long travelTime;
        private Long intersectionID;

        public QEntry(Long i, long travelTime) {
            this.travelTime = travelTime;
            this.intersectionID = i;
        }

        public long getTravelTime() {
            return travelTime;
        }

        public void setTravelTime(int travelTime) {
            this.travelTime = travelTime;
        }

        public Long getIntersectionID() {
            return intersectionID;
        }

        @Override
        public int compareTo(QEntry e) {
            return Long.compare(this.travelTime, e.getTravelTime());
        }


    }


    public static class Data {
        public final Map<Intersection, Trip> trips;
        public final double[][] theta;

        public Data(Map<Intersection, Trip> trips, double[][] theta) {
            this.trips = trips;
            this.theta = theta;
        }
    }


}
