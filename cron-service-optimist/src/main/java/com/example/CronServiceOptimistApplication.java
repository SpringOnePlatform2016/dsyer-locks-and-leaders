package com.example;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Version;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.Hook.State;

@SpringBootApplication
public class CronServiceOptimistApplication {

	public static void main(String[] args) {
		SpringApplication.run(CronServiceOptimistApplication.class, args);
	}
}

@Component
class Scheduler implements SchedulingConfigurer, Closeable {

	private volatile ScheduledTaskRegistrar taskRegistrar;
	private volatile ExecutorService pool;
	private volatile boolean running = false;

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		this.taskRegistrar = taskRegistrar;
	}

	public void addTask(Runnable task, String expression) {
		if (running) {
			taskRegistrar.scheduleCronTask(new CronTask(task, expression));
		}
		else {
			taskRegistrar.addCronTask(new CronTask(task, expression));
		}
	}

	public void start() {
		running = true;
		if (taskRegistrar != null) {
			pool = Executors.newScheduledThreadPool(10);
			taskRegistrar.setScheduler(pool);
			taskRegistrar.afterPropertiesSet();
		}
	}

	public void stop() {
		if (taskRegistrar != null) {
			taskRegistrar.destroy();
			pool.shutdown();
			pool = null;
		}
		running = false;
	}

	@Override
	public void close() throws IOException {
		stop();
	}

}

@Component
class HookPinger implements CommandLineRunner {

	private static Logger logger = LoggerFactory.getLogger(HookPinger.class);

	private final HookService service;
	private final HookRepository hooks;
	private Scheduler scheduler;
	private RestTemplate restTemplate = new RestTemplate();
	private Set<Long> cache = new HashSet<>();

	public HookPinger(HookService service, HookRepository repository,
			Scheduler scheduler) {
		this.service = service;
		this.hooks = repository;
		this.scheduler = scheduler;
	}

	@Override
	public void run(String... args) throws Exception {
		if (hooks.count() == 0) {
			hooks.save(new Hook(HttpMethod.GET, "http://localhost:8080/health",
					"*/10 * * * * *"));
		}
		for (Hook hook : hooks.findAll()) {
			if (!cache.contains(hook.getId())) {
				cache.add(hook.getId());
				scheduler.addTask(getTask(hook.getId()), hook.getCron());
			}
		}
		scheduler.start();
	}

	private Runnable getTask(Long id) {
		return () -> {
			Hook hook = null;
			try {
				hook = service.start(id);
				logger.info("Pinging: " + hook);
				restTemplate.exchange(hook.getUri(), hook.getMethod(), null, Map.class);
				hook.setState(State.COMPLETE);
			}
			catch (AlreadyRunningException e) {
				logger.info(e.getMessage());
			}
			catch (Exception e) {
				// Don't care
				logger.info("Missed: " + e.getMessage());
				if (hook != null) {
					hook.setState(State.FAILED);
				}
			}
			finally {
				try {
					service.update(hook, id);
				}
				catch (Exception e) {
					// Don't care
				}
			}
		};
	}

}

@SuppressWarnings("serial")
class AlreadyRunningException extends RuntimeException {

	public AlreadyRunningException(String message) {
		super(message);
	}

}

@Service
@Transactional
class HookService {

	private final HookRepository hooks;

	public HookService(HookRepository hooks) {
		this.hooks = hooks;
	}

	public Hook update(Hook hook, Long id) {
		if (hook != null) {
			return hooks.save(hook);
		} else {
			hook = hooks.findOne(id);
			hook.setState(State.FAILED);
			hooks.save(hook);
		}
		return null;
	}

	public Hook start(Long id) {
		Hook hook = hooks.findOne(id);
		if (hook.getState() == State.RUNNING) {
			throw new RuntimeException("Already running");
		}
		hook.setState(State.RUNNING);
		return hooks.save(hook);
	}

}

@RepositoryRestResource
interface HookRepository extends PagingAndSortingRepository<Hook, Long> {
}

@Entity
class Hook {

	enum State {
		COMPLETE, RUNNING, FAILED;
	}

	private State state = State.COMPLETE;

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

	@Version
	private long version = 0L;

	public long getVersion() {
		return version;
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

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	@Override
	public String toString() {
		return method + " [id=" + id + ", uri=" + uri + ", state=" + state + "]";
	}

}