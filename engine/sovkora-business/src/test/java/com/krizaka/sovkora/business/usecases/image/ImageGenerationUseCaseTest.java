package com.krizaka.sovkora.business.usecases.image;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.business.api.Capability;
import com.krizaka.sovkora.business.api.ImagePayload;
import com.krizaka.sovkora.business.api.UseCaseContext;
import com.krizaka.sovkora.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.core.domain.model.image.ImageResponse;
import com.krizaka.sovkora.core.domain.ports.inbound.AiClient;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImageGenerationUseCaseTest {

  @Test
  void descriptor_isImageCapability() {
    var useCase = new ImageGenerationUseCase(mock(AiClient.class));
    assertEquals("image.generate", useCase.descriptor().id());
    assertEquals(Capability.IMAGE, useCase.descriptor().capability());
  }

  @Test
  void execute_buildsImageRequestWithSizeSetting_andDelegatesToAiClient() {
    AiClient aiClient = mock(AiClient.class);
    ImageResponse stub = mock(ImageResponse.class);
    when(aiClient.image(any(ImageRequest.class))).thenReturn(stub);

    var useCase = new ImageGenerationUseCase(aiClient);
    var ctx = new UseCaseContext("intent-1", "actor-1", "session-1", Set.of(), Map.of());
    ImageResponse response = useCase.execute(ctx, new ImagePayload("a red fox", "1024x1024"));

    assertSame(stub, response);
    ArgumentCaptor<ImageRequest> captor = ArgumentCaptor.forClass(ImageRequest.class);
    verify(aiClient).image(captor.capture());
    assertEquals("a red fox", captor.getValue().prompt());
    assertEquals("1024x1024", captor.getValue().settings().get("size"));
    assertEquals("actor-1", captor.getValue().context().userId());
  }

  @Test
  void constructor_nullAiClient_throws() {
    assertThrows(NullPointerException.class, () -> new ImageGenerationUseCase(null));
  }
}
