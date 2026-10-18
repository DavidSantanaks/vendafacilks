package br.com.vendafacil.identity.recovery;

import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class RecoveryConfiguration {
  @Bean("recoveryExecutor")
  Executor recoveryExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("password-recovery-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(15);
    executor.setTaskDecorator(
        task -> {
          var context = MDC.getCopyOfContextMap();
          return () -> {
            if (context != null) {
              MDC.setContextMap(context);
            }
            try {
              task.run();
            } finally {
              MDC.clear();
            }
          };
        });
    return executor;
  }
}
