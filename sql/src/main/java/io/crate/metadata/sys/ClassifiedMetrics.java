/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.metadata.sys;

import io.crate.planner.operators.StatementClassifier.Classification;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class ClassifiedMetrics implements Iterable<ClassifiedMetrics.Metrics> {

    private static final long HIGHEST_TRACKABLE_VALUE = TimeUnit.MINUTES.toMillis(10);
    private static final int NUMBER_OF_SIGNIFICANT_VALUE_DIGITS = 3;

    private final ConcurrentHashMap<Classification, Metrics> metrics = new ConcurrentHashMap<>();

    public static class Metrics {

        private final Classification classification;
        private final ConcurrentHistogram histogram;
        private final LongAdder sumOfDurations = new LongAdder();
        private final LongAdder failedCount = new LongAdder();

        /**
         * @param classification
         * @param highestTrackableValue          represents the highest value to track
         * @param numberOfSignificantValueDigits the precision to use
         */
        public Metrics(Classification classification, final long highestTrackableValue, final int numberOfSignificantValueDigits) {
            this.classification = classification;
            this.histogram = new ConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        }

        public void recordValue(long duration) {
            // We use start and end time to calculate the duration (since we track them anyway)
            // If the system time is adjusted this can lead to negative durations
            // so we protect here against it.
            histogram.recordValue(Math.min(Math.max(0, duration), HIGHEST_TRACKABLE_VALUE));
            // we record the real duration (with no upper capping) in the sum of durations as there are no upper limits
            // for the values we record as it is the case with the histogram
            sumOfDurations.add(Math.max(0, duration));
        }

        public void recordFailedExecution(long duration) {
            recordValue(duration);
            failedCount.increment();
        }

        public Histogram histogram() {
            return histogram;
        }

        public Classification classification() {
            return classification;
        }

        public long sumOfDurations() {
            return sumOfDurations.longValue();
        }

        public long failedCount() {
            return failedCount.longValue();
        }
    }

    public void recordValue(Classification classification, long duration) {
        getOrCreate(classification).recordValue(duration);
    }

    public void recordFailedExecution(Classification classification, long duration) {
        getOrCreate(classification).recordFailedExecution(duration);
    }

    private Metrics getOrCreate(Classification classification) {
        Metrics histogram = metrics.get(classification);
        if (histogram == null) {
            histogram = new Metrics(classification, HIGHEST_TRACKABLE_VALUE, NUMBER_OF_SIGNIFICANT_VALUE_DIGITS);
            metrics.put(classification, histogram);
        }
        return histogram;
    }

    public void reset() {
        metrics.clear();
    }

    @Override
    public Iterator<Metrics> iterator() {
        return metrics.entrySet()
            .stream()
            .map(Map.Entry::getValue)
            .iterator();
    }
}
