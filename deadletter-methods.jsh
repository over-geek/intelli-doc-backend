import com.azure.messaging.servicebus.models.DeadLetterOptions;
for (var m : DeadLetterOptions.class.getMethods()) {
  if (m.getDeclaringClass().equals(DeadLetterOptions.class)) {
    System.out.println(m.toString());
  }
}
/exit
