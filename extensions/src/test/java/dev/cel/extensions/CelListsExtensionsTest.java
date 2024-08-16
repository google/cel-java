package dev.cel.extensions;
import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelListsExtensionsTest {
  private static final Cel CEL =
      CelFactory.standardCelBuilder()
          .addCompilerLibraries(CelExtensions.lists())
          .addRuntimeLibraries(CelExtensions.lists())
          .build();

  @Test
  @TestParameters("{expression: '[].flatten() == []'}")
  @TestParameters("{expression: '[[], [[]], [[[]]]].flatten() == [[], [[]]]'}")
  @TestParameters("{expression: '[1,2,3,4].flatten() == [1,2,3,4]'}")
  @TestParameters("{expression: '[1,[2,[3,4]]].flatten() == [1,2,[3,4]]'}")
  @TestParameters("{expression: '[1,2,[],[],[3,4]].flatten() == [1,2,3,4]'}")
  @TestParameters("{expression: '[1,[2,3],[[4,5]], [[[6,7]]]].flatten() == [1,2,3,[4,5],[[6,7]]]'}")
  public void flatten_singleLevel_success(String expression) throws Exception {
     boolean result = (boolean) CEL.createProgram(CEL.compile(expression).getAst()).eval();

     assertThat(result).isTrue();
  }
}
