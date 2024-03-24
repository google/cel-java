package dev.cel.optimizer;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class MutableExprTest {

  @Test
  public void constExpr() throws Exception {
//    CelConstant constant = CelConstant.ofValue(1L);
//    MutableExpr mutableConstExpr = MutableExpr.ofConstant(1, constant);
//    MutableExpr mutableListExpr = MutableExpr.ofCreateList(2, MutableCreateList.create(Arrays.asList(mutableConstExpr)));
//
//    constant.setInt64Value(2L);
//
//    assertThat(mutableListExpr.createList().elements().get(0).constant().int64Value()).isEqualTo(2L);
  }
}
