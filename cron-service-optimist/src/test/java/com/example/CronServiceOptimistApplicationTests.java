package com.example;

import javax.transaction.Transactional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CronServiceOptimistApplicationTests {
	
	@Autowired
	private HookRepository hooks;

	@Autowired
	private ExecutionRepository executions;

	@Test
	@Transactional
	public void contextLoads() {
		Hook hook = hooks.findOne(1L);
		Execution execution = hook.execute();
		executions.save(execution);
	}

}
