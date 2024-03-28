import org.pkl.config.java.Config;
import org.pkl.config.java.ConfigEvaluator;
import org.pkl.config.java.JavaType;
import org.pkl.core.ModuleSource;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
// the pkl/pkl-examples repo has a similar example
public class JavaConfigExample {
  @Test
  public void usage() {
    // tag::usage[]
    Config config;
    try (var evaluator = ConfigEvaluator.preconfigured()) { // <1>
      config = evaluator.evaluate(
        ModuleSource.text("pigeon { age = 5; diet = new Listing { \"Seeds\" } }")); // <2>
    }
    var pigeon = config.get("pigeon"); // <3>
    var age = pigeon.get("age").as(int.class); // <4>
    var diet = pigeon.get("diet").as(JavaType.listOf(String.class)); // <5>
    // end::usage[]
  }
}
