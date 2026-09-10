import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.zookeeper.*;

/** Official fixture client only. Never shares classpath with legacy Kettle. */
class KettleKafkaFixtureProbe {
    static final String BROKER = "127.0.0.1:29092";
    static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    static void named(String value) {
        if (!value.matches("kettle-v2-[a-z0-9-]+")) throw new IllegalArgumentException("Synthetic names only");
    }
    static Properties common() {
        Properties p = new Properties(); p.put("bootstrap.servers", BROKER); p.put("request.timeout.ms", "5000");
        p.put("default.api.timeout.ms", "10000"); return p;
    }
    static Map<String,Object> audit(String group, String topic) throws Exception {
        named(group); named(topic); Map<String,Object> result = new LinkedHashMap<>();
        try (Admin admin = Admin.create(common())) {
            var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            Map<String,Long> flat = new TreeMap<>(); offsets.forEach((k,v)->flat.put(k.toString(),v.offset()));
            result.put("brokerOffsets", flat);
        }
        CountDownLatch connected = new CountDownLatch(1);
        try (ZooKeeper zk = new ZooKeeper("127.0.0.1:22181", 5000, e->{if(e.getState()==Watcher.Event.KeeperState.SyncConnected)connected.countDown();})) {
            if (!connected.await(8,TimeUnit.SECONDS)) throw new IllegalStateException("ZooKeeper connection timeout");
            String path = "/consumers/"+group+"/offsets/"+topic;
            Map<String,String> offsets = new TreeMap<>();
            if (zk.exists(path,false)!=null) for(String partition:zk.getChildren(path,false)) {
                offsets.put(partition,new String(zk.getData(path+"/"+partition,false,null),StandardCharsets.UTF_8));
            }
            result.put("zookeeperOffsets",offsets);
            result.put("zookeeperConsumerOwners",zk.exists("/consumers/"+group+"/owners/"+topic,false)==null ?
                List.of() : zk.getChildren("/consumers/"+group+"/owners/"+topic,false));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        Object result;
        switch(args[0]) {
            case "health":
                try(Admin admin=Admin.create(common())) {
                    var cluster=admin.describeCluster(); var nodes=cluster.nodes().get(10,TimeUnit.SECONDS);
                    if(nodes.size()!=1 || !nodes.iterator().next().host().equals("127.0.0.1") || nodes.iterator().next().port()!=29092)
                        throw new IllegalStateException("Unexpected broker endpoint");
                    result=Map.of("healthy",true,"clusterId",cluster.clusterId().get(10,TimeUnit.SECONDS),"broker",BROKER);
                } break;
            case "seed": {
                String prefix=args[1];named(prefix);int count=Integer.parseInt(args[2]);if(count<1||count>100)throw new IllegalArgumentException();
                try(Admin admin=Admin.create(common())) {
                    admin.createTopics(List.of(new NewTopic(prefix+"-input",1,(short)1),new NewTopic(prefix+"-output",1,(short)1)))
                         .all().get(10,TimeUnit.SECONDS);
                }
                Properties p=common();p.put("key.serializer",StringSerializer.class.getName());p.put("value.serializer",StringSerializer.class.getName());
                p.put("acks","all");p.put("compression.type","none");List<String> records=new ArrayList<>();
                try(KafkaProducer<String,String> producer=new KafkaProducer<>(p)) {
                    for(int i=0;i<count;i++) {
                        String value="kettle-v2-synthetic-"+i;records.add(value);
                        producer.send(new ProducerRecord<>(prefix+"-input",0,"key-"+i,value)).get(10,TimeUnit.SECONDS);
                    }
                }
                result=Map.of("prefix",prefix,"inputTopic",prefix+"-input","outputTopic",prefix+"-output","records",records);break;
            }
            case "read": {
                String topic=args[1];named(topic);int count=Integer.parseInt(args[2]);
                Properties p=common();p.put("key.deserializer",StringDeserializer.class.getName());p.put("value.deserializer",StringDeserializer.class.getName());p.put("enable.auto.commit","false");
                List<Map<String,Object>> records=new ArrayList<>();
                try(KafkaConsumer<String,String> consumer=new KafkaConsumer<>(p)) {
                    var partition=new TopicPartition(topic,0);consumer.assign(List.of(partition));consumer.seekToBeginning(List.of(partition));
                    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
                    while(records.size()<count && System.nanoTime()<end) for(var row:consumer.poll(Duration.ofMillis(300))) {
                        Map<String,Object> value=new LinkedHashMap<>();value.put("offset",row.offset());value.put("key",row.key());value.put("value",row.value());records.add(value);
                    }
                    result=Map.of("records",records,"endOffset",consumer.endOffsets(List.of(partition)).get(partition));
                } break;
            }
            case "audit":result=audit(args[1],args[2]);break;
            default:throw new IllegalArgumentException("Unknown probe action");
        }
        System.out.println(JSON.writeValueAsString(result));
    }
}
