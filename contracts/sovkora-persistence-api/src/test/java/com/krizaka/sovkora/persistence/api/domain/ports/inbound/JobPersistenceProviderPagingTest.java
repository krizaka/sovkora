package com.krizaka.sovkora.persistence.api.domain.ports.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.jobs.api.domain.model.PageResult;
import com.krizaka.sovkora.persistence.api.domain.model.JobDto;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** The plain-Java page methods are the Spring ones, newest first, with the paging kept. */
class JobPersistenceProviderPagingTest {

  private final List<Pageable> asked = new ArrayList<>();

  private JobPersistenceProvider provider() {
    InvocationHandler handler =
        (proxy, method, args) -> {
          if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
          }
          Pageable pageable = (Pageable) args[args.length - 1];
          asked.add(pageable);
          return new PageImpl<JobDto>(List.of(), pageable, 21);
        };
    return (JobPersistenceProvider)
        Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {JobPersistenceProvider.class}, handler);
  }

  @Test
  void aUsersPageIsAskedNewestFirstAndReturnedWithItsPaging() {
    PageResult<JobDto> page = provider().pageJobsByUserId("u1", 2, 10);

    assertThat(asked).hasSize(1);
    assertThat(asked.getFirst().getPageNumber()).isEqualTo(2);
    assertThat(asked.getFirst().getPageSize()).isEqualTo(10);
    assertThat(asked.getFirst().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    assertThat(page.number()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(10);
    assertThat(page.totalElements()).isEqualTo(21);
    assertThat(page.totalPages()).isEqualTo(3);
  }

  @Test
  void theWholePlatformPageIsAskedTheSameWay() {
    PageResult<JobDto> page = provider().pageAllJobs(0, 5);

    assertThat(asked.getFirst().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    assertThat(page.size()).isEqualTo(5);
    assertThat(page.totalPages()).isEqualTo(5);
  }
}
