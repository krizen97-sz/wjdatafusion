import com.alibaba.fastjson.JSON;
import com.hikvision.idatafusion.detl.model.*;
import com.hikvision.idatafusion.detl.dto.task.*;
import com.hikvision.idatafusion.detl.dto.external.api.flow.ModuleDto;
import com.hikvision.idatafusion.detl.service.task.input.DataGeneratorInputServiceImpl;
import com.hikvision.idatafusion.detl.service.task.output.FileOutputServiceImpl;
import com.hikvision.idatafusion.detl.service.task.impl.ZookeeperServiceImpl;
import com.hikvision.idatafusion.detl.service.task.preproc.AdapterServiceFactory;
import com.hikvision.idatafusion.detl.utils.ZkUtil;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
/** Synthetic acceptance fixture only, not a general production compiler.
 * Explicit in-memory metadata implements original DAO contracts; vendor generators are unchanged. */
public class OriginalFlowCompiler {
 static final String P="com.hikvision.idatafusion.detl.";
 static final Map<String,byte[]> captured=new LinkedHashMap<>();
 static Map<String,Object> m(Object... xs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<xs.length;i+=2)m.put((String)xs[i],xs[i+1]);return m;}
 static <T>T bean(Class<T> c,Object... xs){return JSON.parseObject(JSON.toJSONString(m(xs)),c);}
 static Object bean(String c,Object... xs)throws Exception{return bean(Class.forName(P+c),xs);}
 static void inject(Object target,String name,Object value)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass()){try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException e){}}throw new NoSuchFieldException(name);}
 static Object provider(String cls,Map<String,Function<Object[],Object>> calls)throws Exception {
  Class<?> type=Class.forName(P+cls);
  return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(proxy,method,args)->{
   if(method.getName().equals("toString"))return "Explicit synthetic metadata: "+cls;
   if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
   if(method.getName().equals("equals"))return proxy==args[0];
   Function<Object[],Object> fn=calls.get(method.getName());if(fn==null)throw new UnsupportedOperationException(cls+"."+method.getName());return fn.apply(args);
  });
 }
 public static class CaptureZk extends ZkUtil {
  public Boolean upsertNode(String path,byte[] value){captured.put(path,value.clone());return true;}
  public String deleteNode(String path){return path;}
 }
 public static void main(String[] args)throws Exception {
  Path output=Path.of(args[0]).toAbsolutePath();Files.createDirectories(output);
  String constant=args.length>1?args[1]:"原生执行验收";
  String outputType=args.length>2?args[2]:"csv";
  long flowId=900100L,groupId=900000L,streamId=900110L,instanceId=900001L;
  EtlFlow flow=bean(EtlFlow.class,"id",flowId,"groupId",groupId,"flowName","rynew_original_probe","flowDisplayName","原生三条验收","streamCombineType",0,"flowStatus",1,"flowIncrStatus",0,"enableFlag",1,"projectCode","rynew-local-probe");
  List<ModuleDto> modules=new ArrayList<>();
  modules.add(bean(ModuleDto.class,"moduleId",900201L,"flowId",flowId,"instanceId",instanceId,"moduleName","original-generator","adapterType","input","streamId",streamId,"stepId",900301L,"itemId",900401L,"nodeId",900501L,"adapterId",900601L,"adapterName","dataGeneratorInput","adapterVersion","2.0.0","instanceHost","127.0.0.1","runStatus",0));
  modules.add(bean(ModuleDto.class,"moduleId",900202L,"flowId",flowId,"instanceId",instanceId,"moduleName","original-file","adapterType","output","streamId",streamId,"stepId",900302L,"itemId",900402L,"nodeId",900502L,"adapterId",900602L,"adapterName","fileOutput","adapterVersion","2.0.0","instanceHost","127.0.0.1","runStatus",0));
  Map<String,Object> field=m("id",900701L,"fieldName","message","fieldType","string","fieldSize",64,"fieldPrecision",0,"fieldKeyFlag",0,"fieldOrder",1,"fieldDisplayName","message","fieldExpression",constant);
  StreamFieldVo inputField=JSON.parseObject(JSON.toJSONString(field),StreamFieldVo.class);
  Map<String,Object> generatorData=new LinkedHashMap<>(field);generatorData.put("ruleType","FIXED_VALUE");
  EtlAdapterData generator=bean(EtlAdapterData.class,"itemId",900401L,"adapterDataJson",JSON.toJSONString(List.of(generatorData)));
  Object outDto=bean("dto.task.node.FileOutputNodeConfigVo","sourceType","local","localDir",output.resolve("result").toString(),"absoluteDir",output.resolve("result").toString(),"sourcePathType",1,"fileType",outputType,"colSplitter",",","encoding","UTF-8","addHeaderLine","true","isAsyncGenerateText","false");
  EtlAdapterData outputData=bean(EtlAdapterData.class,"itemId",900402L,"adapterDataJson",JSON.toJSONString(outDto));
  NodeVo node1=bean(NodeVo.class,"id",900501L,"adapterName","dataGeneratorInput","adapterData",List.of(generatorData));
  NodeVo node2=bean(NodeVo.class,"id",900502L,"adapterName","fileOutput","adapterData",outDto);
  EtlFlowHistory history=bean(EtlFlowHistory.class,"flowJson",JSON.toJSONString(m("flowVo",flow,"nodes",m("900501",node1,"900502",node2),"links",List.of(m("source",900501L,"target",900502L,"connectStatus",0)))));
  Object adapterData=provider("service.task.AdapterDataService",Map.of("findAdapterDataByItemId",a->a[0].equals(900401L)?generator:outputData));
  Object params=provider("service.globalconfig.ParamManageService",Map.of("getParamConfigByNode",a->{
   if(a[0].equals(8)&&a[1].equals(900401L))return new ArrayList<>(List.of(new EtlParam("etl.plugin.input.generator.data.size","3")));
   if(a[0].equals(8)&&a[1].equals(900402L))return new ArrayList<>(List.of(new EtlParam("etl.file.maxRow","10"),new EtlParam("etl.file.flushTime","1000")));
   return new ArrayList<>();
  }));
  DataGeneratorInputServiceImpl input=new DataGeneratorInputServiceImpl();
  inject(input,"adapterDataService",adapterData);inject(input,"paramService",params);
  inject(input,"streamFieldService",provider("service.task.StreamFieldService",Map.of("findStreamField",a->List.of(inputField))));
  FileOutputServiceImpl file=new FileOutputServiceImpl();inject(file,"adapterDataService",adapterData);inject(file,"paramService",params);
  EtlModule outModule=bean(EtlModule.class,"id",900202L,"itemId",900402L,"visualNodeId",900502L,"flowId",flowId,"moduleInstanceId",instanceId,"moduleAdapterId",900602L,"enableFlag",1);
  Object moduleDao=provider("mapper.EtlModuleDao",Map.of("selectByPrimaryKey",a->outModule,"listFlowModules",a->modules));inject(file,"moduleDao",moduleDao);
  inject(file,"dstTableService",provider("service.task.DstTableService",Map.of("findDstTableByItemId",a->null)));
  DstTableFieldVo outField=bean(DstTableFieldVo.class,"id",900702L,"streamId",streamId,"nodeId",900502L,"fieldName","message","fieldDisplayName","message","fieldType","string","fieldSize",64,"fieldPrecision",0,"fieldOrder",1,"fieldKeyFlag",0,"fieldSrcType",0,"fieldExpression","message","enableFlag",1,"used",true);
  inject(file,"tableFieldDao",provider("mapper.EtlDstTableFieldDao",Map.of("getByNodeId",a->List.of(outField),"getByTableId",a->List.of(outField))));
  AdapterServiceFactory factory=new AdapterServiceFactory();inject(factory,"adapterServiceList",List.of(input,file));
  ZookeeperServiceImpl service=new ZookeeperServiceImpl();
  inject(service,"etlFlowMapper",provider("mapper.EtlFlowMapper",Map.of("selectByPrimaryKey",a->flow)));
  inject(service,"etlScheduleDao",provider("mapper.EtlScheduleDao",Map.of("getByFlowId",a->bean(EtlSchedule.class,"id",900801L,"nodeId",flowId,"flowIncrStatus",1,"execType",1,"enableFlag",1))));
  inject(service,"moduleDao",moduleDao);inject(service,"etlFlowHistoryDao",provider("mapper.EtlFlowHistoryDao",Map.of("selectByFlowId",a->history)));
  inject(service,"adapterMirrorService",provider("service.globalconfig.AdapterMirrorService",Map.of("findBasicMirrorAdapter",a->{
   boolean first=a[0].equals(900601L);
   return bean(EtlAdapter.class,"id",a[0],"groupId",groupId,"adapterName",first?"dataGeneratorInput":"fileOutput","adapterDisplayName",first?"数据生成":"File输出","adapterType",first?"input":"output","languageType",com.hikvision.idatafusion.detl.common.LanguageType.JAVA.getType(),"dbTypeId","","adapterClassName",first?"com.hikvision.idatafusion.adapter.input.data.DataGeneratorInput":"com.hikvision.idatafusion.etl.adapter.output.file.FileOutput","enableFlag",1);
  })));
  inject(service,"codeDao",provider("mapper.SysCodeDao",Map.of("getByParentAndCode",a->null)));
  inject(service,"groupAdapterManageService",provider("service.globalconfig.GroupAdapterManageService",Map.of("getAdapterSource",a->0)));
  inject(service,"etlAdapterVersionDao",provider("mapper.EtlAdapterVersionDao",Map.of("getAllByAdapterIds",a->List.of())));
  inject(service,"workspaceService",provider("service.workspace.WorkspaceService",Map.of("isDfp",a->false)));
  inject(service,"adapterServiceFactory",factory);inject(service,"paramManageService",params);inject(service,"zKutil",new CaptureZk());
  inject(service,"flowService",provider("service.task.FlowService",Map.of("updatePushStatus",a->null)));
  service.forcePushFlowDataToZk(flowId);
  EtlSchedule schedule=bean(EtlSchedule.class,"id",900801L,"nodeId",flowId,"flowIncrStatus",1,"execType",1,"enableFlag",1);
  flow.setFlowIncrStatus(1);
  Object startMessage=com.hikvision.idatafusion.detl.dto.task.control.FlowControlMessageDto.builder().flowId(flowId).type(1).scheduleOnce(schedule).flow(flow).offset(List.of()).execId("original-proof-"+System.currentTimeMillis()).build();
  Files.writeString(output.resolve("original-schedule.json"),com.hikvision.idatafusion.detl.utils.ObjectUtils.toUnderlineJsonString(startMessage));
  for(var entry:captured.entrySet())Files.write(output.resolve("original-flowjson.json"),entry.getValue());
  Files.writeString(output.resolve("original-visual.json"),history.getFlowJson());
  Files.writeString(output.resolve("generation-proof.json"),JSON.toJSONString(m("generator","ZookeeperServiceImpl.forcePushFlowDataToZk","capturedPaths",captured.keySet(),"vendorConfigGenerators",List.of(input.getClass().getName(),file.getClass().getName()),"businessDataSource",false)));
  System.out.println("GENERATED "+captured.keySet());
 }
}
