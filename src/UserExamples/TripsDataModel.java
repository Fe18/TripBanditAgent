package UserExamples;

import COMSETsystem.*;

import java.io.*;
import java.util.*;

public class TripsDataModel {

    private static final int SECONDS_IN_HOUR = 60 * 60;
    private static final int SECONDS_IN_DAY = 24 * SECONDS_IN_HOUR;
    private static final int SECONDS_IN_WEEK = 7 * SECONDS_IN_DAY;

    private static String DEFAULT_DATA_FILE = "resources/data.bin";
    private static String DATA_FILE_TEMPLATE = "resources/data_%02d.bin";

    private String loadedData = null;

    // A reference to the map.
    private final CityMap map;

    // The map of trips.
    private final Map<Intersection, Trip> trips = new HashMap<>();

    // data structures and params that are used for the discretization of time
    private int binSize = -1; // minutes per bin

    private final List<Trip> tripList = new ArrayList<>();
    public final Random random = new Random();
    public double[][] theta;

    // for fast sampling
    private double[][] cumFrequencies;

    /**
     * Constructor for the TripsDataModel.
     *
     * @param map An instance of the CityMap
     */
    public TripsDataModel(CityMap map) {
        this.map = map;
        ensureDataLoaded(-1);
    }

    /**
     * Loads the preprocessed data file according to the given timestamp.
     * If the timestamp is less or equal to 0, then the data file containing the trip probabilities
     * that have been gathered from the entire last year are loaded; otherwise, the trip probabilities
     * of the corresponding month are loaded.
     *
     * @param time The timestamp
     */
    private void ensureDataLoaded(long time) {
        String dataFile;
        if (time <= 0) {
            dataFile = DEFAULT_DATA_FILE;
        } else {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date(time*1000));
            int month = (1+cal.get(Calendar.MONTH));
            dataFile = String.format(DATA_FILE_TEMPLATE, month);
        }

        if (!dataFile.equals(loadedData)) {
            loadedData = dataFile;

            TripsDataProcessor processor = new TripsDataProcessor(map);
//            processor.computeData();
            try {
//                processor.writeData(DATA_FILE);
                processor.loadData(dataFile);
                System.out.println("Data file: " + dataFile);
            } catch (IOException e) {
                System.err.println("Could not load the data file!!!");
                e.printStackTrace();
                // processor.computeData();
                return;
            }
            TripsDataProcessor.Data data = processor.getData();

            trips.clear();
            tripList.clear();
            trips.putAll(data.trips);
            this.theta = data.theta;
            tripList.addAll(trips.values());
            tripList.sort(Comparator.comparing(Trip::getId));

            binSize = SECONDS_IN_WEEK / this.theta.length;
            if (SECONDS_IN_WEEK % this.theta.length != 0) {
                throw new IllegalArgumentException("Theta is not valid.");
            }

            prepareFastSampling();
        }
    }

    /**
     * This method prepares cumulative probability vectors to support fast trip sampling.
     */
    private void prepareFastSampling() {
        assert binSize >= 0 && binSize <= SECONDS_IN_WEEK && SECONDS_IN_WEEK % binSize == 0;

        cumFrequencies = new double[theta.length][trips.size()];

        for (int bin = 0; bin < SECONDS_IN_WEEK / binSize; bin++) {
            double[] cdf = cumFrequencies[bin];
            double[] distribution = distribution(bin);

            double csum = 0.0;
            for (int idx = 0; idx < theta[bin].length; idx++) {
                csum += distribution[idx];
                cdf[idx] = csum;
                if (csum > 1 - 1e-12 && idx < theta[bin].length - 1) {
                    cdf = Arrays.copyOf(cdf, idx+1);
                    break;
                }
            }
            cumFrequencies[bin] = cdf;
        }
    }

    /**
     * Returns the trip probability vector for the given timestamp, resp. the discrete time bin into which the
     * timestamp falls.
     *
     * @param time The timestamp
     * @return
     */
    public double[] distribution(long time) {
        ensureDataLoaded(time);
        int bin = assignTimeIndex(time);
        return distribution(bin);
    }


    /**
     * Softmax distribution vector.
     *
     * @param bin The bin index
     * @return distribution vector
     */
    public double[] distribution(int bin) {
        double[] distribution = new double[theta[bin].length];
        double sum = 0;
        for (int i = 0; i < theta[bin].length; i++) {
            sum += Math.exp(theta[bin][i]);
        }
        for (int i = 0; i < theta[bin].length; i++) {
            distribution[i] = Math.exp(theta[bin][i]) / sum;
        }
        return distribution;
    }


    /**
     * Samples a trip according to the given time and the corresponding distribution.
     *
     * @param time The timestamp
     * @return a trip
     */
    public Trip sampleTrip(long time) {
        ensureDataLoaded(time);
        int currentTimeBin = assignTimeIndex(time);
        int sample_idx = Arrays.binarySearch(cumFrequencies[currentTimeBin], random.nextDouble());

        sample_idx = (sample_idx >= 0) ? sample_idx : (-sample_idx - 1);
        return tripList.get(sample_idx);
    }

    /**
     * Get the index that maps to the time bin of the given timestamp.
     *
     * @param time The timestamp
     * @return bin index
     */
    public int assignTimeIndex(Long time) {
        return (int)((time % SECONDS_IN_WEEK) / binSize);
    }

    public List<Trip> getTrips() {
        return Collections.unmodifiableList(tripList);
    }

    public void writeData(String filename) throws IOException {
        TripsDataProcessor.writeData(filename, trips, theta);
    }
}
