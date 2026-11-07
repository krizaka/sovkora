package com.krizaka.sovkora.adapter.springai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class ClasspathPromptTextSourceTest {

  private final ResourceLoader loader = mock(ResourceLoader.class);
  private final Resource resource = mock(Resource.class);
  private final ClasspathPromptTextSource source = new ClasspathPromptTextSource(loader);

  @Test
  void readsTheUtf8TextOfAnExistingResource() throws IOException {
    when(loader.getResource("classpath:prompts/a.md")).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream("Bonjour é".getBytes(StandardCharsets.UTF_8)));

    assertThat(source.read("classpath:prompts/a.md")).hasValue("Bonjour é");
  }

  @Test
  void aMissingResourceIsEmpty() {
    when(loader.getResource("classpath:prompts/none.md")).thenReturn(resource);
    when(resource.exists()).thenReturn(false);

    assertThat(source.read("classpath:prompts/none.md")).isEmpty();
  }

  @Test
  void anUnreadableResourceThrowsUnchecked() throws IOException {
    when(loader.getResource("classpath:prompts/b.md")).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream()).thenThrow(new IOException("boom"));

    assertThatThrownBy(() -> source.read("classpath:prompts/b.md"))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void rejectsANullLoader() {
    assertThatThrownBy(() -> new ClasspathPromptTextSource(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
