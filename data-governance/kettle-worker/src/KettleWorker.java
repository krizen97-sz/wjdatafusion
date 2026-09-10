import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.pentaho.di.core.plugins.*;
import org.pentaho.di.core.compress.*;
import org.pentaho.di.core.row.*;
import org.pentaho.di.core.row.value.*;
import org.pentaho.di.core.logging.*;
import org.pentaho.di.core.extension.*;
import org.pentaho.di.core.database.DatabaseInterface;
import org.pentaho.di.core.encryption.*;
import org.pentaho.di.job.entry.JobEntryInterface;
import org.pentaho.di.trans.*;
import org.pentaho.di.trans.step.*;

/** One sandboxed process per operation. All execution is performed by original Kettle steps. */
public final class KettleWorker {
  static final PrintStream wire = System.out;
  static long sequence;
  static final List<Map<String,Object>> catalog = new ArrayList<>();
  static final List<Map<String,Object>> jobCatalog = new ArrayList<>();
  static final AtomicBoolean requestedStop = new AtomicBoolean(), previewLimit = new AtomicBoolean();
  static Path root;
  static final List<String> secrets = new ArrayList<>();
  static boolean startupStopRequested(){try{String nonce=System.getProperty("governance.worker.launch.id","");String command=Files.readString(root.resolve("control.stop")).trim();return !nonce.isEmpty()&&(command.equals("STOP:"+nonce)||command.equals("HALT:"+nonce));}catch(IOException e){return false;}}
  static void installControlBridge() throws IOException {
    InputStream original=System.in;PipedOutputStream control=new PipedOutputStream();PipedInputStream commands=new PipedInputStream(control);System.setIn(commands);
    java.util.function.Consumer<String> forward=line->{try{if("HALT".equals(line)){Runtime.getRuntime().halt(143);return;}synchronized(control){control.write((line+"\n").getBytes(StandardCharsets.UTF_8));control.flush();}}catch(IOException ignored){}};
    Thread input=new Thread(()->{try{BufferedReader reader=new BufferedReader(new InputStreamReader(original,StandardCharsets.UTF_8));for(String line;(line=reader.readLine())!=null;)forward.accept(line);}catch(IOException ignored){}finally{forward.accept("STOP");}},"worker-owned-control");input.setDaemon(true);input.start();
    String nonce=System.getProperty("governance.worker.launch.id","");Thread file=new Thread(()->{String seen="";while(true){try{Path stop=root.resolve("control.stop");if(!nonce.isEmpty()&&Files.isRegularFile(stop)){String command=Files.readString(stop).trim();if(!command.equals(seen)&&command.endsWith(":"+nonce)){seen=command;forward.accept(command.substring(0,command.indexOf(':')));}}Thread.sleep(100);}catch(Exception ignored){}}},"worker-owned-stop-file");file.setDaemon(true);file.start();
  }
  static void guardKafkaPreview(Trans trans) throws Exception {
    for(StepMetaDataCombi step:trans.getSteps())if("KafkaConsumer".equals(step.stepMeta.getStepID())){
      Properties config=previewKafkaProperties(step.meta);String group=config.getProperty("group.id","");
      java.lang.reflect.Field field=step.data.getClass().getDeclaredField("consumer");field.setAccessible(true);Object original=field.get(step.data);if(original==null)throw new IllegalStateException("Original Kafka preview consumer was not initialized");Class<?> contract=field.getType();
      Object guarded=java.lang.reflect.Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{if(method.getName().equals("commitOffsets")){event("preview-offset-commit-blocked",Map.of("node",step.stepname,"group",group,"originalMethod",method.getName()));return null;}try{return method.invoke(original,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});field.set(step.data,guarded);
      event("preview-offset-policy",Map.of("node",step.stepname,"group",group,"autoCommit",false,"explicitCommit","blocked"));
    }
  }
  static Properties previewKafkaProperties(StepMetaInterface meta)throws Exception {java.lang.reflect.Method properties=meta.getClass().getDeclaredMethod("getKafkaProperties");properties.setAccessible(true);Properties config=(Properties)properties.invoke(meta);String group=config.getProperty("group.id","");if(!group.matches("kettle-v2-[a-f0-9]{32}(?:-stop)?-preview")||!"false".equalsIgnoreCase(config.getProperty("auto.commit.enable","true")))throw new IllegalArgumentException("Kafka preview requires an independent kettle-v2-<uuid>-preview group and auto.commit.enable=false");return config;}
  static String safe(String message){for(String secret:secrets)if(!secret.isEmpty())message=message.replace(secret,"[redacted]");return message;}
  static synchronized void event(String type, Map<String,Object> fields) {
    Map<String,Object> result=new LinkedHashMap<>();result.put("seq",++sequence);result.put("type",type);result.put("time",System.currentTimeMillis());result.putAll(fields);
    wire.println(json(result));wire.flush();
  }
  // Do not deserialize untrusted JSON with the legacy Fastjson bundled in the original archive.
  static String json(Object value) {
    if(value==null)return "null";
    if(value instanceof Number || value instanceof Boolean)return value.toString();
    if(value instanceof Map){List<String> entries=new ArrayList<>();((Map<?,?>)value).forEach((k,v)->entries.add(json(k.toString())+":"+json(v)));return "{"+String.join(",",entries)+"}";}
    if(value instanceof Iterable){List<String> entries=new ArrayList<>();for(Object v:(Iterable<?>)value)entries.add(json(v));return "["+String.join(",",entries)+"]";}
    StringBuilder out=new StringBuilder("\"");for(char c:value.toString().toCharArray()){switch(c){case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;default:if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}return out.append('"').toString();
  }
  static Document xml(InputStream input)throws Exception {
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);return f.newDocumentBuilder().parse(input);
  }
  static String child(Element e,String name){NodeList n=e.getElementsByTagName(name);return n.getLength()==0?"":n.item(0).getTextContent();}
  static void register(Class<? extends PluginTypeInterface> type,Class<?> main,String id,String name,String category,String implementation)throws Exception {
    PluginRegistry registry=PluginRegistry.getInstance();registry.registerPluginType(type);registry.registerPlugin(type,new Plugin(id.split(","),type,main,category,name,name,"",true,false,Map.of(main,implementation),List.of(),"",null));
  }
  static String source(Class<?> type){return Paths.get(type.getProtectionDomain().getCodeSource().getLocation().getPath()).getFileName().toString();}
  static void init()throws Exception {
    KettleLogStore.init(500,0,false,false);
    register(CompressionPluginType.class,CompressionProvider.class,"None","None","Compression",NoneCompressionProvider.class.getName());
    String[] names={"Number","String","Date","Boolean","Integer","BigNumber","Serializable","Binary","Timestamp","InternetAddress"};
    for(int i=0;i<names.length;i++)register(ValueMetaPluginType.class,ValueMetaInterface.class,""+(i+1),names[i],"Value","org.pentaho.di.core.row.value.ValueMeta"+names[i]);
    PluginRegistry.getInstance().registerPluginType(ExtensionPointPluginType.class);
    PluginRegistry.getInstance().registerPluginType(PartitionerPluginType.class);
    PluginRegistry.getInstance().registerPluginType(DatabasePluginType.class);
    register(TwoWayPasswordEncoderPluginType.class,TwoWayPasswordEncoderInterface.class,"Kettle","Kettle","Encryption",KettleTwoWayPasswordEncoder.class.getName());
    Encr.init("Kettle");
    try(InputStream input=Thread.currentThread().getContextClassLoader().getResourceAsStream("kettle-database-types.xml")){Document db=xml(input);NodeList types=db.getElementsByTagName("database-type");for(int i=0;i<types.getLength();i++){Element e=(Element)types.item(i);register(DatabasePluginType.class,DatabaseInterface.class,e.getAttribute("id"),child(e,"description"),"Database",child(e,"classname"));}}
    Set<String> seen=new HashSet<>();Enumeration<java.net.URL> descriptors=Thread.currentThread().getContextClassLoader().getResources("kettle-steps.xml");
    while(descriptors.hasMoreElements()) {java.net.URL descriptor=descriptors.nextElement();Document doc;try(InputStream input=descriptor.openStream()){doc=xml(input);}NodeList nodes=doc.getElementsByTagName("step");
      for(int i=0;i<nodes.getLength();i++){Element e=(Element)nodes.item(i);String id=e.getAttribute("id");if(!seen.add(id))continue;String impl=child(e,"classname"),name=child(e,"description"),category=child(e,"category");register(StepPluginType.class,StepMetaInterface.class,id,name,category,impl);
        Map<String,Object> capability=new LinkedHashMap<>();capability.put("id",id.split(",")[0]);capability.put("aliases",Arrays.asList(id.split(",")));capability.put("name",name);capability.put("category",category.substring(category.lastIndexOf('.')+1));capability.put("className",impl);capability.put("descriptor",descriptor.toString().replaceAll(".*!/",""));catalog.add(capability);
      }
    }
    // Delay and DataGrid are annotation plugins in some distributions, preserving original classes.
    for(String[] plugin:new String[][]{{"DataGrid","datagrid.DataGridMeta","Input"},{"Delay","delay.DelayMeta","Flow"}})if(seen.add(plugin[0])){String impl="org.pentaho.di.trans.steps."+plugin[1];register(StepPluginType.class,StepMetaInterface.class,plugin[0],plugin[0],plugin[2],impl);catalog.add(new LinkedHashMap<>(Map.of("id",plugin[0],"name",plugin[0],"category",plugin[2],"className",impl)));}
    Set<String> seenJobs=new HashSet<>();Enumeration<java.net.URL> jobDescriptors=Thread.currentThread().getContextClassLoader().getResources("kettle-job-entries.xml");while(jobDescriptors.hasMoreElements()){java.net.URL descriptor=jobDescriptors.nextElement();Document doc;try(InputStream input=descriptor.openStream()){doc=xml(input);}NodeList nodes=doc.getElementsByTagName("job-entry");for(int i=0;i<nodes.getLength();i++){Element element=(Element)nodes.item(i);String id=element.getAttribute("id");if(!seenJobs.add(id))continue;String impl=child(element,"classname"),name=child(element,"description"),category=child(element,"category");register(JobEntryPluginType.class,JobEntryInterface.class,id,name,category,impl);Map<String,Object> entry=new LinkedHashMap<>();entry.put("id",id.split(",")[0]);entry.put("aliases",Arrays.asList(id.split(",")));entry.put("name",name);entry.put("category",category.substring(category.lastIndexOf('.')+1));entry.put("className",impl);jobCatalog.add(entry);}}
    Set<String> inspected=new HashSet<>();for(String file:System.getProperty("java.class.path").split(File.pathSeparator)){if(!file.endsWith(".jar"))continue;try(java.util.jar.JarFile jar=new java.util.jar.JarFile(file)){Enumeration<java.util.jar.JarEntry> entries=jar.entries();while(entries.hasMoreElements()){String path=entries.nextElement().getName();if(!path.endsWith(".class")||path.contains("$")||!path.contains("JobEntry"))continue;String name=path.substring(0,path.length()-6).replace('/','.');if(!inspected.add(name))continue;try{Class<?> type=Class.forName(name,false,Thread.currentThread().getContextClassLoader());org.pentaho.di.core.annotations.JobEntry annotation=type.getAnnotation(org.pentaho.di.core.annotations.JobEntry.class);if(annotation==null||!seenJobs.add(annotation.id()))continue;register(JobEntryPluginType.class,JobEntryInterface.class,annotation.id(),annotation.name(),annotation.categoryDescription(),name);Map<String,Object> entry=new LinkedHashMap<>();entry.put("id",annotation.id().split(",")[0]);entry.put("aliases",Arrays.asList(annotation.id().split(",")));entry.put("name",annotation.name());entry.put("category",annotation.categoryDescription());entry.put("className",name);jobCatalog.add(entry);}catch(LinkageError|ClassNotFoundException ignored){}}}}
  }
  static void discoverJobs(){for(Map<String,Object> entry:jobCatalog){try{Class<?> type=Class.forName((String)entry.get("className"));JobEntryInterface meta=(JobEntryInterface)type.getDeclaredConstructor().newInstance();meta.setName((String)entry.get("id"));meta.setPluginId((String)entry.get("id"));entry.put("loadable",true);entry.put("classSource",source(type));entry.put("defaultXml",meta.getXML());entry.put("executionSupported",Set.of("SPECIAL","TRANS","FTP_PUT").contains(entry.get("id")));}catch(Throwable e){entry.put("loadable",false);entry.put("loadError",e.getClass().getSimpleName());entry.put("executionSupported",false);}}}
  static Map<String,Object> fieldInfo(ValueMetaInterface field){Map<String,Object> info=new LinkedHashMap<>();info.put("name",field.getName());info.put("type",field.getTypeDesc());info.put("length",field.getLength());info.put("precision",field.getPrecision());info.put("origin",field.getOrigin());return info;}
  static TransMeta load()throws Exception {
    Document doc;try(InputStream input=Files.newInputStream(root.resolve("transformation.ktr"))){doc=xml(input);}if(!doc.getDocumentElement().getTagName().equals("transformation"))throw new IllegalArgumentException("Expected transformation XML");
    NodeList elements=doc.getElementsByTagName("*");for(int i=0;i<elements.getLength();i++){Element element=(Element)elements.item(i);if(element.getTagName().toLowerCase(Locale.ROOT).matches(".*(password|passwd|secret|token|username|accesskey).*")){String value=element.getTextContent();if(!value.isEmpty()){secrets.add(value);secrets.add(Encr.decryptPasswordOptionallyEncrypted(value));}}}
    TransMeta meta=new TransMeta(doc.getDocumentElement(),null);meta.setVariable("WORK_DIR",root.resolve("output").toString());meta.setCapturingStepPerformanceSnapShots(false);meta.setSizeRowset(100);
    if(meta.nrSteps()==0)throw new IllegalArgumentException("Transformation has no steps");
    Set<String> names=new HashSet<>();for(StepMeta step:meta.getSteps()){if(!names.add(step.getName()))throw new IllegalArgumentException("Duplicate step name");if(step.getStepMetaInterface()==null)throw new IllegalArgumentException("Missing step plugin: "+step.getStepID());if(meta.hasLoop(step))throw new IllegalArgumentException("Cyclic transformation");}
    return meta;
  }
  static List<Map<String,Object>> nodes(TransMeta meta){List<Map<String,Object>> out=new ArrayList<>();for(StepMeta step:meta.getSteps()){Map<String,Object> n=new LinkedHashMap<>();n.put("name",step.getName());n.put("pluginId",step.getStepID());n.put("className",step.getStepMetaInterface().getClass().getName());n.put("classSource",source(step.getStepMetaInterface().getClass()));try{RowMetaInterface row=meta.getStepFields(step);List<Map<String,Object>> fields=new ArrayList<>();for(ValueMetaInterface field:row.getValueMetaList())fields.add(fieldInfo(field));n.put("fields",fields);}catch(Exception e){n.put("fieldError",e.getClass().getSimpleName());}out.add(n);}return out;}
  static void previewGraph(TransMeta meta,String target){StepMeta selected=meta.findStep(target);if(selected==null)throw new IllegalArgumentException("Preview step does not exist");Set<StepMeta> keep=new HashSet<>();Deque<StepMeta> todo=new ArrayDeque<>();todo.add(selected);while(!todo.isEmpty()){StepMeta step=todo.removeFirst();if(keep.add(step))todo.addAll(meta.findPreviousSteps(step));}for(int i=meta.nrTransHops()-1;i>=0;i--){TransHopMeta hop=meta.getTransHop(i);if(!keep.contains(hop.getFromStep())||!keep.contains(hop.getToStep()))meta.removeTransHop(i);}for(int i=meta.nrSteps()-1;i>=0;i--)if(!keep.contains(meta.getStep(i)))meta.removeStep(i);}
  static List<Map<String,Object>> metrics(Trans trans){List<Map<String,Object>> result=new ArrayList<>();for(StepMetaDataCombi c:trans.getSteps()){StepInterface s=c.step;Map<String,Object> m=new LinkedHashMap<>();m.put("node",c.stepname);m.put("copy",c.copy);m.put("status",s.getStatus().toString());m.put("read",s.getLinesRead());m.put("written",s.getLinesWritten());m.put("input",s.getLinesInput());m.put("output",s.getLinesOutput());m.put("rejected",s.getLinesRejected());m.put("errors",s.getErrors());result.add(m);}return result;}
  static void execute(TransMeta meta,String target,int limit)throws Exception {
    if(startupStopRequested()){requestedStop.set(true);event("terminal",Map.of("state","STOPPED","errors",0,"nodes",List.of(),"started",false));return;}
    if(!target.isEmpty()){previewGraph(meta,target);for(StepMeta step:meta.getSteps())if("KafkaConsumer".equals(step.getStepID()))previewKafkaProperties(step.getStepMetaInterface());}Trans trans=new Trans(meta);trans.setLogLevel(LogLevel.ERROR);AtomicInteger logCount=new AtomicInteger();KettleLogStore.getAppender().addLoggingEventListener(logEvent->{if(logCount.incrementAndGet()<=500)event("log",Map.of("level",String.valueOf(logEvent.getLevel()),"message",safe(String.valueOf(logEvent.getMessage()))));});event("state",Map.of("state","PREPARING","nodes",nodes(meta)));
    Thread controller=new Thread(()->{try{BufferedReader in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));for(String line;(line=in.readLine())!=null;)if(line.equals("STOP")){requestedStop.set(true);trans.stopAll();event("state",Map.of("state","STOPPING"));}}catch(Exception ignored){}});controller.setDaemon(true);controller.start();
    if(startupStopRequested()||requestedStop.get()){requestedStop.set(true);event("terminal",Map.of("state","STOPPED","errors",0,"nodes",List.of(),"started",false));return;}
    trans.prepareExecution(null);
    if(trans.getSteps().isEmpty())throw new IllegalArgumentException("Transformation has no executable drawn steps");
    if(!target.isEmpty())guardKafkaPreview(trans);
    for(StepMetaDataCombi c:trans.getSteps()){Map<String,AtomicInteger> counts=new HashMap<>();counts.put("read",new AtomicInteger());counts.put("written",new AtomicInteger());counts.put("error",new AtomicInteger());c.step.addRowListener(new RowAdapter(){
      void row(String direction,RowMetaInterface rowMeta,Object[] values){int count=counts.get(direction).incrementAndGet();if(count<=limit){Map<String,Object> fields=new LinkedHashMap<>();for(int i=0;i<rowMeta.size();i++){Object value=values[i];try{value=value==null?null:rowMeta.getValueMeta(i).getString(value);}catch(Exception ignored){value="[unavailable]";}String text=value==null?null:value.toString();fields.put(rowMeta.getValueMeta(i).getName(),text!=null&&text.length()>4096?text.substring(0,4096):text);}Map<String,Object> data=new LinkedHashMap<>();data.put("node",c.stepname);data.put("copy",c.copy);data.put("direction",direction);data.put("rowNumber",count);data.put("fields",fields);List<Map<String,Object>> types=new ArrayList<>();for(ValueMetaInterface field:rowMeta.getValueMetaList())types.add(fieldInfo(field));data.put("fieldsMeta",types);event("row",data);}if(!target.isEmpty()&&c.stepname.equals(target)&&direction.equals("written")&&count>=limit&&previewLimit.compareAndSet(false,true)){Thread stop=new Thread(trans::stopAll);stop.setDaemon(true);stop.start();}}
      @Override public void rowReadEvent(RowMetaInterface m,Object[] r){row("read",m,r);}@Override public void rowWrittenEvent(RowMetaInterface m,Object[] r){row("written",m,r);}@Override public void errorRowWrittenEvent(RowMetaInterface m,Object[] r){row("error",m,r);}
    });}
    event("state",Map.of("state","RUNNING"));trans.startThreads();while(!trans.isFinished()){event("metrics",Map.of("nodes",metrics(trans),"errors",trans.getErrors()));Thread.sleep(100);}trans.waitUntilFinished();
    String state=trans.getErrors()>0?"FAILED":requestedStop.get()?"STOPPED":previewLimit.get()?"PREVIEW_COMPLETE":"SUCCEEDED";
    event("terminal",Map.of("state",state,"errors",trans.getErrors(),"resultBoolean",trans.getResult().getResult(),"nodes",metrics(trans),"previewTruncated",previewLimit.get()));
  }
  public static void main(String[] args) {
    System.setOut(System.err);int exit=0;
    try{root=Paths.get(args[0]);String operation=args[1];installControlBridge();init();
      if((operation.equals("run")||operation.equals("job"))&&startupStopRequested()){event("terminal",Map.of("state","STOPPED","errors",0,"nodes",List.of(),"started",false));}
      else if(operation.equals("probe")){Map<String,Object> proof=new LinkedHashMap<>();Path outside=Paths.get(args[2]);try{Files.readString(outside.resolve("synthetic-private.txt"));proof.put("readBlocked",false);}catch(java.nio.file.FileSystemException e){proof.put("readBlocked",String.valueOf(e.getReason()).contains("Operation not permitted"));}try{Files.writeString(outside.resolve("blocked"),"synthetic");proof.put("writeBlocked",false);}catch(java.nio.file.FileSystemException e){proof.put("writeBlocked",String.valueOf(e.getReason()).contains("Operation not permitted"));}try{new java.net.Socket("127.0.0.1",9).close();proof.put("networkBlocked",false);}catch(java.net.SocketException e){proof.put("networkBlocked",String.valueOf(e.getMessage()).contains("Operation not permitted"));}proof.put("securityManagerInstalled",System.getSecurityManager()!=null);event("sandbox-proof",proof);}
      else if(operation.equals("job")||operation.equals("job-validate")){Class.forName("NativeJobExecutor").getMethod(operation.equals("job")?"run":"validate",Path.class).invoke(null,root);}
      else if(operation.equals("capabilities")){for(Map<String,Object> entry:catalog){try{Class<?> type=Class.forName((String)entry.get("className"));StepMetaInterface meta=(StepMetaInterface)type.getDeclaredConstructor().newInstance();meta.setDefault();entry.put("loadable",true);entry.put("classSource",source(type));entry.put("defaultXml",meta.getXML());}catch(Throwable e){entry.put("loadable",false);entry.put("loadError",e.getClass().getSimpleName());}}discoverJobs();event("capabilities",Map.of("engine","Kettle 6.1 original archive","steps",catalog,"jobs",jobCatalog,"networkPolicy","deny-all","filesystemPolicy","private-operation-directory"));}
      else {TransMeta meta=load();if(operation.equals("validate")){event("validation",Map.of("valid",true,"name",meta.getName(),"nodes",nodes(meta)));}else if(operation.equals("run")){execute(meta,args.length>2?args[2]:"",args.length>3?Integer.parseInt(args[3]):20);}else throw new IllegalArgumentException("Unknown operation");}
    }catch(Throwable e){exit=1;event("terminal",Map.of("state","FAILED","errors",1,"errorClass",e.getClass().getName(),"message",safe(String.valueOf(e.getMessage()))));e.printStackTrace(System.err);}
    finally{try{((org.apache.commons.vfs2.impl.DefaultFileSystemManager)org.pentaho.di.core.vfs.KettleVFS.getInstance().getFileSystemManager()).close();}catch(Throwable ignored){}}
    System.exit(exit);
  }
}
