/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.jorphan.math;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This class serves as a way to calculate the median of a list of values. It is
 * not threadsafe.
 * 
 * TODO - currently only works properly for Long (because getDistribution() assumes Long)
 * Will never work for mixed values (e.g. Long and Integer) so should probably be converted to
 * typed class.
 */
public class StatCalculator {
    List values = new LinkedList();

    double sum = 0;

    double sumOfSquares = 0;

    double mean = 0;

    double deviation = 0;

    int count = 0;

    long bytes = 0;

    public void clear() {
        values.clear();
        sum = 0;
        sumOfSquares = 0;
        mean = 0;
        deviation = 0;
        count = 0;
    }

    public void addValue(long newValue) {
        Number val = new Long(newValue);
        addValue(val);
    }

    public void addValue(int newValue) {
        Number val = new Integer(newValue);
        addValue(val);
    }

    public void addValue(float newValue) {
        Number val = new Float(newValue);
        addValue(val);
    }

    public void addValue(double newValue) {
        Number val = new Double(newValue);
        addValue(val);
    }

    public void addBytes(long newValue) {
        bytes += newValue;
    }

    public void addAll(StatCalculator calc) {
        Iterator<Number> iter = calc.values.iterator();
        while (iter.hasNext()) {
            addValue(iter.next());
        }
    }

    public Number getMedian() {
        if (count > 0) {
            return (Number) values.get((int) (values.size() * .5));
        }
        return new Long(0);
    }

    public long getTotalBytes() {
        return bytes;
    }

    /**
     * Get the value which %percent% of the values are less than. This works
     * just like median (where median represents the 50% point). A typical
     * desire is to see the 90% point - the value that 90% of the data points
     * are below, the remaining 10% are above.
     *
     * @param percent
     * @return number of values less than the percentage
     */
    public Number getPercentPoint(float percent) {
        if (count > 0) {
            return (Number) values.get((int) (values.size() * percent));
        }
        return new Long(0);
    }

    /**
     * Get the value which %percent% of the values are less than. This works
     * just like median (where median represents the 50% point). A typical
     * desire is to see the 90% point - the value that 90% of the data points
     * are below, the remaining 10% are above.
     *
     * @param percent
     * @return number of values less than the percentage
     */
    public Number getPercentPoint(double percent) {
        if (count > 0) {
            return (Number) values.get((int) (values.size() * percent));
        }
        return new Long(0);
    }

    /**
     * The method has a limit of 1% as the finest granularity. We do this to
     * make sure we get a whole number for iterating.
     *
     */
    public synchronized HashMap getDistribution() {
        HashMap<Long, Number[]> items = new HashMap<Long, Number[]>();
        Iterator<Number> itr = this.values.iterator();
        Number[] dis;
        while (itr.hasNext()) {
            Number num = itr.next();
            Long nx = new Long(itr.next().longValue()); // TODO this assumes the entries are all Long
            if (items.containsKey(nx)) {
                dis = items.get(nx);
                dis[1] = new Integer(dis[1].intValue() + 1);
                items.put(nx, dis);
            } else {
                dis = new Number[2];
                dis[0] = nx;
                dis[1] = new Integer(1);
                items.put(nx, dis);
            }
        }
        return items;
    }

    public double getMean() {
        return mean;
    }

    public double getStandardDeviation() {
        return deviation;
    }

    public Number getMin() {
        if (count > 0) {
            return (Number) values.get(0);
        }
        return new Long(Long.MIN_VALUE);
    }

    public Number getMax() {
        if (count > 0) {
            return (Number) values.get(count - 1);
        }
        return new Long(Long.MAX_VALUE);
    }

    public int getCount() {
        return count;
    }

    public void addValue(Number val) {
        addSortedValue(val);
        count++;
        double currentVal = val.doubleValue();
        sum += currentVal;
        sumOfSquares += currentVal * currentVal;
        mean = sum / count;
        deviation = Math.sqrt((sumOfSquares / count) - (mean * mean));
    }

    /**
     * @param val
     */
    private void addSortedValue(Number val) {
        int index = Collections.binarySearch(values, val);
        if (index >= 0 && index < values.size()) {
            values.add(index, val);
        } else if (index == values.size() || values.size() == 0) {
            values.add(val);
        } else {
            values.add((index * (-1)) - 1, val);
        }
    }
}