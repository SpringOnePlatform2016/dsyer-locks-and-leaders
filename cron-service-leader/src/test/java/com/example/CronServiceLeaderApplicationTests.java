package com.example;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CronServiceLeaderApplicationTests {

	@Test
	public void contextLoads() {
		// Simple lock API usage
		Lock lock = new ReentrantLock();
		boolean acquired = false;
		try {
			acquired = lock.tryLock(10, TimeUnit.SECONDS);
			if (acquired) {
				// Do something unique!
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted");
		}
		finally {
			if (acquired) {
				lock.unlock();
			}
		}
	}

}
