package com.krizaka.sovkora.assets.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.assets.domain.port.DurableAssetStore;
import com.krizaka.sovkora.assets.domain.port.StoredObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What every adapter of {@link DurableAssetStore} must do; each adapter's test extends this. */
abstract class DurableAssetStoreContract {

  @TempDir Path work;

  abstract DurableAssetStore store();

  @Test
  void aStoredFileComesBackByteForByte() throws IOException {
    Path source = file("a.bin", "sealed-envelope-bytes");
    store().put("t/x/u/1/jobs/j1/image.png", source);

    Path target = work.resolve("restored/image.png");
    assertThat(store().fetch("t/x/u/1/jobs/j1/image.png", target)).isTrue();
    assertThat(Files.readString(target)).isEqualTo("sealed-envelope-bytes");
  }

  @Test
  void anAbsentObjectIsNotAnError() throws IOException {
    Path target = work.resolve("none.png");
    assertThat(store().fetch("t/x/u/1/jobs/missing/image.png", target)).isFalse();
    assertThat(target).doesNotExist();
  }

  @Test
  void putReplacesThePreviousCopy() throws IOException {
    store().put("t/x/u/1/jobs/j2/f", file("a", "one"));
    store().put("t/x/u/1/jobs/j2/f", file("b", "two"));
    Path target = work.resolve("f");
    assertThat(store().fetch("t/x/u/1/jobs/j2/f", target)).isTrue();
    assertThat(Files.readString(target)).isEqualTo("two");
  }

  @Test
  void deletingAnObjectRemovesIt_andDeletingTwiceIsFine() throws IOException {
    store().put("t/x/u/1/jobs/j3/f", file("a", "x"));
    store().delete("t/x/u/1/jobs/j3/f");
    store().delete("t/x/u/1/jobs/j3/f");
    assertThat(store().fetch("t/x/u/1/jobs/j3/f", work.resolve("g"))).isFalse();
  }

  @Test
  void deletingAPrefixRemovesEverythingUnderIt_andNothingElse() throws IOException {
    store().put("t/x/u/1/jobs/j4/a", file("a", "a"));
    store().put("t/x/u/1/jobs/j4/b", file("b", "b"));
    store().put("t/x/u/1/jobs/j5/a", file("c", "c"));
    store().delete("t/x/u/1/jobs/j4/");
    assertThat(store().fetch("t/x/u/1/jobs/j4/a", work.resolve("o1"))).isFalse();
    assertThat(store().fetch("t/x/u/1/jobs/j4/b", work.resolve("o2"))).isFalse();
    assertThat(store().fetch("t/x/u/1/jobs/j5/a", work.resolve("o3"))).isTrue();
  }

  @Test
  void listingAPrefixNamesItsDirectChildren_filesWithADate_directoriesWithASlash()
      throws IOException {
    store().put("t/x/u/1/jobs/uploads/aaa.png", file("a", "a"));
    store().put("t/x/u/1/jobs/uploads/bbb.mp4", file("b", "b"));
    store().put("t/x/u/1/jobs/j9/deep/file", file("c", "c"));
    store().put("t/x/u/2/jobs/uploads/other.png", file("d", "d"));

    List<StoredObject> uploads = store().list("t/x/u/1/jobs/uploads/");
    assertThat(uploads)
        .extracting(StoredObject::key)
        .containsExactlyInAnyOrder("t/x/u/1/jobs/uploads/aaa.png", "t/x/u/1/jobs/uploads/bbb.mp4");
    assertThat(uploads).allSatisfy(o -> assertThat(o.lastModified()).isNotNull());

    assertThat(store().list("t/x/u/"))
        .extracting(StoredObject::key)
        .containsExactlyInAnyOrder("t/x/u/1/", "t/x/u/2/");
  }

  @Test
  void listingAnAbsentPrefixIsEmpty() throws IOException {
    assertThat(store().list("t/x/u/nobody/jobs/uploads/")).isEmpty();
  }

  @Test
  void isEnabled() {
    assertThat(store().enabled()).isTrue();
  }

  Path file(String name, String content) throws IOException {
    Path path = work.resolve("src-" + name);
    Files.writeString(path, content);
    return path;
  }
}
