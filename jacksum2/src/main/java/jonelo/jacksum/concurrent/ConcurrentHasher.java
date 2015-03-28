/*
 * Copyright (C) 2015 Federico Tello Gentile <federicotg@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package jonelo.jacksum.concurrent;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Sets up the concurrent execution and distributes
 * hash algorithms among Hashers based on their weight.
 *
 * @author Federico Tello Gentile <federicotg@gmail.com>
 */
public class ConcurrentHasher {

    private static final int QUEUE_CAPACITY = 1024;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    private static Hasher minWeight(List<Hasher> hashers) {
        Hasher answer = hashers.get(0);
        for (Hasher h : hashers) {
            if (h.getWeight() < answer.getWeight()) {
                answer = h;
            }
        }
        return answer;
    }

    public void updateHashes(File src, List<HashAlgorithm> hashes) {
        try {

            final int workingThreads = Math.max(1, Math.min(THREAD_COUNT, hashes.size()));

            // One queue per worker
            final List<BlockingQueue<DataUnit>> queues =
                    new ArrayList<>(workingThreads);

            /* One worker per processor */
            final List<Hasher> tasks = new ArrayList<>(workingThreads);


            // creo las colas y los workers
            for (int i = 0; i < workingThreads; i++) {
                BlockingQueue<DataUnit> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY, true);
                queues.add(queue);

                tasks.add(new Hasher(queue));
            }

            // LPT-Algorithm (Longest Processing Time)
            // http://en.wikipedia.org/wiki/Multiprocessor_scheduling
            if (THREAD_COUNT > 1) {
                Collections.sort(hashes);
            }
            for (HashAlgorithm hash : hashes) {
                minWeight(tasks).addMessageDigest(hash);
            }

            final ExecutorService pool = Executors.newFixedThreadPool(tasks.size() + 1);
            pool.submit(new DataReader(src, queues));

            List<Future<?>> futures = new ArrayList<>(tasks.size());

            for (Runnable task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<?> f : futures) {
                f.get();
            }
            pool.shutdown();

        } catch (NoSuchAlgorithmException | InterruptedException | ExecutionException ex) {
            Logger.getLogger(ConcurrentHasher.class.getName()).throwing("ConcurrentHasher", "updateHashes", ex);
        }
    }

}
