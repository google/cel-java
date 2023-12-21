package dev.cel.optimizer;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.navigation.MutableExpr;
import dev.cel.common.navigation.MutableExpr.MutableConstant;
import dev.cel.common.navigation.MutableExpr.MutableCreateList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class MutableExprTest {

  @Test
  public void constExpr() throws Exception {
    MutableConstant constant = MutableConstant.ofValue(1L);
    MutableExpr mutableConstExpr = MutableExpr.ofConstant(constant);
    MutableExpr mutableListExpr = MutableExpr.ofCreateList(MutableCreateList.create(Arrays.asList(mutableConstExpr)));

    constant.setInt64Value(2L);

    assertThat(mutableListExpr.createList().elements().get(0).constant().int64Value()).isEqualTo(2L);
  }
}
