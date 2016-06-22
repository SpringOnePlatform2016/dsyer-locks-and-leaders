package com.example;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.http.HttpMethod;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.jdbc.lock.LockRepository;
import org.springframework.integration.support.locks.DefaultLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class CronServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CronServiceApplication.class, args);
	}
}

@Configuration
@Profile("!cloud")
class DefaultLockConfiguration {
	@Bean
	public DefaultLockRegistry defaultLockRegistry() {
		return new DefaultLockRegistry();
	}
}

@Configuration
@Profile("cloud")
@ComponentScan(basePackageClasses = LockRepository.class)
class JdbcLockConfiguration {

	@Bean
	public JdbcLockRegistry jdbcLockRegistry(LockRepository lockRepository) {
		return new JdbcLockRegistry(lockRepository);
	}

}

@Component
class Scheduler implements SchedulingConfigurer, Closeable {

	private ScheduledTaskRegistrar taskRegistrar;
	private ExecutorService pool;

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		this.taskRegistrar = taskRegistrar;
	}

	public void addTask(Runnable task, String expression) {
		taskRegistrar.addCronTask(task, expression);
	}

	public void start() {
		pool = Executors.newScheduledThreadPool(10);
		taskRegistrar.setScheduler(pool);
		taskRegistrar.afterPropertiesSet();
	}

	public void stop() {
		taskRegistrar.destroy();
		pool.shutdown();
	}

	@Override
	public void close() throws IOException {
		stop();
	}

}

@Component
class HookPinger implements CommandLineRunner {

	private static Logger logger = LoggerFactory.getLogger(HookPinger.class);

	private final HookRepository repository;
	private final LockRegistry locks;
	private Scheduler scheduler;
	private RestTemplate restTemplate = new RestTemplate();
	private Set<Long> hooks = new HashSet<>();

	public HookPinger(LockRegistry locks, HookRepository repository,
			Scheduler scheduler) {
		this.locks = locks;
		this.repository = repository;
		this.scheduler = scheduler;
	}

	@Override
	public void run(String... args) throws Exception {
		if (repository.count() == 0) {
			repository.save(new Hook(HttpMethod.GET, "http://localhost:8080/health",
					"*/10 * * * * *"));
		}
		for (Hook hook : repository.findAll()) {
			if (!hooks.contains(hook.getId())) {
				hooks.add(hook.getId());
				scheduler.addTask(getTask(hook.getId()), hook.getCron());
			}
		}
		scheduler.start();
	}

	private Runnable getTask(Long id) {
		return () -> {
			Hook hook = repository.findOne(id);
			long version = hook.getVersion();
			Lock lock = locks.obtain("hooks/" + hook.getId());
			try {
				if (lock.tryLock()) {
					checkVersion(hook, version);
					logger.info("Pinging: " + hook);
					restTemplate.exchange(hook.getUri(), hook.getMethod(), null,
							Map.class);
					updateVersion(hook, version);
				}
				else {
					logger.info("Missed: lock not taken");
				}
			}
			catch (Exception e) {
				// Don't care (even if it's InterruptedException, no one is blocking
				// upstream waiting for an interrupt).
				logger.info("Missed: " + e.getMessage());
			}
			finally {
				try {
					lock.unlock();
				}
				catch (Exception e) {
				}
			}
		};
	}

	private void checkVersion(Hook hook, long version) {
		Hook check = repository.findOne(hook.getId());
		if (check.getVersion() != version) {
			throw new RuntimeException("Version does not match: expected " + version
					+ " but found " + check.getVersion());
		}
	}

	private void updateVersion(Hook hook, long version) {
		hook.setVersion(version + 1);
		repository.save(hook);
	}

}

@RepositoryRestResource
interface HookRepository extends PagingAndSortingRepository<Hook, Long> {
}

@Entity
class Hook {

	@Id
	@GeneratedValue
	private Long id;

	@SuppressWarnings("unused")
	private Hook() {
	}

	public Hook(HttpMethod method, String uri, String cron) {
		this.method = method;
		this.uri = uri;
		this.cron = cron;
	}

	private String cron;

	private String uri;

	private HttpMethod method = HttpMethod.POST;

	private long version = 0L;

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public Long getId() {
		return id;
	}

	public String getCron() {
		return cron;
	}

	public void setCron(String cron) {
		this.cron = cron;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public HttpMethod getMethod() {
		return method;
	}

	public void setMethod(HttpMethod method) {
		this.method = method;
	}

	@Override
	public String toString() {
		return method + " [id=" + id + ", uri=" + uri + "]";
	}

}