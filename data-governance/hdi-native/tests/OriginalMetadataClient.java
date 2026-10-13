import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.curator.framework.*;
import org.apache.curator.retry.ExponentialBackoffRetry;
import com.alibaba.fastjson.JSON;
import com.hikvision.idatafusion.etl.core.task.flow.InstanceInfo;
import com.hikvision.idatafusion.detl.utils.ObjectUtils;
/** Isolated fixture control adapter; payload graph itself is from OriginalFlowCompiler. */
public class OriginalMetadataClient {
 static void put(CuratorFramework c,String p,byte[] d)throws Exception { if(c.checkExists().forPath(p)==null)c.create().creatingParentsIfNeeded().forPath(p,d);else c.setData().forPath(p,d); }
 static byte[] bytes(Object value){return JSON.toJSONBytes(value);}
 public static void main(String[] a)throws Exception {
  Path root=Path.of(a[1]).toAbsolutePath();
  int portBase=Integer.parseInt(a[2]); String namespace=a[3];
  try(CuratorFramework client=CuratorFrameworkFactory.builder().connectString("127.0.0.1:"+(portBase+1)).namespace(namespace).retryPolicy(new ExponentialBackoffRetry(200,2)).build()){
   client.start();if(!client.blockUntilConnected(5,TimeUnit.SECONDS))throw new IllegalStateException("local ZK unavailable");
   if(a[0].equals("seed")){
    for(String old:List.of("/metadata/control/900100","/metadata/schedule/900100"))if(client.checkExists().forPath(old)!=null)client.delete().deletingChildrenIfNeeded().forPath(old);
    InstanceInfo i=new InstanceInfo();i.setId(900001L);i.setGroupId(900000);i.setInstanceName("rynew-local-probe");i.setInstanceDisplayName("原件本机验收");i.setHost("127.0.0.1");i.setPort(portBase+2);i.setRpcPort(portBase+3);i.setEnableFlag(1);i.setAdapterRootPath(root.resolve("instance").toString());i.setFelRootPath(root.resolve("fel").toString());i.setWorkDir(root.resolve("work").toString());
    Files.createDirectories(root.resolve("fel"));
    String json=ObjectUtils.toUnderlineJsonString(i);
    put(client,"/metadata/instance/900001",json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    put(client,"/metadata/group/900000",bytes(Map.of("id",900000)));
    put(client,"/metadata/group/900000/flowjson/900100",Files.readAllBytes(root.resolve("fixture/original-flowjson.json")));
    Files.writeString(root.resolve("fixture/original-instance.json"),json);
    System.out.println("SEEDED original compiler payload");
   }else if(a[0].equals("start")){
    put(client,"/metadata/schedule/900100",Files.readAllBytes(root.resolve("fixture/original-schedule.json")));
    System.out.println("ORIGINAL ONCE SCHEDULE SENT");
   }else if(a[0].equals("await")){
    String wanted=JSON.parseObject(Files.readString(root.resolve("fixture/original-schedule.json"))).getString("exec_id");
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
    while(System.nanoTime()<end){
     String p="/metadata/schedule/900100/exec";
     if(client.checkExists().forPath(p)!=null){
      String data=new String(client.getData().forPath(p),java.nio.charset.StandardCharsets.UTF_8);
      com.alibaba.fastjson.JSONObject status=JSON.parseObject(data);
      if(wanted.equals(status.getString("exec_id"))&&Set.of("finish","error","stop").contains(status.getString("status"))){
       Files.writeString(root.resolve("native-status.json"),data);return;
      }
     }
     Thread.sleep(200);
    }
    throw new IllegalStateException("Original execution did not reach a terminal state in 35 seconds");
   }else if(a[0].equals("status")){
    String p="/metadata/schedule/900100/exec";Files.writeString(root.resolve("native-status.json"),client.checkExists().forPath(p)==null?"{}":new String(client.getData().forPath(p),java.nio.charset.StandardCharsets.UTF_8));
   }else if(a[0].equals("read")){
    String p="/status/instance/900001/pid";System.out.println(p+" "+(client.checkExists().forPath(p)==null?"missing":new String(client.getData().forPath(p))));
   }
  }
 }
}
