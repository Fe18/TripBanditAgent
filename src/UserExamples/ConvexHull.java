package UserExamples;

import COMSETsystem.Intersection;

import java.util.ArrayList;
import java.util.List;

public class ConvexHull {

    public static List<Intersection> convexHull(List<Intersection> intersections) {
        if (intersections.isEmpty()) return new ArrayList<>();
        intersections.sort((Intersection i, Intersection j) -> Double.compare(i.xy.getX(), j.xy.getX()));
        List<Intersection> hull = new ArrayList<>();

        // lower hull
        for (Intersection intersection : intersections) {
            while (hull.size() >= 2 && !ccw(hull.get(hull.size() - 2), hull.get(hull.size() - 1), intersection)) {
                hull.remove(hull.size() - 1);
            }
            hull.add(intersection);
        }

        // upper hull
        int t = hull.size() + 1;
        for (int i = intersections.size() - 1; i >= 0; i--) {
            Intersection intersection = intersections.get(i);
            while (hull.size() >= t && !ccw(hull.get(hull.size() - 2), hull.get(hull.size() - 1), intersection)) {
                hull.remove(hull.size() - 1);
            }
            hull.add(intersection);
        }

        hull.remove(hull.size() - 1);
        return hull;
    }

    // ccw returns true if the three points make a counter-clockwise turn
    private static boolean ccw(Intersection a, Intersection b, Intersection c) {
        return ((b.xy.getX() - a.xy.getX()) * (c.xy.getY() - a.xy.getY())) > ((b.xy.getY() - a.xy.getY()) * (c.xy.getX() - a.xy.getX()));
    }
}
