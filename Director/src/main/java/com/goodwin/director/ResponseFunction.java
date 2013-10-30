package com.goodwin.director;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by Jo on 7/10/13.
 */

/* The class ResponseFunction inputs 2 lists of values, one the driving signal and the other
 * the system response signal. The lists are assumed to be time-correlated. ResponseFunction
  * determines the index difference between the maximum of the response and the driver, which
  * can be converted to time. This really only works for a delta input.
 *
 */
public class ResponseFunction {
    public int getResponseFunction(float[] sentChirp, float[] orientationResponse) {
        /* Finds difference in index between response and driver. Assumes response is behind driver
         * which is acceptable for this situation. Probably need to attach timing to both lists
         * and possibly interpolate. However, the response function could acceptably measure
         * total system delay, including that of the driving method e.g. onOrientationChanged.
         * However, probably need to include a specific system response method which gets the UAV
         * flying smoothly them inserts a jerk and measures.
         */
        return indexOfMaxDiff(orientationResponse) - indexOfMaxDiff(sentChirp);
    }

    private int indexOfMaxDiff(float[] list) {
        List diffAsList = Arrays.asList(differences(list));
        return diffAsList.indexOf(Collections.max(diffAsList));
    }

    public float[] differences(float[] list) {
        float[] diffList = new float[list.length-1];
        for (int i = 0; i < list.length-1; i++) {
            diffList[i] = list[i+1] - list[i];
        }
        return diffList;
    }

    public double[] differences(double[] list) {
        double[] diffList = new double[list.length-1];
        for (int i = 0; i < list.length-1; i++) {
            diffList[i] = list[i+1] - list[i];
        }
        return diffList;
    }

}
