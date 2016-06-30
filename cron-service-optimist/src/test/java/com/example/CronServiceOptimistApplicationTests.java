package com.example;

import static org.assertj.core.api.Assertions.assertThat;

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

	@Test
	@Transactional
	public void contextLoads() {
		Hook hook = hooks.findOne(1L);
		assertThat(hook).isNotNull();
	}

}
