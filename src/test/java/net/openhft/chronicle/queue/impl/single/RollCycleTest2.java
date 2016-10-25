package net.openhft.chronicle.queue.impl.single;

import net.openhft.chronicle.bytes.StopCharTesters;
import net.openhft.chronicle.core.time.SystemTimeProvider;
import net.openhft.chronicle.core.time.TimeProvider;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.StoreFileListener;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.Wires;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.openhft.chronicle.queue.RollCycles.DAILY;
import static net.openhft.chronicle.wire.WireType.FIELDLESS_BINARY;
import static org.junit.Assert.assertEquals;

public class RollCycleTest2 {

    Path path;

    @Before
    public void setUp() throws Exception {
        path = Files.createTempDirectory("rollCycleTest");
    }

    @After
    public void tearDown() throws IOException {
        Files.walk(path)
                .collect(Collectors.toCollection(LinkedList::new))
                .descendingIterator()
                .forEachRemaining(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                    }
                });
    }

    class TestTimeProvider implements TimeProvider {


        private long addInMs;

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis() + addInMs;
        }

        public void add(long addInMs) {
            this.addInMs = addInMs;
        }

    }

    class ParallelQueueObserver implements Callable, StoreFileListener {
        SingleChronicleQueue queue;

        volatile int documentsRead;
        private final ExcerptTailer tailer;

        public ParallelQueueObserver(ChronicleQueue queue) {

            documentsRead = 0;
            tailer = queue.createTailer();

        }


        public int documentsRead() {
            return documentsRead;
        }

        @Override
        public void onAcquired(int cycle, File file) {
            System.out.println("Acquiring " + file);
        }

        @Override
        public void onReleased(int cycle, File file) {
            System.out.println("Releasing " + file);
        }

        @Override
        public Integer call() throws Exception {
            System.out.println("run");
            try (final DocumentContext dc = tailer.readingDocument()) {

                System.out.println("index=" + Long.toHexString(dc.index()));
                if (!dc.isPresent())
                    return documentsRead;

                StringBuilder sb = Wires.acquireStringBuilder();
                dc.wire().bytes().parse8bit(sb, StopCharTesters.ALL);

                String readText = sb.toString();
                if (java.util.Objects.equals(sb, "")) {
                    return null;
                }
                System.out.println("Read a document " + readText);
                documentsRead++;

            }
            return documentsRead;
        }
    }

    @Test
    public void testRead2() throws Exception {
        TestTimeProvider timeProvider = new TestTimeProvider();

        final SystemTimeProvider instance = SystemTimeProvider.INSTANCE;


        ChronicleQueue queue0 = SingleChronicleQueueBuilder
                .binary(path)
                .rollCycle(DAILY)
                .timeProvider(timeProvider)
                .wireType(FIELDLESS_BINARY).build();


        ParallelQueueObserver observer = new ParallelQueueObserver(queue0);

        final ExecutorService scheduledExecutorService = Executors
                .newSingleThreadScheduledExecutor();

        try (SingleChronicleQueue queue = SingleChronicleQueueBuilder.binary(path).rollCycle(DAILY).timeProvider(timeProvider).wireType(
                FIELDLESS_BINARY).build()) {

            ExcerptAppender appender = queue.acquireAppender();

            try (final DocumentContext dc = appender.writingDocument()) {
                dc.wire().write().text("Day 1 data");
            }

            //    Assert.assertEquals(1, (int) observer.call());
            Assert.assertEquals(1, (int) scheduledExecutorService.submit(observer).get());
            //
            // two days pass
             timeProvider.add(TimeUnit.DAYS.toMillis(2));
            //     scheduledExecutorService.submit(observer::call).get();
            try (final DocumentContext dc = appender.writingDocument()) {
                dc.wire().write().text("Day 3 data");
            }
            Assert.assertEquals(2, (int) scheduledExecutorService.submit(observer).get());


            System.out.println(queue.dump());
            assertEquals(2, observer.documentsRead);

        }


    }

    @Test
    public void testRead1() throws Exception {
        TestTimeProvider timeProvider = new TestTimeProvider();

        ChronicleQueue queue0 = SingleChronicleQueueBuilder.binary(path)
                .rollCycle(DAILY).timeProvider(timeProvider).wireType(FIELDLESS_BINARY).build();

        ParallelQueueObserver observer = new ParallelQueueObserver(queue0);

        final ExecutorService scheduledExecutorService = Executors
                .newSingleThreadScheduledExecutor();

        try (SingleChronicleQueue queue = SingleChronicleQueueBuilder.binary(path)
                .rollCycle(DAILY).timeProvider(timeProvider).wireType(FIELDLESS_BINARY).build()) {
            ExcerptAppender appender = queue.acquireAppender();

            Assert.assertEquals(0, (int) scheduledExecutorService.submit(observer::call).get());
            // two days pass
            timeProvider.add(TimeUnit.DAYS.toMillis(2));
            //     scheduledExecutorService.submit(observer::call).get();
            try (final DocumentContext dc = appender.writingDocument()) {
                dc.wire().write().text("Day 3 data");
            }
            Assert.assertEquals(1, (int) scheduledExecutorService.submit(observer::call).get());

            System.out.println(queue.dump());
            assertEquals(1, observer.documentsRead);

        }


    }
}