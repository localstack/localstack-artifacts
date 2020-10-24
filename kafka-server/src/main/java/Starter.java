import no.nav.common.KafkaEnvironment;
import no.nav.common.embeddedutils.ArgsHolder;

public class Starter {
		public static void main(String[] args) throws Exception {
				ArgsHolder.args = args;

				KafkaEnvironment kafkaEnv = new KafkaEnvironment();
				kafkaEnv.start();
				System.out.println("kafka: " + kafkaEnv.getBrokersURL());
				System.out.println("zookeeper: " + kafkaEnv.getZookeeper().getHost() + ":" + kafkaEnv.getZookeeper().getPort());
		}
}
