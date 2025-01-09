package dev.cel.legacy.runtime.async;

// import com.google.testing.testsize.MediumTest;
import dev.cel.common.CelOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for {@code Interpreter} and related functionality via the asynchronous evaluator.
 *
 * <p>TODO: Remove inheritance from `BaseInterpreterTest` and fork these tests.
 */
// @MediumTest
@RunWith(Parameterized.class)
public class AsyncInterpreterTest extends BaseInterpreterTest {

  private static final CelOptions ASYNC_TEST_OPTIONS =
      CelOptions.current()
          .enableTimestampEpoch(true)
          .enableUnsignedLongs(true)
          .enableHeterogeneousNumericComparisons(true)
          // comprehension iteration limits are not supported in the async evaluation stacks.
          .comprehensionMaxIterations(-1)
          .build();

  public AsyncInterpreterTest(boolean declareWithCelType, Eval eval) {
    super(declareWithCelType, eval);
  }

  @Parameters
  public static List<Object[]> testData() {
    return new ArrayList<>(
        Arrays.asList(
            new Object[][] {
              // ASYNC_PROTO_TYPE
              {
                /* declareWithCelType= */ false,
                new EvalAsync(
                    TEST_FILE_DESCRIPTORS, ASYNC_TEST_OPTIONS, /* typeDirectedProcessor= */ false)
              },
              // ASYNC_PROTO_TYPE_DIRECTED_PROCESSOR
              {
                /* declareWithCelType= */ false,
                new EvalAsync(
                    TEST_FILE_DESCRIPTORS, ASYNC_TEST_OPTIONS, /* typeDirectedProcessor= */ true)
              },
              // ASYNC_CEL_TYPE
              {
                /* declareWithCelType= */ true,
                new EvalAsync(
                    TEST_FILE_DESCRIPTORS, ASYNC_TEST_OPTIONS, /* typeDirectedProcessor= */ false)
              },
              // ASYNC_CEL_TYPE_DIRECTED_PROCESSOR
              {
                /* declareWithCelType= */ true,
                new EvalAsync(
                    TEST_FILE_DESCRIPTORS, ASYNC_TEST_OPTIONS, /* typeDirectedProcessor= */ true)
              },
            }));
  }
}
