/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.DefaultCandidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;
import org.springframework.integration.support.locks.DefaultLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;

/**
 * @author Dave Syer
 *
 */
public class LockRegistryLeaderInitiatorTests {

	private CountDownLatch granted = new CountDownLatch(1);
	private CountDownLatch revoked = new CountDownLatch(1);
	private LockRegistry registry = new DefaultLockRegistry();
	private CountingPublisher publisher = new CountingPublisher(granted, revoked);

	private LockRegistryLeaderInitiator initiator = new LockRegistryLeaderInitiator(
			registry, new DefaultCandidate());

	@Before
	public void init() {
		initiator.setLeaderEventPublisher(publisher);
	}

	@Test
	public void startAndStop() throws Exception {
		assertThat(initiator.getContext().isLeader()).isFalse();
		initiator.start();
		assertThat(initiator.isRunning()).isTrue();
		granted.await(2, TimeUnit.SECONDS);
		assertThat(initiator.getContext().isLeader()).isTrue();
		Thread.sleep(200L);
		assertThat(initiator.getContext().isLeader()).isTrue();
		initiator.stop();
		revoked.await(2, TimeUnit.SECONDS);
		assertThat(initiator.getContext().isLeader()).isFalse();
	}

	@Test
	public void yield() throws Exception {
		assertThat(initiator.getContext().isLeader()).isFalse();
		initiator.start();
		assertThat(initiator.isRunning()).isTrue();
		granted.await(2, TimeUnit.SECONDS);
		assertThat(initiator.getContext().isLeader()).isTrue();
		initiator.getContext().yield();
		assertThat(revoked.await(2, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	public void competing() throws Exception {
		LockRegistryLeaderInitiator another = new LockRegistryLeaderInitiator(registry,
				new DefaultCandidate());
		CountDownLatch other = new CountDownLatch(1);
		another.setLeaderEventPublisher(new CountingPublisher(other));
		initiator.start();
		assertThat(granted.await(2, TimeUnit.SECONDS)).isTrue();
		another.start();
		initiator.stop();
		assertThat(other.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(another.getContext().isLeader());
	}

	private static class CountingPublisher implements LeaderEventPublisher {
		private CountDownLatch granted;
		private CountDownLatch revoked;

		public CountingPublisher(CountDownLatch granted, CountDownLatch revoked) {
			this.granted = granted;
			this.revoked = revoked;
		}

		public CountingPublisher(CountDownLatch granted) {
			this(granted, new CountDownLatch(1));
		}

		@Override
		public void publishOnRevoked(Object source, Context context, String role) {
			revoked.countDown();
		}

		@Override
		public void publishOnGranted(Object source, Context context, String role) {
			granted.countDown();
		}
	}

}
