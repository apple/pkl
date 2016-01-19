import org.pkl.core.Evaluator;
import org.pkl.core.ModuleSource;
import java.util.List;

import org.pkl.core.PModule;
import org.pkl.core.PObject;
import org.junit.jupiter.api.Test;

// the pkl/pkl-examples repo has a similar example
@SuppressWarnings({"unchecked", "unused", "ConstantConditions"})
public class CoreEvaluatorExample {
  @Test
  public void usage() {
    // tag::usage[]
    PModule module;
    try (var evaluator =
      Evaluator.preconfigured()) { // <1>
      module = evaluator.evaluate(
        ModuleSource.text("pigeon { age = 30; hobbies = List(\"swimming\", \"surfing\") }")); // <2>
    }
    var pigeon = (PObject) module.get("pigeon"); // <3>
    var className = pigeon.getClassInfo().getQualifiedName(); // <4>
    var hobbies = (List<String>) pigeon.get("hobbies"); // <5>
    // end::usage[]
  }
}
