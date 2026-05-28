package com.insurance.policy.policy_service;

import com.insurance.policy.config.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.insurance.policy.Listener.RabbitMQConfig;
import com.insurance.policy.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// Smoke tests verifies the full Spring context boots without errors.
@SpringBootTest
class PolicyServiceApplicationTests extends BaseContainerTest {

	@Autowired private ApplicationContext context;
	@Autowired private PolicyService      policyService;
	@Autowired private PolicyRepository   policyRepository;
	@Autowired private JwtService jwtService;
	@Autowired private CacheManager       cacheManager;

	@Test
	void contextLoads() {
		// Passes if the Spring context starts without errors
	}

	@Test
	void policyServiceBeanShouldBePresent() {
		assertThat(context.containsBean("policyService")).isTrue();
	}

	@Test
	void jwtServiceBeanShouldBePresent() {
		assertThat(context.containsBean("jwtService")).isTrue();
	}

	@Test
	void cacheManagerShouldBeConfigured() {
		assertThat(cacheManager).isNotNull();
	}

	@Test
	void rabbitMQConstantsShouldBeNonBlank() {
		assertThat(RabbitMQConfig.QUEUE).isNotBlank();
		assertThat(RabbitMQConfig.EXCHANGE).isNotBlank();
		assertThat(RabbitMQConfig.ROUTING_KEY).isNotBlank();
		assertThat(RabbitMQConfig.DLQ).isNotBlank();
	}

	@Test
	void policyRepositoryShouldBeConnectedToDatabase() {
		assertThat(policyRepository.count()).isGreaterThanOrEqualTo(0);
	}

}
