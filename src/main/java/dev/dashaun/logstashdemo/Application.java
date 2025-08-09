package dev.dashaun.logstashdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
@EnableAsync

public class Application {

	public static void main(String[] args) {
		System.setProperty("logback.statusListenerClass",
				"ch.qos.logback.core.status.NopStatusListener");
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public CommandLineRunner performanceTest(LogGenerator logGenerator) {
		return args -> {
			System.out.println("Starting log performance test...");
			System.out.println("Target: 100,000 logs per second");
			System.out.println("Press Ctrl+C to stop");

			// Warm up
			System.out.println("Warming up...");
			logGenerator.warmUp();
			Thread.sleep(2000);

			// Run test
			System.out.println("Starting main test...");
			logGenerator.runTest();
		};
	}


}

@Service
class LogGenerator {
	private static final Logger logger = LoggerFactory.getLogger("dev.dashaun.logstashdemo");
	private final AtomicLong counter = new AtomicLong(0);
	private final AtomicLong totalLogged = new AtomicLong(0);
	private volatile boolean running = true;

	public void warmUp() {
		// Warm up logging pipeline
		for (int i = 0; i < 10000; i++) {
			logger.info("Warm-up log message {}", i);
		}
	}

	public void runTest() throws InterruptedException {
		int numThreads = 1;
		int logsPerThread = Integer.MAX_VALUE / 8; // Continuous logging
		CountDownLatch latch = new CountDownLatch(numThreads);

		// Start monitoring thread
		Thread monitor = new Thread(this::monitorProgress);
		monitor.start();

		// Start logging threads
		for (int t = 0; t < numThreads; t++) {
			final int threadId = t;
			Thread thread = new Thread(() -> {
				generateLogs(threadId, logsPerThread);
				latch.countDown();
			});
			thread.setName("LogGenerator-" + threadId);
			thread.start();
		}

		// Register shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			running = false;
			System.out.println("\nShutting down...");
			System.out.println("Total logs generated: " + totalLogged.get());
		}));

		// Wait indefinitely
		latch.await();
	}

	private void generateLogs(int threadId, int count) {
		// Pre-allocate message components
		String threadName = "Thread-" + threadId;

		// Use tight loop for maximum throughput
		while (running && counter.get() < count) {
			long currentCount = counter.incrementAndGet();

			// Add counter to MDC for tracking
			MDC.put("counter", String.valueOf(currentCount));

			// Log with minimal overhead
			if (currentCount % 10 == 0) {
				// Every 10th message is different to avoid too much repetition
				logger.info("Performance test - {} - Message {}: Testing high throughput logging with variable content [{}]",
						threadName, currentCount, System.nanoTime());
			} else {
				// Most messages are simple for speed
				logger.info("Perf-{}-{}", threadName, currentCount);
			}

			MDC.clear();
			totalLogged.incrementAndGet();

			// Minimal yielding to prevent CPU hogging
			if (currentCount % 1000 == 0) {
				Thread.yield();
			}
		}
	}

	private void monitorProgress() {
		long lastCount = 0;
		long lastTime = System.currentTimeMillis();

		while (running) {
			try {
				Thread.sleep(10000); // Report every 10 seconds
				long currentCount = totalLogged.get();
				long currentTime = System.currentTimeMillis();

				long logsDiff = currentCount - lastCount;
				long timeDiff = currentTime - lastTime;

				if (timeDiff > 0) {
					double logsPerSecond = (logsDiff * 1000.0) / timeDiff;
					System.out.printf("Current rate: %.0f logs/second | Total: %d logs%n",
							logsPerSecond, currentCount);
				}

				lastCount = currentCount;
				lastTime = currentTime;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}
}

// Alternative high-performance log generator using raw SLF4J
@Service
class BurstLogGenerator {
	private static final Logger logger = LoggerFactory.getLogger("dev.dashaun.logstashdemo.burst");

	@Async("logExecutor")
	public void generateBurst(int burstSize) {
		// Pre-format message for speed
		String msg = "BURST";

		// Tight loop with no allocations
		for (int i = 0; i < burstSize; i++) {
			logger.info(msg);
		}
	}

	public void runBurstTest(int totalLogs, int burstSize) throws InterruptedException {
		int numBursts = totalLogs / burstSize;
		long startTime = System.currentTimeMillis();

		for (int i = 0; i < numBursts; i++) {
			generateBurst(burstSize);

			// Small delay between bursts to prevent overwhelming
			if (i % 10 == 0) {
				Thread.sleep(1);
			}
		}

		long duration = System.currentTimeMillis() - startTime;
		double rate = (totalLogs * 1000.0) / duration;
		System.out.printf("Burst test completed: %d logs in %d ms (%.0f logs/sec)%n",
				totalLogs, duration, rate);
	}
}