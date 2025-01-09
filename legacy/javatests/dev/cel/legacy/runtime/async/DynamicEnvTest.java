package dev.cel.legacy.runtime.async;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DynamicEnvTest {
  @Test
  public void withGlobalContext_preservesStack() throws Exception {
    DynamicEnv base =
        new DynamicEnv(
            GlobalContext.of(
                DummyAsyncContext.INSTANCE,
                name -> () -> immediateFuture("original resolver: " + name)),
            ImmutableList.of(immediateFuture("local1"), immediateFuture("local2")),
            ImmutableList.of("global1", "global2"));
    DynamicEnv extended = base.extend(FluentFuture.from(immediateFuture("local3")));
    DynamicEnv cloned =
        extended.withGlobalContext(
            GlobalContext.of(
                DummyAsyncContext.INSTANCE,
                name -> () -> immediateFuture("replaced resolver: " + name)));

    // base has original global bindings
    assertThat(base.getGlobal(0).get()).isEqualTo("original resolver: global1");
    assertThat(base.getGlobal(1).get()).isEqualTo("original resolver: global2");

    // extended also has original global bidings
    assertThat(extended.getGlobal(0).get()).isEqualTo("original resolver: global1");
    assertThat(extended.getGlobal(1).get()).isEqualTo("original resolver: global2");

    // cloned has replaced global bindings
    assertThat(cloned.getGlobal(0).get()).isEqualTo("replaced resolver: global1");
    assertThat(cloned.getGlobal(1).get()).isEqualTo("replaced resolver: global2");

    // base has two locals (slot offsets work in opposite direction!)
    assertThat(base.getLocalAtSlotOffset(1).get()).isEqualTo("local2");
    assertThat(base.getLocalAtSlotOffset(2).get()).isEqualTo("local1");

    // extended has one more local, original bindings are shifted
    assertThat(extended.getLocalAtSlotOffset(1).get()).isEqualTo("local3");
    assertThat(extended.getLocalAtSlotOffset(2).get()).isEqualTo("local2");
    assertThat(extended.getLocalAtSlotOffset(3).get()).isEqualTo("local1");

    // cloned as same locals as extended (from which it was created)
    assertThat(cloned.getLocalAtSlotOffset(1).get()).isEqualTo("local3");
    assertThat(cloned.getLocalAtSlotOffset(2).get()).isEqualTo("local2");
    assertThat(cloned.getLocalAtSlotOffset(3).get()).isEqualTo("local1");
  }
}
