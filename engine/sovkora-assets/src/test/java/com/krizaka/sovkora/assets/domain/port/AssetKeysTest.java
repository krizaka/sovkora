package com.krizaka.sovkora.assets.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AssetKeysTest {

  @Test
  void followsTheTenantUserJobLayout() {
    assertThat(AssetKeys.jobFile("default", "2561abd5", "a3c88e54", "image.png"))
        .isEqualTo("t/default/u/2561abd5/jobs/a3c88e54/image.png");
    assertThat(AssetKeys.jobPrefix("default", "u1", "j1")).isEqualTo("t/default/u/u1/jobs/j1/");
  }

  @Test
  void refusesAnySegmentThatCouldClimbOrNest() {
    for (String bad : new String[] {"..", "a/b", "", " ", "a\\b", "x..y", null, "/abs"}) {
      assertThatThrownBy(() -> AssetKeys.jobFile("t", "u", "j", bad))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> AssetKeys.jobFile("t", bad, "j", "f"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void anUploadLivesUnderTheOwnersPseudoJob_soDeletingTheUserDeletesIt() {
    assertThat(AssetKeys.uploadFile("default", "u1", "0f1e.png"))
        .isEqualTo("t/default/u/u1/jobs/uploads/0f1e.png");
    assertThat(AssetKeys.uploadPrefix("default", "u1")).isEqualTo("t/default/u/u1/jobs/uploads/");
    assertThat(AssetKeys.userPrefix("default", "u1")).isEqualTo("t/default/u/u1/");
    assertThat(AssetKeys.tenantUsersPrefix("default")).isEqualTo("t/default/u/");
    assertThatThrownBy(() -> AssetKeys.uploadFile("t", "u", "../x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
