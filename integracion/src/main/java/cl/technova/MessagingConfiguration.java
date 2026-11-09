package cl.technova;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
final class MessagingConfiguration {
 static String env(String n,String d){return System.getenv().getOrDefault(n,d);}
 static ActiveMQConnectionFactory factory(){String url=env("BROKER_URL","tcp://localhost:61616");String user=System.getenv("BROKER_USER"),password=System.getenv("BROKER_PASSWORD");if((user==null)!=(password==null))throw new IllegalArgumentException("BROKER_USER and BROKER_PASSWORD must be provided together");return user==null?new ActiveMQConnectionFactory(url):new ActiveMQConnectionFactory(url,user,password);}

}
