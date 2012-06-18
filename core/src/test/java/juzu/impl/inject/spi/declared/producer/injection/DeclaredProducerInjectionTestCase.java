package juzu.impl.inject.spi.declared.producer.injection;

import juzu.impl.inject.spi.AbstractInjectManagerTestCase;
import juzu.impl.inject.spi.InjectImplementation;
import org.junit.Test;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public class DeclaredProducerInjectionTestCase<B, I> extends AbstractInjectManagerTestCase<B, I> {

  public DeclaredProducerInjectionTestCase(InjectImplementation di) {
    super(di);
  }

  @Test
  public void test() throws Exception {
    init();
    bootstrap.declareProvider(Bean.class, null, null, BeanProducer.class);
    bootstrap.declareBean(Injected.class, null, null, null);
    boot();

    //
    Injected injected = getBean(Injected.class);
    assertNotNull(injected);
    assertNotNull(injected.dependency);
  }
}