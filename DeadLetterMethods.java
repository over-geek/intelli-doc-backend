import com.azure.messaging.servicebus.models.DeadLetterOptions;
public class DeadLetterMethods {
  public static void main(String[] args) {
    for (var m : DeadLetterOptions.class.getMethods()) {
      if (m.getDeclaringClass().equals(DeadLetterOptions.class)) {
        System.out.println(m.toString());
      }
    }
  }
}
