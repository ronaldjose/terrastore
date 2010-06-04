/**
 * Copyright 2009 - 2010 Sergio Bossa (sergio.bossa@gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package terrastore.util.collect;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import jsr166y.ForkJoinPool;
import jsr166y.RecursiveAction;

/**
 * @author Sergio Bossa
 */
public class ParallelUtils {

    // TODO: make this configurable?
    private final static ForkJoinPool POOL = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    //

    public static <E extends Comparable> Set<E> parallelMerge(List<Set<E>> sets) {
        ParallelMergeTask task = new ParallelMergeTask<E>(sets);
        POOL.execute(task);
        task.join();
        return task.getMerged();
    }

    private static class ParallelMergeTask<E extends Comparable> extends RecursiveAction {

        private final List<Set<E>> sets;
        private Set<E> merged;

        public ParallelMergeTask(List<Set<E>> sets) {
            this.sets = sets;
        }

        @Override
        protected void compute() {
            if (sets.size() == 0) {
                merged = Collections.emptySet();
            } else if (sets.size() == 1) {
                merged = sets.get(0);
            } else if (sets.size() == 2) {
                Set<E> first = sets.get(0);
                Set<E> second = sets.get(1);
                merged = new MergeSet<E>(first, second);
            } else {
                int middle = sets.size() / 2;
                ParallelMergeTask t1 = new ParallelMergeTask(sets.subList(0, middle));
                ParallelMergeTask t2 = new ParallelMergeTask(sets.subList(middle, sets.size()));
                t1.fork();
                t2.fork();
                t1.join();
                t2.join();
                merged = new MergeSet<E>(t1.merged, t2.merged);
            }
        }

        public Set<E> getMerged() {
            return merged;
        }
    }
}
